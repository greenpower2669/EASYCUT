package com.fabvidedit.app.media

import android.graphics.Matrix
import com.fabvidedit.app.FabVidDiagnostics
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.fabvidedit.app.model.AudioTrack
import com.fabvidedit.app.model.ClipFilter
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.ClipTransition
import com.fabvidedit.app.model.SourceAudioTrack
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.model.TransitionType
import com.fabvidedit.app.model.VideoLayerPolicy
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.model.displayAspectRatio
import com.fabvidedit.app.model.VisualMediaKind
import kotlin.math.min

object CompositionFactory {
    fun create(
        project: VideoProject,
        resolutionShortSide: Int? = null,
        frameRate: Int? = null,
        traceExport: Boolean = false,
    ): Composition {
        require(project.clips.isNotEmpty()) { "Le projet doit contenir au moins un clip" }

        // v0.6: every independent stream is normalized to one canvas with SCALE_TO_FIT before
        // sequences are mixed. This keeps a portrait 9:16 stream portrait inside a 16:9 project
        // instead of stretching it to the first stream's geometry.
        val canvasRatio = project.aspectRatio.ratio ?: project.clips.firstNotNullOfOrNull { it.displayAspectRatio() }

        if (traceExport) FabVidDiagnostics.traceExport(
            "COMPOSE mode=${project.timelineMode} ratio=$canvasRatio fpsMax=$frameRate " +
                "shortSide=$resolutionShortSide lanes=${VideoLayerPolicy.frontToBack(project)}",
        )
        val sequences = if (project.timelineMode == TimelineMode.MULTITRACK) {
            multitrackSequences(project, canvasRatio, frameRate, traceExport)
        } else {
            val videoItems = project.clips.map { clip ->
                editedVideoItem(
                    clip = clip,
                    incomingTransition = project.transitions.firstOrNull { it.toClipId == clip.id },
                    outgoingTransition = project.transitions.firstOrNull { it.fromClipId == clip.id },
                    canvasRatio = canvasRatio,
                    frameRate = frameRate,
                    traceExport = traceExport,
                )
            }
            buildList {
                add(EditedMediaItemSequence.withAudioAndVideoFrom(videoItems))
                backgroundAudioSequence(project)?.let(::add)
            }
        }

        val builder = Composition.Builder(sequences)
        if (VideoLayerPolicy.needsVideoCompositor(project)) {
            // Custom compositor is only meaningful with multiple video inputs.
            // SingleInputVideoGraph rejects it (1001) even with multiple audio tracks.
            // Media3 emits blank VIDEO frames for addGap(); hide each blank frame
            // rather than let an upper lane cover the media below it.
            val lanes = VideoLayerPolicy.frontToBack(project)
            builder.setVideoCompositorSettings(object : VideoCompositorSettings {
                private val loggedSeconds = mutableMapOf<Int, Long>()
                override fun getOutputSize(inputSizes: List<Size>): Size =
                    VideoCompositorSettings.DEFAULT.getOutputSize(inputSizes)

                override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                    val lane = lanes.getOrNull(inputId)
                        ?: return StaticOverlaySettings.Builder().build()
                    val alpha = VideoLayerPolicy.opacityAt(project.clips, lane, presentationTimeUs / 1_000L)
                    val second = presentationTimeUs / 1_000_000L
                    if (traceExport && loggedSeconds[inputId] != second &&
                        (second <= 15L || second % 5L == 0L)
                    ) {
                        loggedSeconds[inputId] = second
                        FabVidDiagnostics.traceExport(
                            "COMPOSITOR input=$inputId lane=V${lane + 1} media3Us=$presentationTimeUs alpha=$alpha",
                        )
                    }
                    return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
                }
            })
        }
        val globalEffects = buildList<Effect> {
            resolutionShortSide?.let { add(Presentation.createForShortSide(it)) }
            textOverlayEffect(project.textLayers)?.let(::add)
        }
        if (traceExport) FabVidDiagnostics.traceExport(
            "GLOBAL effects=${globalEffects.map { it.javaClass.simpleName }} sequences=${sequences.size}",
        )
        if (globalEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), globalEffects))
        }
        return builder.build()
    }

    private fun multitrackSequences(
        project: VideoProject,
        canvasRatio: Float?,
        frameRate: Int?,
        traceExport: Boolean,
    ): List<EditedMediaItemSequence> = buildList {
        val groupedTracks = project.clips.groupBy(VideoClip::timelineTrackIndex)
        VideoLayerPolicy.frontToBack(project).forEach { lane ->
                val unsortedTrack = groupedTracks.getValue(lane)
                val track = unsortedTrack.sortedBy(VideoClip::timelineStartMs)
                val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                var cursorMs = 0L
                track.forEach { clip ->
                    val gapMs = (clip.timelineStartMs - cursorMs).coerceAtLeast(0L)
                    if (gapMs > 0L) {
                        if (traceExport) FabVidDiagnostics.traceExport("GAP lane=V${lane + 1} fromMs=$cursorMs durationMs=$gapMs")
                        builder.addGap(gapMs * 1_000L)
                    }
                    if (traceExport) FabVidDiagnostics.traceExport(
                        "ITEM lane=V${lane + 1} id=${clip.id.take(12)} startMs=${clip.timelineStartMs} durationMs=${clip.outputDurationMs}",
                    )
                    builder.addItem(
                        editedVideoItem(
                            clip = clip,
                            incomingTransition = project.transitions.firstOrNull { it.toClipId == clip.id },
                            outgoingTransition = project.transitions.firstOrNull { it.fromClipId == clip.id },
                            canvasRatio = canvasRatio,
                            frameRate = frameRate,
                            traceExport = traceExport,
                        ),
                    )
                    cursorMs = maxOf(cursorMs, clip.timelineStartMs + clip.outputDurationMs)
                }
                val trailingGapMs = (project.durationMs - cursorMs).coerceAtLeast(0L)
                if (trailingGapMs > 0L) {
                    if (traceExport) FabVidDiagnostics.traceExport("GAP_END lane=V${lane + 1} fromMs=$cursorMs durationMs=$trailingGapMs")
                    builder.addGap(trailingGapMs * 1_000L)
                }
                add(builder.build())
            }

        project.sourceAudioTracks
            .sortedBy(SourceAudioTrack::timelineTrackIndex)
            .forEach { track -> add(sourceAudioSequence(track)) }

        backgroundAudioSequence(project)?.let(::add)
    }

    private fun sourceAudioSequence(track: SourceAudioTrack): EditedMediaItemSequence {
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        if (track.timelineStartMs > 0L) builder.addGap(track.timelineStartMs * 1_000L)
        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(track.trimStartMs)
                    .setEndPositionMs(track.trimEndMs)
                    .build(),
            )
            .build()
        builder.addItem(
            EditedMediaItem.Builder(mediaItem)
                .setDurationUs(track.outputDurationMs * 1_000L)
                .setEffects(
                    Effects(
                        if (track.keyframes.isEmpty()) volumeProcessors(track.volume)
                        else listOf(GainProcessor(SourceAudioGainProvider(track))),
                        emptyList(),
                    ),
                )
                .build(),
        )
        return builder.build()
    }

    private fun backgroundAudioSequence(project: VideoProject): EditedMediaItemSequence? {
        val track = project.audioTrack ?: return null
        if (track.keyframes.isEmpty()) {
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(track.uri))
                .setDurationUs(track.durationMs * 1_000)
                .setEffects(Effects(volumeProcessors(track.volume), emptyList()))
                .build()
            return EditedMediaItemSequence.withAudioFrom(listOf(edited))
                .buildUpon()
                .setIsLooping(true)
                .build()
        }

        val repeatedItems = buildList {
            var projectOffsetMs = 0L
            while (projectOffsetMs < project.durationMs) {
                val segmentDurationMs = min(track.durationMs, project.durationMs - projectOffsetMs)
                    .coerceAtLeast(1)
                val mediaItem = MediaItem.Builder()
                    .setUri(track.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(segmentDurationMs)
                            .build(),
                    )
                    .build()
                add(
                    EditedMediaItem.Builder(mediaItem)
                        .setDurationUs(segmentDurationMs * 1_000)
                        .setEffects(
                            Effects(
                                listOf(GainProcessor(AudioTrackGainProvider(track, projectOffsetMs))),
                                emptyList(),
                            ),
                        )
                        .build(),
                )
                projectOffsetMs += segmentDurationMs
            }
        }
        return EditedMediaItemSequence.withAudioFrom(repeatedItems)
    }

    private fun editedVideoItem(
        clip: VideoClip,
        incomingTransition: ClipTransition?,
        outgoingTransition: ClipTransition?,
        canvasRatio: Float?,
        frameRate: Int? = null,
        traceExport: Boolean = false,
    ): EditedMediaItem {
        val mediaItem = if (clip.mediaKind == VisualMediaKind.IMAGE) {
            MediaItem.Builder()
                .setUri(clip.uri)
                .setImageDurationMs(clip.sourceDurationMs)
                .build()
        } else {
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build(),
                )
                .build()
        }

        val videoEffects = buildList<Effect> {
            canvasRatio?.takeIf { it > 0f }?.let {
                add(Presentation.createForAspectRatio(it, Presentation.LAYOUT_SCALE_TO_FIT))
            }
            when (clip.filter) {
                ClipFilter.NONE -> Unit
                ClipFilter.MONO -> add(RgbFilter.createGrayscaleFilter())
                ClipFilter.INVERT -> add(RgbFilter.createInvertedFilter())
                ClipFilter.WARM -> add(
                    RgbAdjustment.Builder()
                        .setRedScale(1.08f)
                        .setGreenScale(1.02f)
                        .setBlueScale(0.90f)
                        .build(),
                )
                ClipFilter.COOL -> add(
                    RgbAdjustment.Builder()
                        .setRedScale(0.90f)
                        .setGreenScale(1.02f)
                        .setBlueScale(1.10f)
                        .build(),
                )
            }
            if (clip.rotationDegrees % 360 != 0) {
                add(
                    ScaleAndRotateTransformation.Builder()
                        // FFprobe side-data rotation is already expressed in Media3's
                        // counter-clockwise convention. Keep source/orientation rotation unchanged.
                        .setRotationDegrees(clip.rotationDegrees.toFloat())
                        .build(),
                )
            }
            if (
                clip.transform != ClipTransform() ||
                clip.keyframes.isNotEmpty() ||
                incomingTransition?.type != null ||
                outgoingTransition?.type != null
            ) {
                add(AnimatedTransformEffect(clip, incomingTransition, outgoingTransition, traceExport))
            }
            if (
                clip.brightness != 1f ||
                clip.keyframes.any { it.brightness != 1f } ||
                incomingTransition?.type == TransitionType.FADE ||
                outgoingTransition?.type == TransitionType.FADE
            ) {
                add(AnimatedBrightnessEffect(clip, incomingTransition, outgoingTransition))
            }
        }

        if (traceExport) FabVidDiagnostics.traceExport(
            "EFFECTS id=${clip.id.take(12)} order=${videoEffects.map { it.javaClass.simpleName }} fpsMax=$frameRate canvas=$canvasRatio",
        )
        val hasAudibleAudio = clip.volume > 0.001f || clip.keyframes.any { it.volume > 0.001f }

        val builder = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(clip.sourceDurationMs * 1_000)
            .setRemoveAudio(!hasAudibleAudio)
            .setEffects(
                Effects(
                    if (hasAudibleAudio) clipVolumeProcessors(clip) else emptyList(),
                    videoEffects,
                ),
            )
            .setSpeed(ConstantSpeedProvider(clip.speed))
        if (clip.mediaKind == VisualMediaKind.IMAGE) {
            builder.setFrameRate(frameRate ?: 30)
        } else {
            frameRate?.let(builder::setFrameRate)
        }
        return builder.build()
    }

    private fun volumeProcessors(volume: Float): List<AudioProcessor> {
        if (volume in 0.995f..1.005f) return emptyList()
        val processor = ChannelMixingAudioProcessor()
        for (channelCount in 1..8) {
            val matrix = ChannelMixingMatrix
                .createForConstantPower(channelCount, channelCount)
                .scaleBy(volume.coerceIn(0f, 1f))
            processor.putChannelMixingMatrix(matrix)
        }
        return listOf(processor)
    }

    private fun clipVolumeProcessors(clip: VideoClip): List<AudioProcessor> {
        if (clip.keyframes.isEmpty()) return volumeProcessors(clip.volume)
        return listOf(GainProcessor(ClipGainProvider(clip)))
    }

    private fun textOverlayEffect(layers: List<TextLayer>): OverlayEffect? {
        val overlays = layers
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .map(::dynamicTextOverlay)
        return overlays.takeIf(List<TextureOverlay>::isNotEmpty)?.let(::OverlayEffect)
    }

    private fun dynamicTextOverlay(layer: TextLayer): TextureOverlay {
        val visibleText = SpannableString(layer.text).apply {
            setSpan(ForegroundColorSpan(layer.colorArgb), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(layer.backgroundArgb), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(layer.sizePx), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val hiddenText = SpannableString("")
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, layer.position.anchorY)
            .build()

        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString =
                if (presentationTimeUs / 1_000 in layer.startMs until layer.endMs) visibleText else hiddenText

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
        }
    }

    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed.coerceIn(0.25f, 4f)
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private data class TransitionMotion(
        val scale: Float = 1f,
        val positionX: Float = 0f,
        val rotationDegrees: Float = 0f,
    )

    private class AnimatedTransformEffect(
        private val clip: VideoClip,
        private val incoming: ClipTransition?,
        private val outgoing: ClipTransition?,
        private val traceExport: Boolean,
    ) : MatrixTransformation {
        private var lastLoggedSecond = -1L
        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val timeMs = (presentationTimeUs / 1_000).coerceIn(0, clip.sourceDurationMs)
            val transform = clip.transformAtSourceTime(timeMs)
            val motion = transitionMotion(clip, timeMs, incoming, outgoing)
            val scaleX = (transform.scaleX * motion.scale).coerceIn(0.05f, 6f)
            val scaleY = (transform.scaleY * motion.scale).coerceIn(0.05f, 6f)
            val matrix = Matrix().apply {
                postTranslate(-transform.pivotX, -transform.pivotY)
                postScale(scaleX, scaleY)
                postRotate(
                    MatrixConventions.modelRotationToMedia3Degrees(
                        transform.rotationDegrees + motion.rotationDegrees,
                    ),
                )
                postTranslate(
                    transform.pivotX + transform.positionX + motion.positionX,
                    transform.pivotY + transform.positionY,
                )
            }
            val second = presentationTimeUs / 1_000_000L
            if (traceExport && second != lastLoggedSecond &&
                (second <= 15L || second % 5L == 0L)
            ) {
                lastLoggedSecond = second
                val before = clip.keyframes.filter { it.timeMs <= timeMs }.maxByOrNull { it.timeMs }
                val after = clip.keyframes.filter { it.timeMs > timeMs }.minByOrNull { it.timeMs }
                val values = FloatArray(9)
                matrix.getValues(values)
                FabVidDiagnostics.traceExport(
                    "MATRIX id=${clip.id.take(12)} media3Us=$presentationTimeUs localMs=$timeMs " +
                        "kfPrev=${before?.timeMs ?: "BASE"} kfNext=${after?.timeMs ?: "END"} " +
                        "model=[x=${transform.positionX},y=${transform.positionY}," +
                        "sx=${transform.scaleX},sy=${transform.scaleY},r=${transform.rotationDegrees}," +
                        "px=${transform.pivotX},py=${transform.pivotY}] " +
                        "transition=[s=${motion.scale},x=${motion.positionX},r=${motion.rotationDegrees}] " +
                        "matrix=[${values.joinToString(",")}]",
                )
            }
            return matrix
        }
    }

    private class AnimatedBrightnessEffect(
        private val clip: VideoClip,
        private val incoming: ClipTransition?,
        private val outgoing: ClipTransition?,
    ) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val timeMs = (presentationTimeUs / 1_000).coerceIn(0, clip.sourceDurationMs)
            val brightness = (
                clip.brightnessAtSourceTime(timeMs) * transitionBrightness(clip, timeMs, incoming, outgoing)
                ).coerceIn(0f, 2f)
            return floatArrayOf(
                brightness, 0f, 0f, 0f,
                0f, brightness, 0f, 0f,
                0f, 0f, brightness, 0f,
                0f, 0f, 0f, 1f,
            )
        }
    }

    private class ClipGainProvider(private val clip: VideoClip) : GainProcessor.GainProvider {
        override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
            val timeMs = samplePosition * 1_000L / sampleRate.coerceAtLeast(1)
            return clip.volumeAtSourceTime(timeMs)
        }

        override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
            if (getGainFactorAtSamplePosition(samplePosition, sampleRate) == 1f) samplePosition + 1 else C.TIME_UNSET
    }

    private class SourceAudioGainProvider(
        private val track: SourceAudioTrack,
    ) : GainProcessor.GainProvider {
        override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
            val localMs = samplePosition * 1_000L / sampleRate.coerceAtLeast(1)
            return track.volumeAtSourceTime(track.trimStartMs + localMs)
        }

        override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
            if (getGainFactorAtSamplePosition(samplePosition, sampleRate) == 1f) samplePosition + 1 else C.TIME_UNSET
    }

    private class AudioTrackGainProvider(
        private val track: AudioTrack,
        private val projectOffsetMs: Long,
    ) : GainProcessor.GainProvider {
        override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
            val itemTimeMs = samplePosition * 1_000L / sampleRate.coerceAtLeast(1)
            return track.volumeAtProjectTime(projectOffsetMs + itemTimeMs)
        }

        override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
            if (getGainFactorAtSamplePosition(samplePosition, sampleRate) == 1f) samplePosition + 1 else C.TIME_UNSET
    }

    private fun transitionMotion(
        clip: VideoClip,
        timeMs: Long,
        incoming: ClipTransition?,
        outgoing: ClipTransition?,
    ): TransitionMotion {
        transitionProgress(clip, timeMs, incoming, isIncoming = true)?.let { (type, progress) ->
            return when (type) {
                TransitionType.FADE -> TransitionMotion()
                TransitionType.SLIDE_LEFT -> TransitionMotion(positionX = 2.05f * (1f - progress))
                TransitionType.SLIDE_RIGHT -> TransitionMotion(positionX = -2.05f * (1f - progress))
                TransitionType.ZOOM -> TransitionMotion(scale = 0.68f + 0.32f * progress)
                TransitionType.ROTATE -> TransitionMotion(
                    scale = 0.78f + 0.22f * progress,
                    rotationDegrees = -18f * (1f - progress),
                )
            }
        }
        transitionProgress(clip, timeMs, outgoing, isIncoming = false)?.let { (type, progress) ->
            return when (type) {
                TransitionType.FADE -> TransitionMotion()
                TransitionType.SLIDE_LEFT -> TransitionMotion(positionX = -2.05f * progress)
                TransitionType.SLIDE_RIGHT -> TransitionMotion(positionX = 2.05f * progress)
                TransitionType.ZOOM -> TransitionMotion(scale = 1f + 0.34f * progress)
                TransitionType.ROTATE -> TransitionMotion(
                    scale = 1f - 0.22f * progress,
                    rotationDegrees = 18f * progress,
                )
            }
        }
        return TransitionMotion()
    }

    private fun transitionBrightness(
        clip: VideoClip,
        timeMs: Long,
        incoming: ClipTransition?,
        outgoing: ClipTransition?,
    ): Float {
        val incomingBrightness = transitionProgress(clip, timeMs, incoming, isIncoming = true)
            ?.takeIf { it.first == TransitionType.FADE }
            ?.second ?: 1f
        val outgoingBrightness = transitionProgress(clip, timeMs, outgoing, isIncoming = false)
            ?.takeIf { it.first == TransitionType.FADE }
            ?.let { 1f - it.second } ?: 1f
        return min(incomingBrightness, outgoingBrightness).coerceIn(0f, 1f)
    }

    private fun transitionProgress(
        clip: VideoClip,
        timeMs: Long,
        transition: ClipTransition?,
        isIncoming: Boolean,
    ): Pair<TransitionType, Float>? {
        transition ?: return null
        val requestedSourceMs = ((transition.durationMs / 2f) * clip.speed).toLong()
        val durationMs = requestedSourceMs
            .coerceAtMost((clip.sourceDurationMs / 3).coerceAtLeast(1))
            .coerceAtLeast(1)
        val raw = if (isIncoming) {
            if (timeMs > durationMs) return null
            timeMs.toFloat() / durationMs
        } else {
            val start = clip.sourceDurationMs - durationMs
            if (timeMs < start) return null
            (timeMs - start).toFloat() / durationMs
        }
        val eased = raw.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        return transition.type to eased
    }
}
