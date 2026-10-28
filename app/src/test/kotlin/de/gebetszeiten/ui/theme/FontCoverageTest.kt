package de.gebetszeiten.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * Alle gebuendelten Schriften muessen die DMG-Zeichen darstellen koennen.
 *
 * `java.awt.Font` steht der Unit-Test-Klassenbahn (android.jar) nicht zur
 * Verfuegung, deshalb wird die cmap-Tabelle der TTF-Datei direkt gelesen
 * (Formate 4 und 12, die alle Unicode-Zeichen der Schriften abdecken).
 */
class FontCoverageTest {
    private val dmg = "ʿʾǦǧḌḍḤḥṢṣṬṭẒẓṮṯḪḫḎḏĠġŠšĀāĪīŪū"
    private val arabic = "وقت العصر إذا صار ظل كل شيء مثله"

    private fun codePoints(name: String): (Int) -> Boolean {
        val file = File("src/main/res/font/$name.ttf")
        require(file.exists()) { "Schrift fehlt: ${file.path}" }
        val b = ByteBuffer.wrap(file.readBytes())
        val numTables = b.getShort(4).toInt() and 0xFFFF
        var cmapOffset = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(ByteArray(4) { b.get(rec + it) }, Charsets.US_ASCII)
            if (tag == "cmap") cmapOffset = b.getInt(rec + 8)
        }
        require(cmapOffset >= 0) { "$name: keine cmap-Tabelle" }
        val subtables = b.getShort(cmapOffset + 2).toInt() and 0xFFFF
        val ranges = mutableListOf<IntRange>()
        for (i in 0 until subtables) {
            val rec = cmapOffset + 4 + i * 8
            val platform = b.getShort(rec).toInt() and 0xFFFF
            val encoding = b.getShort(rec + 2).toInt() and 0xFFFF
            val st = cmapOffset + b.getInt(rec + 4)
            if (!((platform == 0) || (platform == 3 && (encoding == 1 || encoding == 10)))) continue
            when (b.getShort(st).toInt() and 0xFFFF) {
                4 -> {
                    val segX2 = b.getShort(st + 6).toInt() and 0xFFFF
                    val seg = segX2 / 2
                    val endBase = st + 14
                    val startBase = endBase + segX2 + 2
                    val deltaBase = startBase + segX2
                    val rangeBase = deltaBase + segX2
                    for (s in 0 until seg) {
                        val end = b.getShort(endBase + s * 2).toInt() and 0xFFFF
                        val start = b.getShort(startBase + s * 2).toInt() and 0xFFFF
                        val delta = b.getShort(deltaBase + s * 2).toInt()
                        val ro = b.getShort(rangeBase + s * 2).toInt() and 0xFFFF
                        for (c in start..end) {
                            if (c == 0xFFFF) continue
                            val glyph = if (ro == 0) (c + delta) and 0xFFFF else {
                                val g = b.getShort(rangeBase + s * 2 + ro + (c - start) * 2).toInt() and 0xFFFF
                                if (g == 0) 0 else (g + delta) and 0xFFFF
                            }
                            if (glyph != 0) ranges += c..c
                        }
                    }
                }
                12 -> {
                    val groups = b.getInt(st + 12)
                    for (g in 0 until groups) {
                        val o = st + 16 + g * 12
                        ranges += b.getInt(o)..b.getInt(o + 4)
                    }
                }
            }
        }
        return { cp -> ranges.any { cp in it } }
    }

    @Test fun latinFontsCoverEveryDmgGlyph() {
        for (n in listOf("serif_light", "serif_regular", "sans_regular", "sans_medium", "sans_semibold")) {
            val has = codePoints(n)
            val missing = dmg.filter { !has(it.code) }
            assertEquals("$n fehlt: $missing", "", missing)
        }
    }

    @Test fun naskhCoversArabicSample() {
        val has = codePoints("naskh_regular")
        assertEquals("", arabic.filter { it != ' ' && !has(it.code) })
    }
}
