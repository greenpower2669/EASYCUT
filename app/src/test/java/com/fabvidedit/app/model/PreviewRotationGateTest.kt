package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRotationGateTest {
    @Test fun smallTwistsAreIgnoredEvenWhenPinching() {
        val gate = PreviewRotationGate(10f)
        assertEquals(0f, gate.onRotationDelta(3f, true), 0.001f)
        assertEquals(0f, gate.onRotationDelta(-2f, true), 0.001f)
        assertEquals(0f, gate.onRotationDelta(4f, true), 0.001f)
        assertFalse(gate.isUnlocked)
    }

    @Test fun thresholdOpensWithoutReplayingInitialRotation() {
        val gate = PreviewRotationGate(10f)
        assertEquals(0f, gate.onRotationDelta(6f, true), 0.001f)
        assertEquals(0f, gate.onRotationDelta(5f, true), 0.001f)
        assertTrue(gate.isUnlocked)
        assertEquals(3f, gate.onRotationDelta(3f, true), 0.001f)
        assertEquals(-2f, gate.onRotationDelta(-2f, true), 0.001f)
    }

    @Test fun signedNetAngleRejectsAlternatingJitter() {
        val gate = PreviewRotationGate(10f)
        repeat(20) {
            assertEquals(0f, gate.onRotationDelta(4f, true), 0.001f)
            assertEquals(0f, gate.onRotationDelta(-4f, true), 0.001f)
        }
        assertFalse(gate.isUnlocked)
    }

    @Test fun losingOrReplacingTwoFingerPairRequiresFreshIntent() {
        val gate = PreviewRotationGate(10f)
        gate.onRotationDelta(11f, true)
        assertTrue(gate.isUnlocked)
        assertEquals(0f, gate.onRotationDelta(25f, false), 0.001f)
        assertFalse(gate.isUnlocked)
        assertEquals(0f, gate.onRotationDelta(3f, true), 0.001f)
        assertEquals(0f, gate.onRotationDelta(7f, true), 0.001f)
        assertTrue(gate.isUnlocked)
    }

    @Test fun negativeTwistCanUnlockToo() {
        val gate = PreviewRotationGate(10f)
        assertEquals(0f, gate.onRotationDelta(-8f, true), 0.001f)
        assertEquals(0f, gate.onRotationDelta(-3f, true), 0.001f)
        assertTrue(gate.isUnlocked)
        assertEquals(-5f, gate.onRotationDelta(-5f, true), 0.001f)
    }
}
