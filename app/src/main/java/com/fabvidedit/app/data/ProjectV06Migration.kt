package com.fabvidedit.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** One-shot/idempotent repair run before FabVidEditViewModel loads persisted projects. */
object ProjectV06Migration {
    private const val PREFS = "fab_vid_edit_projects"
    private const val KEY_PROJECTS = "projects_v1"
    private const val TAG = "FabVidV09Migration"

    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROJECTS, null) ?: return
        runCatching {
            val projects = JSONArray(raw)
            var changed = false
            for (projectIndex in 0 until projects.length()) {
                val project = projects.optJSONObject(projectIndex) ?: continue
                if (project.optInt("projectFormatVersion", 0) >= 9) continue
                if (repairProject(project)) changed = true
                project.put("projectFormatVersion", 9)
                changed = true
            }
            if (changed) {
                prefs.edit().putString(KEY_PROJECTS, projects.toString()).apply()
                Log.i(TAG, "v0.9 repaired legacy/v0.8 independent streams into overlay tracks")
            }
        }.onFailure { error ->
            Log.w(TAG, "v0.9 project migration skipped", error)
        }
    }

    private fun repairProject(project: JSONObject): Boolean {
        val clips = project.optJSONArray("clips") ?: return false
        if (clips.length() < 2) return false

        var nextTrack = 0
        for (i in 0 until clips.length()) {
            nextTrack = maxOf(nextTrack, clips.getJSONObject(i).optInt("timelineTrackIndex", 0) + 1)
        }

        val byContainer = linkedMapOf<String, MutableList<Int>>()
        for (i in 0 until clips.length()) {
            val clip = clips.getJSONObject(i)
            val container = clip.optString("containerUri").trim()
            if (container.isNotEmpty()) byContainer.getOrPut(container) { mutableListOf() } += i
        }

        var changed = false
        byContainer.values.forEach { indices ->
            val byStream = linkedMapOf<String, MutableList<Int>>()
            indices.forEach { index ->
                val clip = clips.getJSONObject(index)
                val key = if (clip.has("sourceStreamIndex") && !clip.isNull("sourceStreamIndex")) {
                    "stream:${clip.optInt("sourceStreamIndex")}"
                } else {
                    "source:${clip.optString("sourceId", clip.optString("id"))}"
                }
                byStream.getOrPut(key) { mutableListOf() } += index
            }
            if (byStream.size < 2) return@forEach

            val collision = indices
                .groupBy { clips.getJSONObject(it).optInt("timelineTrackIndex", 0) }
                .values
                .any { sameTrack ->
                    sameTrack.map { index ->
                        val clip = clips.getJSONObject(index)
                        if (clip.has("sourceStreamIndex") && !clip.isNull("sourceStreamIndex")) {
                            "stream:${clip.optInt("sourceStreamIndex")}"
                        } else {
                            "source:${clip.optString("sourceId", clip.optString("id"))}"
                        }
                    }.distinct().size > 1
                }

            if (!collision && project.optString("timelineMode") == "MULTITRACK") return@forEach

            val used = mutableSetOf<Int>()
            val streams = byStream.entries.sortedWith(
                compareBy<Map.Entry<String, MutableList<Int>>> { entry ->
                    val first = clips.getJSONObject(entry.value.first())
                    if (first.has("sourceStreamIndex") && !first.isNull("sourceStreamIndex")) {
                        first.optInt("sourceStreamIndex")
                    } else Int.MAX_VALUE
                }.thenBy { it.key },
            )

            streams.forEach { (_, streamIndices) ->
                val preferred = streamIndices
                    .map { clips.getJSONObject(it).optInt("timelineTrackIndex", 0) }
                    .firstOrNull { it !in used }
                val track = preferred ?: nextTrack++
                used += track
                val baseStart = streamIndices.minOf { clips.getJSONObject(it).optLong("timelineStartMs", 0L) }

                streamIndices.forEach { index ->
                    val clip = clips.getJSONObject(index)
                    val relativeStart = (clip.optLong("timelineStartMs", 0L) - baseStart).coerceAtLeast(0L)
                    if (clip.optInt("timelineTrackIndex", 0) != track || clip.optLong("timelineStartMs", 0L) != relativeStart) {
                        clip.put("timelineTrackIndex", track)
                        clip.put("timelineStartMs", relativeStart)
                        changed = true
                    }
                }
            }
            if (project.optString("timelineMode") != "MULTITRACK") {
                project.put("timelineMode", "MULTITRACK")
                changed = true
            }
        }
        return changed
    }
}
