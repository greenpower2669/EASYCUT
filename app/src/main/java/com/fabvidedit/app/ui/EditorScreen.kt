package com.fabvidedit.app.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.media.CompositionFactory
import com.fabvidedit.app.media.ExportBitrate
import com.fabvidedit.app.media.ExportFrameRate
import com.fabvidedit.app.media.ExportResolution
import com.fabvidedit.app.media.ExportSettings
import com.fabvidedit.app.media.ExportState
import com.fabvidedit.app.media.ExportVideoCodec
import com.fabvidedit.app.media.VideoEncoderCapabilities
import com.fabvidedit.app.media.MediaInspector
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.MotionEasing
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

internal enum class EditorPanel { CLIP, MOTION, TRANSITION, TEXT, AUDIO, CANVAS }
private enum class KeyframeMarker { DIAMOND, TRIANGLE }

@Composable
fun EditorScreen(viewModel: FabVidEditViewModel, project: VideoProject) {
    val context = LocalContext.current
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    val selectedClipIndex = project.clips.indexOfFirst { it.id == selectedClipId }
    val selectedClip = project.clips.getOrNull(selectedClipIndex)
    val exportState by viewModel.exportState.collectAsStateWithLifecycleCompat()
    val player = remember { CompositionPlayer.Builder(context).build() }
    val previewShortSide = remember(context) {
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (memory.isLowRamDevice) 480 else 720
    }

    var panel by remember { mutableStateOf(EditorPanel.CLIP) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDiagnosticJournal by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<TextLayer?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }

    val selectedSourceTimeMs = if (selectedClipIndex >= 0) {
        project.sourceTimeForProjectPosition(selectedClipIndex, currentPositionMs)
    } else {
        0L
    }
    val selectedTransform = selectedClip?.transformAtSourceTime(selectedSourceTimeMs) ?: ClipTransform()
    val selectedBrightness = selectedClip?.brightnessAtSourceTime(selectedSourceTimeMs) ?: 1f
    val selectedVolume = selectedClip?.volumeAtSourceTime(selectedSourceTimeMs) ?: 1f
    val playheadOnSelectedClip = selectedClip != null && selectedClipIndex >= 0 &&
        currentPositionMs in project.clipStartMs(selectedClipIndex)..
        (project.clipStartMs(selectedClipIndex) + selectedClip.outputDurationMs)
    val selectedKeyframe = selectedClip?.keyframes
        ?.minByOrNull { abs(it.timeMs - selectedSourceTimeMs) }
        ?.takeIf {
            abs(it.timeMs - selectedSourceTimeMs) <=
                (120L * selectedClip.speed).toLong().coerceAtLeast(60L)
        }
    val selectedAudioVolume = project.audioTrack?.volumeAtProjectTime(currentPositionMs) ?: 0.5f
    val selectedAudioKeyframe = project.audioTrack?.keyframes
        ?.minByOrNull { abs(it.timeMs - currentPositionMs) }
        ?.takeIf { abs(it.timeMs - currentPositionMs) <= 120L }
    val audioKeyframeMode = panel == EditorPanel.AUDIO && project.audioTrack != null
    val selectedNextClip = project.clips.getOrNull(selectedClipIndex + 1)
    val selectedTransition = project.transitionAfter(selectedClipIndex)

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
                playbackError = error.localizedMessage ?: "Ce média ne peut pas être prévisualisé"
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
            if (player.currentPosition >= 0) currentPositionMs = player.currentPosition
            delay(100)
        }
    }

    LaunchedEffect(project.updatedAt, project.clips.size) {
        delay(180)
        if (project.clips.isEmpty()) {
            player.stop()
            currentPositionMs = 0
        } else {
            val positionToRestore = currentPositionMs.coerceIn(0, project.durationMs.coerceAtLeast(1))
            val resume = isPlaying
            runCatching {
                player.setComposition(
                    CompositionFactory.create(project, resolutionShortSide = previewShortSide, frameRate = 30),
                    positionToRestore,
                )
                player.prepare()
                if (resume) player.play()
                playbackError = null
            }.onFailure {
                playbackError = it.localizedMessage ?: "Aperçu indisponible"
            }
        }
    }

    Scaffold(
        containerColor = FabBackground,
        topBar = {
            EditorHeader(
                project = project,
                onBack = viewModel::closeProject,
                onRename = { showRenameDialog = true },
                onExport = { showExportDialog = true },
                onJournal = { showDiagnosticJournal = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PreviewSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                player = player,
                hasClips = project.clips.isNotEmpty(),
                error = playbackError,
                transform = selectedTransform,
                gestureEnabled = playheadOnSelectedClip,
                onTransformCommit = {
                    viewModel.setSelectedTransform(currentPositionMs, it)
                    panel = EditorPanel.MOTION
                },
                onAddClip = { addVideosLauncher.launch(arrayOf("video/*")) },
            )

            PlaybackControls(
                currentPositionMs = currentPositionMs,
                durationMs = project.durationMs,
                isPlaying = isPlaying,
                onTogglePlayback = {
                    if (isPlaying) {
                        player.pause()
                    } else {
                        if (currentPositionMs >= project.durationMs - 100) player.seekTo(0)
                        player.play()
                    }
                },
                onSeek = {
                    currentPositionMs = it
                    player.seekTo(it)
                },
                onScrub = {
                    if (!isScrubbing) {
                        player.setScrubbingModeEnabled(true)
                        isScrubbing = true
                    }
                    currentPositionMs = it
                    player.seekTo(it)
                },
                onScrubFinished = {
                    if (isScrubbing) {
                        player.setScrubbingModeEnabled(false)
                        isScrubbing = false
                    }
                },
                showKeyframeButton = if (audioKeyframeMode) true else playheadOnSelectedClip,
                keyframeAtPlayhead = if (audioKeyframeMode) {
                    selectedAudioKeyframe != null
                } else {
                    selectedKeyframe != null
                },
                marker = if (audioKeyframeMode) KeyframeMarker.TRIANGLE else KeyframeMarker.DIAMOND,
                onToggleKeyframe = {
                    if (audioKeyframeMode) {
                        if (selectedAudioKeyframe == null) {
                            viewModel.addAudioKeyframe(currentPositionMs)
                        } else {
                            viewModel.removeAudioKeyframe(currentPositionMs)
                        }
                    } else {
                        if (selectedKeyframe == null) {
                            viewModel.addSelectedKeyframe(currentPositionMs)
                        } else {
                            viewModel.removeSelectedKeyframe(currentPositionMs)
                        }
                        panel = EditorPanel.MOTION
                    }
                },
            )

            Timeline(
                project = project,
                selectedClipId = selectedClipId,
                currentPositionMs = currentPositionMs,
                onSelectClip = { index, clip ->
                    viewModel.selectClip(clip.id)
                    panel = EditorPanel.CLIP
                    val clipStart = project.clipStartMs(index)
                    val clipEnd = clipStart + clip.outputDurationMs
                    val seek = currentPositionMs.takeIf { it in clipStart..clipEnd } ?: clipStart
                    currentPositionMs = seek
                    player.seekTo(seek)
                },
                onKeyframe = { index, clip, keyframe ->
                    viewModel.selectClip(clip.id)
                    panel = EditorPanel.MOTION
                    val seek = project.projectPositionForSourceTime(index, keyframe.timeMs)
                    currentPositionMs = seek
                    player.seekTo(seek)
                },
                onTransition = { index, clip ->
                    viewModel.selectClip(clip.id)
                    panel = EditorPanel.TRANSITION
                    val seek = project.clipStartMs(index + 1).coerceAtMost(project.durationMs)
                    currentPositionMs = seek
                    player.seekTo(seek)
                },
                onAudioTrack = { panel = EditorPanel.AUDIO },
                onTextTrack = { panel = EditorPanel.TEXT },
                onAddClip = { addVideosLauncher.launch(arrayOf("video/*")) },
            )

            EditorTools(
                selectedPanel = panel,
                clipSelected = selectedClip != null,
                onPanel = { panel = it },
                onSplit = {
                    viewModel.splitSelectedAt(currentPositionMs)
                    panel = EditorPanel.CLIP
                },
                onAddText = {
                    editingText = null
                    showTextDialog = true
                    panel = EditorPanel.TEXT
                },
                onAddClip = { addVideosLauncher.launch(arrayOf("video/*")) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 178.dp, max = 262.dp)
                    .background(FabSurface),
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
                    EditorPanel.CANVAS -> CanvasInspector(
                        selected = project.aspectRatio,
                        onSelect = viewModel::setAspectRatio,
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameProjectDialog(
            currentName = project.name,
            onDismiss = { showRenameDialog = false },
            onSave = {
                viewModel.renameProject(it)
                showRenameDialog = false
            },
        )
    }

    if (showTextDialog) {
        TextLayerDialog(
            existing = editingText,
            projectDurationMs = project.durationMs,
            onDismiss = { showTextDialog = false },
            onSave = { layer ->
                if (editingText == null) viewModel.addText(layer) else viewModel.updateText(layer)
                showTextDialog = false
            },
        )
    }

    if (showDiagnosticJournal) {
        DiagnosticJournalDialog(onDismiss = { showDiagnosticJournal = false })
    }

    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExport = { settings ->
                showExportDialog = false
                viewModel.startExport(settings)
            },
        )
    }

    when (val state = exportState) {
        ExportState.Idle -> Unit
        is ExportState.Running -> ExportProgressDialog(state, viewModel::cancelExport)
        is ExportState.Success -> ExportSuccessDialog(
            state = state,
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    clipData = android.content.ClipData.newRawUri("FabVidEdit", state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Partager la vidéo"))
            },
            onOpen = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(state.uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            },
            onDone = viewModel::clearExportResult,
        )
        is ExportState.Error -> ExportErrorDialog(
            message = state.message,
            onDismiss = viewModel::clearExportResult,
            onRetry = {
                viewModel.clearExportResult()
                showExportDialog = true
            },
        )
    }
}

@Composable
private fun EditorHeader(
    project: VideoProject,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onJournal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FabBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour aux projets")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRename)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                project.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Rounded.Edit,
                contentDescription = "Renommer",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onJournal) {
            Text("GET ERR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onExport,
            enabled = project.clips.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
        ) {
            Text("Exporter", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewSurface(
    modifier: Modifier,
    player: CompositionPlayer,
    hasClips: Boolean,
    error: String?,
    transform: ClipTransform,
    gestureEnabled: Boolean,
    onTransformCommit: (ClipTransform) -> Unit,
    onAddClip: () -> Unit,
) {
    // Keyframe/time updates must not erase an in-flight pinch/pan/rotation gesture.
    var gestureTransform by remember { mutableStateOf<ClipTransform?>(null) }
    val latestTransform by rememberUpdatedState(transform)
    val latestCommit by rememberUpdatedState(onTransformCommit)
    val liveTransform = gestureTransform ?: transform
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (hasClips) {
            AndroidView(
                factory = { context ->
                    FabVidVideoTextureView(context).apply {
                        onBindFailure = { error ->
                            com.fabvidedit.app.FabVidDiagnostics.logError("EDITOR_VIDEO_SURFACE", error)
                        }
                        bind(player)
                    }
                },
                update = { texture -> texture.bind(player) },
                onRelease = { texture -> texture.dispose() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val safeScaleX = transform.scaleX.coerceAtLeast(0.01f)
                        val safeScaleY = transform.scaleY.coerceAtLeast(0.01f)
                        scaleX = liveTransform.scaleX / safeScaleX
                        scaleY = liveTransform.scaleY / safeScaleY
                        translationX = (liveTransform.positionX - transform.positionX) * size.width / 2f
                        translationY = -(liveTransform.positionY - transform.positionY) * size.height / 2f
                        rotationZ = liveTransform.rotationDegrees - transform.rotationDegrees
                        transformOrigin = TransformOrigin(
                            pivotFractionX = ((liveTransform.pivotX + 1f) / 2f).coerceIn(0f, 1f),
                            pivotFractionY = ((1f - liveTransform.pivotY) / 2f).coerceIn(0f, 1f),
                        )
                    },
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = FabPurple,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Ajoute une vidéo pour commencer")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAddClip) { Text("Ajouter un clip") }
            }
        }
        if (hasClips) {
            Text(
                "APERÇU DU MONTAGE",
                color = FabMint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        if (gestureEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, FabMint.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var working = latestTransform
                            var changed = false
                            var pointersPressed: Boolean
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                val pan = event.calculatePan()
                                if (zoom != 1f || rotation != 0f || pan.x != 0f || pan.y != 0f) {
                                    val width = size.width.coerceAtLeast(1)
                                    val height = size.height.coerceAtLeast(1)
                                    working = working.copy(
                                        scaleX = working.scaleX * zoom,
                                        scaleY = working.scaleY * zoom,
                                        positionX = working.positionX + pan.x / width * 2f,
                                        positionY = working.positionY - pan.y / height * 2f,
                                        rotationDegrees = working.rotationDegrees + rotation,
                                    ).normalized()
                                    gestureTransform = working
                                    changed = true
                                    event.changes.forEach { it.consume() }
                                }
                                pointersPressed = event.changes.any { it.pressed }
                            } while (pointersPressed)
                            if (changed) latestCommit(working)
                            gestureTransform = null
                        }
                    },
            )
            Text(
                "Pincer • déplacer • tourner",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        error?.let {
            Text(
                text = it,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    showKeyframeButton: Boolean,
    keyframeAtPlayhead: Boolean,
    marker: KeyframeMarker,
    onToggleKeyframe: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FabBackground)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSeek((currentPositionMs - 5_000).coerceAtLeast(0)) }) {
            Icon(Icons.Rounded.Undo, contentDescription = "Reculer de 5 secondes")
        }
        FilledIconButton(
            onClick = onTogglePlayback,
            enabled = durationMs > 0,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
            )
        }
        IconButton(onClick = { onSeek((currentPositionMs + 5_000).coerceAtMost(durationMs)) }) {
            Icon(Icons.Rounded.Redo, contentDescription = "Avancer de 5 secondes")
        }
        Text(
            formatDuration(currentPositionMs),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = currentPositionMs.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
            onValueChange = { onScrub(it.toLong()) },
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            formatDuration(durationMs),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showKeyframeButton) {
            Spacer(Modifier.width(4.dp))
            KeyframeToggleButton(
                remove = keyframeAtPlayhead,
                marker = marker,
                onClick = onToggleKeyframe,
            )
        }
    }
}

@Composable
private fun KeyframeToggleButton(
    remove: Boolean,
    marker: KeyframeMarker,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .semantics {
                val shape = if (marker == KeyframeMarker.TRIANGLE) "audio" else "vidéo"
                contentDescription = if (remove) {
                    "Retirer l’image-clé $shape"
                } else {
                    "Ajouter une image-clé $shape"
                }
            },
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (marker == KeyframeMarker.DIAMOND) {
                Box(
                    Modifier
                        .size(17.dp)
                        .rotate(45f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (remove) FabPink else FabSurfaceHigh)
                        .border(2.dp, if (remove) FabPink else FabMint, RoundedCornerShape(2.dp)),
                )
            } else {
                Text(
                    text = "▲",
                    color = if (remove) FabPink else FabMint,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (remove) "−" else "+",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.offset(x = 12.dp, y = (-11).dp),
            )
        }
    }
}

@Composable
private fun Timeline(
    project: VideoProject,
    selectedClipId: String?,
    currentPositionMs: Long,
    onSelectClip: (Int, VideoClip) -> Unit,
    onKeyframe: (Int, VideoClip, TransformKeyframe) -> Unit,
    onTransition: (Int, VideoClip) -> Unit,
    onAudioTrack: () -> Unit,
    onTextTrack: () -> Unit,
    onAddClip: () -> Unit,
) {
    val lowerTrackCount = (if (project.audioTrack != null) 1 else 0) +
        (if (project.textLayers.isNotEmpty()) 1 else 0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((106 + lowerTrackCount * 28).dp)
            .background(Color(0xFF100E15)),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(project.clips, key = { _, clip -> clip.id }) { index, clip ->
                val clipStartMs = project.clipStartMs(index)
                val playheadProgress = currentPositionMs
                    .takeIf { it in clipStartMs..(clipStartMs + clip.outputDurationMs) }
                    ?.let { (it - clipStartMs).toFloat() / clip.outputDurationMs.coerceAtLeast(1).toFloat() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimelineClip(
                        clip = clip,
                        selected = clip.id == selectedClipId,
                        playheadProgress = playheadProgress,
                        onClick = { onSelectClip(index, clip) },
                        onKeyframe = { onKeyframe(index, clip, it) },
                    )
                    if (index < project.clips.lastIndex) {
                        TransitionConnector(
                            active = project.transitionAfter(index) != null,
                            onClick = { onTransition(index, clip) },
                        )
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 76.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, FabPurple.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                        .clickable(onClick = onAddClip),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddCircle, contentDescription = "Ajouter un clip", tint = FabPurple)
                }
            }
        }
        if (lowerTrackCount > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                project.audioTrack?.let { track ->
                    TrackLane(
                        icon = Icons.Rounded.MusicNote,
                        label = "Audio • ${track.name}",
                        color = FabMint,
                        triangleMarkers = track.keyframes.map { keyframe ->
                            keyframe.timeMs.toFloat() / project.durationMs.coerceAtLeast(1).toFloat()
                        },
                        onClick = onAudioTrack,
                    )
                }
                if (project.textLayers.isNotEmpty()) {
                    TrackLane(
                        icon = Icons.Rounded.TextFields,
                        label = "Texte • ${project.textLayers.size} calque(s)",
                        color = FabPurple,
                        onClick = onTextTrack,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackLane(
    icon: ImageVector,
    label: String,
    color: Color,
    triangleMarkers: List<Float> = emptyList(),
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        triangleMarkers.forEach { progress ->
            Text(
                text = "▲",
                color = FabPink,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = maxWidth * progress.coerceIn(0f, 1f) - 4.dp, y = 5.dp),
            )
        }
    }
}

@Composable
private fun TimelineClip(
    clip: VideoClip,
    selected: Boolean,
    playheadProgress: Float?,
    onClick: () -> Unit,
    onKeyframe: (TransformKeyframe) -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = clip.uri,
        key2 = clip.trimStartMs,
    ) {
        value = MediaInspector.thumbnail(context, clip.uri, clip.trimStartMs)
    }
    val widthValue = (82L + clip.outputDurationMs / 110).coerceIn(92L, 230L).toInt()
    val width = widthValue.dp
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = 76.dp)
            .clip(shape)
            .background(FabSurfaceHigh)
            .then(if (selected) Modifier.border(3.dp, FabPink, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        thumbnail?.let { bitmap ->
            Row(Modifier.fillMaxSize()) {
                repeat((widthValue / 54).coerceIn(2, 5)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (thumbnail == null) 0f else 0.18f)))
        Text(
            clip.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        )
        Text(
            formatDuration(clip.outputDurationMs),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        clip.keyframes.forEach { keyframe ->
            val progress = keyframe.timeMs.toFloat() / clip.sourceDurationMs.coerceAtLeast(1).toFloat()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = width * progress.coerceIn(0f, 1f) - 6.dp, y = (-4).dp)
                    .size(12.dp)
                    .rotate(45f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FabPink)
                    .border(1.dp, Color.Black, RoundedCornerShape(2.dp))
                    .clickable { onKeyframe(keyframe) },
            )
        }
        playheadProgress?.let { progress ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = width * progress.coerceIn(0f, 1f) - 1.dp)
                    .size(width = 2.dp, height = 76.dp)
                    .background(Color.White),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = width * progress.coerceIn(0f, 1f) - 5.dp, y = (-2).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun TransitionConnector(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) FabPink.copy(alpha = 0.24f) else FabSurfaceHigh)
            .border(1.dp, if (active) FabPink else FabPurple, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AutoFixHigh,
            contentDescription = "Transition entre les clips",
            tint = if (active) FabPink else FabPurple,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun EditorTools(
    selectedPanel: EditorPanel,
    clipSelected: Boolean,
    onPanel: (EditorPanel) -> Unit,
    onSplit: () -> Unit,
    onAddText: () -> Unit,
    onAddClip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FabBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EditorToolButton(Icons.Rounded.Tune, "Clip", selectedPanel == EditorPanel.CLIP, clipSelected) {
            onPanel(EditorPanel.CLIP)
        }
        EditorToolButton(Icons.Rounded.ContentCut, "Scinder", false, clipSelected, onSplit)
        EditorToolButton(Icons.Rounded.Animation, "Mouvement", selectedPanel == EditorPanel.MOTION, clipSelected) {
            onPanel(EditorPanel.MOTION)
        }
        EditorToolButton(Icons.Rounded.AutoFixHigh, "Transition", selectedPanel == EditorPanel.TRANSITION, clipSelected) {
            onPanel(EditorPanel.TRANSITION)
        }
        EditorToolButton(Icons.Rounded.TextFields, "Texte", selectedPanel == EditorPanel.TEXT, true, onAddText)
        EditorToolButton(Icons.Rounded.MusicNote, "Audio", selectedPanel == EditorPanel.AUDIO, true) {
            onPanel(EditorPanel.AUDIO)
        }
        EditorToolButton(Icons.Rounded.AspectRatio, "Format", selectedPanel == EditorPanel.CANVAS, true) {
            onPanel(EditorPanel.CANVAS)
        }
        EditorToolButton(Icons.Rounded.AddCircle, "Ajouter", false, true, onAddClip)
    }
}

@Composable
private fun EditorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) FabPink.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> FabPink
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(23.dp),
        )
        Text(
            label,
            fontSize = 10.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun RenameProjectDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nom du projet") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text("Nom") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onExport: (ExportSettings) -> Unit,
) {
    val hevcAvailable = remember { VideoEncoderCapabilities.hasHardwareHevcEncoder() }
    var settings by remember { mutableStateOf(ExportSettings()) }
    val codecs = if (hevcAvailable) ExportVideoCodec.entries else listOf(ExportVideoCodec.H264)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exporter en MP4 / AAC") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExportChoiceRow("Codec vidéo", codecs, settings.codec, { it.label }) {
                    settings = settings.copy(codec = it)
                }
                if (!hevcAvailable) {
                    Text("H.265 nécessite un encodeur matériel HEVC compatible sur ce téléphone.")
                }
                ExportChoiceRow("Résolution", ExportResolution.entries, settings.resolution, { it.label }) {
                    settings = settings.copy(resolution = it)
                }
                ExportChoiceRow("Cadence", ExportFrameRate.entries, settings.frameRate, { it.label }) {
                    settings = settings.copy(frameRate = it)
                }
                ExportChoiceRow("Débit vidéo", ExportBitrate.entries, settings.bitrate, { it.label }) {
                    settings = settings.copy(bitrate = it)
                }
                Text("L'aperçu léger n'affecte pas la qualité choisie pour l'export.")
            }
        },
        confirmButton = {
            Button(onClick = { onExport(settings) }) { Text("Lancer l’export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun <T> ExportChoiceRow(
    title: String,
    choices: List<T>,
    selection: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selection,
                    onClick = { onSelect(choice) },
                    label = { Text(label(choice)) },
                )
            }
        }
    }
}

@Composable
private fun ExportProgressDialog(state: ExportState.Running, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Création de la vidéo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${state.progress} % • ${state.message}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Garde FabVidEdit ouvert jusqu’à la fin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Annuler l’export") }
        },
    )
}

@Composable
private fun ExportSuccessDialog(
    state: ExportState.Success,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Rounded.Movie, contentDescription = null, tint = FabMint) },
        title = { Text("Vidéo terminée !") },
        text = {
            Text("${state.fileName}\n\nEnregistrée dans Films/FabVidEdit.")
        },
        confirmButton = { Button(onClick = onShare) { Text("Partager") } },
        dismissButton = {
            Row {
                TextButton(onClick = onOpen) { Text("Lire") }
                TextButton(onClick = onDone) { Text("Fermer") }
            }
        },
    )
}

@Composable
private fun ExportErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export impossible") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onRetry) { Text("Réessayer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
