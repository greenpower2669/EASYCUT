package com.fabvidedit.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTouchMathTest {
    @Test fun alignsStartAndEnd() {
        assertEquals(2000L, TimelineTouchMath.snapStart(2010L, 1000L, listOf(2000L), 20L))
        assertEquals(3000L, TimelineTouchMath.snapStart(3010L, 1000L, listOf(4000L), 20L))
        assertEquals(2300L, TimelineTouchMath.snapStart(2300L, 1000L, listOf(2000L), 20L))
    }
    @Test fun toleranceTracksPixels() {
        assertEquals(240L, TimelineTouchMath.toleranceMs(500f, 10000L, 12f))
        assertEquals(120L, TimelineTouchMath.toleranceMs(1000f, 10000L, 12f))
    }
}
