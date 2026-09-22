package com.fabvidedit.app.media

import com.fabvidedit.app.model.PocStreamExportPlan
import com.fabvidedit.app.model.VideoFormatAnalysis
import java.util.concurrent.ConcurrentHashMap

data class RegisteredPocStreamSelection(
    val plan: PocStreamExportPlan,
    val selectedAnalysis: VideoFormatAnalysis?,
)

object PocStreamSelectionRegistry {
    private val selections = ConcurrentHashMap<String, RegisteredPocStreamSelection>()

    fun put(uri: String, selection: RegisteredPocStreamSelection) {
        selections[uri] = selection
    }

    fun get(uri: String): RegisteredPocStreamSelection? = selections[uri]

    fun remove(uri: String) {
        selections.remove(uri)
    }

    fun clear() {
        selections.clear()
    }
}
