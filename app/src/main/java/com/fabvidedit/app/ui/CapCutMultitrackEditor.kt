package com.fabvidedit.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.media.CompositionFactory
import com.fabvidedit.app.media.ExportState
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.MotionEasing
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TextPosition
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.ui.theme.FabBackground
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurface
import com.fabvidedit.app.ui.theme.FabSurfaceHigh
import com.fabvidedit.app.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Main editing UI for MULTITRACK projects.
 *
 * A video stream is a real independent source/layer. New imported videos are appended as new
 * overlay tracks, exactly like adding an overlay in a desktop/mobile NLE. Tracks share the same
 * project clock and keep their own start/duration/format/keyframes.
 */
@Composable
fun CapCutMultitrackEditor(viewModel: FabVidEditViewModel, project: VideoProject) {
    val context = LocalContext.current
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    val exportState by viewModel.exportState.collectAsStateWithLifecycleCompat()
    val selectedIndex = project.clips.indexOfFirst { it.id == selectedClipId }
    val selectedClip = project.clips.getOrNull(selectedIndex)
    val player = remember { CompositionPlayer.Builder(context).build() }

    var panel by remember { mutableStateOf(EditorPanel.CLIP) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf<TextLayer?>(null) }
    var textDialogVisible by remember { mutableStateOf(false) }

    val selectedSourceTimeMs = if (selectedIndex >= 0) {
        project.sourceTimeForProjectPosition(selectedIndex, currentPositionMs)
    } else 0L
    val selectedTransform = selectedClip?.transformAtSourceTime(selectedSourceTimeMs) ?: ClipTransform()
    val selectedBrightness = selectedClip?.brightnessAtSourceTime(selectedSourceTimeMs) ?: 1f
    val selectedVolume = selectedClip?.volumeAtSourceTime(selectedSourceTimeMs) ?: 1f
    val playheadOnSelectedClip = selectedClip != null && selectedIndex >= 0 &&
        currentPositionMs in project.clipStartMs(selectedIndex)..
        (project.clipStartMs(selectedIndex) + selectedClip.outputDurationMs)
    val selectedKeyframe = selectedClip?.keyframes
        ?.minByOrNull { abs(it.timeMs - selectedSourceTimeMs) }
        ?.takeIf { abs(it.timeMs - selectedSourceTimeMs) <= (120L * selectedClip.speed).toLong().coerceAtLeast(60L) }
    val selectedNextClip = project.nextClipOnTrack(selectedIndex)
    val selectedTransition = project.transitionAfter(selectedIndex)
    val selectedAudioVolume = project.audioTrack?.volumeAtProjectTime(currentPositionMs) ?: 0.5f
    val selectedAudioKeyframe = project.audioTrack?.keyframes
        ?.minByOrNull { abs(it.timeMs - currentPositionMs) }
        ?.takeIf { abs(it.timeMs - currentPositionMs) <= 120L }

    val addVideosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.addClips(it)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::setAudio)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Aperçu multipiste indisponible"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.currentPosition >= 0L) currentPositionMs = player.currentPosition
            delay(100L)
        }
    }

    LaunchedEffect(project.updatedAt, project.clips.size, project.sourceAudioTracks.size) {
        delay(120L)
        if (project.clips.isEmpty()) {
            player.stop()
            currentPositionMs = 0L
        } else {
            val restore = currentPositionMs.coerceIn(0L, project.durationMs.coerceAtLeast(1L))
            val resume = isPlaying
            runCatching {
                player.setComposition(CompositionFactory.create(project), restore)
                player.prepare()
                if (resume) player.play()
                playbackError = null
            }.onFailure { playbackError = it.localizedMessage ?: "Aperçu multipiste indisponible" }
        }
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
                    Text(
                        "Éditeur multipiste • ${project.clips.map(VideoClip::timelineTrackIndex).distinct().size} piste(s) vidéo",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { viewModel.startExport(1080) }, enabled = project.clips.isNotEmpty()) {
                    Text("Exporter")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (project.clips.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx).inflate(com.fabvidedit.app.R.layout.fabvid_texture_player_view, null, false) as PlayerView).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = player
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "APERÇU MULTIPISTE",
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

            CapCutPlaybackControls(
                currentPositionMs = currentPositionMs,
                durationMs = project.durationMs,
                isPlaying = isPlaying,
                showDiamond = playheadOnSelectedClip,
                diamondActive = selectedKeyframe != null,
                onTogglePlay = {
                    if (isPlaying) player.pause() else {
                        if (currentPositionMs >= project.durationMs - 100L) {
                            currentPositionMs = 0L
                            player.seekTo(0L)
                        }
                        player.play()
                    }
                },
                onSeek = { position ->
                    currentPositionMs = position
                    player.seekTo(position)
                },
                onDiamond = {
                    if (selectedKeyframe == null) viewModel.addSelectedKeyframe(currentPositionMs)
                    else viewModel.removeSelectedKeyframe(currentPositionMs)
                    panel = EditorPanel.MOTION
                },
            )

            CapCutMultitrackTimeline(
                project = project,
                selectedClipId = selectedClipId,
                currentPositionMs = currentPositionMs,
                onSelectClip = { clip ->
                    val index = project.clips.indexOfFirst { it.id == clip.id }
                    if (index >= 0) {
                        viewModel.selectClip(clip.id)
                        panel = EditorPanel.CLIP
                        val start = project.clipStartMs(index)
                        val end = start + clip.outputDurationMs
                        val seek = currentPositionMs.takeIf { it in start..end } ?: start
                        currentPositionMs = seek
                        player.seekTo(seek)
                    }
                },
                onKeyframe = { clip, keyframe ->
                    val index = project.clips.indexOfFirst { it.id == clip.id }
                    if (index >= 0) {
                        viewModel.selectClip(clip.id)
                        panel = EditorPanel.MOTION
                        val seek = project.projectPositionForSourceTime(index, keyframe.timeMs)
                        currentPositionMs = seek
                        player.seekTo(seek)
                    }
                },
                onAudioTrack = { panel = EditorPanel.AUDIO },
                onTextTrack = { panel = EditorPanel.TEXT },
            )

            CapCutTools(
                selectedPanel = panel,
                clipSelected = selectedClip != null,
                onPanel = { panel = it },
                onSplit = { viewModel.splitSelectedAt(currentPositionMs) },
                onText = {
                    editingText = null
                    textDialogVisible = true
                    panel = EditorPanel.TEXT
                },
                onAdd = { addVideosLauncher.launch(arrayOf("video/*")) },
            )

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 260.dp).background(FabSurface),
            ) {
                when (panel) {
                    EditorPanel.CLIP -> ClipInspector(
                        clip = selectedClip,
                        currentVolume = selectedVolume,
                        onTrim = viewModel::trimSelected,
                        onSpeed = viewModel::setSelectedSpeed,
                        onVolume = { viewModel.setSelectedVolume(currentPositionMs, it) },
                        onFilter = viewModel::setSelectedFilter,
                        onRotate = viewModel::rotateSelected,
                        onDuplicate = viewModel::duplicateSelected,
                        onMoveLeft = { viewModel.moveSelected(-1) },
                        onMoveRight = { viewModel.moveSelected(1) },
                        onDelete = viewModel::removeSelectedClip,
                    )
                    EditorPanel.MOTION -> TransformInspector(
                        clip = selectedClip,
                        transform = selectedTransform,
                        brightness = selectedBrightness,
                        volume = selectedVolume,
                        keyframeAtPlayhead = selectedKeyframe != null,
                        selectedEasing = selectedKeyframe?.easing,
                        selectedAspectKeyframe = selectedKeyframe,
                        onAspectOverride = { sar, dar ->
                            viewModel.setSelectedKeyframeAspect(currentPositionMs, sar, dar)
                        },
                        onCreateWorkingCopy = viewModel::createSelectedNormalizedWorkingCopy,
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
                            textDialogVisible = true
                        },
                        onEdit = {
                            editingText = it
                            textDialogVisible = true
                        },
                        onDelete = viewModel::removeText,
                    )
                    EditorPanel.AUDIO -> AudioInspector(
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
                    EditorPanel.CANVAS -> CanvasInspector(project.aspectRatio, viewModel::setAspectRatio)
                }
            }
        }
    }

    if (textDialogVisible) {
        CapCutTextDialog(
            existing = editingText,
            playheadMs = currentPositionMs,
            projectDurationMs = project.durationMs,
            onDismiss = { textDialogVisible = false },
            onSave = { layer ->
                if (editingText == null) viewModel.addText(layer) else viewModel.updateText(layer)
                textDialogVisible = false
            },
        )
    }

    when (val state = exportState) {
        ExportState.Idle -> Unit
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Export multipiste") },
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
            text = { Text(state.fileName) },
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

@Composable
private fun CapCutPlaybackControls(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showDiamond: Boolean,
    diamondActive: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onDiamond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FabBackground).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(42.dp)) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
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
                    Modifier.size(17.dp).rotate(45f).clip(RoundedCornerShape(2.dp))
                        .background(if (diamondActive) FabPink else FabSurfaceHigh)
                        .border(2.dp, if (diamondActive) FabPink else FabMint, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun CapCutMultitrackTimeline(
    project: VideoProject,
    selectedClipId: String?,
    currentPositionMs: Long,
    onSelectClip: (VideoClip) -> Unit,
    onKeyframe: (VideoClip, TransformKeyframe) -> Unit,
    onAudioTrack: () -> Unit,
    onTextTrack: () -> Unit,
) {
    val scroll = rememberScrollState()
    val duration = project.durationMs.coerceAtLeast(1L)
    val widthDp = ((duration / 1000f) * 8f).coerceIn(720f, 8_000f).dp
    val videoTracks = project.clips.groupBy(VideoClip::timelineTrackIndex).toSortedMap(compareByDescending { it })
    val rows = videoTracks.size + project.sourceAudioTracks.size +
        (if (project.audioTrack != null) 1 else 0) + (if (project.textLayers.isNotEmpty()) 1 else 0)
    val visibleRows = rows.coerceIn(1, 5)

    Row(
        modifier = Modifier.fillMaxWidth().height((30 + visibleRows * 54).dp).background(Color(0xFF100E15)),
    ) {
        Column(Modifier.width(48.dp).padding(top = 24.dp)) {
            videoTracks.keys.forEach { track -> TrackLabel("V${track + 1}", FabPink) }
            project.sourceAudioTracks.sortedBy { it.timelineTrackIndex }.forEachIndexed { index, _ ->
                TrackLabel("A${index + 1}", FabMint)
            }
            project.audioTrack?.let { TrackLabel("♫", FabMint) }
            if (project.textLayers.isNotEmpty()) TrackLabel("T", FabPurple)
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll)) {
            TimelineRuler(widthDp, duration, currentPositionMs)
            videoTracks.forEach { (_, clips) ->
                VideoTrackLane(
                    width = widthDp,
                    projectDurationMs = duration,
                    clips = clips.sortedBy(VideoClip::timelineStartMs),
                    selectedClipId = selectedClipId,
                    currentPositionMs = currentPositionMs,
                    onSelectClip = onSelectClip,
                    onKeyframe = onKeyframe,
                )
            }
            project.sourceAudioTracks.sortedBy { it.timelineTrackIndex }.forEach { track ->
                SimpleTrackLane(
                    width = widthDp,
                    durationMs = duration,
                    startMs = track.timelineStartMs,
                    itemDurationMs = track.durationMs,
                    label = "stream AUDIO ${track.sourceStreamIndex ?: track.timelineTrackIndex}",
                    color = FabMint,
                    currentPositionMs = currentPositionMs,
                    onClick = onAudioTrack,
                )
            }
            project.audioTrack?.let { track ->
                SimpleTrackLane(
                    width = widthDp,
                    durationMs = duration,
                    startMs = 0L,
                    itemDurationMs = duration,
                    label = "Musique • ${track.name}",
                    color = FabMint,
                    currentPositionMs = currentPositionMs,
                    onClick = onAudioTrack,
                )
            }
            if (project.textLayers.isNotEmpty()) {
                TextTrackLane(widthDp, duration, project.textLayers, currentPositionMs, onTextTrack)
            }
        }
    }
}

@Composable
private fun TrackLabel(label: String, color: Color) {
    Box(Modifier.width(48.dp).height(54.dp), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimelineRuler(width: Dp, durationMs: Long, currentPositionMs: Long) {
    Box(Modifier.width(width).height(24.dp).background(Color(0xFF16131D))) {
        val seconds = (durationMs / 1000L).coerceAtLeast(1L)
        val step = when {
            seconds <= 30 -> 5L
            seconds <= 120 -> 10L
            else -> 30L
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
        Playhead(width, durationMs, currentPositionMs, 24.dp)
    }
}

@Composable
private fun VideoTrackLane(
    width: Dp,
    projectDurationMs: Long,
    clips: List<VideoClip>,
    selectedClipId: String?,
    currentPositionMs: Long,
    onSelectClip: (VideoClip) -> Unit,
    onKeyframe: (VideoClip, TransformKeyframe) -> Unit,
) {
    Box(Modifier.width(width).height(54.dp).background(Color(0xFF131019))) {
        clips.forEach { clip ->
            val startProgress = clip.timelineStartMs.toFloat() / projectDurationMs.toFloat()
            val durationProgress = clip.outputDurationMs.toFloat() / projectDurationMs.toFloat()
            val clipWidth = (width * durationProgress).coerceAtLeast(42.dp)
            Box(
                modifier = Modifier.offset(x = width * startProgress).padding(vertical = 4.dp)
                    .width(clipWidth).height(46.dp).clip(RoundedCornerShape(7.dp))
                    .background(FabPurple.copy(alpha = 0.42f))
                    .border(
                        if (clip.id == selectedClipId) 2.dp else 1.dp,
                        if (clip.id == selectedClipId) FabPink else FabPurple,
                        RoundedCornerShape(7.dp),
                    )
                    .clickable { onSelectClip(clip) },
            ) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(
                        clip.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 9.sp,
                        color = Color.White,
                    )
                    Text(
                        "VIDEO ${clip.sourceStreamIndex ?: clip.timelineTrackIndex} • ${formatDuration(clip.outputDurationMs)}",
                        maxLines = 1,
                        fontSize = 8.sp,
                        color = FabMint,
                    )
                }
                clip.keyframes.forEach { keyframe ->
                    val k = keyframe.timeMs.toFloat() / clip.sourceDurationMs.coerceAtLeast(1L).toFloat()
                    Box(
                        Modifier.align(Alignment.BottomStart).offset(x = clipWidth * k.coerceIn(0f, 1f) - 5.dp, y = (-3).dp)
                            .size(10.dp).rotate(45f).clip(RoundedCornerShape(1.dp)).background(FabPink)
                            .clickable { onKeyframe(clip, keyframe) },
                    )
                }
            }
        }
        Playhead(width, projectDurationMs, currentPositionMs, 54.dp)
    }
}

@Composable
private fun SimpleTrackLane(
    width: Dp,
    durationMs: Long,
    startMs: Long,
    itemDurationMs: Long,
    label: String,
    color: Color,
    currentPositionMs: Long,
    onClick: () -> Unit,
) {
    val start = startMs.toFloat() / durationMs.toFloat()
    val itemWidth = (width * (itemDurationMs.toFloat() / durationMs.toFloat())).coerceAtLeast(42.dp)
    Box(Modifier.width(width).height(54.dp).background(Color(0xFF131019))) {
        Box(
            Modifier.offset(x = width * start).padding(vertical = 8.dp).width(itemWidth).height(38.dp)
                .clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.20f))
                .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(7.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 7.dp), color = Color.White, fontSize = 9.sp, maxLines = 1)
        }
        Playhead(width, durationMs, currentPositionMs, 54.dp)
    }
}

@Composable
private fun TextTrackLane(
    width: Dp,
    durationMs: Long,
    layers: List<TextLayer>,
    currentPositionMs: Long,
    onClick: () -> Unit,
) {
    Box(Modifier.width(width).height(54.dp).background(Color(0xFF131019))) {
        layers.forEach { layer ->
            val start = layer.startMs.toFloat() / durationMs.toFloat()
            val itemWidth = (width * ((layer.endMs - layer.startMs).coerceAtLeast(1L).toFloat() / durationMs.toFloat()))
                .coerceAtLeast(42.dp)
            Box(
                Modifier.offset(x = width * start).padding(vertical = 8.dp).width(itemWidth).height(38.dp)
                    .clip(RoundedCornerShape(7.dp)).background(FabPurple.copy(alpha = 0.22f))
                    .border(1.dp, FabPurple, RoundedCornerShape(7.dp)).clickable(onClick = onClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(layer.text, modifier = Modifier.padding(horizontal = 7.dp), fontSize = 9.sp, color = Color.White, maxLines = 1)
            }
        }
        Playhead(width, durationMs, currentPositionMs, 54.dp)
    }
}

@Composable
private fun Playhead(width: Dp, durationMs: Long, currentPositionMs: Long, height: Dp) {
    val progress = currentPositionMs.coerceIn(0L, durationMs).toFloat() / durationMs.coerceAtLeast(1L).toFloat()
    Box(
        Modifier.offset(x = width * progress.coerceIn(0f, 1f)).width(2.dp).height(height).background(Color.White),
    )
}

@Composable
private fun CapCutTools(
    selectedPanel: EditorPanel,
    clipSelected: Boolean,
    onPanel: (EditorPanel) -> Unit,
    onSplit: () -> Unit,
    onText: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FabBackground).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CapCutTool(Icons.Rounded.Tune, "Clip", selectedPanel == EditorPanel.CLIP, clipSelected) { onPanel(EditorPanel.CLIP) }
        CapCutTool(Icons.Rounded.ContentCut, "Scinder", false, clipSelected, onSplit)
        CapCutTool(Icons.Rounded.Animation, "Keyframes", selectedPanel == EditorPanel.MOTION, clipSelected) { onPanel(EditorPanel.MOTION) }
        CapCutTool(Icons.Rounded.AutoFixHigh, "Transition", selectedPanel == EditorPanel.TRANSITION, clipSelected) { onPanel(EditorPanel.TRANSITION) }
        CapCutTool(Icons.Rounded.TextFields, "Texte", selectedPanel == EditorPanel.TEXT, true, onText)
        CapCutTool(Icons.Rounded.MusicNote, "Audio", selectedPanel == EditorPanel.AUDIO, true) { onPanel(EditorPanel.AUDIO) }
        CapCutTool(Icons.Rounded.AspectRatio, "Format", selectedPanel == EditorPanel.CANVAS, true) { onPanel(EditorPanel.CANVAS) }
        CapCutTool(Icons.Rounded.AddCircle, "Superposer", false, true, onAdd)
    }
}

@Composable
private fun CapCutTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(72.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) FabPink.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
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
private fun CapCutTextDialog(
    existing: TextLayer?,
    playheadMs: Long,
    projectDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (TextLayer) -> Unit,
) {
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var startText by remember(existing?.id) {
        mutableStateOf(((existing?.startMs ?: playheadMs) / 1000f).toString())
    }
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
                onClick = {
                    val start = ((startText.toFloatOrNull() ?: 0f) * 1000f).toLong().coerceIn(0L, projectDurationMs)
                    val end = ((endText.toFloatOrNull() ?: 0f) * 1000f).toLong().coerceIn(start + 1L, projectDurationMs.coerceAtLeast(start + 1L))
                    onSave(
                        (existing ?: TextLayer(
                            text = text.trim(),
                            startMs = start,
                            endMs = end,
                            position = TextPosition.BOTTOM,
                        )).copy(text = text.trim(), startMs = start, endMs = end),
                    )
                },
                enabled = text.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
