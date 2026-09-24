package com.fabvidedit.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabvidedit.app.media.ExportAacBitrate
import com.fabvidedit.app.media.ExportAudioChannels
import com.fabvidedit.app.media.ExportAudioMode
import com.fabvidedit.app.media.ExportBitrate
import com.fabvidedit.app.media.ExportQualityProfile
import com.fabvidedit.app.media.ExportSettings

/** Shared by both editors. All options affect EXPORT only; nothing here changes the preview. */
@Composable
internal fun ExportEconomyOptions(
    settings: ExportSettings,
    durationMs: Long,
    onChange: (ExportSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Taille du fichier", fontWeight = FontWeight.Bold)
        EconomyChips(
            items = ExportQualityProfile.entries,
            selected = settings.profile,
            label = { it.label },
        ) { profile ->
            onChange(settings.copy(profile = profile, bitrate = ExportBitrate.AUTO))
        }
        Text(
            when (settings.profile) {
                ExportQualityProfile.MINIMAL -> "Débit réduit : fichier plus petit, moins de détails."
                ExportQualityProfile.BALANCED -> "Compromis qualité / taille, selon résolution et cadence."
                ExportQualityProfile.HIGH -> "Débit accru : meilleure conservation des détails, fichier plus gros."
                ExportQualityProfile.CUSTOM -> "Débit Auto comme avant, ou choisissez manuellement ci-dessous."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (settings.profile == ExportQualityProfile.CUSTOM) {
            Text("Débit vidéo personnalisé", fontWeight = FontWeight.Bold)
            EconomyChips(
                items = ExportBitrate.entries,
                selected = settings.bitrate,
                label = { it.label },
            ) { bitrate -> onChange(settings.copy(bitrate = bitrate)) }
        }
        Text("Son", fontWeight = FontWeight.Bold)
        EconomyChips(
            items = ExportAudioMode.entries,
            selected = settings.audioMode,
            label = { it.label },
        ) { mode -> onChange(settings.copy(audioMode = mode)) }
        if (settings.audioMode == ExportAudioMode.KEEP) {
            Text("Débit AAC", fontWeight = FontWeight.Bold)
            EconomyChips(
                items = ExportAacBitrate.entries,
                selected = settings.audioBitrate,
                label = { it.label },
            ) { rate -> onChange(settings.copy(audioBitrate = rate)) }
            Text("Canaux audio", fontWeight = FontWeight.Bold)
            EconomyChips(
                items = ExportAudioChannels.entries,
                selected = settings.audioChannels,
                label = { it.label },
            ) { channels -> onChange(settings.copy(audioChannels = channels)) }
            Text(
                "Par défaut : canaux d’origine. Mono uniquement si vous le choisissez.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Le MP4 sera exporté sans piste audio.", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Taille prévue : ${settings.estimatedSizeLabel(durationMs)}",
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Estimation basée sur durée, débit visé et audio supposé. " +
                "Le débit réellement obtenu dépend de l’encodeur et du contenu. " +
                "Aucun réglage ne change la vitesse ni la durée.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun <T> EconomyChips(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
            )
        }
    }
}
