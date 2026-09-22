package com.fabvidedit.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState as rememberHorizontalScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.media.ExportState
import com.fabvidedit.app.media.PocExportManager
import com.fabvidedit.app.media.PocThumbnailCache
import com.fabvidedit.app.media.PocVideoAnalyzer
import com.fabvidedit.app.model.PocCompression
import com.fabvidedit.app.model.PocExportSettings
import com.fabvidedit.app.model.PocFrameRate
import com.fabvidedit.app.model.PocOutputPreset
import com.fabvidedit.app.model.PocRatioMode
import com.fabvidedit.app.model.PocVideoCodec
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoFormatAnalysis
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.ui.theme.FabBackground
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurface
import com.fabvidedit.app.ui.theme.FabSurfaceHigh
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PocEditorScreen(viewModel: FabVidEditViewModel, project: VideoProject) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycleCompat()
    val selectedIndex = project.clips.indexOfFirst { it.id == selectedClipId }.let { if (it >= 0) it else 0 }
    val clip = project.clips.getOrNull(selectedIndex)

    var analysis by remember(clip?.id) { mutableStateOf<VideoFormatAnalysis?>(null) }
    var analysisError by remember(clip?.id) { mutableStateOf<String?>(null) }
    var analyzing by remember(clip?.id) { mutableStateOf(false) }
    var currentPositionMs by remember(clip?.id) { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var trimStart by remember(clip?.id) { mutableFloatStateOf(clip?.trimStartMs?.toFloat() ?: 0f) }
    var trimEnd by remember(clip?.id) { mutableFloatStateOf(clip?.trimEndMs?.toFloat() ?: 1f) }
    var settings by remember { mutableStateOf(PocExportSettings()) }
    var exportState by remember { mutableStateOf<ExportState>(ExportState.Idle) }
    var customWidthText by remember { mutableStateOf(settings.customWidth.toString()) }
    var customHeightText by remember { mutableStateOf(settings.customHeight.toString()) }
    var customBitrateText by remember { mutableStateOf(settings.customVideoBitrateMbps.toString()) }

    val player = remember { ExoPlayer.Builder(context).build() }
    val exportManager = remember { PocExportManager(context, scope) { exportState = it } }

    val addVideosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.addClips(it)
    }

    DisposableEffect(player, exportManager) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            exportManager.cancel(resetState = false)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.currentPosition >= 0L) currentPositionMs = player.currentPosition
            delay(100L)
        }
    }

    LaunchedEffect(clip?.id, clip?.uri) {
        clip ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(clip.uri))
        player.prepare()
        player.seekTo(clip.trimStartMs)
        currentPositionMs = clip.trimStartMs
        analyzing = true
        analysisError = null
        analysis = runCatching { PocVideoAnalyzer.analyze(context, android.net.Uri.parse(clip.uri)) }
            .onFailure { analysisError = it.localizedMessage ?: "Analyse impossible" }
            .getOrNull()
        analyzing = false
    }

    val durationMs = clip?.durationMs?.coerceAtLeast(1L) ?: 1L
    val selectedDurationMs = (trimEnd - trimStart).toLong().coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FabBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        PocHeader(
            project = project,
            onBack = viewModel::closeProject,
            onAdd = { addVideosLauncher.launch(arrayOf("video/*")) },
        )

        if (project.clips.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(project.clips, key = { _, item -> item.id }) { index, item ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectClip(item.id) },
                        label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        if (clip == null) {
            Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = { addVideosLauncher.launch(arrayOf("video/*")) }) {
                    Icon(Icons.Rounded.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importer une vidéo")
                }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { androidContext ->
                    PlayerView(androidContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "POC • FORMAT INTELLIGENT",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                color = FabMint,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) player.pause() else {
                        if (currentPositionMs >= trimEnd.toLong() - 80L) player.seekTo(trimStart.toLong())
                        player.play()
                    }
                },
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
            }
            Text(pocTime(currentPositionMs), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Total ${pocTime(durationMs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnalysisCard(
            analysis = analysis,
            analyzing = analyzing,
            error = analysisError,
            onGoTo = { timeMs ->
                player.pause()
                player.seekTo(timeMs)
                currentPositionMs = timeMs
            },
        )

        Text(
            "Timeline visuelle et découpage",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold,
        )

        PocFilmstrip(
            clip = clip,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            currentPositionMs = currentPositionMs,
            onRangeChange = { start, end, movedTime ->
                trimStart = start.coerceIn(0f, durationMs.toFloat())
                trimEnd = end.coerceIn(trimStart + 1f, durationMs.toFloat())
                player.pause()
                player.seekTo(movedTime.toLong())
                currentPositionMs = movedTime.toLong()
            },
            onRangeFinished = {
                viewModel.trimSelected(trimStart.toLong(), trimEnd.toLong())
            },
            onSeek = { timeMs ->
                player.pause()
                player.seekTo(timeMs)
                currentPositionMs = timeMs
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Début ${pocTime(trimStart.toLong())}", color = FabMint, fontSize = 12.sp)
            Text("Conservé ${pocTime(selectedDurationMs)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Fin ${pocTime(trimEnd.toLong())}", color = FabPink, fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = {
                viewModel.trimSelected(trimStart.toLong(), trimEnd.toLong())
                player.seekTo(trimStart.toLong())
            },
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Appliquer le cut")
        }

        ExportSettingsCard(
            clip = clip,
            analysis = analysis,
            settings = settings,
            customWidthText = customWidthText,
            customHeightText = customHeightText,
            customBitrateText = customBitrateText,
            onSettings = { settings = it },
            onCustomWidth = {
                customWidthText = it.filter(Char::isDigit).take(5)
                customWidthText.toIntOrNull()?.let { value -> settings = settings.copy(customWidth = value.coerceAtLeast(2)) }
            },
            onCustomHeight = {
                customHeightText = it.filter(Char::isDigit).take(5)
                customHeightText.toIntOrNull()?.let { value -> settings = settings.copy(customHeight = value.coerceAtLeast(2)) }
            },
            onCustomBitrate = {
                customBitrateText = it.replace(',', '.').filter { char -> char.isDigit() || char == '.' }.take(5)
                customBitrateText.toFloatOrNull()?.let { value -> settings = settings.copy(customVideoBitrateMbps = value) }
            },
            trimStartMs = trimStart.toLong(),
            trimEndMs = trimEnd.toLong(),
            onExport = {
                exportManager.start(
                    clip = clip,
                    trimStartMs = trimStart.toLong(),
                    trimEndMs = trimEnd.toLong(),
                    settings = settings,
                    analysis = analysis,
                )
            },
        )
        Spacer(Modifier.height(28.dp))
    }

    when (val state = exportState) {
        ExportState.Idle -> Unit
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Encodage POC") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${state.progress} % • ${state.message}")
                }
            },
            confirmButton = {
                TextButton(onClick = { exportManager.cancel() }) { Text("Annuler") }
            },
        )
        is ExportState.Success -> AlertDialog(
            onDismissRequest = { exportState = ExportState.Idle },
            title = { Text("Export terminé") },
            text = { Text("${state.fileName}\nEnregistré dans Films/FabVidEdit.") },
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
            dismissButton = {
                TextButton(onClick = { exportState = ExportState.Idle }) { Text("Fermer") }
            },
        )
        is ExportState.Error -> AlertDialog(
            onDismissRequest = { exportState = ExportState.Idle },
            title = { Text("Export impossible") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { exportState = ExportState.Idle }) { Text("Fermer") }
            },
        )
    }
}

@Composable
private fun PocHeader(project: VideoProject, onBack: () -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FabSurface).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour") }
        Column(Modifier.weight(1f)) {
            Text("FabVid Edit — POC", fontWeight = FontWeight.Bold)
            Text(project.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onAdd) { Icon(Icons.Rounded.AddCircle, contentDescription = "Ajouter une vidéo") }
    }
}

@Composable
private fun AnalysisCard(
    analysis: VideoFormatAnalysis?,
    analyzing: Boolean,
    error: String?,
    onGoTo: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = FabSurfaceHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Analyse intelligente du format", fontWeight = FontWeight.Bold)
            when {
                analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyse de 11 positions réparties sur toute la vidéo…")
                }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                analysis != null -> {
                    val main = analysis.majority
                    Text(
                        "Format principal : ${main.format.width}×${main.format.height} • ${main.format.ratioLabel} • ${main.format.orientation.label}",
                        color = FabMint,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Majoritaire sur ${main.samples}/${analysis.samples.size} échantillons (${(main.share * 100).toInt()} %)")
                    main.format.codecMime?.let { mime ->
                        val fps = main.format.frameRate?.let { " • ${"%.2f".format(it)} fps" } ?: ""
                        Text("Codec : $mime$fps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    analysis.secondaryFormats.forEach { secondary ->
                        Text(
                            "Secondaire : ${secondary.format.width}×${secondary.format.height} • ${secondary.format.ratioLabel} • ${secondary.samples} échantillon(s)",
                            fontSize = 12.sp,
                        )
                    }
                    if (analysis.possibleIntroOrAd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = FabPink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Introduction ou publicité potentielle détectée", color = FabPink, fontWeight = FontWeight.Bold)
                        }
                    }
                    analysis.ruptures.take(4).forEach { rupture ->
                        OutlinedButton(onClick = { onGoTo(rupture.timeMs) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Aller à ${pocTime(rupture.timeMs)} • ${rupture.from.width}×${rupture.from.height} → ${rupture.to.width}×${rupture.to.height}",
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (analysis.ruptures.isEmpty()) {
                        Text("Aucune rupture de résolution détectée dans les échantillons.", fontSize = 12.sp)
                    }
                }
                else -> Text("Analyse en attente…")
            }
        }
    }
}

@Composable
private fun PocFilmstrip(
    clip: VideoClip,
    trimStartMs: Float,
    trimEndMs: Float,
    currentPositionMs: Long,
    onRangeChange: (Float, Float, Float) -> Unit,
    onRangeFinished: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val context = LocalContext.current
    val duration = clip.durationMs.coerceAtLeast(1L)
    val thumbCount = 9
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black),
        ) {
            repeat(thumbCount) { index ->
                val timeMs = ((duration - 1L) * index / (thumbCount - 1).coerceAtLeast(1)).coerceAtLeast(0L)
                val bitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    key1 = clip.uri,
                    key2 = timeMs,
                ) {
                    value = PocThumbnailCache.get(context, clip.uri, timeMs)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable { onSeek(timeMs) },
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        val playheadX = maxWidth * (currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .height(104.dp)
                .width(2.dp)
                .background(Color.White)
                .align(Alignment.CenterStart)
                .then(Modifier.padding(start = playheadX)),
        )
        RangeSlider(
            value = trimStartMs.coerceIn(0f, duration.toFloat())..trimEndMs.coerceIn(0f, duration.toFloat()),
            onValueChange = { range ->
                val startDelta = abs(range.start - trimStartMs)
                val endDelta = abs(range.endInclusive - trimEndMs)
                val moved = if (startDelta >= endDelta) range.start else range.endInclusive
                onRangeChange(range.start, range.endInclusive, moved)
            },
            onValueChangeFinished = onRangeFinished,
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun ExportSettingsCard(
    clip: VideoClip,
    analysis: VideoFormatAnalysis?,
    settings: PocExportSettings,
    customWidthText: String,
    customHeightText: String,
    customBitrateText: String,
    onSettings: (PocExportSettings) -> Unit,
    onCustomWidth: (String) -> Unit,
    onCustomHeight: (String) -> Unit,
    onCustomBitrate: (String) -> Unit,
    trimStartMs: Long,
    trimEndMs: Long,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = FabSurface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Paramètres de sortie", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            ChoiceRow("Format", PocOutputPreset.entries, settings.outputPreset) {
                onSettings(settings.copy(outputPreset = it))
            }
            if (settings.outputPreset == PocOutputPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customWidthText,
                        onValueChange = onCustomWidth,
                        label = { Text("Largeur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = customHeightText,
                        onValueChange = onCustomHeight,
                        label = { Text("Hauteur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ChoiceRow("Ratio", PocRatioMode.entries, settings.ratioMode) {
                onSettings(settings.copy(ratioMode = it))
            }
            ChoiceRow("Codec", PocVideoCodec.entries, settings.codec) {
                onSettings(settings.copy(codec = it))
            }
            ChoiceRow("FPS", PocFrameRate.entries, settings.frameRate) {
                onSettings(settings.copy(frameRate = it))
            }
            ChoiceRow("Compression", PocCompression.entries, settings.compression) {
                onSettings(settings.copy(compression = it))
            }
            if (settings.compression == PocCompression.CUSTOM) {
                OutlinedTextField(
                    value = customBitrateText,
                    onValueChange = onCustomBitrate,
                    label = { Text("Débit vidéo cible (Mb/s)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Audio AAC", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberHorizontalScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf<Int?>(null, 64, 96, 128, 160, 192).forEach { bitrate ->
                    FilterChip(
                        selected = settings.audioBitrateKbps == bitrate,
                        onClick = { onSettings(settings.copy(audioBitrateKbps = bitrate)) },
                        label = { Text(bitrate?.let { "$it kb/s" } ?: "Original") },
                    )
                }
            }

            val (width, height) = if (settings.ratioMode == PocRatioMode.ORIGINAL) {
                val format = analysis?.sourceFormat
                (format?.width?.takeIf { it > 0 } ?: clip.width) to
                    (format?.height?.takeIf { it > 0 } ?: clip.height)
            } else {
                settings.outputSize(analysis, clip.width, clip.height)
            }
            val bitrateMbps = settings.requestedVideoBitrate(width.coerceAtLeast(2), height.coerceAtLeast(2)) / 1_000_000f
            Card(colors = CardDefaults.cardColors(containerColor = FabSurfaceHigh)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Résumé avant export", fontWeight = FontWeight.Bold)
                    Text("SOURCE : ${analysis?.majority?.format?.width ?: clip.width}×${analysis?.majority?.format?.height ?: clip.height} • ${analysis?.majority?.format?.ratioLabel ?: "?"}")
                    analysis?.ruptures?.firstOrNull()?.let { Text("RUPTURE : ${pocTime(it.timeMs)}") }
                    Text("DÉCOUPAGE : ${pocTime(trimStartMs)} → ${pocTime(trimEndMs)} • ${pocTime(trimEndMs - trimStartMs)}")
                    Text("SORTIE : ${width}×${height} • ${settings.codec.label} • ${settings.frameRate.label}")
                    Text("Compression : ${settings.compression.label} • cible ~${"%.1f".format(bitrateMbps)} Mb/s")
                    Text("Audio : ${settings.audioBitrateKbps?.let { "AAC $it kb/s" } ?: "Original si possible"}")
                }
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Movie, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Encoder")
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    values: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) where T : Enum<T> {
    Text(title, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberHorizontalScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            val label = when (value) {
                is PocOutputPreset -> value.label
                is PocRatioMode -> value.label
                is PocVideoCodec -> value.label
                is PocFrameRate -> value.label
                is PocCompression -> value.label
                else -> value.name
            }
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun pocTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe / 60_000L) % 60L
    val seconds = (safe / 1_000L) % 60L
    val millis = safe % 1_000L
    return if (hours > 0L) {
        "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}
