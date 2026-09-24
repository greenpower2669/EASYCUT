package com.fabvidedit.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FfprobeInventoryCommandTest {
    @Test fun explicitOutputFileRemainsSeparateFromInputAndIsNotBlank() {
        val expected = arrayOf("-v", "error", "-print_format", "json",
            "-show_format", "-show_streams", "-o",
            "/data/user/0/app/cache/ffprobe_metadata/inventory-1.json",
            "/storage/media/odd name (1).mp4")
        assertArrayEquals(expected, FfprobeInventoryCommand.arguments(
            "/storage/media/odd name (1).mp4",
            "/data/user/0/app/cache/ffprobe_metadata/inventory-1.json"))
    }
    @Test fun extendedInventoryOnlyInspectsStreamsNotFrames() {
        val args = FfprobeInventoryCommand.expandedStdoutArguments("/source/old cam.mkv")
        org.junit.Assert.assertTrue(args.contains("-probesize"))
        org.junit.Assert.assertTrue(args.contains("-analyzeduration"))
        org.junit.Assert.assertTrue(args.contains("-show_streams"))
        org.junit.Assert.assertFalse(args.contains("-show_frames"))
        org.junit.Assert.assertFalse(args.contains("-o"))
        assertEquals("/source/old cam.mkv", args.last())
    }

    @Test fun boundedStdoutFallbackHasNoOutputFileArgument() {
        assertArrayEquals(arrayOf("-v", "error", "-print_format", "json",
            "-show_format", "-show_streams", "/storage/source.mp4"),
            FfprobeInventoryCommand.stdoutArguments("/storage/source.mp4"))
    }
}
