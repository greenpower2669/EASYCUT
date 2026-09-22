package com.fabvidedit.app.media

import org.junit.Assert.assertArrayEquals
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
}
