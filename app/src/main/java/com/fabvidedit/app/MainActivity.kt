package com.fabvidedit.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.fabvidedit.app.data.ProjectV06Migration
import com.fabvidedit.app.media.IncomingMediaResolver
import com.fabvidedit.app.ui.FabVidEditApp
import com.fabvidedit.app.ui.theme.FabVidEditTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: FabVidEditViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FabVidDiagnostics.install(applicationContext)
        FabVidDiagnostics.recordPreviousExit(applicationContext)
        // Automatic orphan deletion raced against an active, not-yet-saved import.
        // Preserve project data until cleanup can be transactionally guarded.
        ProjectV06Migration.migrate(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            FabVidEditTheme {
                FabVidEditApp(viewModel)
            }
        }
        if (savedInstanceState == null) handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (!IncomingMediaResolver.supports(intent)) return
        val incomingIntent = intent ?: return

        lifecycleScope.launch {
            val resolution = IncomingMediaResolver.resolve(this@MainActivity, incomingIntent)
            if (resolution.failures.isNotEmpty()) {
                val message = if (resolution.uris.isEmpty()) {
                    resolution.failures.first()
                } else {
                    "${resolution.failures.size} média(s) n’ont pas pu être importés"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
            if (resolution.uris.isEmpty()) return@launch

            val active = viewModel.activeProject.value
            Log.i(
                TAG,
                "Dispatch ${resolution.uris.size} incoming media URI(s) to the normal import pipeline; activeProject=${active?.id ?: "none"}",
            )
            if (active == null) {
                viewModel.createProject(resolution.uris)
            } else {
                viewModel.addClips(resolution.uris)
            }
        }
    }

    private companion object {
        const val TAG = "FabVidIncoming"
    }
}
