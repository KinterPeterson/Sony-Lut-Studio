package com.sonylut.studio

import android.graphics.Bitmap
import kotlin.math.floor

/**
 * Applies a 3D .cube LUT with tetrahedral interpolation.
 * LUT storage follows the common .cube order: red changes fastest.
 *
 * [intensity] is expressed as a 0..2 multiplier: 1f = 100% full LUT,
 * 2f = 200% pushed/extrapolated look. Values above 1 extrapolate away
 * from the original pixel toward (and beyond) the LUT result; final
 * channels are clamped back to the valid 0..1 range.
 */
object TetrahedralLutProcessor {
    const val MAX_INTENSITY = 2f

    fun apply(source: Bitmap, lut: CubeLutInfo, intensity: Float = 1f): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val amount = intensity.coerceIn(0f, MAX_INTENSITY)
        if (amount <= 0f) return source.copy(Bitmap.Config.ARGB_8888, false)

        val n = lut.size
        val maxIndex = n - 1
        val output = lut.rgbValues
        val dMin = lut.domainMin
        val dMax = lut.domainMax

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color ushr 16) and 0xFF) / 255f
            val g = ((color ushr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f

            val mappedR = ((r - dMin[0]) / (dMax[0] - dMin[0])).coerceIn(0f, 1f) * maxIndex
            val mappedG = ((g - dMin[1]) / (dMax[1] - dMin[1])).coerceIn(0f, 1f) * maxIndex
            val mappedB = ((b - dMin[2]) / (dMax[2] - dMin[2])).coerceIn(0f, 1f) * maxIndex

            val out = interpolate(mappedR, mappedG, mappedB, n, output)
            val rr = mix(r, out[0], amount)
            val gg = mix(g, out[1], amount)
            val bb = mix(b, out[2], amount)
            val a = (color ushr 24) and 0xFF
            val ri = (rr * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (gg * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (bb * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun mix(original: Float, transformed: Float, amount: Float): Float =
        (original + (transformed - original) * amount).coerceIn(0f, 1f)

    private fun interpolate(r: Float, g: Float, b: Float, n: Int, data: FloatArray): FloatArray {
        val r0 = floor(r).toInt().coerceIn(0, n - 2)
        val g0 = floor(g).toInt().coerceIn(0, n - 2)
        val b0 = floor(b).toInt().coerceIn(0, n - 2)
        val fr = (r - r0).coerceIn(0f, 1f)
        val fg = (g - g0).coerceIn(0f, 1f)
        val fb = (b - b0).coerceIn(0f, 1f)

        fun sample(ri: Int, gi: Int, bi: Int): FloatArray {
            val index = ((bi * n + gi) * n + ri) * 3
            return floatArrayOf(data[index], data[index + 1], data[index + 2])
        }
        fun blend(c0: FloatArray, c1: FloatArray, w1: Float,
                  c2: FloatArray, w2: Float, c3: FloatArray, w3: Float): FloatArray {
            return FloatArray(3) { channel ->
                c0[channel] +
                    w1 * (c1[channel] - c0[channel]) +
                    w2 * (c2[channel] - c1[channel]) +
                    w3 * (c3[channel] - c2[channel])
            }
        }

        val c000 = sample(r0, g0, b0)
        val c111 = sample(r0 + 1, g0 + 1, b0 + 1)

        return when {
            fr >= fg && fg >= fb -> {
                val c100 = sample(r0 + 1, g0, b0)
                val c110 = sample(r0 + 1, g0 + 1, b0)
                blend(c000, c100, fr, c110, fg, c111, fb)
            }
            fr >= fb && fb > fg -> {
                val c100 = sample(r0 + 1, g0, b0)
                val c101 = sample(r0 + 1, g0, b0 + 1)
                blend(c000, c100, fr, c101, fb, c111, fg)
            }
            fb > fr && fr >= fg -> {
                val c001 = sample(r0, g0, b0 + 1)
                val c101 = sample(r0 + 1, g0, b0 + 1)
                blend(c000, c001, fb, c101, fr, c111, fg)
            }
            fg > fr && fr >= fb -> {
                val c010 = sample(r0, g0 + 1, b0)
                val c110 = sample(r0 + 1, g0 + 1, b0)
                blend(c000, c010, fg, c110, fr, c111, fb)
            }
            fg >= fb && fb > fr -> {
                val c010 = sample(r0, g0 + 1, b0)
                val c011 = sample(r0, g0 + 1, b0 + 1)
                blend(c000, c010, fg, c011, fb, c111, fr)
            }
            else -> {
                val c001 = sample(r0, g0, b0 + 1)
                val c011 = sample(r0, g0 + 1, b0 + 1)
                blend(c000, c001, fb, c011, fg, c111, fr)
            }
        }
    }
}
