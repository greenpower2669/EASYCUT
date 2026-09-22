package com.fabvidedit.app.model

/**
 * Two-finger rotation intent gate. During the initial net angular change, pan and
 * zoom still run; tiny accidental twists are ignored. Crossing the threshold
 * establishes a NEW angular baseline, with no stored-angle jump on that event.
 * The gate must reset whenever the stable two-finger pair is lost or changes.
 */
internal class PreviewRotationGate(private val thresholdDegrees: Float = 10f) {
    private var accumulatedDegrees = 0f
    var isUnlocked: Boolean = false
        private set

    init { require(thresholdDegrees > 0f && thresholdDegrees.isFinite()) }

    fun onRotationDelta(deltaDegrees: Float, stableTwoFingers: Boolean): Float {
        if (!stableTwoFingers) {
            reset()
            return 0f
        }
        if (!deltaDegrees.isFinite()) return 0f
        if (isUnlocked) return deltaDegrees

        accumulatedDegrees += deltaDegrees
        if (kotlin.math.abs(accumulatedDegrees) >= thresholdDegrees) {
            isUnlocked = true
            accumulatedDegrees = 0f
            // Deliberately skip the triggering delta: current finger orientation
            // becomes zero so there is no jump when the gate opens.
        }
        return 0f
    }

    fun reset() {
        accumulatedDegrees = 0f
        isUnlocked = false
    }
}
