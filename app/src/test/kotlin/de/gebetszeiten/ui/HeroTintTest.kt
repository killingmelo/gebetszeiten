package de.gebetszeiten.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Restzeit-Farbe auf „Heute“: passt zum Bild und hält immer 4,5:1. */
class HeroTintTest {

    private val nacht = 0xFF000000.toInt()
    private val tag = 0xFFF7F1E8.toInt()

    private fun bild(vararg farben: Int) = IntArray(400) { farben[it % farben.size] }

    // Sand (Ẓuhr), Abendrot (Maġrib), Nachtblau (ʿIšāʾ), grau, fast weiß.
    private val bilder = listOf(
        bild(0xFFC89A5E.toInt(), 0xFF8A6235.toInt(), 0xFFE8CFA0.toInt()),
        bild(0xFFD0562C.toInt(), 0xFF6B2A1A.toInt()),
        bild(0xFF1B2A4A.toInt(), 0xFF33507A.toInt()),
        bild(0xFF808080.toInt()),
        bild(0xFFFAFAF5.toInt()),
    )

    @Test fun `nachts und tags immer mindestens 4,5 zu 1 auf dem Verlauf`() {
        bilder.forEach { px ->
            listOf(nacht to true, tag to false).forEach { (grund, dunkel) ->
                val hinter = blend(grund, averageColor(px), 0.86)
                val farbe = heroTint(px, dunkel, hinter)
                assertTrue(
                    "Kontrast %.2f".format(contrast(farbe, hinter)),
                    contrast(farbe, hinter) >= 4.5,
                )
            }
        }
    }

    @Test fun `der Farbton kommt aus dem Bild`() {
        val sand = bilder[0]
        val f = heroTint(sand, dark = true, background = nacht)
        val r = f shr 16 and 0xFF
        val b = f and 0xFF
        assertTrue("Sand soll warm bleiben: r=$r b=$b", r > b)

        val blau = bilder[2]
        val g = heroTint(blau, dark = true, background = nacht)
        assertTrue("Nachtblau soll kühl bleiben", (g and 0xFF) > (g shr 16 and 0xFF))
    }

    @Test fun `ein graues Bild ergibt eine neutrale Farbe`() {
        val f = heroTint(bilder[3], dark = true, background = nacht)
        val r = f shr 16 and 0xFF
        val g = f shr 8 and 0xFF
        val b = f and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
    }

    @Test fun `Kontrast nach WCAG`() {
        assertEquals(21.0, contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, contrast(tag, tag), 0.0001)
    }
}
