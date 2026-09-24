package com.fabvidedit.app.media

import org.junit.Assert.*
import org.junit.Test

class ExportEconomyTest {
    @Test fun newSmallSizesCoexistWithExistingResolutions() {
        assertEquals(
            listOf(null, 240, 360, 480, 540, 720, 1080, 1440, 2160),
            ExportResolution.entries.map { it.shortSide },
        )
    }

    @Test fun presetsAreOrderedAndDoNotOverrideManualBitrate() {
        val small = ExportSettings(resolution = ExportResolution.P480, profile = ExportQualityProfile.BALANCED)
        val minimal = small.copy(profile = ExportQualityProfile.MINIMAL).effectiveVideoBitrate!!
        val balanced = small.effectiveVideoBitrate!!
        val high = small.copy(profile = ExportQualityProfile.HIGH).effectiveVideoBitrate!!
        assertTrue(minimal < balanced && balanced < high)
        assertEquals(500_000, small.copy(profile = ExportQualityProfile.CUSTOM,
            bitrate = ExportBitrate.KBPS_500).effectiveVideoBitrate)
        assertNull(small.copy(profile = ExportQualityProfile.CUSTOM).effectiveVideoBitrate)
    }

    @Test fun noSoundIsExplicitAndStereoPreservedByDefault() {
        val s = ExportSettings()
        assertEquals(ExportAudioMode.KEEP, s.audioMode)
        assertEquals(ExportAudioChannels.KEEP, s.audioChannels)
        assertFalse(s.needsAudioRemux)
        assertEquals(0, ExportEconomy.audioBitrate(ExportAudioMode.MUTE, ExportAacBitrate.K128))
        assertTrue(s.copy(audioChannels = ExportAudioChannels.MONO).needsAudioRemux)
        assertTrue(s.copy(audioMode = ExportAudioMode.MUTE).needsAudioRemux)
    }

    @Test fun sizeIsOnlyAnEstimateAndUsesAudioMode() {
        val s = ExportSettings(resolution = ExportResolution.P480)
        val a = s.estimatedSizeMb(10_000)!!
        val b = s.copy(audioMode = ExportAudioMode.MUTE).estimatedSizeMb(10_000)!!
        assertTrue(b < a)
        assertTrue(s.estimatedSizeLabel(10_000).contains("estimation"))
        assertNull(s.copy(profile = ExportQualityProfile.CUSTOM).effectiveVideoBitrate)
        assertNotNull(s.copy(profile = ExportQualityProfile.CUSTOM).estimatedSizeMb(10_000))
    }
}
