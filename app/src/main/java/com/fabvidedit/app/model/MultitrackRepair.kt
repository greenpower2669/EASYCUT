package com.fabvidedit.app.model

/**
 * Repairs projects produced by early multistream POC builds where independent streams from the
 * same source container could be converted to one sequential track.
 *
 * The migration is deliberately narrow: it only touches a container when two distinct original
 * video streams collide on the same timeline track (or the project is still sequential). Split
 * segments from one stream keep their relative offsets and stay together on the same track.
 */
fun VideoProject.repairIndependentStreamLayout(): VideoProject {
    if (clips.size < 2) return this

    val indexed = clips.withIndex().toList()
    val repaired = clips.toMutableList()
    var nextTrack = (clips.maxOfOrNull(VideoClip::timelineTrackIndex) ?: -1) + 1
    var changed = false

    indexed
        .filter { it.value.containerUri?.isNotBlank() == true }
        .groupBy { it.value.containerUri.orEmpty() }
        .values
        .forEach { containerEntries ->
            val byStream = containerEntries.groupBy { entry ->
                entry.value.sourceStreamIndex?.let { "stream:$it" } ?: "source:${entry.value.sourceId}"
            }
            if (byStream.size < 2) return@forEach

            val collision = containerEntries
                .groupBy { it.value.timelineTrackIndex }
                .values
                .any { sameTrack ->
                    sameTrack.map { entry ->
                        entry.value.sourceStreamIndex?.let { "stream:$it" } ?: "source:${entry.value.sourceId}"
                    }.distinct().size > 1
                }

            if (!collision && timelineMode == TimelineMode.MULTITRACK) return@forEach

            val usedTracks = mutableSetOf<Int>()
            byStream.entries
                .sortedWith(
                    compareBy<Map.Entry<String, List<IndexedValue<VideoClip>>>> {
                        it.value.firstOrNull()?.value?.sourceStreamIndex ?: Int.MAX_VALUE
                    }.thenBy { it.key },
                )
                .forEach { (_, streamEntries) ->
                    val preferred = streamEntries
                        .map { it.value.timelineTrackIndex }
                        .firstOrNull { it !in usedTracks }
                    val targetTrack = preferred ?: nextTrack++
                    usedTracks += targetTrack

                    val baseStart = streamEntries.minOf { it.value.timelineStartMs }
                    streamEntries.forEach { entry ->
                        val old = repaired[entry.index]
                        val relativeStart = (old.timelineStartMs - baseStart).coerceAtLeast(0L)
                        if (old.timelineTrackIndex != targetTrack || old.timelineStartMs != relativeStart) {
                            repaired[entry.index] = old.copy(
                                timelineTrackIndex = targetTrack,
                                timelineStartMs = relativeStart,
                            )
                            changed = true
                        }
                    }
                }
        }

    if (!changed && timelineMode == TimelineMode.MULTITRACK) return this
    if (!changed) return this

    return copy(
        clips = repaired,
        timelineMode = TimelineMode.MULTITRACK,
    ).withValidTransitions()
}
