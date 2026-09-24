package com.fabvidedit.app.model

import org.junit.Assert.*
import org.junit.Test

class AspectRatioInputTest {
    @Test fun parsesRatiosAndFrenchDecimals() {
        assertEquals(16f / 15f, AspectRatioInput.parse("16:15")!!, 0.00001f)
        assertEquals(4f / 3f, AspectRatioInput.parse("4/3")!!, 0.00001f)
        assertEquals(1.0667f, AspectRatioInput.parse("1,0667")!!, 0.0001f)
        assertNull(AspectRatioInput.parse("0/0"))
        assertNull(AspectRatioInput.parse("7:1"))
        assertNull(AspectRatioInput.parse("sans données"))
    }
}
