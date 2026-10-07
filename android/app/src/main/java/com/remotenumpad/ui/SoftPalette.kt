package com.remotenumpad.ui

import android.graphics.Color

data class SoftPalette(
    val surface: Int, val text: Int, val muted: Int, val accent: Int,
    val highlight: Int, val shadow: Int, val rim: Int,
    val ok: Int, val warn: Int, val error: Int
) {
    companion object {
        fun forDark(dark: Boolean): SoftPalette {
            val hex = if (dark) listOf(
                "#263142", "#F1F3F7", "#BAC2CF", "#EFB348", "#354257", "#1B2330", "#2C384A",
                "#9BD4BF", "#EFBD74", "#FFACB8"
            ) else listOf(
                "#F2F3F7", "#242A36", "#626977", "#99600F", "#FFFFFF", "#D2D5DF", "#E5E7EE",
                "#28735E", "#925918", "#B83244"
            )
            val c = hex.map(Color::parseColor)
            return SoftPalette(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9])
        }
    }
}
