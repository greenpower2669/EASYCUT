package com.fabvidedit.app.media

import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedImportPolicyTest {
    @Test fun singleVideoWithAudioIsAccepted() {
        UnifiedImportPolicy.requireSupported(videoCount = 1, audioCount = 1)
        UnifiedImportPolicy.requireSupported(videoCount = 1, audioCount = 0)
    }

    @Test fun multipleVideoStreamsRequireHistoricalSplit() {
        val error = runCatching {
            UnifiedImportPolicy.requireSupported(videoCount = 2, audioCount = 1)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("split"))
    }

    @Test fun multipleAudioStreamsRequireHistoricalSplit() {
        val error = runCatching {
            UnifiedImportPolicy.requireSupported(videoCount = 1, audioCount = 2)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("split"))
    }
}
