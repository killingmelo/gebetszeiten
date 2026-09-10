package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections

class CompositeDiyanetFetcherTest {

    private val settings = AppSettings.DEFAULT
    private val day = LocalDate.of(2026, 7, 29)
    private fun six(fajrMinute: Int) = SixTimes(
        fajr = LocalTime.of(3, fajrMinute), sunrise = LocalTime.of(5, 36),
        dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 35),
        maghrib = LocalTime.of(21, 9), isha = LocalTime.of(22, 39),
    )

    /** Ein Fenster ab [day]. Der Direktabruf liefert das laengere („das
     *  Jahr"), die beiden Pruefer je ein kuerzeres — genau die Lage, fuer die
     *  `crossCheck` nur die Schnittmenge vergleicht. */
    private fun window(days: Int, fajrMinute: Int): Map<LocalDate, SixTimes> =
        (0 until days).associate { day.plusDays(it.toLong()) to six(fajrMinute) }

    private val yearData = window(5, 52)
    private val proxyData = window(3, 52)
    private val ezanData = window(3, 52)

    private fun fetcher(
        id: Int? = 11024,
        direct: suspend (Int) -> Map<LocalDate, SixTimes> = { yearData },
        proxy: suspend (Int) -> Map<LocalDate, SixTimes> = { proxyData },
        ezanvakti: suspend (Int) -> Map<LocalDate, SixTimes> = { ezanData },
        now: () -> Long = { 1_700_000_000_000L },
    ) = CompositeDiyanetFetcher({ id }, direct, proxy, ezanvakti, now, log = { _, _ -> })

    @Test
    fun `alle drei Quellen werden immer gefragt`() = runBlocking {
        // Bis Task 11 stand hier `direkt liefert - Proxy wird nie gefragt`.
        // Diese Aussage war der FEHLER, um den es geht: ein Fallback springt
        // nur ein, wenn die erste Quelle SCHWEIGT — nicht, wenn sie luegt. Er
        // verifiziert also nichts. Der Test ist deshalb nicht angepasst,
        // sondern in seiner Aussage umgedreht.
        var directCalls = 0
        var proxyCalls = 0
        var ezanCalls = 0
        val result = fetcher(
            direct = { directCalls++; yearData },
            proxy = { proxyCalls++; proxyData },
            ezanvakti = { ezanCalls++; ezanData },
        ).fetch(settings)
        assertEquals(1, directCalls)
        assertEquals(1, proxyCalls)
        assertEquals(1, ezanCalls)
        assertEquals(yearData, result.schedule)
        assertEquals(11024, result.locationId)
    }

    @Test
    fun `die Quellen laufen nebenlaeufig - keine wartet auf die vorige`() = runBlocking {
        // KEINE Stoppuhr: wie lange ein Abruf dauert, haengt von der Maschine
        // ab, und eine Zeitschwelle waere dort mal wahr und mal falsch.
        // Stattdessen vermerkt jede Quelle Start und Ende. Liefen sie
        // nacheinander, stuende ein "-ende" VOR dem zweiten "-start"; das
        // gilt auf jeder Maschine.
        val marken: MutableList<String> = Collections.synchronizedList(mutableListOf())
        suspend fun quelle(name: String, data: Map<LocalDate, SixTimes>): Map<LocalDate, SixTimes> {
            marken += "$name-start"
            delay(50)
            marken += "$name-ende"
            return data
        }
        fetcher(
            direct = { quelle("direct", yearData) },
            proxy = { quelle("proxy", proxyData) },
            ezanvakti = { quelle("ezan", ezanData) },
        ).fetch(settings)

        val erstesEnde = marken.indexOfFirst { it.endsWith("-ende") }
        assertTrue("keine Quelle hat geendet: $marken", erstesEnde >= 0)
        for (name in listOf("direct", "proxy", "ezan")) {
            assertTrue(
                "$name begann erst, nachdem die erste Quelle fertig war: $marken",
                marken.indexOf("$name-start") in 0 until erstesEnde,
            )
        }
    }

    @Test
    fun `alle drei einig - bestaetigt, volles Fenster des Direktabrufs`() = runBlocking {
        val result = fetcher().fetch(settings)
        assertEquals(yearData, result.schedule)
        assertEquals(5, result.schedule.size)
        val v = result.verification
        assertNotNull("keine Pruefnotiz im Ergebnis", v)
        assertEquals(VerificationNote.VERIFIED, v!!.note)
        assertEquals(SourceId.DIRECT, v.chosen)
        assertEquals(listOf(SourceId.PROXY_ABDUS, SourceId.EZANVAKTI), v.confirmedBy)
        // Die Uhr wird hineingereicht, nicht aus dem System geholt — sonst
        // waere `checkedEpochMs` nicht pruefbar.
        assertEquals(1_700_000_000_000L, v.checkedEpochMs)
        assertNull(result.errorSummary)
    }

    @Test
    fun `beide Pruefer widersprechen dem Jahresabruf einig - ihre Zeiten gelten`() = runBlocking {
        // Der Test, der den Double-Fail-Safe beweist: der Direktabruf
        // liefert, wird aber verworfen, weil BEIDE Kontrollquellen ihm einig
        // widersprechen. Mit der alten Kette waere er nie ueberprueft worden.
        val abweichend = window(3, 30) // 22 Minuten frueher — weit ueber MINOR_DRIFT
        val result = fetcher(proxy = { abweichend }, ezanvakti = { abweichend }).fetch(settings)
        assertEquals(abweichend, result.schedule)
        assertEquals(VerificationNote.CONFLICT_OVERRIDDEN, result.verification?.note)
        assertEquals(11024, result.locationId)
    }

    @Test
    fun `eine Quelle wirft - die anderen tragen das Ergebnis trotzdem`() = runBlocking {
        // `coroutineScope` bricht bei einer Ausnahme in einem `async` ALLE
        // Geschwister mit ab. Deshalb faengt `attempt` INNEN: aus der
        // Ausnahme wird ein Fehlereintrag, kein Abbruch der beiden anderen.
        val result = fetcher(ezanvakti = { throw IOException("Verbindung weg") }).fetch(settings)
        assertEquals(yearData, result.schedule)
        assertEquals(VerificationNote.VERIFIED, result.verification?.note)
        assertEquals("ezanvakti: Verbindung weg", result.errorSummary)
    }

    @Test
    fun `direkt wirft - die Pruefer tragen das Ergebnis`() = runBlocking {
        // Das Ergebnis ist dasselbe wie vor Task 11, der Weg ein anderer:
        // frueher uebernahm der Proxy als naechstes Glied der Kette, heute
        // gewinnt er das Quorum, weil der Direktabruf nichts beisteuert.
        val result = fetcher(direct = { error("HTML-Umbau") }).fetch(settings)
        assertEquals(proxyData, result.schedule)
        assertEquals(11024, result.locationId)
        assertEquals(VerificationNote.VERIFIED, result.verification?.note)
        assertEquals(SourceId.PROXY_ABDUS, result.verification?.chosen)
    }

    @Test
    fun `direkt leer - die Pruefer tragen das Ergebnis`() = runBlocking {
        val result = fetcher(direct = { emptyMap() }).fetch(settings)
        assertEquals(proxyData, result.schedule)
        // Leer ohne Ausnahme ist KEIN Fehlschlag: es gibt nichts zu melden.
        assertNull(result.errorSummary)
    }

    @Test
    fun `alle drei scheitern - leeres Ergebnis ohne ID`() = runBlocking {
        val result = fetcher(
            direct = { error("down") },
            proxy = { error("down") },
            ezanvakti = { error("down") },
        ).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
        assertEquals(VerificationNote.NONE, result.verification?.note)
    }

    @Test
    fun `errorSummary nennt jede gescheiterte Quelle in Quellen-Reihenfolge`() = runBlocking {
        val result = fetcher(
            direct = { error("HTTP 503") },
            ezanvakti = { throw SocketTimeoutException("Read timed out") },
        ).fetch(settings)
        assertEquals("Direktabruf: HTTP 503 · ezanvakti: Zeitüberschreitung", result.errorSummary)
        // Der Proxy allein bleibt uebrig — eine Quelle belegt sich nicht selbst.
        assertEquals(proxyData, result.schedule)
        assertEquals(VerificationNote.UNVERIFIED_SINGLE, result.verification?.note)
    }

    @Test
    fun `fetchErrorText macht kurze Klartexte fuer die Statuszeile`() {
        // Was hier herauskommt, liest der Nutzer. Keine Stacktraces, keine
        // Klassennamen mit Paket.
        assertEquals("Zeitüberschreitung", fetchErrorText(SocketTimeoutException("Read timed out")))
        assertEquals("Zeitüberschreitung", fetchErrorText(SocketTimeoutException()))
        assertEquals("Kein Netz", fetchErrorText(UnknownHostException("ezanvakti.emushaf.net")))
        assertEquals("HTTP 503", fetchErrorText(IllegalStateException("HTTP 503")))
        assertEquals("IllegalStateException", fetchErrorText(IllegalStateException()))
        assertEquals("IllegalStateException", fetchErrorText(IllegalStateException("   ")))
    }

    @Test
    fun `keine ID aufloesbar - keine Abrufe`() = runBlocking {
        var calls = 0
        val result = fetcher(
            id = null,
            direct = { calls++; yearData },
            proxy = { calls++; proxyData },
            ezanvakti = { calls++; ezanData },
        ).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
        assertEquals(0, calls)
        // Ohne Abruf gibt es nichts zu pruefen und nichts zu melden.
        assertNull(result.verification)
        assertNull(result.errorSummary)
    }

    @Test
    fun `ID-Aufloesung wirft - leeres Ergebnis statt Crash`() = runBlocking {
        val f = CompositeDiyanetFetcher(
            { error("Suche down") },
            { yearData },
            { proxyData },
            { ezanData },
            log = { _, _ -> },
        )
        val result = f.fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
    }

    @Test
    fun `nicht aufloesbarer Standort wird protokolliert statt still verschluckt`() = runBlocking {
        // Vor dem Umbau war dies ein stilles `?: return`: Serdivan fiel ohne
        // jede Spur auf die eigene Berechnung zurueck.
        val logged = mutableListOf<String>()
        val f = CompositeDiyanetFetcher(
            resolveId = { null },
            direct = { yearData },
            proxy = { proxyData },
            ezanvakti = { ezanData },
            log = { msg, _ -> logged.add(msg) },
        )
        val result = f.fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
        assertTrue(
            "kein Log-Eintrag zum nicht aufloesbaren Standort: $logged",
            logged.any { it.contains("Kein Diyanet-Standort") && it.contains(settings.city) },
        )
    }

    @Test
    fun `CancellationException wird durchgereicht statt geschluckt`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                fetcher(direct = { throw CancellationException("abbruch") }).fetch(settings)
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                CompositeDiyanetFetcher(
                    { throw CancellationException("abbruch") },
                    { yearData },
                    { proxyData },
                    { ezanData },
                    log = { _, _ -> },
                ).fetch(settings)
            }
        }
    }
}
