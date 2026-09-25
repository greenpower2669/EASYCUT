package com.fabvidedit.app.media

/**
 * The unsplit pilot deliberately targets an ordinary container (one video and at most one audio
 * stream). More complex inventories keep the established physical split path on user request.
 */
internal object UnifiedImportPolicy {
    fun requireSupported(videoCount: Int, audioCount: Int) {
        require(videoCount == 1) {
            "Import unifié : ce média contient $videoCount pistes vidéo. Décoche « Désactiver le split » pour utiliser l’import historique."
        }
        require(audioCount in 0..1) {
            "Import unifié : ce média contient $audioCount pistes audio. Décoche « Désactiver le split » pour utiliser l’import historique."
        }
    }
}
