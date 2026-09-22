package com.fabvidedit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.media.FfmpegTrackTools
import com.fabvidedit.app.model.MediaStreamInfo
import com.fabvidedit.app.model.MediaStreamInventory
import com.fabvidedit.app.model.StreamSelectionConfidence
import com.fabvidedit.app.model.VideoFormatAnalysis
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabSurfaceHigh

@Composable
fun PocMultiStreamPanel(
    clipUri: String,
    inventory: MediaStreamInventory,
    trackAnalyses: Map<Int, VideoFormatAnalysis>,
    selectedVideoIndex: Int,
    keptVideoIndexes: Set<Int>,
    keepSubtitles: Boolean,
    onSelectVideo: (Int) -> Unit,
    onToggleKeepVideo: (Int, Boolean) -> Unit,
    onUseRecommended: () -> Unit,
    onKeepAll: () -> Unit,
    onKeepSubtitles: (Boolean) -> Unit,
) {
    val recommendation = inventory.recommendation
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = FabSurfaceHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Pistes détectées", fontWeight = FontWeight.Bold)
            Text(
                "${inventory.videoStreams.size} vidéo • ${inventory.audioStreams.size} audio • " +
                    "${inventory.subtitleStreams.size} sous-titre • ${inventory.dataStreams.size} data",
                fontSize = 12.sp,
            )
            Text(
                "Piste principale proposée : VIDEO ${recommendation.selectedVideoIndex ?: "?"} • " +
                    "Confiance : ${recommendation.confidence.label}",
                color = when (recommendation.confidence) {
                    StreamSelectionConfidence.HIGH -> FabMint
                    StreamSelectionConfidence.MEDIUM -> MaterialTheme.colorScheme.primary
                    StreamSelectionConfidence.LOW -> FabPink
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(recommendation.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (recommendation.simultaneousVideoConflict) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = FabPink, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text("PLUSIEURS PISTES VIDÉO SIMULTANÉES", color = FabPink, fontWeight = FontWeight.Bold)
                        Text(
                            "Risque de superposition, mauvais ratio ou rendu différent selon le lecteur. " +
                                "Recommandation : conserver uniquement la piste principale.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (recommendation.requiresManualChoice) {
                Text(
                    "Impossible de déterminer automatiquement la piste principale avec assez de confiance. " +
                        "Aucune piste n'est exclue automatiquement : vérifie les aperçus ci-dessous.",
                    color = FabPink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Button(onClick = onUseRecommended) { Text("Sélection recommandée") }
                OutlinedButton(onClick = onKeepAll) { Text("Conserver toutes") }
                FilterChip(
                    selected = showDetails,
                    onClick = { showDetails = !showDetails },
                    label = { Text("Détails") },
                )
            }

            if (showDetails) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("MEDIA DIAGNOSTIC", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Container : ${inventory.containerName ?: inventory.containerLongName ?: "?"}", fontSize = 10.sp)
                        Text(
                            "Video streams : ${inventory.videoStreams.size} • Audio : ${inventory.audioStreams.size} • " +
                                "Subtitle : ${inventory.subtitleStreams.size} • Data : ${inventory.dataStreams.size}",
                            fontSize = 10.sp,
                        )
                        Text("SELECTED : VIDEO $selectedVideoIndex", fontSize = 10.sp, color = FabMint)
                        Text(
                            "EXCLUDED : ${inventory.videoStreams.map { it.index }.filterNot { it in keptVideoIndexes }.joinToString { "VIDEO $it" }.ifBlank { "aucune" }}",
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            inventory.videoStreams.forEachIndexed { position, stream ->
                if (position > 0) HorizontalDivider()
                VideoStreamRow(
                    clipUri = clipUri,
                    inventory = inventory,
                    stream = stream,
                    analysis = trackAnalyses[stream.index],
                    isSelected = selectedVideoIndex == stream.index,
                    isKept = stream.index in keptVideoIndexes,
                    showDetails = showDetails,
                    onSelect = { onSelectVideo(stream.index) },
                    onKeep = { onToggleKeepVideo(stream.index, it) },
                )
            }

            inventory.audioStreams.firstOrNull { it.index == recommendation.selectedAudioIndex }?.let { audio ->
                HorizontalDivider()
                Text(
                    "Audio principal : AUDIO ${audio.index} • ${audio.codecName ?: "codec ?"}" +
                        (audio.tags["language"]?.let { " • $it" } ?: ""),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (inventory.subtitleStreams.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepSubtitles, onCheckedChange = onKeepSubtitles)
                    Column {
                        Text("Sous-titres détectés : ${inventory.subtitleStreams.size}")
                        Text(
                            if (keepSubtitles) "Conserver lors d'un remux multi-pistes" else "Exclure de l'export",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (inventory.dataStreams.isNotEmpty()) {
                Text(
                    "Data streams : ${inventory.dataStreams.size} • exclus par défaut de l'export vidéo",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoStreamRow(
    clipUri: String,
    inventory: MediaStreamInventory,
    stream: MediaStreamInfo,
    analysis: VideoFormatAnalysis?,
    isSelected: Boolean,
    isKept: Boolean,
    showDetails: Boolean,
    onSelect: () -> Unit,
    onKeep: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    "VIDEO ${stream.index} — ${stream.width}×${stream.height} • ${stream.ratioLabel}",
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) FabMint else Color.Unspecified,
                )
                Text(
                    "Durée ${streamDuration(stream, inventory.durationMs)}" +
                        if (isSelected) " • PRINCIPALE" else " • SECONDAIRE",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = isKept, onCheckedChange = onKeep)
            Text(if (isKept) "Conserver" else "Exclure", fontSize = 11.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val duration = (stream.durationMs ?: inventory.durationMs).coerceAtLeast(1L)
            listOf(0.15f, 0.50f, 0.85f).forEach { fraction ->
                val timeMs = (duration * fraction).toLong().coerceIn(0L, duration - 1L)
                StreamThumbnail(clipUri, stream.index, timeMs)
            }
        }

        analysis?.let { detected ->
            Text(
                "Analyse 11 points : format majoritaire ${detected.majority.format.width}×${detected.majority.format.height} " +
                    "(${(detected.majority.share * 100).toInt()} %)",
                fontSize = 11.sp,
            )
        }
        if (showDetails) {
            val score = inventory.recommendation.scores.firstOrNull { it.streamIndex == stream.index }
            Text(
                buildString {
                    append("codec=${stream.codecName ?: "?"}")
                    stream.frameRate?.let { append(" • %.2f fps".format(it)) }
                    append(" • default=${stream.isDefault}")
                    stream.frameCount?.let { append(" • frames=$it") }
                    score?.let { append(" • score=${"%.1f".format(it.score)}") }
                    if (stream.rotationDegrees != 0) append(" • rotation=${stream.rotationDegrees}°")
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreamThumbnail(clipUri: String, streamIndex: Int, timeMs: Long) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = clipUri,
        key2 = "$streamIndex:$timeMs",
    ) {
        value = FfmpegTrackTools.thumbnail(
            context = context,
            uri = android.net.Uri.parse(clipUri),
            streamIndex = streamIndex,
            timeMs = timeMs,
        )
    }
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Aperçu VIDEO $streamIndex",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        } ?: Text("aperçu…", color = Color.LightGray, fontSize = 10.sp)
    }
}

private fun streamDuration(stream: MediaStreamInfo, fallbackMs: Long): String {
    val totalSeconds = (stream.durationMs ?: fallbackMs).coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
