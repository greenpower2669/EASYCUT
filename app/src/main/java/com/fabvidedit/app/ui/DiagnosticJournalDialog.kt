package com.fabvidedit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.DiagnosticJournalFormatter
import com.fabvidedit.app.FabVidDiagnostics

/** One shared journal UI, opened without closing the project or its preview player. */
@Composable
internal fun DiagnosticJournalDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(FabVidDiagnostics.getReport(context)) }
    var clearConfirmation by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }

    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text("Vider les anciennes lignes ?") },
            text = {
                Text(
                    "Seul le journal GET ERR sera vidé. Tes projets, vidéos et montages restent " +
                        "intacts. Le dernier arrêt Android et la dernière erreur restent consultables.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (FabVidDiagnostics.clearJournal(context)) {
                        report = FabVidDiagnostics.getReport(context)
                        clearFailed = false
                    } else {
                        clearFailed = true
                    }
                    clearConfirmation = false
                }) { Text("Oui, vider") }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmation = false }) { Text("Annuler") }
            },
        )
    } else {
        val colored = remember(report) {
            buildAnnotatedString {
                DiagnosticJournalFormatter.classify(report).forEachIndexed { index, line ->
                    if (index > 0) append("\n")
                    if (line.text.isNotBlank()) {
                        val tint = when (line.status) {
                            DiagnosticJournalFormatter.Status.ERROR -> Color(0xFFFF8585)
                            DiagnosticJournalFormatter.Status.OK,
                            DiagnosticJournalFormatter.Status.INFO -> Color(0xFF89E6A3)
                            DiagnosticJournalFormatter.Status.RECOVERED -> Color(0xFFB5BECE)
                        }
                        withStyle(SpanStyle(color = tint)) {
                            append("[")
                            append(line.label)
                            append("] ")
                            append(line.text)
                        }
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Journal GET ERR") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ERREUR rouge • OK / INFO vert • incident récupéré gris. " +
                            "Le texte copié contient les balises HTML, pas cet affichage.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Le journal peut contenir des noms de fichiers. Aucun envoi automatique.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (clearFailed) {
                        Text("ERREUR : impossible de vider le journal. Aucune vidéo supprimée.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    SelectionContainer {
                        Text(
                            text = colored,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "EASYCUT GET ERR (HTML lisible)",
                            DiagnosticJournalFormatter.forCopy(report),
                        ),
                    )
                }) { Text("Copier HTML") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clearConfirmation = true }) { Text("Vider") }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(share, "Partager le journal"))
                    }) { Text("Partager") }
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
            },
        )
    }
}
