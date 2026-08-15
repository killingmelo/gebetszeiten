package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Auswahl der Diyanet-ID aus einer Namenssuche — die letzte Stufe der
 * Aufloesungskette.
 *
 * Der Test existiert vor allem wegen des punktlosen tuerkischen ı (U+0131):
 * es ist NICHT NFD-zerlegbar, hat also keine abzustreifende Akzentmarke. Eine
 * Normalisierung, die nur Unicode-Marken entfernt, verfehlt daher jeden Ort mit
 * diesem Buchstaben — "Şanlıurfa" (so steht der Ort in cities.tsv und damit in
 * settings.city) traf Diyanets "ŞANLIURFA" nicht.
 */
class ProxyLocationPickTest {

    private val pick = DiyanetProxyFetcher()::pickLocationId

    /** Realistische Antwortform des Proxys: city = Provinz, region = Bezirk. */
    private val sanliurfa = """
        [{"id":9950,"country":"TÜRKİYE","city":"ŞANLIURFA","region":"AKÇAKALE"},
         {"id":9951,"country":"TÜRKİYE","city":"ŞANLIURFA","region":"BİRECİK"},
         {"id":9958,"country":"TÜRKİYE","city":"ŞANLIURFA","region":"ŞANLIURFA"}]
    """.trimIndent()

    @Test
    fun `tuerkische Schreibweise mit punktlosem i findet den Standort`() {
        // DER Regressionsfall: mit reinem NFD-Strippen bleibt das ı erhalten und
        // dieser Vergleich scheitert.
        assertEquals(9958, pick(sanliurfa, "Şanlıurfa"))
    }

    @Test
    fun `mit deutscher Tastatur getippt findet denselben Standort`() {
        assertEquals(9958, pick(sanliurfa, "sanliurfa"))
        assertEquals(9958, pick(sanliurfa, "Sanliurfa"))
    }

    @Test
    fun `Bezirk schlaegt Provinz - Zentrum vor erstbestem Stadtbezirk`() {
        // Tuerkische Grossstaedte listen jeden Bezirk mit city = Provinzname.
        // Ein City-Match wuerde den erstbesten Bezirk liefern statt des Zentrums.
        val istanbul = """
            [{"id":9500,"country":"TÜRKİYE","city":"İSTANBUL","region":"ARNAVUTKÖY"},
             {"id":9541,"country":"TÜRKİYE","city":"İSTANBUL","region":"İSTANBUL"}]
        """.trimIndent()
        assertEquals(9541, pick(istanbul, "İstanbul"))
    }

    @Test
    fun `ohne Bezirkstreffer greift der Provinzname`() {
        val nurOrte = """
            [{"id":9500,"country":"TÜRKİYE","city":"İSTANBUL","region":"ARNAVUTKÖY"},
             {"id":9501,"country":"TÜRKİYE","city":"İSTANBUL","region":"BEYKOZ"}]
        """.trimIndent()
        assertEquals(9500, pick(nurOrte, "Istanbul"))
    }

    @Test
    fun `leere Antwort ergibt null statt Absturz`() {
        assertNull(pick("[]", "Serdivan"))
    }

    @Test
    fun `voellig fremder Ort faellt auf den ersten Treffer zurueck`() {
        assertEquals(9950, pick(sanliurfa, "Buxtehude"))
    }

    @Test
    fun `Eintraege ohne brauchbare id werden uebersprungen`() {
        val kaputt = """
            [{"country":"TÜRKİYE","city":"ŞANLIURFA","region":"ŞANLIURFA"},
             {"id":0,"country":"TÜRKİYE","city":"ŞANLIURFA","region":"AKÇAKALE"},
             {"id":9958,"country":"TÜRKİYE","city":"ŞANLIURFA","region":"ŞANLIURFA"}]
        """.trimIndent()
        assertEquals(9958, pick(kaputt, "Şanlıurfa"))
    }
}
