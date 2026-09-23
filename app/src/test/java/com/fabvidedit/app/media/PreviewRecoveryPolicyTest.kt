package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRecoveryPolicyTest {
    @Test fun normalVideoDoesNotStartUnnecessaryFrameRecovery() {
        assertFalse(PreviewRecoveryPolicy.shouldRecover(snapshotAvailable = true))
        assertTrue(PreviewRecoveryPolicy.shouldRecover(snapshotAvailable = false))
    }

    @Test fun lowRamAndNormalDevicesHaveBoundedProgressiveFrameSizes() {
        assertEquals(480, PreviewRecoveryPolicy.captureEdge(true))
        assertEquals(720, PreviewRecoveryPolicy.captureEdge(false))
        assertEquals(listOf(360, 480), PreviewRecoveryPolicy.recoveryEdges(true))
        assertEquals(listOf(480, 720), PreviewRecoveryPolicy.recoveryEdges(false))
    }

    @Test fun simpleModeRecoveryOnlyWhenNoFrameAndNoExistingBridge() {
        assertTrue(PreviewRecoveryPolicy.shouldRecoverInSimpleMode(false, false))
        assertFalse(PreviewRecoveryPolicy.shouldRecoverInSimpleMode(true, false))
        assertFalse(PreviewRecoveryPolicy.shouldRecoverInSimpleMode(false, true))
    }
}
