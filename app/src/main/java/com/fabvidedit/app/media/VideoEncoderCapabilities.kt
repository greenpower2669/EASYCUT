package com.fabvidedit.app.media

import android.media.MediaCodecList
import android.media.MediaFormat

/** Availability check for a real HEVC hardware encoder; supported codec does not
 * guarantee every resolution/frame-rate combination will be accepted by the device.
 */
object VideoEncoderCapabilities {
    fun hasHardwareHevcEncoder(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            codec.isEncoder && codec.isHardwareAccelerated &&
                codec.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
        }
    }.getOrDefault(false)
}
