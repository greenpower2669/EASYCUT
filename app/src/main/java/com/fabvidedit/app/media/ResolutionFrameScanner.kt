package com.fabvidedit.app.media

import android.util.Log
import com.fabvidedit.app.FabVidDiagnostics
import java.util.Locale
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.MediaStreamInventory
import java.io.File
import kotlin.math.ceil

/** A time interval for an actual video stream, never a decoded image retained in RAM. */
data class ResolutionSegment(
    val streamIndex: Int,
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val width: Int,
    val height: Int,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)
}

/**
 * FABVID-LRG-002: ffprobe MUST write small textual frame metadata to a disposable file (-o).
 * FFprobeKit.session.output MUST NOT contain all frames of a long video. Read that file via
 * buffered line sequences, twice if needed, keeping only a resolution histogram and <=256
 * candidate segments. Video frames/bitmaps are never retained by this class.
 *
 * Inspect keyframes only: ordinary codec resolution changes have to be visible at an intra frame.
 * A damaged/nonseekable file or non-keyframe-only resize can require a separate diagnosis.
 * On scan failure, retain the original stream metadata rather than exhausting the phone's RAM.
 */
object ResolutionFrameScanner {
    private const val TAG = "FabVidResolution"
    private const val MIN_RESOLUTION_SHARE = 0.005
    private const val MIN_RESOLUTION_FRAMES = 2 // keyframes, NOT every decoded frame
    private const val MAX_DOMINANT_RESOLUTIONS = 4
    private const val MAX_HISTOGRAM_VARIANTS = 32
    private const val MIN_SWITCH_FRAMES = 2
    private const val MIN_SWITCH_MS = 160L
    private const val COARSE_SWITCH_FRAMES = 3
    private const val COARSE_SWITCH_MS = 700L
    private const val MAX_SEGMENTS_PER_STREAM = 24
    private const val MAX_UNCOALESCED_SEGMENTS = 256
    private const val MAX_SCAN_FILE_BYTES = 64L * 1024L * 1024L
    private const val LARGE_INPUT_BYTES = 256L * 1024L * 1024L

    fun scanInput(
        input: String,
        inventory: MediaStreamInventory,
        temporaryDirectory: File,
        sourceBytes: Long? = null,
    ): Map<Int, List<ResolutionSegment>> {
        val videos = inventory.videoStreams
        if (videos.isEmpty()) return emptyMap()
        require(temporaryDirectory.isDirectory) { "Dossier de travail du scan absent" }
        val metadata = File(temporaryDirectory, "resolution-frames-" + System.nanoTime() + ".txt")
        try {
            val args = mutableListOf(
                "-v", "error",
                "-skip_frame", "nokey",
                "-select_streams", "v",
                "-show_frames",
                "-show_entries", "frame=stream_index,best_effort_timestamp_time,width,height",
                "-of", "compact=p=0:nk=0",
                "-o", metadata.absolutePath,
            )
            // A one-gigabyte video is sampled in separate windows rather than native
            // decoding all its GOPs just to check dimensions. For rare dynamic-resolution
            // files, boundaries found between windows are approximate (see debughistorical).
            if ((sourceBytes ?: 0L) >= LARGE_INPUT_BYTES && inventory.durationMs >= 10_000L) {
                val totalSeconds = inventory.durationMs / 1000.0
                val maxStart = (totalSeconds - 2.0).coerceAtLeast(0.0)
                val intervals = (0..15).map { index ->
                    val second = maxStart * index / 15.0
                    String.format(Locale.US, "%.3f%%+2.0", second)
                }.distinct().joinToString(",")
                args += listOf("-read_intervals", intervals)
                FabVidDiagnostics.mark("SCAN_SAMPLED_KEYFRAMES")
            } else {
                FabVidDiagnostics.mark("SCAN_KEYFRAMES")
            }
            args += input
            val session = FfprobeNativeGate.run {
                FFprobeKit.executeWithArguments(args.toTypedArray())
            }
            val success = ReturnCode.isSuccess(session.returnCode)
            val diagnostic = session.output.orEmpty().takeLast(300)
            if (!success || !metadata.isFile || metadata.length() == 0L ||
                metadata.length() > MAX_SCAN_FILE_BYTES
            ) {
                Log.w(TAG, "Keyframe scan skipped/failed; keeping stream metadata: " + diagnostic)
                FabVidDiagnostics.logError("FFPROBE_KEYFRAME", IllegalStateException(
                    "code=" + session.returnCode + " bytes=" + metadata.length() + " " + diagnostic,
                ))
                return fallback(videos, inventory.durationMs)
            }

            // First streaming pass: histogram only. A corrupt video cannot grow it indefinitely.
            val counts = mutableMapOf<Int, MutableMap<ResolutionKey, Int>>()
            val frameCounts = mutableMapOf<Int, Long>()
            val invalid = mutableSetOf<Int>()
            metadata.forEachLine { line ->
                val frame = parseFrame(line) ?: return@forEachLine
                if (frame.streamIndex in invalid) return@forEachLine
                val group = counts.getOrPut(frame.streamIndex) { mutableMapOf() }
                val key = ResolutionKey(frame.width, frame.height)
                if (key !in group && group.size >= MAX_HISTOGRAM_VARIANTS) {
                    counts.remove(frame.streamIndex)
                    invalid += frame.streamIndex
                    return@forEachLine
                }
                group[key] = (group[key] ?: 0) + 1
                frameCounts[frame.streamIndex] = (frameCounts[frame.streamIndex] ?: 0L) + 1L
            }

            return videos.associate { stream ->
                val histogram = counts[stream.index].orEmpty()
                val ranked = histogram.entries.sortedByDescending { it.value }
                val threshold = maxOf(
                    MIN_RESOLUTION_FRAMES,
                    ceil((frameCounts[stream.index] ?: 0L) * MIN_RESOLUTION_SHARE).toInt(),
                )
                val accepted = ranked.filter { it.value >= threshold }
                    .take(MAX_DOMINANT_RESOLUTIONS)
                    .map { it.key }
                    .toSet()
                    .ifEmpty { ranked.firstOrNull()?.let { setOf(it.key) } ?: emptySet() }

                if (stream.index in invalid || accepted.isEmpty()) {
                    Log.w(TAG, "VIDEO " + stream.index + ": no safe keyframe scan; stream fallback")
                    stream.index to fallbackSegment(stream, inventory.durationMs)
                } else {
                    // Each subsequent pass has an iterator over the file, not a frames list.
                    var segments = buildStableSegments(
                        metadata, stream, accepted, inventory.durationMs,
                        MIN_SWITCH_FRAMES, MIN_SWITCH_MS,
                    )
                    if (segments != null && segments.size > MAX_SEGMENTS_PER_STREAM) {
                        segments = buildStableSegments(
                            metadata, stream, accepted, inventory.durationMs,
                            COARSE_SWITCH_FRAMES, COARSE_SWITCH_MS,
                        )
                    }
                    if (segments == null) {
                        Log.w(TAG, "VIDEO " + stream.index + ": too many changes; stream fallback")
                        segments = fallbackSegment(stream, inventory.durationMs)
                    } else if (segments.size > MAX_SEGMENTS_PER_STREAM) {
                        segments = coalesceToLimit(segments, MAX_SEGMENTS_PER_STREAM)
                    }
                    Log.i(TAG, "VIDEO " + stream.index + ": keyframes=" +
                        (frameCounts[stream.index] ?: 0L) + " segments=" + segments.size)
                    stream.index to segments
                }
            }
        } finally {
            if (metadata.exists() && !metadata.delete()) {
                Log.w(TAG, "Unable to remove temporary keyframe metadata: " + metadata.name)
            }
        }
    }

    private fun buildStableSegments(
        metadata: File,
        stream: MediaStreamInfo,
        accepted: Set<ResolutionKey>,
        containerDurationMs: Long,
        minimumSwitchFrames: Int,
        minimumSwitchMs: Long,
    ): List<ResolutionSegment>? = metadata.useLines { lines ->
        val frames = lines.mapNotNull(::parseFrame).filter { frame ->
            frame.streamIndex == stream.index &&
                ResolutionKey(frame.width, frame.height) in accepted
        }.iterator()
        if (!frames.hasNext()) return@useLines fallbackSegment(stream, containerDurationMs)
        val first = frames.next()
        val result = mutableListOf<ResolutionSegment>()
        var current = ResolutionKey(first.width, first.height)
        var startMs = stream.startTimeMs.coerceAtLeast(0L)
        var lastMs = first.timeMs
        var candidate: ResolutionKey? = null
        var candidateStartMs = 0L
        var candidateLastMs = 0L
        var candidateFrames = 0

        while (frames.hasNext()) {
            val frame = frames.next()
            lastMs = maxOf(lastMs, frame.timeMs)
            val next = ResolutionKey(frame.width, frame.height)
            if (next == current) {
                candidate = null
                candidateFrames = 0
                continue
            }
            if (candidate == next) {
                candidateFrames++
                candidateLastMs = frame.timeMs
            } else {
                candidate = next
                candidateStartMs = frame.timeMs
                candidateLastMs = frame.timeMs
                candidateFrames = 1
            }
            if (candidateFrames >= minimumSwitchFrames &&
                candidateLastMs - candidateStartMs >= minimumSwitchMs
            ) {
                val boundary = candidateStartMs.coerceAtLeast(startMs + 1L)
                result += ResolutionSegment(
                    stream.index, result.size, startMs, boundary, current.width, current.height,
                )
                if (result.size > MAX_UNCOALESCED_SEGMENTS) return@useLines null
                current = next
                startMs = boundary
                candidate = null
                candidateFrames = 0
            }
        }

        val endMs = maxOf(
            startMs + 1L,
            lastMs + (stream.frameRate?.takeIf { it > 0.1f }
                ?.let { (1000f / it).toLong().coerceAtLeast(1L) } ?: 40L),
            stream.startTimeMs + (stream.durationMs ?: containerDurationMs).coerceAtLeast(1L),
        )
        result += ResolutionSegment(
            stream.index, result.size, startMs, endMs, current.width, current.height,
        )
        result
    }

    /**
     * Last-resort protection. Merge the shortest regions first until extraction count is bounded.
     * Same-resolution A/B/A glitches are preferentially collapsed back into A.
     */
    private fun coalesceToLimit(
        input: List<ResolutionSegment>,
        limit: Int,
    ): List<ResolutionSegment> {
        val work = input.toMutableList()
        while (work.size > limit && work.size > 1) {
            val index = work.indices.minByOrNull { work[it].durationMs } ?: break
            val current = work[index]
            val previous = work.getOrNull(index - 1)
            val next = work.getOrNull(index + 1)

            when {
                previous != null && next != null &&
                    previous.width == next.width && previous.height == next.height -> {
                    work[index - 1] = previous.copy(endMs = next.endMs)
                    work.removeAt(index + 1)
                    work.removeAt(index)
                }
                previous == null && next != null -> {
                    work[1] = next.copy(startMs = current.startMs)
                    work.removeAt(0)
                }
                next == null && previous != null -> {
                    work[index - 1] = previous.copy(endMs = current.endMs)
                    work.removeAt(index)
                }
                previous != null && next != null && previous.durationMs >= next.durationMs -> {
                    work[index - 1] = previous.copy(endMs = current.endMs)
                    work.removeAt(index)
                }
                previous != null && next != null -> {
                    work[index + 1] = next.copy(startMs = current.startMs)
                    work.removeAt(index)
                }
            }
        }
        return work.mapIndexed { index, segment -> segment.copy(segmentIndex = index) }
    }

    private fun fallback(videos: List<MediaStreamInfo>, containerDurationMs: Long): Map<Int, List<ResolutionSegment>> =
        videos.associate { it.index to fallbackSegment(it, containerDurationMs) }

    private fun fallbackSegment(stream: MediaStreamInfo, containerDurationMs: Long): List<ResolutionSegment> {
        val start = stream.startTimeMs.coerceAtLeast(0L)
        val end = start + (stream.durationMs ?: containerDurationMs).coerceAtLeast(1L)
        return listOf(
            ResolutionSegment(
                streamIndex = stream.index,
                segmentIndex = 0,
                startMs = start,
                endMs = end,
                width = stream.width.coerceAtLeast(1),
                height = stream.height.coerceAtLeast(1),
            ),
        )
    }

    internal fun parseFrame(line: String): FrameResolution? {
        if (line.isBlank()) return null
        val values = line.split('|').mapNotNull { token ->
            val key = token.substringBefore('=', missingDelimiterValue = "").trim()
            if (key.isBlank()) null else key to token.substringAfter('=', missingDelimiterValue = "").trim()
        }.toMap()
        val streamIndex = values["stream_index"]?.toIntOrNull() ?: return null
        val width = values["width"]?.toIntOrNull()?.takeIf { it in 16..16_384 } ?: return null
        val height = values["height"]?.toIntOrNull()?.takeIf { it in 16..16_384 } ?: return null
        val timeMs = values["best_effort_timestamp_time"]
            ?.toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?: return null
        return FrameResolution(streamIndex, timeMs.coerceAtLeast(0L), width, height)
    }

    private data class ResolutionKey(
        val width: Int,
        val height: Int,
    )

    internal data class FrameResolution(
        val streamIndex: Int,
        val timeMs: Long,
        val width: Int,
        val height: Int,
    )
}
