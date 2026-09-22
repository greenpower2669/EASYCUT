package com.fabvidedit.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fabvidedit.app.FabVidEditViewModel
import com.fabvidedit.app.model.TimelineMode
import com.fabvidedit.app.ui.theme.FabBackground

@Composable
fun FabVidEditApp(viewModel: FabVidEditViewModel) {
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycleCompat()
    val busyMessage by viewModel.busyMessage.collectAsStateWithLifecycleCompat()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycleCompat()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = activeProject != null) {
        viewModel.closeProject()
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FabBackground),
    ) {
        val project = activeProject
        when {
            project == null -> HomeScreen(viewModel)
            project.timelineMode == TimelineMode.MULTITRACK -> CapCutMultitrackEditorV06(viewModel, project)
            else -> EditorScreen(viewModel, project)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        busyMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(message, color = Color.White)
                }
            }
        }
    }
}
