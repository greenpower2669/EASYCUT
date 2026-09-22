package com.fabvidedit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.media.FfprobeMediaInspector
import com.fabvidedit.app.media.PocStreamSelectionRegistry
import com.fabvidedit.app.media.PocVideoAnalyzer
import com.fabvidedit.app.media.RegisteredPocStreamSelection
import com.fabvidedit.app.model.MediaStreamInventory
import com.fabvidedit.app.model.PocStreamExportPlan
import com.fabvidedit.app.model.StreamSelectionConfidence
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.model.VideoFormatAnalysis
import com.fabvidedit.app.model.VideoProject

@Composable
fun PocStreamAwareEditor(viewModel: FabVidEditViewModel, project: VideoProject) {
    if (project.timelineMode == TimelineMode.MULTITRACK) {
        IndependentStreamEditor(viewModel, project)
        return
    }
    LegacyContainerStreamEditor(viewModel, project)
}

@Composable
private fun IndependentStreamEditor(viewModel: FabVidEditViewModel, project: VideoProject) {
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    var panelVisible by remember(project.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PocEditorScreen(viewModel, project)
        Button(
            onClick = { panelVisible = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 10.dp),
        ) {
            Text("Pistes ${project.clips.size}V/${project.sourceAudioTracks.size}A")
        }
        if (panelVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .heightIn(max = 720.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 10.dp,
                ) {
                    MultitrackTimelinePanel(
                        project = project,
                        selectedClipId = selectedClipId,
                        onSelectClip = viewModel::selectClip,
                        onClose = { panelVisible = false },
                    )
                }
            }
        }
    }
}

/** Compatibility UI for old projects that were saved before immediate stream separation existed. */
@Composable
private fun LegacyContainerStreamEditor(viewModel: FabVidEditViewModel, project: VideoProject) {
    val context = LocalContext.current
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    val selectedIndex = project.clips.indexOfFirst { it.id == selectedClipId }.let { if (it >= 0) it else 0 }
    val clip = project.clips.getOrNull(selectedIndex)

    var inventory by remember(clip?.id) { mutableStateOf<MediaStreamInventory?>(null) }
    var analyses by remember(clip?.id) { mutableStateOf<Map<Int, VideoFormatAnalysis>>(emptyMap()) }
    var selectedVideoIndex by remember(clip?.id) { mutableStateOf<Int?>(null) }
    var keptVideoIndexes by remember(clip?.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var keepSubtitles by remember(clip?.id) { mutableStateOf(false) }
    var panelVisible by remember(clip?.id) { mutableStateOf(false) }
    var manualConfirmed by remember(clip?.id) { mutableStateOf(false) }
    var detecting by remember(clip?.id) { mutableStateOf(false) }
    var detectionError by remember(clip?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(clip?.id, clip?.uri) {
        clip ?: return@LaunchedEffect
        PocStreamSelectionRegistry.remove(clip.uri)
        detecting = true
        detectionError = null
        val inspected = runCatching {
            FfprobeMediaInspector.inspect(context, android.net.Uri.parse(clip.uri))
        }.onFailure {
            detectionError = it.localizedMessage ?: "Analyse FFprobe impossible"
        }.getOrNull()
        inventory = inspected

        if (inspected != null && inspected.videoStreams.size > 1) {
            val recommended = inspected.recommendation.selectedVideoIndex
                ?: inspected.videoStreams.first().index
            selectedVideoIndex = recommended
            keptVideoIndexes = if (inspected.recommendation.confidence == StreamSelectionConfidence.LOW) {
                inspected.videoStreams.map { it.index }.toSet()
            } else {
                setOf(recommended)
            }
            manualConfirmed = inspected.recommendation.confidence != StreamSelectionConfidence.LOW
            panelVisible = true

            val perTrack = linkedMapOf<Int, VideoFormatAnalysis>()
            inspected.videoStreams.forEach { stream ->
                runCatching {
                    PocVideoAnalyzer.analyzeTrack(
                        context = context,
                        uri = android.net.Uri.parse(clip.uri),
                        stream = stream,
                        containerDurationMs = inspected.durationMs,
                    )
                }.getOrNull()?.let { analysis -> perTrack[stream.index] = analysis }
            }
            analyses = perTrack
        } else {
            selectedVideoIndex = inspected?.videoStreams?.firstOrNull()?.index
            keptVideoIndexes = selectedVideoIndex?.let(::setOf) ?: emptySet()
            manualConfirmed = true
            panelVisible = false
        }
        detecting = false
    }

    LaunchedEffect(
        clip?.uri,
        inventory,
        selectedVideoIndex,
        keptVideoIndexes,
        keepSubtitles,
        analyses,
        manualConfirmed,
    ) {
        val currentClip = clip ?: return@LaunchedEffect
        val currentInventory = inventory ?: return@LaunchedEffect
        val selected = selectedVideoIndex ?: return@LaunchedEffect
        if (currentInventory.videoStreams.size <= 1 || !manualConfirmed) {
            PocStreamSelectionRegistry.remove(currentClip.uri)
            return@LaunchedEffect
        }
        val safeKept = (keptVideoIndexes + selected)
            .intersect(currentInventory.videoStreams.map { it.index }.toSet())
            .ifEmpty { setOf(selected) }
        PocStreamSelectionRegistry.put(
            currentClip.uri,
            RegisteredPocStreamSelection(
                plan = PocStreamExportPlan(
                    inventory = currentInventory,
                    selectedVideoIndex = selected,
                    keptVideoIndexes = safeKept,
                    selectedAudioIndex = currentInventory.recommendation.selectedAudioIndex,
                    keepSubtitles = keepSubtitles,
                ),
                selectedAnalysis = analyses[selected],
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        PocEditorScreen(viewModel, project)

        val currentInventory = inventory
        if (currentInventory != null && currentInventory.videoStreams.size > 1 && !panelVisible) {
            Button(
                onClick = { panelVisible = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 10.dp),
            ) {
                Text("Pistes ${currentInventory.videoStreams.size}")
            }
        }

        if (detecting) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
                tonalElevation = 8.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Inventaire FFprobe des pistes…", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (currentInventory != null && currentInventory.videoStreams.size > 1 && panelVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .heightIn(max = 720.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        PocMultiStreamPanel(
                            clipUri = clip?.uri.orEmpty(),
                            inventory = currentInventory,
                            trackAnalyses = analyses,
                            selectedVideoIndex = selectedVideoIndex ?: currentInventory.videoStreams.first().index,
                            keptVideoIndexes = keptVideoIndexes,
                            keepSubtitles = keepSubtitles,
                            onSelectVideo = { index ->
                                selectedVideoIndex = index
                                keptVideoIndexes = keptVideoIndexes + index
                                manualConfirmed = true
                            },
                            onToggleKeepVideo = { index, keep ->
                                if (keep) {
                                    keptVideoIndexes = keptVideoIndexes + index
                                } else if (index != selectedVideoIndex) {
                                    keptVideoIndexes = keptVideoIndexes - index
                                }
                                manualConfirmed = true
                            },
                            onUseRecommended = {
                                val recommended = currentInventory.recommendation.selectedVideoIndex
                                    ?: currentInventory.videoStreams.first().index
                                selectedVideoIndex = recommended
                                keptVideoIndexes = setOf(recommended)
                                manualConfirmed = true
                            },
                            onKeepAll = {
                                keptVideoIndexes = currentInventory.videoStreams.map { it.index }.toSet()
                                manualConfirmed = true
                            },
                            onKeepSubtitles = {
                                keepSubtitles = it
                                manualConfirmed = true
                            },
                        )
                        if (detectionError != null) {
                            Text(
                                detectionError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        Button(
                            onClick = { panelVisible = false },
                            enabled = manualConfirmed,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (manualConfirmed) "Continuer avec ce choix"
                                else "Choisir une piste ou Conserver toutes",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
