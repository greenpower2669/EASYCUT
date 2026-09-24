package com.fabvidedit.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowLeft
import androidx.compose.material.icons.rounded.ArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.model.AspectRatioPreset
import com.fabvidedit.app.model.AudioTrack
import com.fabvidedit.app.model.ClipFilter
import com.fabvidedit.app.model.ClipTransform
import com.fabvidedit.app.model.ClipTransition
import com.fabvidedit.app.model.MotionEasing
import com.fabvidedit.app.model.TextLayer
import com.fabvidedit.app.model.TextPosition
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.TransitionType
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurfaceHigh
import com.fabvidedit.app.util.formatDuration

@Composable
internal fun ClipInspector(
    clip: VideoClip?,
    currentVolume: Float,
    onTrim: (Long, Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onFilter: (ClipFilter) -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
) {
    if (clip == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.VideoSettings, contentDescription = null, tint = FabPurple)
            Spacer(Modifier.height(8.dp))
            Text("Sélectionne un clip dans la timeline")
        }
        return
    }

    var trimRange by remember(clip.id, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf(clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat())
    }
    var volume by remember(clip.id, currentVolume) { mutableStateOf(currentVolume) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    clip.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${formatDuration(clip.sourceDurationMs)} • ${clip.width}×${clip.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InspectorAction(Icons.Rounded.ArrowLeft, "Déplacer à gauche", onMoveLeft)
            InspectorAction(Icons.Rounded.ArrowRight, "Déplacer à droite", onMoveRight)
            InspectorAction(Icons.Rounded.ContentCopy, "Dupliquer", onDuplicate)
            InspectorAction(Icons.Rounded.RotateRight, "Rotation 90 degrés", onRotate)
            InspectorAction(
                Icons.Rounded.DeleteOutline,
                "Supprimer le clip",
                onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }

        LabelValue("Coupe", "${formatDuration(trimRange.start.toLong())} — ${formatDuration(trimRange.endInclusive.toLong())}")
        RangeSlider(
            value = trimRange,
            onValueChange = { range ->
                val start = range.start.coerceAtMost(range.endInclusive - 250f)
                val end = range.endInclusive.coerceAtLeast(start + 250f)
                trimRange = start..end
            },
            onValueChangeFinished = {
                onTrim(trimRange.start.toLong(), trimRange.endInclusive.toLong())
            },
            valueRange = 0f..clip.durationMs.coerceAtLeast(1).toFloat(),
        )

        LabelValue("Vitesse", "×${clip.speed}")
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

        LabelValue("Volume du clip", "${(volume * 100).toInt()} %")
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { onVolume(volume) },
            valueRange = 0f..1f,
        )

        LabelValue("Filtre", clip.filter.label)
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
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun InspectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
internal fun TransformInspector(
    clip: VideoClip?,
    transform: ClipTransform,
    brightness: Float,
    volume: Float,
    keyframeAtPlayhead: Boolean,
    selectedEasing: MotionEasing?,
    selectedAspectKeyframe: com.fabvidedit.app.model.TransformKeyframe?,
    onAspectOverride: (Float?, Float?) -> Unit,
    onCreateWorkingCopy: () -> Unit,
    onTransform: (ClipTransform) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onAddKeyframe: () -> Unit,
    onRemoveKeyframe: () -> Unit,
    onEasing: (MotionEasing) -> Unit,
    onReset: () -> Unit,
) {
    if (clip == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Sélectionne un clip pour régler son mouvement")
        }
        return
    }
    var live by remember(clip.id, transform) { mutableStateOf(transform) }
    var liveBrightness by remember(clip.id, brightness) { mutableStateOf(brightness) }
    var liveVolume by remember(clip.id, volume) { mutableStateOf(volume) }
    var linkedScale by remember(clip.id) { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Images-clés du clip", fontWeight = FontWeight.Bold)
                Text(
                    "Un losange mémorise la matrice, la luminosité et le volume à cet instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = if (keyframeAtPlayhead) onRemoveKeyframe else onAddKeyframe) {
                DiamondGlyph(if (keyframeAtPlayhead) FabPink else FabMint)
                Spacer(Modifier.width(7.dp))
                Text(if (keyframeAtPlayhead) "− Losange" else "+ Losange")
            }
        }

        Text(
            if (clip.keyframes.isEmpty()) {
                "Réglage fixe • ajoute un losange pour commencer une animation"
            } else {
                "${clip.keyframes.size} losange(s) • les modifications créent automatiquement une image-clé"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (clip.keyframes.isEmpty()) FabPurple else FabMint,
        )

        FilterChip(
            selected = linkedScale,
            onClick = { linkedScale = !linkedScale },
            label = { Text("Conserver les proportions") },
        )
        LabelValue("Échelle X", "${(live.scaleX * 100).toInt()} %")
        Slider(
            value = live.scaleX,
            onValueChange = { value ->
                live = if (linkedScale) live.copy(scaleX = value, scaleY = value) else live.copy(scaleX = value)
            },
            onValueChangeFinished = { onTransform(live) },
            valueRange = 0.25f..4f,
        )
        LabelValue("Échelle Y", "${(live.scaleY * 100).toInt()} %")
        Slider(
            value = live.scaleY,
            enabled = !linkedScale,
            onValueChange = { live = live.copy(scaleY = it) },
            onValueChangeFinished = { onTransform(live) },
            valueRange = 0.25f..4f,
        )

        LabelValue("Position X", "${(live.positionX * 100).toInt()} %")
        Slider(
            value = live.positionX,
            onValueChange = { live = live.copy(positionX = it) },
            onValueChangeFinished = { onTransform(live) },
            valueRange = -2f..2f,
        )
        LabelValue("Position Y", "${(live.positionY * 100).toInt()} %")
        Slider(
            value = live.positionY,
            onValueChange = { live = live.copy(positionY = it) },
            onValueChangeFinished = { onTransform(live) },
            valueRange = -2f..2f,
        )
        LabelValue("Rotation", "${live.rotationDegrees.toInt()}°")
        Slider(
            value = live.rotationDegrees.coerceIn(-180f, 180f),
            onValueChange = { live = live.copy(rotationDegrees = it) },
            onValueChangeFinished = { onTransform(live) },
            valueRange = -180f..180f,
        )

        LabelValue("Luminosité", "${(liveBrightness * 100).toInt()} %")
        Slider(
            value = liveBrightness,
            onValueChange = { liveBrightness = it },
            onValueChangeFinished = { onBrightness(liveBrightness) },
            valueRange = 0f..2f,
        )
        LabelValue("Volume du clip", "${(liveVolume * 100).toInt()} %")
        Slider(
            value = liveVolume,
            onValueChange = { liveVolume = it },
            onValueChangeFinished = { onVolume(liveVolume) },
            valueRange = 0f..1f,
        )

        Text("Pivot", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                LabelValue("X", "${(live.pivotX * 100).toInt()} %")
                Slider(
                    value = live.pivotX,
                    onValueChange = { live = live.copy(pivotX = it) },
                    onValueChangeFinished = { onTransform(live) },
                    valueRange = -1f..1f,
                )
            }
            Column(Modifier.weight(1f)) {
                LabelValue("Y", "${(live.pivotY * 100).toInt()} %")
                Slider(
                    value = live.pivotY,
                    onValueChange = { live = live.copy(pivotY = it) },
                    onValueChangeFinished = { onTransform(live) },
                    valueRange = -1f..1f,
                )
            }
        }

        if (keyframeAtPlayhead) {
            Text("Courbe vers ce losange", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MotionEasing.entries.forEach { easing ->
                    FilterChip(
                        selected = selectedEasing == easing,
                        onClick = { onEasing(easing) },
                        label = { Text(easing.label) },
                    )
                }
            }
        }
        SarDarKeyframeControls(
            clip = clip,
            selected = selectedAspectKeyframe,
            onOverride = onAspectOverride,
            onCreateWorkingCopy = onCreateWorkingCopy,
        )
        TextButton(onClick = onReset) { Text("Réinitialiser matrice, luminosité et losanges") }
    }
}

@Composable
private fun DiamondGlyph(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .rotate(45f)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
internal fun TransitionInspector(
    fromClip: VideoClip?,
    toClip: VideoClip?,
    transition: ClipTransition?,
    onType: (TransitionType) -> Unit,
    onDuration: (Long) -> Unit,
    onAutomatic: () -> Unit,
    onRemove: () -> Unit,
    onClearAll: () -> Unit,
) {
    var duration by remember(transition?.id, transition?.durationMs) {
        mutableStateOf((transition?.durationMs ?: 600).toFloat())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Transitions calculées", fontWeight = FontWeight.Bold)
                Text(
                    if (fromClip != null && toClip != null) "${fromClip.name}  →  ${toClip.name}"
                    else "Sélectionne un clip qui possède un clip suivant",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAutomatic) { Text("Auto") }
        }

        if (fromClip != null && toClip != null) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransitionType.entries.forEach { type ->
                    FilterChip(
                        selected = transition?.type == type,
                        onClick = { onType(type) },
                        label = { Text(type.label) },
                    )
                }
            }
            LabelValue("Durée totale", "${duration.toInt()} ms")
            Slider(
                value = duration,
                enabled = transition != null,
                onValueChange = { duration = it },
                onValueChangeFinished = { onDuration(duration.toLong()) },
                valueRange = 200f..2_000f,
            )
            Text(
                "La sortie du premier clip et l’entrée du suivant sont coordonnées automatiquement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (transition != null) {
                TextButton(onClick = onRemove) { Text("Retirer cette transition") }
            }
        }
        TextButton(onClick = onClearAll) { Text("Retirer toutes les transitions") }
    }
}

@Composable
internal fun TextInspector(
    layers: List<TextLayer>,
    onAdd: () -> Unit,
    onEdit: (TextLayer) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Textes", fontWeight = FontWeight.Bold)
                Text(
                    "Ajoute plusieurs titres synchronisés",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAdd) { Text("+ Ajouter") }
        }
        if (layers.isEmpty()) {
            Text(
                "Aucun texte pour le moment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        layers.forEach { layer ->
            Card(
                colors = CardDefaults.cardColors(containerColor = FabSurfaceHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(layer.colorArgb)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(layer.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatDuration(layer.startMs)} — ${formatDuration(layer.endMs)} • ${layer.position.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(layer) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Modifier le texte")
                    }
                    IconButton(onClick = { onDelete(layer.id) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Supprimer le texte",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioInspector(
    track: AudioTrack?,
    currentVolume: Float,
    keyframeAtPlayhead: Boolean,
    selectedEasing: MotionEasing?,
    onChoose: () -> Unit,
    onVolume: (Float) -> Unit,
    onAddKeyframe: () -> Unit,
    onRemoveKeyframe: () -> Unit,
    onEasing: (MotionEasing) -> Unit,
    onRemove: () -> Unit,
) {
    var volume by remember(track?.uri, currentVolume) { mutableStateOf(currentVolume) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = FabMint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.name ?: "Musique de fond", fontWeight = FontWeight.Bold)
                Text(
                    if (track == null) "MP3, M4A, WAV et formats Android" else "Bouclée jusqu’à la fin du montage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onChoose) { Text(if (track == null) "Choisir" else "Changer") }
        }
        if (track != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Images-clés de volume", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (track.keyframes.isEmpty()) {
                            "Volume fixe • ajoute un triangle pour commencer"
                        } else {
                            "${track.keyframes.size} triangle(s) sur la piste audio"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (track.keyframes.isEmpty()) FabPurple else FabMint,
                    )
                }
                OutlinedButton(onClick = if (keyframeAtPlayhead) onRemoveKeyframe else onAddKeyframe) {
                    Text("▲", color = if (keyframeAtPlayhead) FabPink else FabMint)
                    Spacer(Modifier.width(6.dp))
                    Text(if (keyframeAtPlayhead) "− Triangle" else "+ Triangle")
                }
            }
            LabelValue("Volume de la musique", "${(volume * 100).toInt()} %")
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { onVolume(volume) },
                valueRange = 0f..1f,
            )
            if (keyframeAtPlayhead) {
                Text("Courbe vers ce triangle", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MotionEasing.entries.forEach { easing ->
                        FilterChip(
                            selected = selectedEasing == easing,
                            onClick = { onEasing(easing) },
                            label = { Text(easing.label) },
                        )
                    }
                }
            }
            TextButton(onClick = onRemove) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Retirer la musique", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun CanvasInspector(
    selected: AspectRatioPreset,
    onSelect: (AspectRatioPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Format de la vidéo", fontWeight = FontWeight.Bold)
        Text(
            "Le contenu est ajusté sans être déformé.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AspectRatioPreset.entries.forEach { preset ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (preset == selected) FabPink.copy(alpha = 0.18f) else FabSurfaceHigh)
                        .border(
                            1.dp,
                            if (preset == selected) FabPink else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(preset) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val ratio = preset.ratio ?: 16f / 9f
                    Box(
                        Modifier
                            .then(
                                if (ratio >= 1f) Modifier.size(width = 38.dp, height = (38 / ratio).dp)
                                else Modifier.size(width = (38 * ratio).dp, height = 38.dp),
                            )
                            .border(2.dp, if (preset == selected) FabPink else FabPurple, RoundedCornerShape(3.dp)),
                    )
                    Text(preset.label, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun TextLayerDialog(
    existing: TextLayer?,
    projectDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (TextLayer) -> Unit,
) {
    val maximum = projectDurationMs.coerceAtLeast(1_000)
    var text by remember(existing?.id) { mutableStateOf(existing?.text ?: "") }
    var position by remember(existing?.id) { mutableStateOf(existing?.position ?: TextPosition.BOTTOM) }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorArgb ?: 0xFFFFFFFF.toInt()) }
    var size by remember(existing?.id) { mutableStateOf((existing?.sizePx ?: 72).toFloat()) }
    var range by remember(existing?.id, maximum) {
        mutableStateOf(
            (existing?.startMs ?: 0L).toFloat()..
                (existing?.endMs ?: maximum).coerceAtMost(maximum).toFloat(),
        )
    }
    val colors = listOf(
        0xFFFFFFFF.toInt(),
        0xFFFFE66D.toInt(),
        0xFFFF4D76.toInt(),
        0xFF31D6C4.toInt(),
        0xFF9F7AEA.toInt(),
        0xFF111111.toInt(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Ajouter un texte" else "Modifier le texte") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(120) },
                    label = { Text("Texte affiché") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Position", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextPosition.entries.forEach {
                        FilterChip(
                            selected = position == it,
                            onClick = { position = it },
                            label = { Text(it.label) },
                        )
                    }
                }
                Text("Couleur", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .border(
                                    if (color == value) 3.dp else 1.dp,
                                    if (color == value) FabPink else Color.Gray,
                                    CircleShape,
                                )
                                .clickable { color = value },
                        )
                    }
                }
                LabelValue("Taille", size.toInt().toString())
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 42f..128f,
                )
                LabelValue("Affichage", "${formatDuration(range.start.toLong())} — ${formatDuration(range.endInclusive.toLong())}")
                RangeSlider(
                    value = range,
                    onValueChange = { changed ->
                        val start = changed.start.coerceAtMost(changed.endInclusive - 250f)
                        val end = changed.endInclusive.coerceAtLeast(start + 250f)
                        range = start..end
                    },
                    valueRange = 0f..maximum.toFloat(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: TextLayer(
                            text = text.trim(),
                            startMs = range.start.toLong(),
                            endMs = range.endInclusive.toLong(),
                        )).copy(
                            text = text.trim(),
                            startMs = range.start.toLong(),
                            endMs = range.endInclusive.toLong(),
                            position = position,
                            colorArgb = color,
                            sizePx = size.toInt(),
                        ),
                    )
                },
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
