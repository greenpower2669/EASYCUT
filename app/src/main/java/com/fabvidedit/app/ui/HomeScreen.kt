package com.fabvidedit.app.ui


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.model.VideoProject
import com.fabvidedit.app.ui.theme.FabBackground
import com.fabvidedit.app.ui.theme.FabMint
import com.fabvidedit.app.ui.theme.FabPink
import com.fabvidedit.app.ui.theme.FabPurple
import com.fabvidedit.app.ui.theme.FabSurface
import com.fabvidedit.app.util.formatDate
import com.fabvidedit.app.util.formatDuration

@Composable
fun HomeScreen(viewModel: FabVidEditViewModel) {
    var showDiagnosticJournal by remember { mutableStateOf(false) }
    val projects by viewModel.projects.collectAsStateWithLifecycleCompat()
    val disableSplit by viewModel.disableSplitAtImport.collectAsStateWithLifecycleCompat()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.createProject(it)
    }
    var projectToDelete by remember { mutableStateOf<VideoProject?>(null) }

    Scaffold(
        containerColor = FabBackground,
        topBar = {
            HomeHeader(
                onNewProject = { picker.launch(arrayOf("video/*", "image/*")) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedButton(
                    onClick = { showDiagnosticJournal = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Journal d’erreurs — GET ERR") }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.setDisableSplitAtImport(!disableSplit)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = disableSplit,
                        onCheckedChange = viewModel::setDisableSplitAtImport,
                    )
                    Column {
                        Text("Désactiver le split (test)", fontWeight = FontWeight.Bold)
                        Text(
                            if (disableSplit) "Import vidéo + son dans un seul fichier"
                            else "Import historique : séparation des pistes",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (projects.isEmpty()) {
                item {
                    EmptyProjects(onStart = { picker.launch(arrayOf("video/*", "image/*")) })
                }
            } else {
                item {
                    Text(
                        text = "Mes montages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(projects, key = VideoProject::id) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { viewModel.openProject(project.id) },
                        onDelete = { projectToDelete = project },
                    )
                }
            }
        }
    }

    if (showDiagnosticJournal) {
        DiagnosticJournalDialog(onDismiss = { showDiagnosticJournal = false })
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Supprimer le projet ?") },
            text = { Text("« ${project.name} » sera retiré d’EASYCUT. Les vidéos originales ne seront pas supprimées.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(project.id)
                        projectToDelete = null
                    },
                ) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun HomeHeader(onNewProject: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FabBackground)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(FabPink, FabPurple))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MovieCreation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "EASYCUT",
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Le montage vidéo simple et libre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledIconButton(onClick = onNewProject) {
            Icon(Icons.Rounded.Add, contentDescription = "Nouveau projet")
        }
    }
}

@Composable
private fun EmptyProjects(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(FabPink.copy(alpha = 0.32f), FabPurple.copy(alpha = 0.08f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MovieCreation,
                contentDescription = null,
                tint = FabPink,
                modifier = Modifier.size(76.dp),
            )
        }
        Text(
            text = "Crée ta première vidéo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Assemble, coupe, ajoute du texte et de la musique, puis exporte en MP4.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onStart, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Nouveau projet")
        }
        Spacer(Modifier.height(8.dp))
        FeatureRow(Icons.Rounded.Bolt, "Rapide", "Accélération matérielle", FabPink)
        FeatureRow(Icons.Rounded.AutoAwesome, "Créatif", "Filtres, textes et formats", FabPurple)
        FeatureRow(Icons.Rounded.Security, "Privé", "Tout reste sur ton téléphone", FabMint)
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FabSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: VideoProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = FabSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(FabPurple.copy(alpha = 0.8f), FabPink.copy(alpha = 0.7f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = formatDuration(project.durationMs),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${project.clips.size} clip${if (project.clips.size > 1) "s" else ""} • ${project.aspectRatio.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Modifié ${formatDate(project.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Supprimer ${project.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

