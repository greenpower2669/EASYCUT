package com.fabvidedit.app.media

import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.FabVidDiagnostics
import com.fabvidedit.app.model.AspectRatioInput
import com.fabvidedit.app.model.MediaStreamInventory
import java.util.Locale
import kotlin.math.abs

/** Short *sampled* keyframe probe. Does not re-enable FFprobeKit's broken -o writer.
 * All inputs are optional: unknown is NOT reported as constant SAR/DAR.
 */
internal object AspectTimelineSampler {
    private const val MAX_SOURCE_BYTES = 256L * 1024L * 1024L
    private const val MAX_RESULT_BYTES = 256 * 1024
    data class Result(val changingStreams: Set<Int>, val observedStreams: Set<Int>)

    fun sample(input: String, inventory: MediaStreamInventory, sourceBytes: Long?): Result {
        if (inventory.videoStreams.isEmpty() || sourceBytes == null ||
            sourceBytes <= 0 || sourceBytes > MAX_SOURCE_BYTES ||
            inventory.durationMs < 2_000L
        ) return Result(emptySet(), emptySet())

        val seconds = inventory.durationMs / 1000.0
        val windows = (0..7).map { index ->
            String.format(Locale.US, "%.3f%%+0.40", seconds * index / 8.0)
        }.joinToString(",")
        val args = arrayOf(
            "-v", "error", "-skip_frame", "nokey",
            "-select_streams", "v", "-read_intervals", windows,
            "-show_frames", "-show_entries",
            "frame=stream_index,sample_aspect_ratio,display_aspect_ratio",
            "-of", "compact=p=0:nk=0", input,
        )
        return runCatching {
            val session = FfprobeNativeGate.run {
                FFprobeKit.executeWithArguments(args)
            }
            val output = session.output.orEmpty()
            if (!ReturnCode.isSuccess(session.returnCode) ||
                output.toByteArray(Charsets.UTF_8).size > MAX_RESULT_BYTES) {
                FabVidDiagnostics.mark("SAR_SAMPLE_UNAVAILABLE")
                return@runCatching Result(emptySet(), emptySet())
            }
            val all = mutableMapOf<Int, MutableList<Pair<Float?, Float?>>>()
            output.lineSequence().take(4096).forEach { line ->
                if (!line.contains("stream_index=")) return@forEach
                val fields = line.split('|').mapNotNull { part ->
                    val pos = part.indexOf('=')
                    if (pos < 0) null else part.substring(0, pos).trim() to part.substring(pos+1).trim()
                }.toMap()
                val stream = fields["stream_index"]?.toIntOrNull() ?: return@forEach
                val sar = fields["sample_aspect_ratio"]?.let(AspectRatioInput::parse)
                val dar = fields["display_aspect_ratio"]?.let(AspectRatioInput::parse)
                if (sar != null || dar != null) all.getOrPut(stream) { mutableListOf() } += sar to dar
            }
            val observed = all.filterValues { it.size >= 2 }.keys
            val changed = all.filterValues { samples ->
                val validSar = samples.mapNotNull { it.first }
                val validDar = samples.mapNotNull { it.second }
                (validSar.size > 1 && validSar.max() - validSar.min() > 0.015f) ||
                    (validDar.size > 1 && validDar.max() - validDar.min() > 0.015f)
            }.keys
            if (changed.isNotEmpty()) FabVidDiagnostics.mark(
                "SAR_DAR_VARIATION_SAMPLED streams=${changed.sorted().joinToString(",")}",
            )
            Result(changed, observed)
        }.getOrElse {
            FabVidDiagnostics.mark("SAR_SAMPLE_UNAVAILABLE")
            Result(emptySet(), emptySet())
        }
    }
}
