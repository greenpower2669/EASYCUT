package com.fabvidedit.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.fabvidedit.app.model.MediaAssetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaInspector {
    suspend fun inspect(context: Context, uri: Uri): MediaAssetInfo =
        MediaImportPipeline.inspect(context, uri)

    suspend fun thumbnail(
        context: Context,
        uri: String,
        timeMs: Long,
        width: Int = 320,
        height: Int = 180,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                retriever.getScaledFrameAtTime(
                    timeMs.coerceAtLeast(0) * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height,
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}
