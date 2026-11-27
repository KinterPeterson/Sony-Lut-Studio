package com.sonylut.studio

import android.graphics.Bitmap
import kotlin.math.floor

/**
 * 3D .cube LUT processing with tetrahedral interpolation.
 * Processes one scanline at a time to avoid allocating a full-image pixel array.
 */
object TetrahedralLutProcessor {

    fun apply(
        source: Bitmap,
        lut: CubeLutInfo,
        intensity: Float = 1f,
        onRowProcessed: ((Int, Int) -> Unit)? = null
    ): Bitmap {
        val width = source.width
        val height = source.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        val mapped = FloatArray(3)
        val amount = intensity.coerceIn(0f, 2f)
        val n = lut.size
        val maxIndex = n - 1
        val data = lut.rgbValues
        val dMin = lut.domainMin
        val dMax = lut.domainMax

        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val color = row[x]
                val r = ((color ushr 16) and 0xFF) / 255f
                val g = ((color ushr 8) and 0xFF) / 255f
                val b = (color and 0xFF) / 255f

                val mr = ((r - dMin[0]) / (dMax[0] - dMin[0])).coerceIn(0f, 1f) * maxIndex
                val mg = ((g - dMin[1]) / (dMax[1] - dMin[1])).coerceIn(0f, 1f) * maxIndex
                val mb = ((b - dMin[2]) / (dMax[2] - dMin[2])).coerceIn(0f, 1f) * maxIndex
                interpolate(mr, mg, mb, n, data, mapped)

                val rr = (r + (mapped[0] - r) * amount).coerceIn(0f, 1f)
                val gg = (g + (mapped[1] - g) * amount).coerceIn(0f, 1f)
                val bb = (b + (mapped[2] - b) * amount).coerceIn(0f, 1f)
                val a = (color ushr 24) and 0xFF
                row[x] = (a shl 24) or
                    (((rr * 255f + 0.5f).toInt().coerceIn(0, 255)) shl 16) or
                    (((gg * 255f + 0.5f).toInt().coerceIn(0, 255)) shl 8) or
                    ((bb * 255f + 0.5f).toInt().coerceIn(0, 255))
            }
            outputBitmap.setPixels(row, 0, width, 0, y, width, 1)
            onRowProcessed?.invoke(y + 1, height)
        }
        return outputBitmap
    }

    private fun interpolate(
        r: Float, g: Float, b: Float, n: Int, data: FloatArray, out: FloatArray
    ) {
        val r0 = floor(r).toInt().coerceIn(0, n - 2)
        val g0 = floor(g).toInt().coerceIn(0, n - 2)
        val b0 = floor(b).toInt().coerceIn(0, n - 2)
        val fr = (r - r0).coerceIn(0f, 1f)
        val fg = (g - g0).coerceIn(0f, 1f)
        val fb = (b - b0).coerceIn(0f, 1f)

        fun index(ri: Int, gi: Int, bi: Int) = ((bi * n + gi) * n + ri) * 3
        val i000 = index(r0, g0, b0)
        val i111 = index(r0 + 1, g0 + 1, b0 + 1)

        // Tetrahedral interpolation expressed as weighted edge differences.
        var i1: Int
        var i2: Int
        var w1: Float
        var w2: Float
        var w3: Float
        when {
            fr >= fg && fg >= fb -> {
                i1 = index(r0 + 1, g0, b0); i2 = index(r0 + 1, g0 + 1, b0)
                w1 = fr; w2 = fg; w3 = fb
            }
            fr >= fb && fb > fg -> {
                i1 = index(r0 + 1, g0, b0); i2 = index(r0 + 1, g0, b0 + 1)
                w1 = fr; w2 = fb; w3 = fg
            }
            fb > fr && fr >= fg -> {
                i1 = index(r0, g0, b0 + 1); i2 = index(r0 + 1, g0, b0 + 1)
                w1 = fb; w2 = fr; w3 = fg
            }
            fg > fr && fr >= fb -> {
                i1 = index(r0, g0 + 1, b0); i2 = index(r0 + 1, g0 + 1, b0)
                w1 = fg; w2 = fr; w3 = fb
            }
            fg >= fb && fb > fr -> {
                i1 = index(r0, g0 + 1, b0); i2 = index(r0, g0 + 1, b0 + 1)
                w1 = fg; w2 = fb; w3 = fr
            }
            else -> {
                i1 = index(r0, g0, b0 + 1); i2 = index(r0, g0 + 1, b0 + 1)
                w1 = fb; w2 = fg; w3 = fr
            }
        }
        for (c in 0..2) {
            val c0 = data[i000 + c]
            val c1 = data[i1 + c]
            val c2 = data[i2 + c]
            val c3 = data[i111 + c]
            out[c] = c0 + w1 * (c1 - c0) + w2 * (c2 - c1) + w3 * (c3 - c2)
        }
    }
}
