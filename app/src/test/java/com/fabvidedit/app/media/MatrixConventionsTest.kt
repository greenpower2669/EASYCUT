package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixConventionsTest {
    @Test
    fun modelRotation_isNegatedForMedia3() {
        assertEquals(-90f, MatrixConventions.modelRotationToMedia3Degrees(90f), 0.001f)
        assertEquals(45f, MatrixConventions.modelRotationToMedia3Degrees(-45f), 0.001f)
        assertEquals(0f, MatrixConventions.modelRotationToMedia3Degrees(0f), 0.001f)
    }
}
