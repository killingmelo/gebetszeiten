package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parst die Jahres-Tabelle (tab-2) einer Diyanet-Jahresseite
 * (namazvakitleri.diyanet.gov.tr/tr-TR/{id}) — 1:1-Port von
 * parse_year_table aus tools/diyanet-fetch/fetch_diyanet.py.
 * Wirft bei jedem Format-Bruch, damit der Aufrufer auf den
 * Proxy zurückfallen kann.
 */
object DiyanetYearPageParser {

    private val TR_MONTHS = mapOf(
        "Ocak" to 1, "Şubat" to 2, "Mart" to 3, "Nisan" to 4,
        "Mayıs" to 5, "Haziran" to 6, "Temmuz" to 7, "Ağustos" to 8,
        "Eylül" to 9, "Ekim" to 10, "Kasım" to 11, "Aralık" to 12,
    )
    private val CELL = Regex("<td>\\s*([^<]*?)\\s*</td>")
    private val DATE = Regex("(\\d{2}) (\\S+) (\\d{4})")
    private val TIME = Regex("\\d{2}:\\d{2}")

    fun parse(html: String): Map<LocalDate, SixTimes> {
        val start = html.indexOf("id=\"tab-2\"")
        require(start >= 0) { "Jahres-Tabelle (tab-2) nicht gefunden" }
        val end = html.indexOf("</table>", start)
        require(end >= 0) { "Tabellen-Ende fehlt" }
        val cells = CELL.findAll(html.substring(start, end))
            .map { unescape(it.groupValues[1]) }
            .toList()
        require(cells.isNotEmpty() && cells.size % 8 == 0) {
            "Zellenzahl ${cells.size} nicht durch 8 teilbar"
        }
        val out = LinkedHashMap<LocalDate, SixTimes>(cells.size / 8)
        for (i in cells.indices step 8) {
            val m = requireNotNull(DATE.find(cells[i])) { "Datum unlesbar: ${cells[i]}" }
            val month = requireNotNull(TR_MONTHS[m.groupValues[2]]) { "Monat unlesbar: ${cells[i]}" }
            val date = LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            val times = (2..7).map { off ->
                cells[i + off].also { require(TIME.matches(it)) { "Zeit unlesbar am $date: $it" } }
            }
            out[date] = SixTimes(
                fajr = LocalTime.parse(times[0]),
                sunrise = LocalTime.parse(times[1]),
                dhuhr = LocalTime.parse(times[2]),
                asr = LocalTime.parse(times[3]),
                maghrib = LocalTime.parse(times[4]),
                isha = LocalTime.parse(times[5]),
            )
        }
        return out
    }

    /** Minimal-Unescape — die Zellen enthalten nur Datum/Uhrzeiten/Monatsnamen. */
    private fun unescape(s: String): String {
        var result = s
            .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
        // Numeric entities wie &#252; (ü)
        result = Regex("&#(\\d+);").replace(result) { m ->
            m.groupValues[1].toInt().toChar().toString()
        }
        // &amp; zuletzt, um keine anderen Entities zu zerstören
        result = result.replace("&amp;", "&")
        return result
    }
}
