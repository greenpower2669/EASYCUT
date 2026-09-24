package com.fabvidedit.app.data

import android.content.Context
import android.util.Log
import com.fabvidedit.app.model.AspectRatioPreset
import com.fabvidedit.app.model.AudioKeyframe
import com.fabvidedit.app.model.AudioTrack
import com.fabvidedit.app.model.ClipFilter
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.ClipTransition
import com.fabvidedit.app.model.MotionEasing
import com.fabvidedit.app.model.SourceAudioKeyframe
import com.fabvidedit.app.model.SourceAudioTrack
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TextPosition
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.TransitionType
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.model.VisualMediaKind
import com.fabvidedit.app.model.repairIndependentStreamLayout
import org.json.JSONArray
import org.json.JSONObject

class ProjectStore(context: Context) {
    private val preferences = context.getSharedPreferences("fab_vid_edit_projects", Context.MODE_PRIVATE)

    fun load(): List<VideoProject> = runCatching {
        val value = preferences.getString(KEY_PROJECTS, null) ?: return emptyList()
        val array = JSONArray(value)
        val loaded = buildList {
            for (index in 0 until array.length()) {
                add(projectFromJson(array.getJSONObject(index)))
            }
        }
        val repaired = loaded.map { project ->
            if (project.projectFormatVersion < 9) {
                project.repairIndependentStreamLayout().copy(projectFormatVersion = 9)
            } else {
                project
            }
        }
        if (repaired != loaded) {
            save(repaired)
            Log.i(TAG, "v0.9 repaired persisted independent video streams into distinct tracks")
        }
        repaired.forEach(::logMultitrackReload)
        repaired.sortedByDescending(VideoProject::updatedAt)
    }.getOrDefault(emptyList())

    fun save(projects: List<VideoProject>) {
        val array = JSONArray()
        projects.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PROJECTS, array.toString()).apply()
    }

    private fun VideoProject.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("projectFormatVersion", projectFormatVersion)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("aspectRatio", aspectRatio.name)
        put("timelineMode", timelineMode.name)
        put("clips", JSONArray().apply { clips.forEach { put(it.toJson()) } })
        put("sourceAudioTracks", JSONArray().apply { sourceAudioTracks.forEach { put(it.toJson()) } })
        put("textLayers", JSONArray().apply { textLayers.forEach { put(it.toJson()) } })
        put("transitions", JSONArray().apply { transitions.forEach { put(it.toJson()) } })
        audioTrack?.let { put("audioTrack", it.toJson()) }
    }

    private fun VideoClip.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceId", sourceId)
        containerUri?.let { put("containerUri", it) }
        sourceStreamIndex?.let { put("sourceStreamIndex", it) }
        put("mediaKind", mediaKind.name)
        syncGroupId?.let { put("syncGroupId", it) }
        put("syncLocked", syncLocked)
        put("timelineTrackIndex", timelineTrackIndex)
        put("timelineStartMs", timelineStartMs)
        put("uri", uri)
        put("name", name)
        put("durationMs", durationMs)
        put("width", width)
        put("height", height)
        put("sampleAspectRatio", sampleAspectRatio.toDouble())
        put("displayAspectRatio", displayAspectRatio.toDouble())
        put("aspectVaries", aspectVaries)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("speed", speed.toDouble())
        put("volume", volume.toDouble())
        put("brightness", brightness.toDouble())
        put("opacity", opacity.toDouble())
        put("rotationDegrees", rotationDegrees)
        put("filter", filter.name)
        put("transform", transform.toJson())
        put("keyframes", JSONArray().apply { keyframes.forEach { put(it.toJson()) } })
    }

    private fun SourceAudioTrack.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceId", sourceId)
        containerUri?.let { put("containerUri", it) }
        sourceStreamIndex?.let { put("sourceStreamIndex", it) }
        syncGroupId?.let { put("syncGroupId", it) }
        put("syncLocked", syncLocked)
        put("timelineTrackIndex", timelineTrackIndex)
        put("timelineStartMs", timelineStartMs)
        put("uri", uri)
        put("name", name)
        put("durationMs", durationMs)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("volume", volume.toDouble())
        put("keyframes", JSONArray().apply {
            keyframes.forEach { keyframe ->
                put(JSONObject().apply {
                    put("id", keyframe.id)
                    put("timeMs", keyframe.timeMs)
                    put("volume", keyframe.volume.toDouble())
                    put("easing", keyframe.easing.name)
                })
            }
        })
    }

    private fun ClipTransform.toJson(): JSONObject = JSONObject().apply {
        put("scaleX", scaleX.toDouble())
        put("scaleY", scaleY.toDouble())
        put("positionX", positionX.toDouble())
        put("positionY", positionY.toDouble())
        put("rotationDegrees", rotationDegrees.toDouble())
        put("pivotX", pivotX.toDouble())
        put("pivotY", pivotY.toDouble())
    }

    private fun TransformKeyframe.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timeMs", timeMs)
        put("easing", easing.name)
        put("transform", transform.toJson())
        sarOverride?.let { put("sarOverride", it.toDouble()) }
        darOverride?.let { put("darOverride", it.toDouble()) }
        put("brightness", brightness.toDouble())
        put("volume", volume.toDouble())
    }

    private fun ClipTransition.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fromClipId", fromClipId)
        put("toClipId", toClipId)
        put("type", type.name)
        put("durationMs", durationMs)
    }

    private fun AudioTrack.toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("name", name)
        put("durationMs", durationMs)
        put("volume", volume.toDouble())
        put("keyframes", JSONArray().apply { keyframes.forEach { put(it.toJson()) } })
    }

    private fun AudioKeyframe.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timeMs", timeMs)
        put("volume", volume.toDouble())
        put("easing", easing.name)
    }

    private fun TextLayer.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("startMs", startMs)
        put("endMs", endMs)
        put("colorArgb", colorArgb)
        put("backgroundArgb", backgroundArgb)
        put("position", position.name)
        put("sizePx", sizePx)
    }

    private fun projectFromJson(json: JSONObject): VideoProject {
        val clipsArray = json.optJSONArray("clips") ?: JSONArray()
        val clips = buildList {
            for (index in 0 until clipsArray.length()) {
                add(clipFromJson(clipsArray.getJSONObject(index)))
            }
        }
        val sourceAudioArray = json.optJSONArray("sourceAudioTracks") ?: JSONArray()
        val sourceAudio = buildList {
            for (index in 0 until sourceAudioArray.length()) {
                sourceAudioFromJson(sourceAudioArray.getJSONObject(index))?.let(::add)
            }
        }
        val textArray = json.optJSONArray("textLayers") ?: JSONArray()
        val texts = buildList {
            for (index in 0 until textArray.length()) {
                add(textFromJson(textArray.getJSONObject(index)))
            }
        }
        val transitionsArray = json.optJSONArray("transitions") ?: JSONArray()
        val transitions = buildList {
            for (index in 0 until transitionsArray.length()) {
                transitionFromJson(transitionsArray.getJSONObject(index))?.let(::add)
            }
        }
        return VideoProject(
            id = json.getString("id"),
            projectFormatVersion = json.optInt("projectFormatVersion", 0),
            name = json.optString("name", "Projet FabVidEdit"),
            clips = clips,
            sourceAudioTracks = sourceAudio,
            timelineMode = enumValue(json.optString("timelineMode"), TimelineMode.SEQUENTIAL),
            audioTrack = json.optJSONObject("audioTrack")?.let(::audioFromJson),
            textLayers = texts,
            transitions = transitions,
            aspectRatio = enumValue(json.optString("aspectRatio"), AspectRatioPreset.SOURCE),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        ).withValidTransitions()
    }

    private fun clipFromJson(json: JSONObject): VideoClip {
        val duration = json.optLong("durationMs", 1).coerceAtLeast(1)
        val sourceDuration = (json.optLong("trimEndMs", duration) - json.optLong("trimStartMs", 0))
            .coerceAtLeast(1)
        val baseVolume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f)
        val baseBrightness = json.optDouble("brightness", 1.0).toFloat().coerceIn(0f, 2f)
        val keyframesArray = json.optJSONArray("keyframes") ?: JSONArray()
        val keyframes = buildList {
            for (index in 0 until keyframesArray.length()) {
                keyframeFromJson(
                    json = keyframesArray.getJSONObject(index),
                    sourceDurationMs = sourceDuration,
                    fallbackBrightness = baseBrightness,
                    fallbackVolume = baseVolume,
                )?.let(::add)
            }
        }.sortedBy(TransformKeyframe::timeMs)
        val id = json.getString("id")
        return VideoClip(
            id = id,
            sourceId = json.optString("sourceId", id).ifBlank { id },
            containerUri = json.optString("containerUri").takeIf(String::isNotBlank),
            sourceStreamIndex = if (json.has("sourceStreamIndex") && !json.isNull("sourceStreamIndex")) {
                json.optInt("sourceStreamIndex")
            } else {
                null
            },
            mediaKind = enumValue(json.optString("mediaKind"), VisualMediaKind.VIDEO),
            syncGroupId = json.optString("syncGroupId").takeIf(String::isNotBlank),
            syncLocked = json.optBoolean("syncLocked", false),
            timelineTrackIndex = json.optInt("timelineTrackIndex", 0).coerceAtLeast(0),
            timelineStartMs = json.optLong("timelineStartMs", 0L).coerceAtLeast(0L),
            uri = json.getString("uri"),
            name = json.optString("name", "Vidéo"),
            durationMs = duration,
            width = json.optInt("width"),
            height = json.optInt("height"),
            sampleAspectRatio = json.optDouble("sampleAspectRatio", 1.0).toFloat()
                .takeIf { it.isFinite() && it in 0.1f..10f } ?: 1f,
            displayAspectRatio = json.optDouble("displayAspectRatio", 0.0).toFloat()
                .takeIf { it.isFinite() && it in 0.1f..10f } ?: 0f,
            aspectVaries = json.optBoolean("aspectVaries", false),
            trimStartMs = json.optLong("trimStartMs", 0).coerceIn(0, duration - 1),
            trimEndMs = json.optLong("trimEndMs", duration).coerceIn(1, duration),
            speed = json.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f),
            volume = baseVolume,
            brightness = baseBrightness,
            opacity = json.optDouble("opacity", 1.0).toFloat().takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f,
            rotationDegrees = json.optInt("rotationDegrees", 0),
            filter = enumValue(json.optString("filter"), ClipFilter.NONE),
            transform = json.optJSONObject("transform")?.let(::transformFromJson) ?: ClipTransform(),
            keyframes = keyframes,
        )
    }

    private fun sourceAudioFromJson(json: JSONObject): SourceAudioTrack? {
        val uri = json.optString("uri")
        val sourceId = json.optString("sourceId")
        if (uri.isBlank() || sourceId.isBlank()) return null
        val duration = json.optLong("durationMs", 1L).coerceAtLeast(1L)
        val trimStart = json.optLong("trimStartMs", 0L).coerceIn(0L, duration - 1L)
        val trimEnd = json.optLong("trimEndMs", duration).coerceIn(trimStart + 1L, duration)
        val keyframesArray = json.optJSONArray("keyframes") ?: JSONArray()
        val keyframes = buildList {
            for (index in 0 until keyframesArray.length()) {
                val item = keyframesArray.optJSONObject(index) ?: continue
                add(
                    SourceAudioKeyframe(
                        id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        timeMs = item.optLong("timeMs").coerceIn(0L, duration),
                        volume = item.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
                        easing = enumValue(item.optString("easing"), MotionEasing.EASE_IN_OUT),
                    ),
                )
            }
        }.sortedBy(SourceAudioKeyframe::timeMs)
        return SourceAudioTrack(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            sourceId = sourceId,
            containerUri = json.optString("containerUri").takeIf(String::isNotBlank),
            sourceStreamIndex = if (json.has("sourceStreamIndex") && !json.isNull("sourceStreamIndex")) {
                json.optInt("sourceStreamIndex")
            } else {
                null
            },
            syncGroupId = json.optString("syncGroupId").takeIf(String::isNotBlank),
            syncLocked = json.optBoolean("syncLocked", false),
            timelineTrackIndex = json.optInt("timelineTrackIndex", 0).coerceAtLeast(0),
            timelineStartMs = json.optLong("timelineStartMs", 0L).coerceAtLeast(0L),
            uri = uri,
            name = json.optString("name", "Audio"),
            durationMs = duration,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            volume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
            keyframes = keyframes,
        )
    }

    private fun transformFromJson(json: JSONObject): ClipTransform = ClipTransform(
        scaleX = json.optDouble("scaleX", 1.0).toFloat(),
        scaleY = json.optDouble("scaleY", 1.0).toFloat(),
        positionX = json.optDouble("positionX", 0.0).toFloat(),
        positionY = json.optDouble("positionY", 0.0).toFloat(),
        rotationDegrees = json.optDouble("rotationDegrees", 0.0).toFloat(),
        pivotX = json.optDouble("pivotX", 0.0).toFloat(),
        pivotY = json.optDouble("pivotY", 0.0).toFloat(),
    ).normalized()

    private fun keyframeFromJson(
        json: JSONObject,
        sourceDurationMs: Long,
        fallbackBrightness: Float,
        fallbackVolume: Float,
    ): TransformKeyframe? {
        val transformJson = json.optJSONObject("transform") ?: return null
        return TransformKeyframe(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            timeMs = json.optLong("timeMs").coerceIn(0, sourceDurationMs),
            transform = transformFromJson(transformJson),
            brightness = json.optDouble("brightness", fallbackBrightness.toDouble())
                .toFloat()
                .coerceIn(0f, 2f),
            volume = json.optDouble("volume", fallbackVolume.toDouble())
                .toFloat()
                .coerceIn(0f, 1f),
            easing = enumValue(json.optString("easing"), MotionEasing.EASE_IN_OUT),
            sarOverride = json.optDouble("sarOverride", Double.NaN).toFloat()
                .takeIf { it.isFinite() && it in 0.1f..10f },
            darOverride = json.optDouble("darOverride", Double.NaN).toFloat()
                .takeIf { it.isFinite() && it in 0.1f..10f },
        )
    }

    private fun transitionFromJson(json: JSONObject): ClipTransition? {
        val from = json.optString("fromClipId")
        val to = json.optString("toClipId")
        if (from.isBlank() || to.isBlank()) return null
        return ClipTransition(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            fromClipId = from,
            toClipId = to,
            type = enumValue(json.optString("type"), TransitionType.FADE),
            durationMs = json.optLong("durationMs", 600).coerceIn(200, 2_000),
        )
    }

    private fun audioFromJson(json: JSONObject): AudioTrack {
        val keyframesArray = json.optJSONArray("keyframes") ?: JSONArray()
        val keyframes = buildList {
            for (index in 0 until keyframesArray.length()) {
                val item = keyframesArray.getJSONObject(index)
                add(
                    AudioKeyframe(
                        id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        timeMs = item.optLong("timeMs").coerceAtLeast(0),
                        volume = item.optDouble("volume", 0.5).toFloat().coerceIn(0f, 1f),
                        easing = enumValue(item.optString("easing"), MotionEasing.EASE_IN_OUT),
                    ),
                )
            }
        }.sortedBy(AudioKeyframe::timeMs)
        return AudioTrack(
            uri = json.getString("uri"),
            name = json.optString("name", "Musique"),
            durationMs = json.optLong("durationMs", 1).coerceAtLeast(1),
            volume = json.optDouble("volume", 0.5).toFloat().coerceIn(0f, 1f),
            keyframes = keyframes,
        )
    }

    private fun textFromJson(json: JSONObject): TextLayer = TextLayer(
        id = json.getString("id"),
        text = json.optString("text"),
        startMs = json.optLong("startMs"),
        endMs = json.optLong("endMs"),
        colorArgb = json.optInt("colorArgb", 0xFFFFFFFF.toInt()),
        backgroundArgb = json.optInt("backgroundArgb", 0x66000000),
        position = enumValue(json.optString("position"), TextPosition.BOTTOM),
        sizePx = json.optInt("sizePx", 72).coerceIn(32, 160),
    )

    private fun logMultitrackReload(project: VideoProject) {
        if (project.timelineMode != TimelineMode.MULTITRACK) return
        project.clips
            .sortedWith(compareBy<VideoClip> { it.timelineTrackIndex }.thenBy { it.timelineStartMs })
            .forEach { clip ->
                Log.i(
                    TAG,
                    "MULTITRACK RELOAD: V${clip.timelineTrackIndex + 1} " +
                        "stream=${clip.sourceStreamIndex ?: "?"} start=${clip.timelineStartMs} " +
                        "source=${clip.sourceId}",
                )
            }
        project.sourceAudioTracks
            .sortedBy(SourceAudioTrack::timelineTrackIndex)
            .forEach { track ->
                Log.i(
                    TAG,
                    "MULTITRACK RELOAD: A${track.timelineTrackIndex + 1} " +
                        "stream=${track.sourceStreamIndex ?: "?"} start=${track.timelineStartMs} " +
                        "source=${track.sourceId}",
                )
            }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_PROJECTS = "projects_v1"
        const val TAG = "FabVidProjectStore"
    }
}
