package com.sonylut.studio

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

data class CubeLutInfo(
    val title: String,
    val size: Int,
    val domainMin: List<Float>,
    val domainMax: List<Float>,
    val dataCount: Int,
    // .cube RGB triplets, with red varying fastest, then green, then blue.
    val rgbValues: FloatArray
)

class CubeLutParseException(message: String) : Exception(message)

object CubeLutParser {
    fun parse(input: InputStream): CubeLutInfo {
        var title = "Untitled LUT"
        var size: Int? = null
        var min = listOf(0f, 0f, 0f)
        var max = listOf(1f, 1f, 1f)
        var hasMin = false
        var hasMax = false
        var count = 0
        val values = ArrayList<Float>()
        var lineNo = 0

        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { raw ->
                lineNo++
                val line = raw.removePrefix("\uFEFF").substringBefore("#").trim()
                if (line.isEmpty()) return@forEach
                val p = line.split(Regex("\\s+"))
                when (p[0].uppercase(Locale.US)) {
                    "TITLE" -> title = line.substringAfter("TITLE").trim()
                        .removeSurrounding("\"").ifBlank { "Untitled LUT" }
                    "LUT_1D_SIZE" -> throw CubeLutParseException("第 $lineNo 行：本版本暂不支持一维 LUT。")
                    "LUT_3D_SIZE" -> {
                        if (size != null || p.size != 2) throw CubeLutParseException("第 $lineNo 行：LUT_3D_SIZE 格式错误或重复。")
                        val n = p[1].toIntOrNull() ?: throw CubeLutParseException("第 $lineNo 行：LUT 尺寸不是整数。")
                        if (n !in 2..64) throw CubeLutParseException("LUT 尺寸 $n 超出支持范围（2–64）。")
                        size = n
                    }
                    "DOMAIN_MIN" -> { min = triple(p, lineNo); hasMin = true }
                    "DOMAIN_MAX" -> { max = triple(p, lineNo); hasMax = true }
                    else -> {
                        if (p[0].toFloatOrNull() != null) {
                            if (p.size != 3) throw CubeLutParseException("第 $lineNo 行：RGB 数据必须恰好有 3 个数值。")
                            val rgb = triple(p, lineNo)
                            values.addAll(rgb)
                            count++
                            val n = size
                            if (n != null && count > n * n * n) throw CubeLutParseException("颜色数据行数超过 LUT 声明数量。")
                        }
                    }
                }
            }
        }
        val n = size ?: throw CubeLutParseException("缺少 LUT_3D_SIZE 声明。")
        if (hasMin != hasMax) throw CubeLutParseException("DOMAIN_MIN 和 DOMAIN_MAX 必须同时出现或同时省略。")
        if ((0..2).any { min[it] >= max[it] }) throw CubeLutParseException("DOMAIN_MIN 必须小于对应的 DOMAIN_MAX。")
        val expected = n * n * n
        if (count != expected) throw CubeLutParseException("颜色数据数量不正确：应为 $expected 行，实际为 $count 行。")
        return CubeLutInfo(title, n, min, max, count, values.toFloatArray())
    }

    private fun triple(parts: List<String>, line: Int): List<Float> {
        if (parts.size != 4 && parts.size != 3) throw CubeLutParseException("第 $line 行：需要 3 个数值。")
        val tokens = if (parts.size == 4) parts.drop(1) else parts
        return try {
            tokens.map { it.toFloat().also { v -> if (!v.isFinite()) throw NumberFormatException() } }
        } catch (_: NumberFormatException) {
            throw CubeLutParseException("第 $line 行：包含无效数值。")
        }
    }
}
