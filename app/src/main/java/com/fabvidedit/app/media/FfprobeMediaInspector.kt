package com.fabvidedit.app.media

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.MediaStreamInventory
import com.fabvidedit.app.model.MediaStreamKind
import com.fabvidedit.app.model.StreamSelectionConfidence
import com.fabvidedit.app.model.StreamSelectionRecommendation
import com.fabvidedit.app.model.VideoStreamScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

object FfprobeMediaInspector {
    private const val MAX_INVENTORY_JSON_BYTES = 4L * 1024L * 1024L
    suspend fun inspect(context: Context, uri: Uri): MediaStreamInventory =
        LocalFfmpegInput.withPath(context, uri) { input -> inspectInput(input, context.cacheDir) }

    private fun inspectInput(input: String, cacheDir: File): MediaStreamInventory {
        // Explicit -o is vital: the native FFprobe JSON writer must not depend on
        // the FFmpegKit stdout/log callback, which also carries diagnostic text.
        val dir = File(cacheDir, "ffprobe_metadata")
        require(dir.isDirectory || dir.mkdirs()) { "Cache FFprobe indisponible" }
        val metadata = File.createTempFile("inventory-", ".json", dir)
        val root = try {
            val first = FfprobeNativeGate.run {
                FFprobeKit.executeWithArguments(
                    FfprobeInventoryCommand.arguments(input, metadata.absolutePath),
                )
            }
            val size = metadata.length()
            val fileRoot = if (ReturnCode.isSuccess(first.returnCode) &&
                metadata.isFile && size in 1L..MAX_INVENTORY_JSON_BYTES
            ) {
                runCatching { JSONObject(metadata.readText(Charsets.UTF_8)) }.getOrNull()
            } else null
            fileRoot ?: run {
                // Retry a SHORT inventory only; never collect decoded frame scans in memory.
                val retry = FfprobeNativeGate.run {
                    FFprobeKit.executeWithArguments(
                        FfprobeInventoryCommand.stdoutArguments(input),
                    )
                }
                val json = retry.output.orEmpty()
                val standard = if (ReturnCode.isSuccess(retry.returnCode) &&
                    json.toByteArray(Charsets.UTF_8).size in 1..MAX_INVENTORY_JSON_BYTES.toInt()
                ) runCatching { JSONObject(json) }.getOrNull() else null
                standard ?: run {
                    // Some embedded formats need more header data to identify the streams.
                    // This does NOT decode every frame or re-enable native -o frame scanning.
                    val expanded = FfprobeNativeGate.run {
                        FFprobeKit.executeWithArguments(
                            FfprobeInventoryCommand.expandedStdoutArguments(input),
                        )
                    }
                    val extraJson = expanded.output.orEmpty()
                    val parsed = if (ReturnCode.isSuccess(expanded.returnCode) &&
                        extraJson.toByteArray(Charsets.UTF_8).size in 1..MAX_INVENTORY_JSON_BYTES.toInt()
                    ) runCatching { JSONObject(extraJson) }.getOrNull() else null
                    require(parsed != null) {
                        "FFprobe : source non inventoriable même après analyse étendue. " +
                            "Codes fichier=${first.returnCode}, standard=${retry.returnCode}, " +
                            "étendu=${expanded.returnCode} ; détail=" +
                            extraJson.replace(Regex("\\s+"), " ").takeLast(350)
                    }
                    com.fabvidedit.app.FabVidDiagnostics.mark("FFPROBE_EXPANDED_INVENTORY_OK")
                    parsed
                }
            }
        } finally {
            if (metadata.exists() && !metadata.delete()) {
                android.util.Log.w("EASYCUT_FFprobe", "Unable to remove disposable inventory")
            }
        }
        val format = root.optJSONObject("format")
        val containerDurationMs = format?.optString("duration")
            ?.toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?.coerceAtLeast(0L)
            ?: 0L
        val streamsJson = root.optJSONArray("streams")
        val streams = buildList {
            if (streamsJson != null) {
                for (position in 0 until streamsJson.length()) {
                    add(parseStream(streamsJson.getJSONObject(position)))
                }
            }
        }
        val effectiveDurationMs = max(
            containerDurationMs,
            streams.mapNotNull(MediaStreamInfo::durationMs).maxOrNull() ?: 0L,
        )
        val recommendation = StreamSelectionPolicy.recommend(streams, effectiveDurationMs)

        return MediaStreamInventory(
            containerName = format?.optNullableString("format_name"),
            containerLongName = format?.optNullableString("format_long_name"),
            durationMs = effectiveDurationMs,
            streams = streams,
            recommendation = recommendation,
        )
    }

    private fun parseStream(json: JSONObject): MediaStreamInfo {
        val codecType = json.optNullableString("codec_type")
        val tagsObject = json.optJSONObject("tags")
        val tags = buildMap {
            if (tagsObject != null) {
                val keys = tagsObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, tagsObject.optString(key, ""))
                }
            }
        }
        val disposition = json.optJSONObject("disposition")
        val sar = parseRatio(json.optNullableString("sample_aspect_ratio"), fallback = 1f)
        val dar = parseRatio(json.optNullableString("display_aspect_ratio"), fallback = 0f)
        val sideData = json.optJSONArray("side_data_list")
        var sideDataRotation: Int? = null
        if (sideData != null) {
            for (index in 0 until sideData.length()) {
                val rotation = sideData.optJSONObject(index)?.optDouble("rotation", Double.NaN)
                if (rotation != null && !rotation.isNaN()) {
                    sideDataRotation = rotation.toInt()
                    break
                }
            }
        }
        val tagRotation = tags["rotate"]?.toIntOrNull()

        return MediaStreamInfo(
            index = json.optInt("index", -1),
            kind = when (codecType) {
                "video" -> MediaStreamKind.VIDEO
                "audio" -> MediaStreamKind.AUDIO
                "subtitle" -> MediaStreamKind.SUBTITLE
                "data" -> MediaStreamKind.DATA
                else -> MediaStreamKind.OTHER
            },
            codecName = json.optNullableString("codec_name"),
            codecLongName = json.optNullableString("codec_long_name"),
            width = json.optInt("width", 0),
            height = json.optInt("height", 0),
            sampleAspectRatio = sar,
            displayAspectRatio = dar,
            frameRate = parseRational(json.optNullableString("avg_frame_rate"))
                ?: parseRational(json.optNullableString("r_frame_rate")),
            durationMs = json.optNullableString("duration")
                ?.toDoubleOrNull()
                ?.times(1_000.0)
                ?.toLong()
                ?.coerceAtLeast(0L),
            startTimeMs = json.optNullableString("start_time")
                ?.toDoubleOrNull()
                ?.times(1_000.0)
                ?.toLong()
                ?: 0L,
            frameCount = json.optNullableString("nb_frames")?.toLongOrNull(),
            bitRate = json.optNullableString("bit_rate")?.toLongOrNull(),
            isDefault = disposition?.optInt("default", 0) == 1,
            isAttachedPicture = disposition?.optInt("attached_pic", 0) == 1,
            rotationDegrees = sideDataRotation ?: tagRotation ?: 0,
            tags = tags,
        )
    }

    private fun parseRational(value: String?): Float? {
        if (value.isNullOrBlank() || value == "0/0") return null
        if ('/' !in value) return value.toFloatOrNull()
        val numerator = value.substringBefore('/').toFloatOrNull() ?: return null
        val denominator = value.substringAfter('/').toFloatOrNull() ?: return null
        return if (denominator == 0f) null else numerator / denominator
    }

    private fun parseRatio(value: String?, fallback: Float): Float {
        if (value.isNullOrBlank() || value == "N/A") return fallback
        val separator = when {
            ':' in value -> ':'
            '/' in value -> '/'
            else -> return value.toFloatOrNull() ?: fallback
        }
        val left = value.substringBefore(separator).toFloatOrNull() ?: return fallback
        val right = value.substringAfter(separator).toFloatOrNull() ?: return fallback
        return if (right == 0f) fallback else left / right
    }

    private fun JSONObject.optNullableString(key: String): String? =
        optString(key, "").takeIf { it.isNotBlank() && it != "N/A" && it != "null" }
}

object StreamSelectionPolicy {
    fun recommend(
        allStreams: List<MediaStreamInfo>,
        containerDurationMs: Long,
    ): StreamSelectionRecommendation {
        val videos = allStreams.filter { it.kind == MediaStreamKind.VIDEO && !it.isAttachedPicture }
        val audios = allStreams.filter { it.kind == MediaStreamKind.AUDIO }
        if (videos.isEmpty()) {
            return StreamSelectionRecommendation(
                selectedVideoIndex = null,
                selectedAudioIndex = selectAudio(audios, containerDurationMs)?.index,
                confidence = StreamSelectionConfidence.LOW,
                simultaneousVideoConflict = false,
                automaticallyExcludedVideoIndexes = emptySet(),
                scores = emptyList(),
                reason = "Aucune piste vidéo exploitable détectée.",
            )
        }

        val effectiveDuration = max(
            containerDurationMs,
            videos.mapNotNull(MediaStreamInfo::durationMs).maxOrNull() ?: 1L,
        ).coerceAtLeast(1L)
        val maxArea = videos.maxOfOrNull { it.width.toLong() * it.height.toLong() }
            ?.coerceAtLeast(1L)
            ?: 1L
        val maxFrames = videos.mapNotNull(MediaStreamInfo::frameCount).maxOrNull()?.coerceAtLeast(1L)

        val scores = videos.map { stream ->
            val duration = (stream.durationMs ?: effectiveDuration).coerceAtLeast(0L)
            val coverage = (duration.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1.25f)
            val areaShare = ((stream.width.toLong() * stream.height.toLong()).toFloat() / maxArea.toFloat())
                .coerceIn(0f, 1f)
            val frameShare = if (maxFrames != null && stream.frameCount != null) {
                (stream.frameCount.toFloat() / maxFrames.toFloat()).coerceIn(0f, 1f)
            } else {
                coverage.coerceIn(0f, 1f)
            }
            var score = coverage.coerceIn(0f, 1f) * 60f
            if (stream.isDefault) score += 14f
            score += areaShare * 7f
            score += frameShare * 8f
            if (stream.startTimeMs <= 1_000L) score += 4f
            if (coverage < 0.20f) score -= 24f
            if (coverage < 0.05f) score -= 18f
            VideoStreamScore(stream.index, score, coverage.coerceIn(0f, 1f))
        }.sortedByDescending(VideoStreamScore::score)

        val top = scores.first()
        val second = scores.getOrNull(1)
        val margin = if (second == null) 100f else top.score - second.score
        val confidence = when {
            videos.size == 1 -> StreamSelectionConfidence.HIGH
            top.durationCoverage >= 0.75f && (margin >= 18f || (second?.durationCoverage ?: 0f) < 0.35f) ->
                StreamSelectionConfidence.HIGH
            margin >= 8f -> StreamSelectionConfidence.MEDIUM
            else -> StreamSelectionConfidence.LOW
        }
        val selected = videos.first { it.index == top.streamIndex }
        val conflict = videos.indices.any { left ->
            (left + 1 until videos.size).any { right ->
                overlap(videos[left], videos[right], effectiveDuration)
            }
        }
        val excluded = if (confidence == StreamSelectionConfidence.LOW) {
            emptySet()
        } else {
            videos.map(MediaStreamInfo::index).filterNot { it == selected.index }.toSet()
        }
        val reason = when {
            videos.size == 1 -> "Une seule piste vidéo exploitable."
            confidence == StreamSelectionConfidence.LOW ->
                "Plusieurs pistes ont des caractéristiques proches : choix utilisateur requis."
            top.durationCoverage >= 0.75f && (second?.durationCoverage ?: 0f) < 0.35f ->
                "La piste ${selected.index} couvre l'essentiel du fichier ; les autres sont nettement plus courtes."
            selected.isDefault -> "La piste ${selected.index} est durable, cohérente et marquée par défaut."
            else -> "La piste ${selected.index} obtient le meilleur score durée/cohérence/présence."
        }

        return StreamSelectionRecommendation(
            selectedVideoIndex = selected.index,
            selectedAudioIndex = selectAudio(audios, effectiveDuration)?.index,
            confidence = confidence,
            simultaneousVideoConflict = conflict,
            automaticallyExcludedVideoIndexes = excluded,
            scores = scores,
            reason = reason,
        )
    }

    private fun selectAudio(audios: List<MediaStreamInfo>, durationMs: Long): MediaStreamInfo? =
        audios.maxByOrNull { audio ->
            val coverage = ((audio.durationMs ?: durationMs).toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            coverage * 100f + if (audio.isDefault) 25f else 0f
        }

    private fun overlap(a: MediaStreamInfo, b: MediaStreamInfo, fallbackDurationMs: Long): Boolean {
        val aDuration = (a.durationMs ?: fallbackDurationMs).coerceAtLeast(0L)
        val bDuration = (b.durationMs ?: fallbackDurationMs).coerceAtLeast(0L)
        if (aDuration <= 0L || bDuration <= 0L) return false
        val aEnd = a.startTimeMs + aDuration
        val bEnd = b.startTimeMs + bDuration
        return min(aEnd, bEnd) - max(a.startTimeMs, b.startTimeMs) > 250L
    }
}
