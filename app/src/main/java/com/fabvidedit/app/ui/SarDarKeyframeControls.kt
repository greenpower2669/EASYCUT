package com.fabvidedit.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabvidedit.app.model.AspectRatioInput
import com.fabvidedit.app.model.TransformKeyframe
import com.fabvidedit.app.model.VideoClip
import com.fabvidedit.app.model.VisualMediaKind
import java.util.Locale

/** Explicit test controls in the diamond editor. Auto never alters the raw player. */
@Composable
internal fun SarDarKeyframeControls(
    clip: VideoClip,
    selected: TransformKeyframe?,
    onOverride: (Float?, Float?) -> Unit,
    onCreateWorkingCopy: () -> Unit,
) {
    var confirmCopy by remember(clip.id) { mutableStateOf(false) }
    if (confirmCopy) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text("Créer une copie de travail ?") },
            text = { Text(
                "EasyCut proposera un MP4 à pixels carrés sur une nouvelle piste. " +
                    "Le média original et les losanges sont préservés. " +
                    "Le réencodage utilise du stockage et peut modifier légèrement la qualité ; " +
                    "il peut échouer si le décodeur ou l'encodeur n'est pas compatible."
            ) },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmCopy = false
                    onCreateWorkingCopy()
                }) { Text("Créer la copie") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmCopy = false }) { Text("Annuler") }
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Proportions des pixels • hypothèses SAR / DAR", fontWeight = FontWeight.Bold)
        Text(
            "Détecté : SAR ${"%.4f".format(Locale.FRANCE, clip.sampleAspectRatio)} • " +
                "DAR ${if (clip.displayAspectRatio > 0f) "%.4f".format(Locale.FRANCE, clip.displayAspectRatio) else "non renseigné"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Le lecteur brut reste inchangé. Ces options testent uniquement la matrice " +
                "du losange (zoom, pivot et rotation). DAR manuel prend le pas sur SAR.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (clip.aspectVaries) Text(
            "⚠ Proportions variables observées sur les images-clés échantillonnées : " +
                "une copie à pixels carrés peut faciliter le montage.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (clip.mediaKind == VisualMediaKind.VIDEO) {
            OutlinedButton(onClick = { confirmCopy = true }) {
                Text("Créer une copie de travail à pixels carrés…")
            }
        }
        if (selected == null) {
            Text("Place la tête sur un losange ◆ pour activer SAR / DAR.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        OutlinedButton(onClick = { onOverride(null, null) }) {
            Text("Auto détecté — réinitialiser ce losange")
        }
        Text("SAR — forme des pixels", fontWeight = FontWeight.SemiBold)
        RatioChips(
            values = listOf("1:1" to 1f, "16:15" to 16f/15f, "8:9" to 8f/9f,
                "4:3" to 4f/3f, "64:45" to 64f/45f),
            selected = selected.sarOverride,
        ) { onOverride(it, null) }
        Text("DAR — format affiché", fontWeight = FontWeight.SemiBold)
        RatioChips(
            values = listOf("16:9" to 16f/9f, "4:3" to 4f/3f,
                "9:16" to 9f/16f, "1:1" to 1f),
            selected = selected.darOverride,
        ) { onOverride(null, it) }
        var customSar by remember(clip.id, selected.id) { mutableStateOf("") }
        var customDar by remember(clip.id, selected.id) { mutableStateOf("") }
        RatioCustom("SAR personnalisé (ex. 16:15)", customSar, { customSar = it }) {
            AspectRatioInput.parse(customSar)?.let { onOverride(it, null) }
        }
        RatioCustom("DAR personnalisé (ex. 16:9)", customDar, { customDar = it }) {
            AspectRatioInput.parse(customDar)?.let { onOverride(null, it) }
        }
        Text(
            "Une valeur manuelle s'applique jusqu'au prochain losange. Auto par défaut " +
                "sur chaque losange. Le montage d'origine n'est pas remplacé.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RatioChips(
    values: List<Pair<String, Float>>,
    selected: Float?,
    onSelect: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        values.forEach { (label, ratio) ->
            FilterChip(
                selected = selected != null && kotlin.math.abs(selected - ratio) < .0001f,
                onClick = { onSelect(ratio) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun RatioCustom(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            isError = value.isNotBlank() && AspectRatioInput.parse(value) == null,
        )
        OutlinedButton(
            onClick = onApply,
            enabled = AspectRatioInput.parse(value) != null,
        ) { Text("Appliquer") }
    }
}
