package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DiyanetYearPageParserTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.bufferedReader(Charsets.UTF_8).readText()

    private fun assertPageMatchesBundledTable(pageResource: String, tableResource: String) {
        val expected = javaClass.getResourceAsStream("/$tableResource")!!
            .bufferedReader(Charsets.UTF_8).useLines { parseOfficialTimes(it) }
        assertEquals(expected, DiyanetYearPageParser.parse(resource(pageResource)))
    }

    @Test
    fun `parst die Nuernberg-Jahresseite identisch zur gebuendelten Tabelle`() {
        assertPageMatchesBundledTable("diyanet-11024.html", "t507-2026.tsv")
        // Stichprobe zur Lesbarkeit: 29.07.2026 amtlich verifiziert.
        val jul29 = DiyanetYearPageParser.parse(resource("diyanet-11024.html"))
            .getValue(LocalDate.of(2026, 7, 29))
        assertEquals(LocalTime.of(3, 52), jul29.fajr)
        assertEquals(LocalTime.of(22, 39), jul29.isha)
    }

    @Test
    fun `parst die Espelkamp-Jahresseite identisch zur gebuendelten Tabelle`() {
        assertPageMatchesBundledTable("diyanet-9976.html", "t000-2026.tsv")
    }

    @Test
    fun `wirft bei HTML ohne Jahres-Tabelle`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiyanetYearPageParser.parse("<html><body>Bakim calismasi</body></html>")
        }
    }

    @Test
    fun `wirft bei abgeschnittener Tabelle`() {
        val html = resource("diyanet-11024.html")
        val broken = html.substring(0, html.indexOf("id=\"tab-2\"") + 5000)
        assertThrows(IllegalArgumentException::class.java) {
            DiyanetYearPageParser.parse(broken)
        }
    }
}
