package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineSnapPolicyTest {
    @Test fun snapsRightEdgeToNeighborStart() {
        assertEquals(2000L, TimelineSnapPolicy.snapStart(2070L, 1000L, listOf(3000L), null))
    }
    @Test fun snapsLeftEdgeToPlayhead() {
        assertEquals(5000L, TimelineSnapPolicy.snapStart(4940L, 1000L, emptyList(), 5000L))
    }
    @Test fun doesNotSnapOutsideTolerance() {
        assertEquals(1700L, TimelineSnapPolicy.snapStart(1700L, 1000L, listOf(3000L), null))
    }
    @Test fun neverMovesBeforeTimelineOrigin() {
        assertEquals(0L, TimelineSnapPolicy.snapStart(-50L, 2000L, emptyList(), null))
    }
}
