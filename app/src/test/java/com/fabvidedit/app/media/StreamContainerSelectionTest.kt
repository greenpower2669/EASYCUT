package com.fabvidedit.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamContainerSelectionTest {
    @Test fun seekFriendlyAvcAndHevcPreferMp4WithTsFallback() {
        listOf("h264", "hevc", "h265").forEach { codec ->
            assertEquals(listOf("mp4", "ts", "mkv"),
                StreamSourceMaterializer.candidateExtensions(codec))
        }
    }

    @Test fun mpeg2RetainsTransportStreamFirst() {
        assertEquals(listOf("ts", "mp4", "mkv"),
            StreamSourceMaterializer.candidateExtensions("mpeg2video"))
    }
}
