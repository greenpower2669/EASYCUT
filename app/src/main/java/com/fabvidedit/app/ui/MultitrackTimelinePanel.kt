package com.fabvidedit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.model.SourceAudioTrack
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabSurfaceHigh

@Composable
fun MultitrackTimelinePanel(
    project: VideoProject,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("TIMELINE MULTIPISTE", fontWeight = FontWeight.Bold, color = FabMint)
        Text(
            "Chaque stream vidéo importé est une source indépendante. Les pistes démarrent à leur position propre et peuvent avoir des durées, ratios et résolutions différents.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        project.clips
            .groupBy(VideoClip::timelineTrackIndex)
            .toSortedMap(reverseOrder())
            .forEach { (trackIndex, clips) ->
                val ordered = clips.sortedBy(VideoClip::timelineStartMs)
                val selected = ordered.any { it.id == selectedClipId }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) FabMint.copy(alpha = 0.16f) else FabSurfaceHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ordered.firstOrNull()?.let { onSelectClip(it.id) } },
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "V${trackIndex + 1}",
                            fontWeight = FontWeight.Bold,
                            color = if (selected) FabMint else Color.Unspecified,
                        )
                        ordered.forEach { clip ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    clip.sourceStreamIndex?.let { "VIDEO $it" } ?: "VIDEO",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    clip.name,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(trackTime(clip.outputDurationMs), fontSize = 10.sp)
                            }
                            Text(
                                "${clip.width}×${clip.height} • départ ${trackTime(clip.timelineStartMs)} • source ${clip.sourceId.take(8)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

        if (project.sourceAudioTracks.isNotEmpty()) {
            HorizontalDivider()
            project.sourceAudioTracks
                .sortedByDescending(SourceAudioTrack::timelineTrackIndex)
                .forEach { track ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = FabSurfaceHigh),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("A${track.timelineTrackIndex + 1}", fontWeight = FontWeight.Bold)
                            Text(
                                "${track.sourceStreamIndex?.let { "AUDIO $it • " } ?: ""}${track.name}",
                                fontSize = 11.sp,
                            )
                            Text(
                                "${trackTime(track.durationMs)} • départ ${trackTime(track.timelineStartMs)} • source ${track.sourceId.take(8)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        }

        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Fermer les pistes")
        }
    }
}

private fun trackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds / 60L % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
