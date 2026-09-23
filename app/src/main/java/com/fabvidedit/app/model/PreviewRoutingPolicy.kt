package com.fabvidedit.app.model

/**
 * Route a genuinely single, unfiltered VIDEO through the same ExoPlayer
 * from project open to the first gesture: no CompositionPlayer surface handoff.
 * Keep composition for images, text, colour processing and multiple visual clips.
 */
internal object PreviewRoutingPolicy {
    fun singleVideoEligible(project: VideoProject): Boolean {
        val clip = project.clips.singleOrNull() ?: return false
        return clip.mediaKind == VisualMediaKind.VIDEO &&
            clip.filter == ClipFilter.NONE &&
            clip.brightness == 1f &&
            clip.keyframes.all { it.brightness == 1f } &&
            project.textLayers.isEmpty() &&
            project.transitions.isEmpty()
    }

    fun initialSimple(project: VideoProject, unequalVideoSpans: Boolean): Boolean =
        singleVideoEligible(project) || unequalVideoSpans

    /**
     * Do not undo the user's already-selected compatible fallback on unrelated
     * recompositions. Only revert automatic single mode when its eligibility is
     * lost (e.g. add a second video or a text layer).
     */
    fun afterRoutingChange(
        currentSimple: Boolean,
        wasSingleEligible: Boolean,
        isSingleEligible: Boolean,
        unequalVideoSpans: Boolean,
    ): Boolean = when {
        isSingleEligible || unequalVideoSpans -> true
        wasSingleEligible -> false
        else -> currentSimple
    }
}
