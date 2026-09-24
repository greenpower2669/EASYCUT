package com.fabvidedit.app.model

/** Readable SAR/DAR input: 16:15, 4/3, 1,0667. Only plausible display ratios. */
internal object AspectRatioInput {
    fun parse(text: String): Float? {
        val trimmed = text.trim().replace(',', '.')
        val separator = when {
            ':' in trimmed -> ':'
            '/' in trimmed -> '/'
            else -> null
        }
        val result = if (separator != null) {
            val left = trimmed.substringBefore(separator).trim().toFloatOrNull()
            val right = trimmed.substringAfter(separator).trim().toFloatOrNull()
            if (left == null || right == null || right == 0f) null else left / right
        } else trimmed.toFloatOrNull()
        return result?.takeIf { it.isFinite() && it in 0.2f..5f }
    }
}
