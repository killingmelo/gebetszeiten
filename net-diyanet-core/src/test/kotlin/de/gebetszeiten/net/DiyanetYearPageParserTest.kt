package de.gebetszeiten.net

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
    fun `vereinigt Ende Oktober Monats-Tabelle des laufenden mit Jahres-Tabelle des Folgejahres`() {
        val parsed = DiyanetYearPageParser.parse(resource("diyanet-jahreswechsel.html"))
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2),
            ),
            parsed.keys.toList(),
        )
        assertEquals(LocalTime.of(6, 17), parsed.getValue(LocalDate.of(2026, 11, 25)).fajr)
        assertEquals(LocalTime.of(18, 16), parsed.getValue(LocalDate.of(2027, 1, 2)).isha)
    }

    @Test
    fun `kaputte Monats-Tabelle laesst die Jahres-Tabelle allein gelten`() {
        val html = resource("diyanet-jahreswechsel.html").replace("<td>05:42</td>", "<td>5.42</td>")
        assertEquals(
            listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)),
            DiyanetYearPageParser.parse(html).keys.toList(),
        )
    }

    @Test
    fun `fehlende Monats-Tabelle laesst die Jahres-Tabelle allein gelten`() {
        val html = resource("diyanet-jahreswechsel.html").replace("id=\"tab-1\"", "id=\"tab-x\"")
        assertEquals(
            listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)),
            DiyanetYearPageParser.parse(html).keys.toList(),
        )
    }

    @Test
    fun `Monats-Tabelle allein genuegt nicht - ohne Jahres-Tabelle wird geworfen`() {
        val html = resource("diyanet-jahreswechsel.html").replace("id=\"tab-2\"", "id=\"tab-x\"")
        assertThrows(IllegalArgumentException::class.java) {
            DiyanetYearPageParser.parse(html)
        }
    }

    @Test
    fun `bei gleichem Tag in beiden Tabellen gewinnt die Jahres-Tabelle`() {
        // tab-1 bekommt den 01.01.2027 mit abweichender Isha-Zeit dazu.
        val html = resource("diyanet-jahreswechsel.html").replace(
            "<td>25 Kasım 2026 Çarşamba</td>",
            "<td>01 Ocak 2027 Cuma</td>",
        ).replace("<td>18:18</td>", "<td>19:59</td>")
        assertEquals(LocalTime.of(18, 15), DiyanetYearPageParser.parse(html).getValue(LocalDate.of(2027, 1, 1)).isha)
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
