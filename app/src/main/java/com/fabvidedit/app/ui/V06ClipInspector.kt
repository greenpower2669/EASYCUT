package com.fabvidedit.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.media.MediaInspector
import com.fabvidedit.app.model.ClipFilter
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurfaceHigh
import com.fabvidedit.app.util.formatDuration

/** CapCut-like clip inspector used by v0.6: visual filmstrip + trim handles tied to the playhead. */
@Composable
internal fun V06ClipInspector(
    clip: VideoClip?,
    playheadSourceMs: Long?,
    currentVolume: Float,
    onTrim: (Long, Long) -> Unit,
    onSplit: () -> Unit,
    onSpeed: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFilter: (ClipFilter) -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    if (clip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sélectionne un clip dans une piste vidéo")
        }
        return
    }

    var trimRange by remember(clip.id, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf(clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat())
    }
    var volume by remember(clip.id, currentVolume) { mutableStateOf(currentVolume) }
    val head = playheadSourceMs?.coerceIn(0L, clip.durationMs)

    fun normalizeRange(range: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
        val minGap = 250f.coerceAtMost(clip.durationMs.toFloat())
        val start = range.start.coerceIn(0f, (range.endInclusive - minGap).coerceAtLeast(0f))
        val end = range.endInclusive.coerceIn(
            (start + minGap).coerceAtMost(clip.durationMs.toFloat()),
            clip.durationMs.toFloat(),
        )
        return start..end
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clip.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    "${formatDuration(clip.sourceDurationMs)} • ${clip.width}×${clip.height} • V${clip.timelineTrackIndex + 1}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDuplicate) { Icon(Icons.Rounded.ContentCopy, "Dupliquer") }
            IconButton(onClick = onRotate) { Icon(Icons.Rounded.RotateRight, "Rotation 90°") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Coupe visuelle", fontWeight = FontWeight.SemiBold)
                Text("Glisse directement les deux poignées sur les images", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Tête ${head?.let(::formatDuration) ?: "—"}",
                color = FabMint,
                fontSize = 11.sp,
            )
        }

        V06ThumbnailStrip(
            clip = clip,
            trimRange = trimRange,
            playheadSourceMs = head,
            onRangeChange = { trimRange = normalizeRange(it) },
            onRangeFinished = { onTrim(trimRange.start.toLong(), trimRange.endInclusive.toLong()) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val h = head ?: return@OutlinedButton
                    val start = h.coerceIn(0L, (trimRange.endInclusive.toLong() - 250L).coerceAtLeast(0L))
                    trimRange = start.toFloat()..trimRange.endInclusive
                    onTrim(start, trimRange.endInclusive.toLong())
                },
                enabled = head != null && head < trimRange.endInclusive.toLong() - 249L,
                modifier = Modifier.weight(1f),
            ) { Text("Début = tête", fontSize = 11.sp) }
            OutlinedButton(
                onClick = {
                    val h = head ?: return@OutlinedButton
                    val end = h.coerceIn((trimRange.start.toLong() + 250L).coerceAtMost(clip.durationMs), clip.durationMs)
                    trimRange = trimRange.start..end.toFloat()
                    onTrim(trimRange.start.toLong(), end)
                },
                enabled = head != null && head > trimRange.start.toLong() + 249L,
                modifier = Modifier.weight(1f),
            ) { Text("Fin = tête", fontSize = 11.sp) }
        }

        OutlinedButton(onClick = onSplit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text("Scinder exactement à la tête de lecture")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Vitesse", fontWeight = FontWeight.SemiBold)
            Text("×${clip.speed}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f).forEach { speed ->
                FilterChip(
                    selected = clip.speed == speed,
                    onClick = { onSpeed(speed) },
                    label = { Text("×$speed") },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Volume du clip", fontWeight = FontWeight.SemiBold)
            Text("${(volume * 100).toInt()} %", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { onVolume(volume) },
            valueRange = 0f..1f,
        )

        Text("Filtre • ${clip.filter.label}", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClipFilter.entries.forEach { filter ->
                FilterChip(
                    selected = clip.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun V06ThumbnailStrip(
    clip: VideoClip,
    trimRange: ClosedFloatingPointRange<Float>,
    playheadSourceMs: Long?,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRangeFinished: () -> Unit,
) {
    val context = LocalContext.current
    val count = 7
    val shape = RoundedCornerShape(9.dp)
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(82.dp).clip(shape).background(FabSurfaceHigh)
            .border(1.dp, FabPurple.copy(alpha = 0.7f), shape),
    ) {
        Row(Modifier.fillMaxSize()) {
            repeat(count) { index ->
                val sampleMs = ((index + 0.5f) / count * clip.durationMs).toLong().coerceIn(0L, clip.durationMs)
                val bitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    key1 = clip.uri,
                    key2 = sampleMs,
                ) {
                    value = MediaInspector.thumbnail(context, clip.uri, sampleMs, width = 160, height = 90)
                }
                Box(Modifier.weight(1f).fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
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

        val duration = clip.durationMs.coerceAtLeast(1L).toFloat()
        val startP = (trimRange.start / duration).coerceIn(0f, 1f)
        val endP = (trimRange.endInclusive / duration).coerceIn(0f, 1f)
        if (startP > 0f) {
            Box(Modifier.width(maxWidth * startP).fillMaxSize().background(Color.Black.copy(alpha = 0.56f)))
        }
        if (endP < 1f) {
            Box(
                Modifier.offset(x = maxWidth * endP).width(maxWidth * (1f - endP)).fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
            )
        }

        playheadSourceMs?.let { head ->
            val headP = (head.toFloat() / duration).coerceIn(0f, 1f)
            Box(
                Modifier.offset(x = maxWidth * headP - 1.dp).width(2.dp).fillMaxSize().background(Color.White),
            )
            Box(
                Modifier.offset(x = maxWidth * headP - 4.dp).size(8.dp).clip(RoundedCornerShape(2.dp)).background(FabMint),
            )
        }

        RangeSlider(
            value = trimRange,
            onValueChange = onRangeChange,
            onValueChangeFinished = onRangeFinished,
            valueRange = 0f..clip.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
        )
    }
}
