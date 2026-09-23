package com.fabvidedit.app.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.media.CompositionFactory
import com.fabvidedit.app.media.ExportState
import com.fabvidedit.app.media.ExportSettings
import com.fabvidedit.app.media.ExportVideoCodec
import com.fabvidedit.app.media.ExportFrameRate
import com.fabvidedit.app.media.ExportResolution
import com.fabvidedit.app.media.ExportBitrate
import com.fabvidedit.app.media.VideoEncoderCapabilities
import com.fabvidedit.app.media.TimelineThumbnailCache
import com.fabvidedit.app.media.TimelineWaveformCache
import com.fabvidedit.app.model.AspectRatioPreset
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.PreviewGestureMath
import com.fabvidedit.app.model.PreviewRotationGate
import com.fabvidedit.app.model.SourceAudioKeyframe
import com.fabvidedit.app.model.SourceAudioTrack
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TextPosition
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.TransitionType
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoLayerPolicy
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.model.displayAspectRatio
import com.fabvidedit.app.model.VisualMediaKind
import com.fabvidedit.app.ui.theme.FabBackground
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurface
import com.fabvidedit.app.ui.theme.FabSurfaceHigh
import com.fabvidedit.app.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** v0.8 editor: real overlay video tracks, magnetic playhead, visual trimming and keyframes. */
@Composable
fun CapCutMultitrackEditorV06(viewModel: FabVidEditViewModel, project: VideoProject) {
    val context = LocalContext.current
    val previewShortSide = remember(context) {
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (memory.isLowRamDevice) 480 else 720
    }
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    val exportState by viewModel.exportState.collectAsStateWithLifecycleCompat()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycleCompat()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycleCompat()
    val selectedIndex = project.clips.indexOfFirst { it.id == selectedClipId }
    val selectedClip = project.clips.getOrNull(selectedIndex)
    val player = remember {
        CompositionPlayer.Builder(context)
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()
    }
    val fallbackPlayer = remember { ExoPlayer.Builder(context).build() }
    val sourceAudioPlayer = remember { ExoPlayer.Builder(context).build() }
    val musicPreviewPlayer = remember { ExoPlayer.Builder(context).build() }

    var panel by remember { mutableStateOf(EditorPanel.CLIP) }
    var inspectorOpen by remember(project.id) { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var fallbackClip by remember(project.id) { mutableStateOf<VideoClip?>(null) }
    var fallbackClipId by remember(project.id) { mutableStateOf<String?>(null) }
    val videoTracks = project.clips.groupBy(VideoClip::timelineTrackIndex)
    val unequalVideoSpans = videoTracks.size > 1 && videoTracks.values.map { track -> track.maxOfOrNull { it.timelineStartMs + it.outputDurationMs } ?: 0L }.distinct().size > 1
    var stablePreview by remember(project.id) { mutableStateOf(unequalVideoSpans) }
    var editingText by remember { mutableStateOf<TextLayer?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var previewMuted by remember { mutableStateOf(false) }
    var selectedSourceAudioId by remember(project.id) { mutableStateOf<String?>(null) }
    var sourceAudioPreviewId by remember(project.id) { mutableStateOf<String?>(null) }
    var musicPreviewUri by remember(project.id) { mutableStateOf<String?>(null) }
    var previewWidthPx by remember(project.id) { mutableFloatStateOf(1f) }
    var previewHeightPx by remember(project.id) { mutableFloatStateOf(1f) }
    var directPreviewTransform by remember(selectedClip?.id) { mutableStateOf(ClipTransform()) }
    var directGestureActive by remember(project.id) { mutableStateOf(false) }
    // ExoPlayer cannot drive the PROJECT clock through a gap, so the robust
    // one-layer fallback has a separate wall-clock transport for holes/clip switches.
    var fallbackTransportPlaying by remember(project.id) { mutableStateOf(false) }
    var fallbackAnchorPositionMs by remember(project.id) { mutableLongStateOf(0L) }
    var fallbackAnchorRealtimeMs by remember(project.id) { mutableLongStateOf(0L) }
    var linkArmedClipId by remember(project.id) { mutableStateOf<String?>(null) }

    val selectedLocalSourceMs = if (selectedIndex >= 0) {
        project.sourceTimeForProjectPosition(selectedIndex, currentPositionMs)
    } else 0L
    val selectedAbsoluteSourceMs = selectedClip?.let { it.trimStartMs + selectedLocalSourceMs }
    val selectedTransform = selectedClip?.transformAtSourceTime(selectedLocalSourceMs) ?: ClipTransform()
    val selectedBrightness = selectedClip?.brightnessAtSourceTime(selectedLocalSourceMs) ?: 1f
    val selectedVolume = selectedClip?.volumeAtSourceTime(selectedLocalSourceMs) ?: 1f
    val playheadOnSelectedClip = selectedClip != null && selectedIndex >= 0 &&
        currentPositionMs in project.clipStartMs(selectedIndex)..
        (project.clipStartMs(selectedIndex) + selectedClip.outputDurationMs)
    val selectedKeyframe = selectedClip?.keyframes
        ?.minByOrNull { abs(it.timeMs - selectedLocalSourceMs) }
        ?.takeIf { abs(it.timeMs - selectedLocalSourceMs) <= (150L * selectedClip.speed).toLong().coerceAtLeast(70L) }
    val selectedNextClip = project.nextClipOnTrack(selectedIndex)
    val selectedTransition = project.transitionAfter(selectedIndex)
    val selectedAudioVolume = project.audioTrack?.volumeAtProjectTime(currentPositionMs) ?: 0.5f
    val selectedAudioKeyframe = project.audioTrack?.keyframes
        ?.minByOrNull { abs(it.timeMs - currentPositionMs) }
        ?.takeIf { abs(it.timeMs - currentPositionMs) <= 150L }
    val selectedSourceAudio = project.sourceAudioTracks.firstOrNull { it.id == selectedSourceAudioId }
    val selectedSourceAudioTimeMs = selectedSourceAudio?.let { track ->
        (track.trimStartMs + currentPositionMs - track.timelineStartMs).coerceIn(track.trimStartMs, track.trimEndMs)
    }
    val selectedSourceAudioVolume = selectedSourceAudio?.let { track ->
        track.volumeAtSourceTime(selectedSourceAudioTimeMs ?: track.trimStartMs)
    } ?: 1f
    val selectedSourceAudioKeyframe = selectedSourceAudio?.keyframes
        ?.minByOrNull { abs(it.timeMs - (selectedSourceAudioTimeMs ?: 0L)) }
        ?.takeIf { abs(it.timeMs - (selectedSourceAudioTimeMs ?: 0L)) <= 150L }

    val fallbackVisual = fallbackClip?.let { clip ->
        v020FallbackVisual(
            project = project,
            clip = clip,
            projectPositionMs = currentPositionMs,
            directTransform = directPreviewTransform.takeIf { clip.id == selectedClip?.id },
        )
    }

    LaunchedEffect(selectedClip?.id, selectedTransform) {
        if (!directGestureActive) directPreviewTransform = selectedTransform
    }
    val addVideosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.addClips(it, currentPositionMs)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::setAudio)
    }

    // Simplified preview must select the VISUAL front lane, never stale selected footage
    // after its end or an invisible clip while a lower lane is actually on screen.
    fun previewClipFor(positionMs: Long): VideoClip? =
        VideoLayerPolicy.activeClip(project.clips, positionMs)

    fun syncFallback(positionMs: Long, playWhenReady: Boolean) {
        val clip = previewClipFor(positionMs)
        if (clip == null) {
            fallbackPlayer.stop()
            fallbackClip = null
            fallbackClipId = null
            return
        }
        if (fallbackClipId != clip.id) {
            val mediaItem = if (clip.mediaKind == VisualMediaKind.IMAGE) {
                MediaItem.Builder().setUri(clip.uri).setImageDurationMs(clip.sourceDurationMs).build()
            } else {
                MediaItem.fromUri(clip.uri)
            }
            fallbackPlayer.setMediaItem(mediaItem)
            fallbackPlayer.prepare()
            fallbackClipId = clip.id
        }
        fallbackClip = clip
        fallbackPlayer.setPlaybackParameters(PlaybackParameters(clip.speed.coerceIn(0.25f, 4f)))
        val projectDeltaMs = (positionMs - clip.timelineStartMs).coerceIn(0L, clip.outputDurationMs)
        val sourceDeltaMs = (projectDeltaMs * clip.speed).toLong()
        val sourcePositionMs = (clip.trimStartMs + sourceDeltaMs)
            .coerceIn(clip.trimStartMs, (clip.trimEndMs - 1L).coerceAtLeast(clip.trimStartMs))
        fallbackPlayer.seekTo(sourcePositionMs)
        if (playWhenReady) fallbackPlayer.play() else fallbackPlayer.pause()
    }

    fun syncStableAudio(positionMs: Long, playWhenReady: Boolean) {
        val activeSource = project.sourceAudioTracks
            .filter { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.outputDurationMs }
            .maxByOrNull { it.timelineTrackIndex }
        if (activeSource == null) {
            sourceAudioPlayer.pause()
            sourceAudioPreviewId = null
        } else {
            if (sourceAudioPreviewId != activeSource.id) {
                sourceAudioPlayer.setMediaItem(MediaItem.fromUri(activeSource.uri))
                sourceAudioPlayer.prepare()
                sourceAudioPreviewId = activeSource.id
            }
            val sourceTime = (activeSource.trimStartMs + positionMs - activeSource.timelineStartMs)
                .coerceIn(activeSource.trimStartMs, (activeSource.trimEndMs - 1L).coerceAtLeast(activeSource.trimStartMs))
            sourceAudioPlayer.volume = if (previewMuted) 0f else activeSource.volumeAtSourceTime(sourceTime)
            sourceAudioPlayer.seekTo(sourceTime)
            if (playWhenReady) sourceAudioPlayer.play() else sourceAudioPlayer.pause()
        }

        val music = project.audioTrack
        if (music == null || music.durationMs <= 0L) {
            musicPreviewPlayer.pause()
            musicPreviewUri = null
        } else {
            if (musicPreviewUri != music.uri) {
                musicPreviewPlayer.setMediaItem(MediaItem.fromUri(music.uri))
                musicPreviewPlayer.repeatMode = Player.REPEAT_MODE_ONE
                musicPreviewPlayer.prepare()
                musicPreviewUri = music.uri
            }
            musicPreviewPlayer.volume = if (previewMuted) 0f else music.volumeAtProjectTime(positionMs)
            musicPreviewPlayer.seekTo(positionMs % music.durationMs.coerceAtLeast(1L))
            if (playWhenReady) musicPreviewPlayer.play() else musicPreviewPlayer.pause()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Aperçu multipiste limité sur cet appareil"
                stablePreview = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(sourceAudioPlayer, musicPreviewPlayer) {
        onDispose {
            sourceAudioPlayer.release()
            musicPreviewPlayer.release()
        }
    }

    DisposableEffect(fallbackPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Aperçu piste impossible : ${error.localizedMessage ?: "format non décodable"}"
            }
        }
        fallbackPlayer.addListener(listener)
        onDispose {
            fallbackPlayer.removeListener(listener)
            fallbackPlayer.release()
        }
    }

    LaunchedEffect(player, fallbackPlayer, stablePreview, fallbackClip?.id) {
        while (true) {
            if (stablePreview) {
                if (fallbackTransportPlaying) {
                    val elapsed = (SystemClock.elapsedRealtime() - fallbackAnchorRealtimeMs).coerceAtLeast(0L)
                    val next = (fallbackAnchorPositionMs + elapsed).coerceAtMost(project.durationMs)
                    currentPositionMs = next
                    val front = previewClipFor(next)
                    if (front?.id != fallbackClip?.id) {
                        syncFallback(next, front != null)
                        syncStableAudio(next, true)
                    }
                    if (next >= project.durationMs) {
                        fallbackTransportPlaying = false
                        fallbackPlayer.pause()
                        sourceAudioPlayer.pause()
                        musicPreviewPlayer.pause()
                    }
                }
                isPlaying = fallbackTransportPlaying
                project.sourceAudioTracks
                    .filter { currentPositionMs >= it.timelineStartMs && currentPositionMs < it.timelineStartMs + it.outputDurationMs }
                    .maxByOrNull { it.timelineTrackIndex }
                    ?.let { source ->
                        val sourceTime = (source.trimStartMs + currentPositionMs - source.timelineStartMs)
                            .coerceIn(source.trimStartMs, source.trimEndMs)
                        sourceAudioPlayer.volume = if (previewMuted) 0f else source.volumeAtSourceTime(sourceTime)
                    }
                project.audioTrack?.let { music ->
                    musicPreviewPlayer.volume = if (previewMuted) 0f else music.volumeAtProjectTime(currentPositionMs)
                }
            } else {
                if (player.currentPosition >= 0L) currentPositionMs = player.currentPosition
                isPlaying = player.isPlaying
            }
            delay(80L)
        }
    }

    LaunchedEffect(unequalVideoSpans) { if (unequalVideoSpans) stablePreview = true }

    LaunchedEffect(project.updatedAt, project.clips.size, project.sourceAudioTracks.size, stablePreview, selectedClip?.timelineTrackIndex) {
        delay(120L)
        if (project.clips.isEmpty()) {
            player.stop()
            currentPositionMs = 0L
        } else {
            val restore = currentPositionMs.coerceIn(0L, project.durationMs.coerceAtLeast(1L))
            val resume = isPlaying
            if (stablePreview) {
                player.pause()
                fallbackTransportPlaying = resume
                fallbackAnchorPositionMs = restore
                fallbackAnchorRealtimeMs = SystemClock.elapsedRealtime()
                runCatching {
                    syncFallback(restore, resume)
                    syncStableAudio(restore, resume)
                    playbackError = null
                }.onFailure {
                    playbackError = it.localizedMessage ?: "Aperçu piste limité sur cet appareil"
                }
            } else {
                sourceAudioPlayer.pause()
                musicPreviewPlayer.pause()
                runCatching {
                    player.setComposition(
                        CompositionFactory.create(project, resolutionShortSide = previewShortSide, frameRate = 30),
                        restore,
                    )
                    player.prepare()
                    if (resume) player.play()
                    playbackError = null
                }.onFailure {
                    playbackError = it.localizedMessage ?: "Aperçu multipiste limité sur cet appareil"
                    stablePreview = true
                }
            }
        }
    }

    fun seekAndStop(candidate: Long) {
        val snapped = snapV06(project, candidate)
        player.pause()
        fallbackPlayer.pause()
        fallbackTransportPlaying = false
        isPlaying = false
        currentPositionMs = snapped
        if (stablePreview) {
            syncFallback(snapped, false)
            syncStableAudio(snapped, false)
        } else player.seekTo(snapped)
    }

    Scaffold(
        containerColor = FabBackground,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(FabBackground).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::closeProject) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Column(Modifier.weight(1f)) {
                    Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    val trackSummary = project.clips
                        .groupBy(VideoClip::timelineTrackIndex)
                        .toSortedMap()
                        .entries
                        .joinToString(" / ") { (track, clips) ->
                            val streams = clips.mapNotNull(VideoClip::sourceStreamIndex).distinct().sorted()
                            if (streams.isEmpty()) "V${track + 1}" else "V${track + 1}(S${streams.joinToString(",")})"
                        }
                    Text(
                        "EASYCUT v${com.fabvidedit.app.BuildConfig.VERSION_NAME} • ${trackSummary.ifBlank { "aucune piste visuelle" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::undo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Annuler")
                }
                IconButton(onClick = viewModel::redo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Rétablir")
                }
                Button(onClick = { showExportDialog = true }, enabled = project.clips.isNotEmpty()) {
                    Text("Exporter")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val sourceRatio = project.clips.firstNotNullOfOrNull { it.displayAspectRatio() }
                    ?: (16f / 9f)
                val outputRatio = (project.aspectRatio.ratio ?: sourceRatio).coerceIn(0.25f, 4f)
                val availableRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                val outputFrameWidth = if (availableRatio > outputRatio) {
                    (maxHeight.value * outputRatio).dp
                } else maxWidth
                val outputFrameHeight = if (availableRatio > outputRatio) {
                    maxHeight
                } else {
                    (maxWidth.value / outputRatio).dp
                }
                val sideMaskWidth = ((maxWidth.value - outputFrameWidth.value) / 2f).coerceAtLeast(0f).dp
                val topMaskHeight = ((maxHeight.value - outputFrameHeight.value) / 2f).coerceAtLeast(0f).dp
                if (project.clips.isNotEmpty()) {
                    // The editor frame is the touch coordinate system, NOT the whole black
                    // preview area. Keep the transparent gesture layer unscaled so that a
                    // 20-pixel finger drag stays 20 pixels after a 2x/4x zoom.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(outputFrameWidth, outputFrameHeight)
                            .onSizeChanged { frame ->
                                previewWidthPx = frame.width.toFloat().coerceAtLeast(1f)
                                previewHeightPx = frame.height.toFloat().coerceAtLeast(1f)
                            },
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                AspectRatioFrameLayout(ctx).apply {
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setAspectRatio(outputRatio)
                                    val texture = FabVidVideoTextureView(ctx).apply {
                                        onBindFailure = { _ ->
                                            playbackError = "Sortie vidéo multipiste indisponible : mode compatible activé"
                                            stablePreview = true
                                        }
                                    }
                                    addView(
                                        texture,
                                        android.widget.FrameLayout.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        ),
                                    )
                                    texture.bind(if (stablePreview) fallbackPlayer else player)
                                }
                            },
                            update = { frame ->
                                frame.setAspectRatio(outputRatio)
                                val texture = frame.getChildAt(0) as FabVidVideoTextureView
                                texture.onBindFailure = { _ ->
                                    playbackError = "Sortie vidéo multipiste indisponible : mode compatible activé"
                                    stablePreview = true
                                }
                                texture.bind(if (stablePreview) fallbackPlayer else player)
                                val rawAspectRatio = fallbackClip?.displayAspectRatio() ?: outputRatio
                                texture.applyPreviewTransform(
                                    if (stablePreview) fallbackVisual?.transform else null,
                                    sourceAspectRatio = rawAspectRatio,
                                    canvasAspectRatio = outputRatio,
                                )
                            },
                            onRelease = { frame ->
                                (frame.getChildAt(0) as? FabVidVideoTextureView)?.dispose()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (stablePreview && fallbackVisual == null) {
                                        alpha = 0f // hide the stale Surface frame throughout an empty interval
                                    } else if (stablePreview && fallbackVisual != null) {
                                        // TextureView owns the video FIT + zoom + rotation + pan.
                                        // Do NOT zoom AndroidView again: its outer transform was
                                        // a different geometry from Media3's FIT export canvas.
                                        alpha = fallbackVisual.alpha * (fallbackClip?.opacity ?: 0f)
                                    }
                                },
                        )

                        // Independent overlay: no inverse-scaled pointer coordinates, even
                        // when the video itself is already zoomed or rotated.
                        Box(
                            Modifier.fillMaxSize()
                                .pointerInput(selectedClip?.id, playheadOnSelectedClip) {
                                    if (selectedClip != null && playheadOnSelectedClip) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            val editPositionMs = currentPositionMs
                                            var working = directPreviewTransform
                                            var changed = false
                                            val rotationGate = PreviewRotationGate(thresholdDegrees = 10f)
                                            try {
                                                var hasPressedPointers: Boolean
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val pan = event.calculatePan()
                                                    // A new/lifted finger re-arms the intent threshold.
                                                    // One finger and noisy pinch never rotate the clip.
                                                    val twoStableFingers =
                                                        event.changes.count { it.pressed } == 2 &&
                                                        event.changes.count { it.pressed && it.previousPressed } == 2
                                                    val zoom = if (twoStableFingers) event.calculateZoom() else 1f
                                                    val rotationDelta = rotationGate.onRotationDelta(
                                                        deltaDegrees = if (twoStableFingers) event.calculateRotation() else 0f,
                                                        stableTwoFingers = twoStableFingers,
                                                    )
                                                    if (pan.x != 0f || pan.y != 0f || zoom != 1f ||
                                                        rotationDelta != 0f
                                                    ) {
                                                        if (!changed) {
                                                            player.pause()
                                                            fallbackPlayer.pause()
                                                            sourceAudioPlayer.pause()
                                                            musicPreviewPlayer.pause()
                                                            isPlaying = false
                                                            fallbackTransportPlaying = false
                                                            stablePreview = true
                                                            panel = EditorPanel.MOTION
                                                            inspectorOpen = false
                                                            directGestureActive = true
                                                        }
                                                        val center = event.calculateCentroid(useCurrent = false)
                                                        working = PreviewGestureMath.panZoom(
                                                            old = working,
                                                            frameWidthPx = size.width.toFloat(),
                                                            frameHeightPx = size.height.toFloat(),
                                                            centroidX = center.x,
                                                            centroidY = center.y,
                                                            panX = pan.x,
                                                            panY = pan.y,
                                                            zoom = zoom,
                                                            rotationDeltaDegrees = rotationDelta,
                                                        )
                                                        directPreviewTransform = working
                                                        changed = true
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                    hasPressedPointers = event.changes.any { it.pressed }
                                                } while (hasPressedPointers)
                                                // One project/history/keyframe update per completed gesture.
                                                if (changed) viewModel.setSelectedTransform(editPositionMs, working)
                                            } finally {
                                                directGestureActive = false
                                            }
                                        }
                                    }
                                },
                        )
                    }

                    // Output stencil: content may move/scale freely, but anything outside the
                    // final canvas is hidden exactly as it will be at export.
                    val stencil = Color.Black.copy(alpha = 0.97f)
                    if (sideMaskWidth.value > 0.5f) {
                        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(sideMaskWidth).background(stencil))
                        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(sideMaskWidth).background(stencil))
                    }
                    if (topMaskHeight.value > 0.5f) {
                        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topMaskHeight).background(stencil))
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(topMaskHeight).background(stencil))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(outputFrameWidth, outputFrameHeight)
                            .border(1.5.dp, FabMint.copy(alpha = 0.78f), RoundedCornerShape(2.dp)),
                    ) {
                        FilledIconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(34.dp),
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "Propriétés de sortie")
                        }
                    }

                    if (selectedClip != null) {
                        Text(
                            "${if (selectedKeyframe != null) "◆ KF" else "◇ BASE"}  X ${"%.2f".format(directPreviewTransform.positionX)}  Y ${"%.2f".format(directPreviewTransform.positionY)}  S ${"%.2f".format(directPreviewTransform.scaleX)}  R ${directPreviewTransform.rotationDegrees.roundToInt()}°",
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            color = if (selectedKeyframe != null) FabPink else FabMint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (stablePreview) {
                            "APERÇU SIMPLIFIÉ • " +
                                (fallbackClip?.let { "V${it.timelineTrackIndex + 1} • PISTE ACTIVE" } ?: "INTERVALLE VIDE")
                        } else "APERÇU MULTIPISTE • FIT",
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = FabMint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                playbackError?.let { message ->
                    Text(
                        "$message\nLa timeline et l’export restent disponibles.",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.78f)).padding(8.dp),
                    )
                }
            }

            V06PlaybackControls(
                currentPositionMs = currentPositionMs,
                durationMs = project.durationMs,
                isPlaying = isPlaying,
                showDiamond = playheadOnSelectedClip,
                diamondActive = selectedKeyframe != null,
                previewMuted = previewMuted,
                onToggleMute = {
                    previewMuted = !previewMuted
                    player.volume = if (previewMuted) 0f else 1f
                    fallbackPlayer.volume = if (previewMuted) 0f else 1f
                    if (stablePreview) syncStableAudio(currentPositionMs, isPlaying)
                },
                onTogglePlay = {
                    if (stablePreview) {
                        if (fallbackTransportPlaying) {
                            fallbackTransportPlaying = false
                            fallbackPlayer.pause()
                            sourceAudioPlayer.pause()
                            musicPreviewPlayer.pause()
                            isPlaying = false
                        } else {
                            if (currentPositionMs >= project.durationMs - 100L) currentPositionMs = 0L
                            fallbackAnchorPositionMs = currentPositionMs
                            fallbackAnchorRealtimeMs = SystemClock.elapsedRealtime()
                            fallbackTransportPlaying = true
                            isPlaying = true
                            syncFallback(currentPositionMs, true)
                            syncStableAudio(currentPositionMs, true)
                        }
                    } else {
                        if (isPlaying) player.pause() else {
                            if (currentPositionMs >= project.durationMs - 100L) {
                                currentPositionMs = 0L
                                player.seekTo(0L)
                            }
                            player.play()
                        }
                    }
                },
                onSeek = ::seekAndStop,
                onDiamond = {
                    if (selectedKeyframe == null) viewModel.addSelectedKeyframe(currentPositionMs)
                    else viewModel.removeSelectedKeyframe(currentPositionMs)
                    panel = EditorPanel.MOTION
                },
            )

            if (selectedClip != null || selectedSourceAudio != null || linkArmedClipId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(FabSurfaceHigh)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    selectedClip?.let { clip ->
                        Text("V${clip.timelineTrackIndex + 1}", color = FabMint, fontSize = 11.sp)
                        TextButton(onClick = { viewModel.changeSelectedLayer(1) },
                            enabled = VideoLayerPolicy.frontToBack(project).firstOrNull() != clip.timelineTrackIndex
                        ) { Text("Plan ↑", fontSize = 11.sp) }
                        TextButton(onClick = { viewModel.changeSelectedLayer(-1) },
                            enabled = VideoLayerPolicy.frontToBack(project).lastOrNull() != clip.timelineTrackIndex
                        ) { Text("Plan ↓", fontSize = 11.sp) }
                        TextButton(
                            onClick = viewModel::appendSelectedToMainTrack,
                            enabled = clip.timelineTrackIndex != 0 ||
                                project.clips.any { it.id != clip.id && it.timelineTrackIndex == 0 },
                        ) { Text("→ Suite V1", fontSize = 11.sp) }
                        TextButton(onClick = { viewModel.setClipSyncLocked(clip.id, !clip.syncLocked) }) {
                            Text(if (clip.syncLocked) "🔗 Aimanté" else "🔓 Libre", fontSize = 11.sp)
                        }
                        TextButton(onClick = { linkArmedClipId = if (linkArmedClipId == clip.id) null else clip.id }) {
                            Text(if (linkArmedClipId == clip.id) "✕ Annuler liaison" else "🔗+ Lier", fontSize = 11.sp)
                        }
                    }
                    selectedSourceAudio?.let { track ->
                        TextButton(onClick = { viewModel.setSourceAudioSyncLocked(track.id, !track.syncLocked) }) {
                            Text(if (track.syncLocked) "🔗 Audio aimanté" else "🔓 Audio libre", fontSize = 11.sp)
                        }
                    }
                    if (linkArmedClipId != null && selectedClip?.id != linkArmedClipId) {
                        Text("Touchez une vidéo, image ou piste audio", color = FabMint, fontSize = 10.sp)
                    }
                }
            }

            if (selectedClip != null && inspectorOpen && panel == EditorPanel.MOTION) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(FabSurfaceHigh)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Opacité ${(selectedClip.opacity * 100).roundToInt()} %",
                        fontSize = 11.sp, color = FabMint)
                    Slider(
                        value = selectedClip.opacity.coerceIn(0f, 1f),
                        onValueChange = viewModel::setSelectedOpacity,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }

            V06MultitrackTimeline(
                project = project,
                selectedClipId = selectedClipId,
                selectedSourceAudioId = selectedSourceAudioId,
                currentPositionMs = currentPositionMs,
                onSeek = ::seekAndStop,
                onSelectClip = { clip, position ->
                    val armed = linkArmedClipId
                    if (armed != null && armed != clip.id) {
                        viewModel.linkClipToClip(armed, clip.id)
                        linkArmedClipId = null
                    }
                    selectedSourceAudioId = null
                    viewModel.selectClip(clip.id)
                    panel = EditorPanel.CLIP
                    inspectorOpen = false
                    seekAndStop(position)
                },
                onKeyframe = { clip, keyframe ->
                    val index = project.clips.indexOfFirst { it.id == clip.id }
                    if (index >= 0) {
                        selectedSourceAudioId = null
                        viewModel.selectClip(clip.id)
                        panel = EditorPanel.MOTION
                        inspectorOpen = true
                        seekAndStop(project.projectPositionForSourceTime(index, keyframe.timeMs))
                    }
                },
                onAudioTrack = { panel = EditorPanel.AUDIO; inspectorOpen = true },
                onTextTrack = { panel = EditorPanel.TEXT; inspectorOpen = true },
                onMoveClip = viewModel::moveClip,
                onTrimClip = viewModel::trimClipOnTimeline,
                onDuplicateClip = { clip ->
                    selectedSourceAudioId = null
                    viewModel.selectClip(clip.id)
                    viewModel.duplicateSelected()
                },
                onSplitClip = { clip ->
                    selectedSourceAudioId = null
                    viewModel.selectClip(clip.id)
                    viewModel.splitSelectedAt(currentPositionMs)
                },
                onSelectSourceAudio = { track, position ->
                    linkArmedClipId?.let { armed ->
                        viewModel.linkClipToSourceAudio(armed, track.id)
                        linkArmedClipId = null
                    }
                    selectedSourceAudioId = track.id
                    viewModel.clearClipSelection()
                    panel = EditorPanel.AUDIO
                    inspectorOpen = true
                    seekAndStop(position)
                },
                onMoveSourceAudio = viewModel::moveSourceAudioTrack,
                onTrimSourceAudio = viewModel::trimSourceAudioTrack,
                onDuplicateSourceAudio = viewModel::duplicateSourceAudioTrack,
                onDeleteSourceAudio = { id ->
                    viewModel.removeSourceAudioTrack(id)
                    if (selectedSourceAudioId == id) selectedSourceAudioId = null
                },
            )

            V06Tools(
                selectedPanel = panel,
                inspectorOpen = inspectorOpen,
                clipSelected = selectedClip != null,
                onPanel = { requested ->
                    if (panel == requested && inspectorOpen) {
                        inspectorOpen = false
                    } else {
                        panel = requested
                        inspectorOpen = true
                    }
                },
                onSplit = { viewModel.splitSelectedAt(currentPositionMs) },
                onDelete = { viewModel.removeSelectedClip(); inspectorOpen = false },
                onText = {
                    editingText = null
                    showTextDialog = true
                    panel = EditorPanel.TEXT
                },
                onAdd = { addVideosLauncher.launch(arrayOf("video/*", "image/*")) },
            )

            if (inspectorOpen) Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp, max = 150.dp).background(FabSurface),
            ) {
                when (panel) {
                    EditorPanel.CLIP -> V06ClipInspector(
                        clip = selectedClip,
                        playheadSourceMs = selectedAbsoluteSourceMs,
                        currentVolume = selectedVolume,
                        onTrim = viewModel::trimSelected,
                        onSplit = { viewModel.splitSelectedAt(currentPositionMs) },
                        onSpeed = viewModel::setSelectedSpeed,
                        onVolume = { viewModel.setSelectedVolume(currentPositionMs, it) },
                        onFilter = viewModel::setSelectedFilter,
                        onRotate = viewModel::rotateSelected,
                        onDuplicate = viewModel::duplicateSelected,
                        onDelete = viewModel::removeSelectedClip,
                    )
                    EditorPanel.MOTION -> TransformInspector(
                        clip = selectedClip,
                        transform = selectedTransform,
                        brightness = selectedBrightness,
                        volume = selectedVolume,
                        keyframeAtPlayhead = selectedKeyframe != null,
                        selectedEasing = selectedKeyframe?.easing,
                        onTransform = { viewModel.setSelectedTransform(currentPositionMs, it) },
                        onBrightness = { viewModel.setSelectedBrightness(currentPositionMs, it) },
                        onVolume = { viewModel.setSelectedVolume(currentPositionMs, it) },
                        onAddKeyframe = { viewModel.addSelectedKeyframe(currentPositionMs) },
                        onRemoveKeyframe = { viewModel.removeSelectedKeyframe(currentPositionMs) },
                        onEasing = { viewModel.setSelectedKeyframeEasing(currentPositionMs, it) },
                        onReset = viewModel::resetSelectedTransform,
                    )
                    EditorPanel.TRANSITION -> TransitionInspector(
                        fromClip = selectedClip,
                        toClip = selectedNextClip,
                        transition = selectedTransition,
                        onType = viewModel::setTransitionAfterSelected,
                        onDuration = viewModel::setTransitionDurationAfterSelected,
                        onAutomatic = viewModel::applyAutomaticTransitions,
                        onRemove = viewModel::removeTransitionAfterSelected,
                        onClearAll = viewModel::clearTransitions,
                    )
                    EditorPanel.TEXT -> TextInspector(
                        layers = project.textLayers,
                        onAdd = {
                            editingText = null
                            showTextDialog = true
                        },
                        onEdit = {
                            editingText = it
                            showTextDialog = true
                        },
                        onDelete = viewModel::removeText,
                    )
                    EditorPanel.AUDIO -> if (selectedSourceAudio != null) {
                        V019SourceAudioInspector(
                            track = selectedSourceAudio,
                            currentVolume = selectedSourceAudioVolume,
                            keyframeAtPlayhead = selectedSourceAudioKeyframe != null,
                            onVolume = { viewModel.setSourceAudioTrackVolume(selectedSourceAudio.id, currentPositionMs, it) },
                            onAddKeyframe = { viewModel.addSourceAudioKeyframe(selectedSourceAudio.id, currentPositionMs) },
                            onRemoveKeyframe = { viewModel.removeSourceAudioKeyframe(selectedSourceAudio.id, currentPositionMs) },
                            onFadeIn = { viewModel.applySourceAudioFadeIn(selectedSourceAudio.id) },
                            onFadeOut = { viewModel.applySourceAudioFadeOut(selectedSourceAudio.id) },
                            onClear = { viewModel.clearSourceAudioKeyframes(selectedSourceAudio.id) },
                        )
                    } else {
                        AudioInspector(
                            track = project.audioTrack,
                            currentVolume = selectedAudioVolume,
                            keyframeAtPlayhead = selectedAudioKeyframe != null,
                            selectedEasing = selectedAudioKeyframe?.easing,
                            onChoose = { audioLauncher.launch(arrayOf("audio/*")) },
                            onVolume = { viewModel.setAudioVolume(currentPositionMs, it) },
                            onAddKeyframe = { viewModel.addAudioKeyframe(currentPositionMs) },
                            onRemoveKeyframe = { viewModel.removeAudioKeyframe(currentPositionMs) },
                            onEasing = { viewModel.setAudioKeyframeEasing(currentPositionMs, it) },
                            onRemove = viewModel::removeAudio,
                        )
                    }
                    EditorPanel.CANVAS -> CanvasInspector(project.aspectRatio, viewModel::setAspectRatio)
                }
            }
        }
    }

    if (showExportDialog) {
        V017ExportDialog(
            currentAspectRatio = project.aspectRatio,
            onAspectRatio = viewModel::setAspectRatio,
            onDismiss = { showExportDialog = false },
            onExport = { settings ->
                showExportDialog = false
                viewModel.startExport(settings)
            },
        )
    }

    if (showTextDialog) {
        V06TextDialog(
            existing = editingText,
            playheadMs = currentPositionMs,
            projectDurationMs = project.durationMs,
            onDismiss = { showTextDialog = false },
            onSave = { layer ->
                if (editingText == null) viewModel.addText(layer) else viewModel.updateText(layer)
                showTextDialog = false
            },
        )
    }

    when (val state = exportState) {
        ExportState.Idle -> Unit
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Export v${com.fabvidedit.app.BuildConfig.VERSION_NAME} multipiste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress} % • ${state.message}")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::cancelExport) { Text("Annuler") } },
        )
        is ExportState.Success -> AlertDialog(
            onDismissRequest = viewModel::clearExportResult,
            title = { Text("Export terminé") },
            text = { Text("${state.fileName}\nEnregistré dans Films/EASYCUT.") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, state.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Partager la vidéo"))
                }) { Text("Partager") }
            },
            dismissButton = { TextButton(onClick = viewModel::clearExportResult) { Text("Fermer") } },
        )
        is ExportState.Error -> AlertDialog(
            onDismissRequest = viewModel::clearExportResult,
            title = { Text("Export impossible") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::clearExportResult) { Text("Fermer") } },
        )
    }
}

private data class V020FallbackVisual(
    val transform: ClipTransform,
    val alpha: Float = 1f,
    val transitionLabel: String? = null,
)

private fun v020FallbackVisual(
    project: VideoProject,
    clip: VideoClip,
    projectPositionMs: Long,
    directTransform: ClipTransform?,
): V020FallbackVisual {
    val outputLocalMs = (projectPositionMs - clip.timelineStartMs).coerceIn(0L, clip.outputDurationMs)
    val sourceLocalMs = (outputLocalMs * clip.speed).toLong().coerceIn(0L, clip.sourceDurationMs)
    var transform = directTransform ?: clip.transformAtSourceTime(sourceLocalMs)
    var alpha = 1f
    var label: String? = null

    fun smooth(raw: Float): Float {
        val p = raw.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    val incoming = project.transitions.firstOrNull { it.toClipId == clip.id }
    val outgoing = project.transitions.firstOrNull { it.fromClipId == clip.id }
    val incomingDuration = ((incoming?.durationMs ?: 0L) / 2L).coerceAtLeast(1L)
    val outgoingDuration = ((outgoing?.durationMs ?: 0L) / 2L).coerceAtLeast(1L)

    val incomingProgress = incoming?.takeIf { outputLocalMs <= incomingDuration }?.let {
        smooth(outputLocalMs.toFloat() / incomingDuration.toFloat())
    }
    val outgoingProgress = outgoing?.takeIf { outputLocalMs >= clip.outputDurationMs - outgoingDuration }?.let {
        smooth((outputLocalMs - (clip.outputDurationMs - outgoingDuration)).toFloat() / outgoingDuration.toFloat())
    }

    incomingProgress?.let { p ->
        label = incoming?.type?.name
        when (incoming?.type) {
            TransitionType.FADE -> alpha *= p
            TransitionType.SLIDE_LEFT -> transform = transform.copy(positionX = transform.positionX + 2.05f * (1f - p))
            TransitionType.SLIDE_RIGHT -> transform = transform.copy(positionX = transform.positionX - 2.05f * (1f - p))
            TransitionType.ZOOM -> transform = transform.copy(
                scaleX = transform.scaleX * (0.68f + 0.32f * p),
                scaleY = transform.scaleY * (0.68f + 0.32f * p),
            )
            TransitionType.ROTATE -> transform = transform.copy(
                scaleX = transform.scaleX * (0.78f + 0.22f * p),
                scaleY = transform.scaleY * (0.78f + 0.22f * p),
                rotationDegrees = transform.rotationDegrees - 18f * (1f - p),
            )
            null -> Unit
        }
    }
    outgoingProgress?.let { p ->
        label = outgoing?.type?.name
        when (outgoing?.type) {
            TransitionType.FADE -> alpha *= 1f - p
            TransitionType.SLIDE_LEFT -> transform = transform.copy(positionX = transform.positionX - 2.05f * p)
            TransitionType.SLIDE_RIGHT -> transform = transform.copy(positionX = transform.positionX + 2.05f * p)
            TransitionType.ZOOM -> transform = transform.copy(
                scaleX = transform.scaleX * (1f + 0.34f * p),
                scaleY = transform.scaleY * (1f + 0.34f * p),
            )
            TransitionType.ROTATE -> transform = transform.copy(
                scaleX = transform.scaleX * (1f - 0.22f * p),
                scaleY = transform.scaleY * (1f - 0.22f * p),
                rotationDegrees = transform.rotationDegrees + 18f * p,
            )
            null -> Unit
        }
    }
    return V020FallbackVisual(transform.normalized(), alpha.coerceIn(0f, 1f), label)
}

@Composable
private fun V017ExportDialog(
    currentAspectRatio: AspectRatioPreset,
    onAspectRatio: (AspectRatioPreset) -> Unit,
    onDismiss: () -> Unit,
    onExport: (ExportSettings) -> Unit,
) {
    var outputAspectRatio by remember(currentAspectRatio) { mutableStateOf(currentAspectRatio) }
    val hevcAvailable = remember { VideoEncoderCapabilities.hasHardwareHevcEncoder() }
    var codec by remember { mutableStateOf(ExportVideoCodec.H264) }
    var frameRate by remember { mutableStateOf(ExportFrameRate.SOURCE) }
    var resolution by remember { mutableStateOf(ExportResolution.P1080) }
    var bitrate by remember { mutableStateOf(ExportBitrate.AUTO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages de sortie") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                V017ChoiceRow("Conteneur", listOf("MP4"), "MP4") { }
                V017ChoiceRow(
                    "Format / cadre de sortie",
                    AspectRatioPreset.entries.map { it.label },
                    outputAspectRatio.label,
                ) { label ->
                    val preset = AspectRatioPreset.entries.first { it.label == label }
                    outputAspectRatio = preset
                    onAspectRatio(preset)
                }
                V017ChoiceRow("Codec vidéo", ExportVideoCodec.entries.filter { it != ExportVideoCodec.H265 || hevcAvailable }.map { it.label }, codec.label) { label ->
                    codec = ExportVideoCodec.entries.first { it.label == label }
                }
                V017ChoiceRow("Images / seconde", ExportFrameRate.entries.map { it.label }, frameRate.label) { label ->
                    frameRate = ExportFrameRate.entries.first { it.label == label }
                }
                V017ChoiceRow("Résolution", ExportResolution.entries.map { it.label }, resolution.label) { label ->
                    resolution = ExportResolution.entries.first { it.label == label }
                }
                V017ChoiceRow("Débit vidéo", ExportBitrate.entries.map { it.label }, bitrate.label) { label ->
                    bitrate = ExportBitrate.entries.first { it.label == label }
                }
                Text(
                    if (hevcAvailable) "MP4 • AAC. H.265 disponible selon les capacités du téléphone." else "MP4 • AAC. H.265 indisponible : encodeur matériel HEVC absent.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onExport(ExportSettings(codec, frameRate, resolution, bitrate)) }) {
                Text("Exporter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun V017ChoiceRow(
    title: String,
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                Text(
                    choice,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (choice == selected) FabPink.copy(alpha = 0.22f) else FabSurfaceHigh)
                        .border(1.dp, if (choice == selected) FabPink else Color.Transparent, RoundedCornerShape(9.dp))
                        .pointerInput(choice) { detectTapGestures { onSelect(choice) } }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    color = if (choice == selected) FabPink else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = if (choice == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun V06PlaybackControls(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showDiamond: Boolean,
    diamondActive: Boolean,
    previewMuted: Boolean,
    onToggleMute: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onDiamond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FabBackground).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(44.dp)) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
        }
        IconButton(onClick = onToggleMute, modifier = Modifier.size(38.dp)) {
            Icon(
                if (previewMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (previewMuted) "Activer le son de prévisualisation" else "Couper le son de prévisualisation",
                tint = if (previewMuted) MaterialTheme.colorScheme.onSurfaceVariant else FabMint,
            )
        }
        Text(formatDuration(currentPositionMs), fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
        Slider(
            value = currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(formatDuration(durationMs), fontSize = 10.sp)
        if (showDiamond) {
            IconButton(onClick = onDiamond) {
                Box(
                    Modifier.size(18.dp).rotate(45f).clip(RoundedCornerShape(2.dp))
                        .background(if (diamondActive) FabPink else FabSurfaceHigh)
                        .border(2.dp, if (diamondActive) FabPink else FabMint, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun V06MultitrackTimeline(
    project: VideoProject,
    selectedClipId: String?,
    selectedSourceAudioId: String?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onSelectClip: (VideoClip, Long) -> Unit,
    onKeyframe: (VideoClip, TransformKeyframe) -> Unit,
    onAudioTrack: () -> Unit,
    onTextTrack: () -> Unit,
    onMoveClip: (String, Int, Long) -> Unit,
    onTrimClip: (String, Long, Long, Boolean) -> Unit,
    onDuplicateClip: (VideoClip) -> Unit,
    onSplitClip: (VideoClip) -> Unit,
    onSelectSourceAudio: (SourceAudioTrack, Long) -> Unit,
    onMoveSourceAudio: (String, Int, Long) -> Unit,
    onTrimSourceAudio: (String, Long, Long, Boolean) -> Unit,
    onDuplicateSourceAudio: (String) -> Unit,
    onDeleteSourceAudio: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val duration = project.durationMs.coerceAtLeast(1L)
    val rawWidthAt100 = (duration / 1000f) * 18f
    val fitZoom = (360f / rawWidthAt100.coerceAtLeast(1f)).coerceIn(0.05f, 8f)
    var timelineZoom by remember(project.id) { mutableFloatStateOf(fitZoom) }
    val widthDp = (rawWidthAt100 * timelineZoom).coerceIn(360f, 40_000f).dp
    val videoTracks = project.clips.groupBy(VideoClip::timelineTrackIndex)
    val highestUsedTrack = videoTracks.keys.maxOrNull() ?: 0
    val visualVideoTrackCount = maxOf(6, highestUsedTrack + 1)
    val visualTrackIndexes = (0 until visualVideoTrackCount).reversed().toList()
    val sourceAudioByLane = project.sourceAudioTracks.groupBy(SourceAudioTrack::timelineTrackIndex)
    val highestAudioTrack = sourceAudioByLane.keys.maxOrNull() ?: 0
    val visualAudioTrackCount = if (project.sourceAudioTracks.isEmpty()) 0 else maxOf(2, highestAudioTrack + 1)
    val rows = visualVideoTrackCount + visualAudioTrackCount +
        (if (project.audioTrack != null) 1 else 0) + (if (project.textLayers.isNotEmpty()) 1 else 0)
    val visibleRows = 6

    Box(Modifier.fillMaxWidth().height((26 + visibleRows * 42).dp).background(Color(0xFF100E15))) {
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScroll)) {
        Column(Modifier.width(68.dp)) {
            V06ZoomHeader(
                zoom = timelineZoom,
                fitZoom = fitZoom,
                onZoom = { timelineZoom = it.coerceIn(0.05f, 8f) },
            )
            visualTrackIndexes.forEach { track ->
                val clips = videoTracks[track].orEmpty()
                val streams = clips.mapNotNull(VideoClip::sourceStreamIndex).distinct().sorted()
                val suffix = if (streams.isEmpty()) "" else " S${streams.joinToString(",")}"
                V06TrackLabel("V${track + 1}$suffix", if (clips.isEmpty()) FabPink.copy(alpha = 0.45f) else FabPink)
            }
            repeat(visualAudioTrackCount) { index ->
                val occupied = sourceAudioByLane[index].orEmpty().isNotEmpty()
                V06TrackLabel("A${index + 1}", if (occupied) FabMint else FabMint.copy(alpha = 0.45f))
            }
            project.audioTrack?.let { V06TrackLabel("♫", FabMint) }
            if (project.textLayers.isNotEmpty()) V06TrackLabel("T", FabPurple)
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll)) {
            V06Ruler(widthDp, duration, currentPositionMs, onSeek)
            val maxTrackIndex = visualVideoTrackCount - 1
            visualTrackIndexes.forEach { trackIndex ->
                val clips = videoTracks[trackIndex].orEmpty()
                V06VideoLane(
                    trackIndex = trackIndex,
                    maxTrackIndex = maxTrackIndex,
                    width = widthDp,
                    durationMs = duration,
                    clips = clips.sortedBy(VideoClip::timelineStartMs),
                    selectedClipId = selectedClipId,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek,
                    onSelectClip = onSelectClip,
                    onKeyframe = onKeyframe,
                    onMoveClip = onMoveClip,
                    onTrimClip = onTrimClip,
                    onDuplicateClip = onDuplicateClip,
                    onSplitClip = onSplitClip,
                )
            }
            val maxAudioTrackIndex = (visualAudioTrackCount - 1).coerceAtLeast(0)
            repeat(visualAudioTrackCount) { audioTrackIndex ->
                V018AudioLane(
                    trackIndex = audioTrackIndex,
                    maxTrackIndex = maxAudioTrackIndex,
                    width = widthDp,
                    durationMs = duration,
                    tracks = sourceAudioByLane[audioTrackIndex].orEmpty().sortedBy(SourceAudioTrack::timelineStartMs),
                    selectedAudioId = selectedSourceAudioId,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek,
                    onSelect = onSelectSourceAudio,
                    onMove = onMoveSourceAudio,
                    onTrim = onTrimSourceAudio,
                    onDuplicate = onDuplicateSourceAudio,
                    onDelete = onDeleteSourceAudio,
                )
            }
            project.audioTrack?.let { track ->
                V06SimpleLane(
                    width = widthDp,
                    durationMs = duration,
                    startMs = 0L,
                    itemDurationMs = duration,
                    label = "Musique • ${track.name}",
                    color = FabMint,
                    waveformUri = track.uri,
                    waveformStartMs = 0L,
                    waveformEndMs = track.durationMs,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek,
                    onClick = onAudioTrack,
                )
            }
            if (project.textLayers.isNotEmpty()) {
                V06TextLane(widthDp, duration, project.textLayers, currentPositionMs, onSeek, onTextTrack)
            }
        }
        }
    }
}

@Composable
private fun V06ZoomHeader(zoom: Float, fitZoom: Float, onZoom: (Float) -> Unit) {
    Row(
        Modifier.width(68.dp).height(24.dp).background(Color(0xFF16131D)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            "−",
            color = FabMint,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pointerInput(zoom) { detectTapGestures { onZoom(zoom / 1.45f) } },
        )
        Text(
            if (zoom <= fitZoom * 1.05f) "FIT" else "${(zoom / fitZoom * 100f).roundToInt()}%",
            color = Color.White,
            fontSize = 7.sp,
            modifier = Modifier.pointerInput(fitZoom) { detectTapGestures { onZoom(fitZoom) } },
        )
        Text(
            "+",
            color = FabMint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pointerInput(zoom) { detectTapGestures { onZoom(zoom * 1.45f) } },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.V06TrimHandle(
    left: Boolean,
    clip: VideoClip,
    durationMs: Long,
    timelineWidthPx: Float,
    onTrimClip: (String, Long, Long, Boolean) -> Unit,
) {
    var dragPx by remember(clip.id, left, clip.trimStartMs, clip.trimEndMs) { mutableFloatStateOf(0f) }
    val modifier = if (left) Modifier.align(Alignment.CenterStart) else Modifier.align(Alignment.CenterEnd)
    Box(
        modifier
            .width(10.dp)
            .fillMaxHeight()
            .background(FabPink.copy(alpha = 0.92f))
            .pointerInput(clip.id, left, clip.trimStartMs, clip.trimEndMs, durationMs) {
                detectDragGestures(
                    onDragEnd = {
                        val deltaProjectMs = (dragPx / timelineWidthPx * durationMs.toFloat()).toLong()
                        val deltaSourceMs = (deltaProjectMs * clip.speed).toLong()
                        if (left) {
                            val minStart = maxOf(0L, clip.trimStartMs - (clip.timelineStartMs * clip.speed).toLong())
                            val start = (clip.trimStartMs + deltaSourceMs)
                                .coerceIn(minStart, (clip.trimEndMs - 250L).coerceAtLeast(minStart))
                            onTrimClip(clip.id, start, clip.trimEndMs, true)
                        } else {
                            val end = (clip.trimEndMs + deltaSourceMs)
                                .coerceIn(clip.trimStartMs + 250L, clip.durationMs)
                            onTrimClip(clip.id, clip.trimStartMs, end, false)
                        }
                        dragPx = 0f
                    },
                    onDragCancel = { dragPx = 0f },
                ) { change, amount ->
                    change.consume()
                    dragPx += amount.x
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(16.dp).background(Color.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun V06TrackLabel(label: String, color: Color) {
    Box(Modifier.width(68.dp).height(42.dp), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun V06Ruler(width: Dp, durationMs: Long, currentPositionMs: Long, onSeek: (Long) -> Unit) {
    Box(
        Modifier.width(width).height(24.dp).background(Color(0xFF16131D))
            .pointerInput(durationMs) {
                detectTapGestures { tap ->
                    onSeek((tap.x / size.width.coerceAtLeast(1) * durationMs).toLong())
                }
            },
    ) {
        val seconds = (durationMs / 1000L).coerceAtLeast(1L)
        val step = when {
            seconds <= 30 -> 5L
            seconds <= 120 -> 10L
            seconds <= 600 -> 30L
            else -> 60L
        }
        var second = 0L
        while (second <= seconds) {
            val progress = second * 1000f / durationMs.toFloat()
            Text(
                "${second}s",
                modifier = Modifier.offset(x = width * progress.coerceIn(0f, 1f) + 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
            )
            second += step
        }
        V06Playhead(width, durationMs, currentPositionMs, 24.dp)
    }
}

@Composable
private fun V06VideoLane(
    trackIndex: Int,
    maxTrackIndex: Int,
    width: Dp,
    durationMs: Long,
    clips: List<VideoClip>,
    selectedClipId: String?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onSelectClip: (VideoClip, Long) -> Unit,
    onKeyframe: (VideoClip, TransformKeyframe) -> Unit,
    onMoveClip: (String, Int, Long) -> Unit,
    onTrimClip: (String, Long, Long, Boolean) -> Unit,
    onDuplicateClip: (VideoClip) -> Unit,
    onSplitClip: (VideoClip) -> Unit,
) {
    val density = LocalDensity.current
    val timelineWidthPx = with(density) { width.toPx() }.coerceAtLeast(1f)
    val rowHeightPx = with(density) { 42.dp.toPx() }.coerceAtLeast(1f)
    Box(Modifier.width(width).height(42.dp).background(if (clips.isEmpty()) Color(0xFF0F0D14) else Color(0xFF131019)).pointerInput(durationMs) {
        detectTapGestures { tap -> onSeek((tap.x / size.width.coerceAtLeast(1) * durationMs).toLong()) }
    }) {
        clips.forEach { clip ->
            val startProgress = clip.timelineStartMs.toFloat() / durationMs.toFloat()
            val durationProgress = clip.outputDurationMs.toFloat() / durationMs.toFloat()
            val clipWidth = (width * durationProgress).coerceAtLeast(46.dp)
            var dragX by remember(clip.id) { mutableStateOf(0f) }
            var dragY by remember(clip.id) { mutableStateOf(0f) }
            val dragXDp = with(density) { dragX.toDp() }
            val dragYDp = with(density) { dragY.toDp() }
            Box(
                modifier = Modifier.offset(x = width * startProgress + dragXDp, y = dragYDp).padding(vertical = 3.dp)
                    .width(clipWidth).height(36.dp).clip(RoundedCornerShape(7.dp))
                    .background(FabPurple.copy(alpha = if (dragX != 0f || dragY != 0f) 0.72f else 0.44f))
                    .border(if (clip.id == selectedClipId) 2.dp else 1.dp, if (clip.id == selectedClipId) FabPink else FabPurple, RoundedCornerShape(7.dp))
                    .pointerInput(clip.id, clip.outputDurationMs) {
                        detectTapGestures { tap ->
                            val local = (tap.x / size.width.coerceAtLeast(1) * clip.outputDurationMs).toLong()
                            onSelectClip(clip, clip.timelineStartMs + local)
                        }
                    }
                    .pointerInput(clip.id, durationMs, trackIndex, maxTrackIndex) {
                        detectDragGesturesAfterLongPress(
                            // Selection triggers a seek and may recompose trim handles while
                            // the pointer is down. Keep this gesture stable; select on drop.
                            onDragStart = { dragX = 0f; dragY = 0f },
                            onDragEnd = {
                                val deltaMs = (dragX / timelineWidthPx * durationMs.toFloat()).toLong()
                                val deltaTrack = (-dragY / rowHeightPx).roundToInt()
                                val targetTrack = (trackIndex + deltaTrack).coerceIn(0, maxTrackIndex)
                                onMoveClip(clip.id, targetTrack, (clip.timelineStartMs + deltaMs).coerceAtLeast(0L))
                                onSelectClip(clip, (clip.timelineStartMs + deltaMs).coerceAtLeast(0L))
                                dragX = 0f; dragY = 0f
                            },
                            onDragCancel = { dragX = 0f; dragY = 0f },
                        ) { change, dragAmount ->
                            change.consume(); dragX += dragAmount.x; dragY += dragAmount.y
                        }
                    },
            ) {
                V06ClipFilmstrip(clip = clip, clipWidth = clipWidth)
                if (clip.id == selectedClipId) {
                    V06TrimHandle(
                        left = true,
                        clip = clip,
                        durationMs = durationMs,
                        timelineWidthPx = timelineWidthPx,
                        onTrimClip = onTrimClip,
                    )
                    V06TrimHandle(
                        left = false,
                        clip = clip,
                        durationMs = durationMs,
                        timelineWidthPx = timelineWidthPx,
                        onTrimClip = onTrimClip,
                    )
                }
                if (clip.id == selectedClipId) {
                    Text(
                        "⧉",
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 2.dp)
                            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .pointerInput(clip.id) { detectTapGestures { onDuplicateClip(clip) } },
                        color = FabMint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (clip.id == selectedClipId &&
                    currentPositionMs > clip.timelineStartMs + 250L &&
                    currentPositionMs < clip.timelineStartMs + clip.outputDurationMs - 250L
                ) {
                    Text(
                        "✂",
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                            .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .pointerInput(clip.id, currentPositionMs) {
                                detectTapGestures { onSplitClip(clip) }
                            },
                        color = FabMint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                clip.keyframes.forEach { keyframe ->
                    val k = keyframe.timeMs.toFloat() / clip.sourceDurationMs.coerceAtLeast(1L).toFloat()
                    Box(Modifier.align(Alignment.BottomStart).offset(x = clipWidth * k.coerceIn(0f, 1f) - 5.dp, y = (-3).dp).size(10.dp).rotate(45f).clip(RoundedCornerShape(1.dp)).background(FabPink).pointerInput(keyframe.id) { detectTapGestures { onKeyframe(clip, keyframe) } })
                }
            }
        }
        V06Playhead(width, durationMs, currentPositionMs, 42.dp)
    }
}

@Composable
private fun V06ClipFilmstrip(clip: VideoClip, clipWidth: Dp) {
    val context = LocalContext.current
    var thumbnail by remember(clip.id, clip.uri, clip.trimStartMs) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(clip.id, clip.uri, clip.trimStartMs) {
        thumbnail = TimelineThumbnailCache.load(context, clip)
    }

    Box(Modifier.fillMaxSize()) {
        val bitmap = thumbnail
        if (bitmap != null) {
            val tileWidth = 52.dp
            val tileCount = (clipWidth.value / tileWidth.value).roundToInt().coerceIn(1, 12)
            Row(Modifier.fillMaxSize()) {
                repeat(tileCount) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(tileWidth).fillMaxHeight(),
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                repeat((clipWidth.value / 52f).roundToInt().coerceIn(1, 12)) { index ->
                    Box(
                        Modifier.width(52.dp).fillMaxHeight()
                            .background(if (index % 2 == 0) FabSurfaceHigh else Color(0xFF24202D)),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${clip.width}×${clip.height}",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                formatDuration(clip.outputDurationMs),
                color = FabMint,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun V018AudioLane(
    trackIndex: Int,
    maxTrackIndex: Int,
    width: Dp,
    durationMs: Long,
    tracks: List<SourceAudioTrack>,
    selectedAudioId: String?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (SourceAudioTrack, Long) -> Unit,
    onMove: (String, Int, Long) -> Unit,
    onTrim: (String, Long, Long, Boolean) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val density = LocalDensity.current
    val timelineWidthPx = with(density) { width.toPx() }.coerceAtLeast(1f)
    val rowHeightPx = with(density) { 42.dp.toPx() }.coerceAtLeast(1f)
    Box(
        Modifier.width(width).height(42.dp)
            .background(if (tracks.isEmpty()) Color(0xFF0F0D14) else Color(0xFF11171A))
            .pointerInput(durationMs) {
                detectTapGestures { tap -> onSeek((tap.x / size.width.coerceAtLeast(1) * durationMs).toLong()) }
            },
    ) {
        tracks.forEach { track ->
            val startProgress = track.timelineStartMs.toFloat() / durationMs.toFloat()
            val durationProgress = track.outputDurationMs.toFloat() / durationMs.toFloat()
            val itemWidth = (width * durationProgress).coerceAtLeast(54.dp)
            var dragX by remember(track.id) { mutableFloatStateOf(0f) }
            var dragY by remember(track.id) { mutableFloatStateOf(0f) }
            val dragXDp = with(density) { dragX.toDp() }
            val dragYDp = with(density) { dragY.toDp() }
            Box(
                Modifier.offset(x = width * startProgress + dragXDp, y = dragYDp)
                    .padding(vertical = 4.dp).width(itemWidth).height(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FabMint.copy(alpha = if (dragX != 0f || dragY != 0f) 0.26f else 0.16f))
                    .border(if (track.id == selectedAudioId) 2.dp else 1.dp, if (track.id == selectedAudioId) Color.White else FabMint, RoundedCornerShape(7.dp))
                    .pointerInput(track.id, track.outputDurationMs) {
                        detectTapGestures { tap ->
                            val local = (tap.x / size.width.coerceAtLeast(1) * track.outputDurationMs).toLong()
                            onSelect(track, track.timelineStartMs + local)
                        }
                    }
                    .pointerInput(track.id, durationMs, trackIndex, maxTrackIndex) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onSelect(track, track.timelineStartMs) },
                            onDragEnd = {
                                val deltaMs = (dragX / timelineWidthPx * durationMs.toFloat()).toLong()
                                val deltaTrack = (dragY / rowHeightPx).roundToInt()
                                val targetTrack = (trackIndex + deltaTrack).coerceIn(0, maxTrackIndex)
                                onMove(track.id, targetTrack, (track.timelineStartMs + deltaMs).coerceAtLeast(0L))
                                dragX = 0f
                                dragY = 0f
                            },
                            onDragCancel = { dragX = 0f; dragY = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount.x
                            dragY += amount.y
                        }
                    },
            ) {
                V018Waveform(
                    uri = track.uri,
                    trimStartMs = track.trimStartMs,
                    trimEndMs = track.trimEndMs,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp),
                )
                track.keyframes
                    .filter { it.timeMs in track.trimStartMs..track.trimEndMs }
                    .forEach { keyframe ->
                        val progress = (keyframe.timeMs - track.trimStartMs).toFloat() / track.sourceDurationMs.coerceAtLeast(1L).toFloat()
                        Box(
                            Modifier.align(Alignment.BottomStart)
                                .offset(x = itemWidth * progress.coerceIn(0f, 1f) - 5.dp, y = (-5).dp)
                                .size(10.dp).rotate(45f).clip(RoundedCornerShape(1.dp))
                                .background(FabPink)
                                .pointerInput(track.id, keyframe.id) {
                                    detectTapGestures {
                                        onSelect(track, track.timelineStartMs + keyframe.timeMs - track.trimStartMs)
                                    }
                                },
                        )
                    }
                Text(
                    "A${track.timelineTrackIndex + 1} • ${formatDuration(track.outputDurationMs)}",
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f)).padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
                if (track.id == selectedAudioId) {
                    V018AudioTrimHandle(true, track, durationMs, timelineWidthPx, onTrim)
                    V018AudioTrimHandle(false, track, durationMs, timelineWidthPx, onTrim)
                    Text(
                        "⧉",
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 25.dp, top = 1.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.70f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .pointerInput(track.id) { detectTapGestures { onDuplicate(track.id) } },
                        color = FabMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "×",
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 11.dp, top = 1.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.70f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .pointerInput(track.id) { detectTapGestures { onDelete(track.id) } },
                        color = FabPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        V06Playhead(width, durationMs, currentPositionMs, 42.dp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.V018AudioTrimHandle(
    left: Boolean,
    track: SourceAudioTrack,
    durationMs: Long,
    timelineWidthPx: Float,
    onTrim: (String, Long, Long, Boolean) -> Unit,
) {
    var dragPx by remember(track.id, left, track.trimStartMs, track.trimEndMs) { mutableFloatStateOf(0f) }
    val modifier = if (left) Modifier.align(Alignment.CenterStart) else Modifier.align(Alignment.CenterEnd)
    Box(
        modifier.width(12.dp).fillMaxHeight().background(FabMint.copy(alpha = 0.88f))
            .pointerInput(track.id, left, track.trimStartMs, track.trimEndMs, durationMs) {
                detectDragGestures(
                    onDragEnd = {
                        val deltaMs = (dragPx / timelineWidthPx * durationMs.toFloat()).toLong()
                        if (left) {
                            val minStart = maxOf(0L, track.trimStartMs - track.timelineStartMs)
                            val start = (track.trimStartMs + deltaMs)
                                .coerceIn(minStart, (track.trimEndMs - 120L).coerceAtLeast(minStart))
                            onTrim(track.id, start, track.trimEndMs, true)
                        } else {
                            val end = (track.trimEndMs + deltaMs)
                                .coerceIn(track.trimStartMs + 120L, track.durationMs)
                            onTrim(track.id, track.trimStartMs, end, false)
                        }
                        dragPx = 0f
                    },
                    onDragCancel = { dragPx = 0f },
                ) { change, amount ->
                    change.consume()
                    dragPx += amount.x
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(17.dp).background(Color.White))
    }
}

@Composable
private fun V019SourceAudioInspector(
    track: SourceAudioTrack,
    currentVolume: Float,
    keyframeAtPlayhead: Boolean,
    onVolume: (Float) -> Unit,
    onAddKeyframe: () -> Unit,
    onRemoveKeyframe: () -> Unit,
    onFadeIn: () -> Unit,
    onFadeOut: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A${track.timelineTrackIndex + 1} • Courbe volume • ${track.keyframes.size} KF",
                modifier = Modifier.weight(1f),
                color = FabMint,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Text("${(currentVolume * 100).roundToInt()} %", fontSize = 10.sp)
        }
        Slider(
            value = currentVolume.coerceIn(0f, 1f),
            onValueChange = onVolume,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = if (keyframeAtPlayhead) onRemoveKeyframe else onAddKeyframe) {
                Text(if (keyframeAtPlayhead) "◆ Suppr KF" else "◇ Ajouter KF")
            }
            TextButton(onClick = onFadeIn) { Text("↗ Fondu entrée 1 s") }
            TextButton(onClick = onFadeOut) { Text("↘ Fondu sortie 1 s") }
            TextButton(onClick = onClear, enabled = track.keyframes.isNotEmpty()) { Text("Effacer KF") }
        }
    }
}

@Composable
private fun V018Waveform(
    uri: String,
    trimStartMs: Long,
    trimEndMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var waveform by remember(uri, trimStartMs, trimEndMs) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri, trimStartMs, trimEndMs) {
        waveform = TimelineWaveformCache.load(context, uri, trimStartMs, trimEndMs)
    }
    Box(modifier) {
        val bitmap = waveform
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Courbe audio",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(FabMint.copy(alpha = 0.55f)))
        }
    }
}

@Composable
private fun V06SimpleLane(
    width: Dp,
    durationMs: Long,
    startMs: Long,
    itemDurationMs: Long,
    label: String,
    color: Color,
    waveformUri: String? = null,
    waveformStartMs: Long = 0L,
    waveformEndMs: Long = Long.MAX_VALUE,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
) {
    val start = startMs.toFloat() / durationMs.toFloat()
    val itemWidth = (width * (itemDurationMs.toFloat() / durationMs.toFloat())).coerceAtLeast(46.dp)
    Box(
        Modifier.width(width).height(42.dp).background(Color(0xFF131019))
            .pointerInput(durationMs) {
                detectTapGestures { tap ->
                    onClick()
                    onSeek((tap.x / size.width.coerceAtLeast(1) * durationMs).toLong())
                }
            },
    ) {
        Box(
            Modifier.offset(x = width * start).padding(vertical = 4.dp).width(itemWidth).height(34.dp)
                .clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.20f))
                .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            waveformUri?.let { uri ->
                V018Waveform(
                    uri = uri,
                    trimStartMs = waveformStartMs,
                    trimEndMs = waveformEndMs,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            }
            Text(
                label,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 7.dp, vertical = 1.dp),
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        V06Playhead(width, durationMs, currentPositionMs, 42.dp)
    }
}

@Composable
private fun V06TextLane(
    width: Dp,
    durationMs: Long,
    layers: List<TextLayer>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(width).height(42.dp).background(Color(0xFF131019))
            .pointerInput(durationMs) {
                detectTapGestures { tap ->
                    onClick()
                    onSeek((tap.x / size.width.coerceAtLeast(1) * durationMs).toLong())
                }
            },
    ) {
        layers.forEach { layer ->
            val start = layer.startMs.toFloat() / durationMs.toFloat()
            val itemWidth = (width * ((layer.endMs - layer.startMs).coerceAtLeast(1L).toFloat() / durationMs.toFloat()))
                .coerceAtLeast(46.dp)
            Box(
                Modifier.offset(x = width * start).padding(vertical = 4.dp).width(itemWidth).height(34.dp)
                    .clip(RoundedCornerShape(7.dp)).background(FabPurple.copy(alpha = 0.22f))
                    .border(1.dp, FabPurple, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(layer.text, modifier = Modifier.padding(horizontal = 7.dp), fontSize = 9.sp, color = Color.White, maxLines = 1)
            }
        }
        V06Playhead(width, durationMs, currentPositionMs, 42.dp)
    }
}

@Composable
private fun V06Playhead(width: Dp, durationMs: Long, currentPositionMs: Long, height: Dp) {
    val progress = currentPositionMs.coerceIn(0L, durationMs).toFloat() / durationMs.coerceAtLeast(1L).toFloat()
    Box(Modifier.offset(x = width * progress.coerceIn(0f, 1f) - 1.dp).width(2.dp).height(height).background(Color.White))
}

@Composable
private fun V06Tools(
    selectedPanel: EditorPanel,
    inspectorOpen: Boolean,
    clipSelected: Boolean,
    onPanel: (EditorPanel) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onText: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FabBackground).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        V06Tool(Icons.Rounded.ContentCut, "Scinder", false, clipSelected, onSplit)
        V06Tool(Icons.Rounded.Tune, "Modifier", inspectorOpen && selectedPanel == EditorPanel.CLIP, clipSelected) { onPanel(EditorPanel.CLIP) }
        V06Tool(Icons.Rounded.Animation, "Keyframes", inspectorOpen && selectedPanel == EditorPanel.MOTION, clipSelected) { onPanel(EditorPanel.MOTION) }
        V06Tool(Icons.Rounded.AutoFixHigh, "Transition", inspectorOpen && selectedPanel == EditorPanel.TRANSITION, clipSelected) { onPanel(EditorPanel.TRANSITION) }
        V06Tool(Icons.Rounded.Delete, "Supprimer", false, clipSelected, onDelete)
        V06Tool(Icons.Rounded.TextFields, "Texte", inspectorOpen && selectedPanel == EditorPanel.TEXT, true, onText)
        V06Tool(Icons.Rounded.MusicNote, "Audio", inspectorOpen && selectedPanel == EditorPanel.AUDIO, true) { onPanel(EditorPanel.AUDIO) }
        V06Tool(Icons.Rounded.AspectRatio, "Format", inspectorOpen && selectedPanel == EditorPanel.CANVAS, true) { onPanel(EditorPanel.CANVAS) }
        V06Tool(Icons.Rounded.AddCircle, "Ajouter", false, true, onAdd)
    }
}

@Composable
private fun V06Tool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(72.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) FabPink.copy(alpha = 0.16f) else Color.Transparent)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> FabPink
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(22.dp),
        )
        Text(label, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun V06TextDialog(
    existing: TextLayer?,
    playheadMs: Long,
    projectDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (TextLayer) -> Unit,
) {
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var startText by remember(existing?.id) { mutableStateOf(((existing?.startMs ?: playheadMs) / 1000f).toString()) }
    var endText by remember(existing?.id) {
        val defaultEnd = (playheadMs + 5_000L).coerceAtMost(projectDurationMs.coerceAtLeast(playheadMs + 1L))
        mutableStateOf(((existing?.endMs ?: defaultEnd) / 1000f).toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Ajouter un texte" else "Modifier le texte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(160) }, label = { Text("Texte") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.replace(',', '.') },
                        label = { Text("Début (s)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.replace(',', '.') },
                        label = { Text("Fin (s)") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val start = ((startText.toFloatOrNull() ?: 0f) * 1000).toLong().coerceIn(0L, projectDurationMs)
                    val end = ((endText.toFloatOrNull() ?: 0f) * 1000).toLong().coerceIn(start + 1L, projectDurationMs.coerceAtLeast(start + 1L))
                    onSave(
                        (existing ?: TextLayer(
                            text = text,
                            startMs = start,
                            endMs = end,
                            position = TextPosition.BOTTOM,
                        )).copy(text = text, startMs = start, endMs = end),
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun snapV06(project: VideoProject, candidateMs: Long): Long {
    val candidate = candidateMs.coerceIn(0L, project.durationMs.coerceAtLeast(0L))
    val points = buildList {
        add(0L)
        add(project.durationMs)
        project.clips.forEachIndexed { index, clip ->
            val start = project.clipStartMs(index)
            add(start)
            add(start + clip.outputDurationMs)
            clip.keyframes.forEach { keyframe -> add(project.projectPositionForSourceTime(index, keyframe.timeMs)) }
        }
        project.textLayers.forEach { layer ->
            add(layer.startMs)
            add(layer.endMs)
        }
    }
    val nearest = points.minByOrNull { abs(it - candidate) } ?: return candidate
    return if (abs(nearest - candidate) <= 180L) nearest else candidate
}
