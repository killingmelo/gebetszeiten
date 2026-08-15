package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CompositeDiyanetFetcherTest {

    private val settings = AppSettings.DEFAULT
    private val day = LocalDate.of(2026, 7, 29)
    private fun six(fajrMinute: Int) = SixTimes(
        fajr = LocalTime.of(3, fajrMinute), sunrise = LocalTime.of(5, 36),
        dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 35),
        maghrib = LocalTime.of(21, 9), isha = LocalTime.of(22, 39),
    )
    private val yearData = mapOf(day to six(52))
    private val proxyData = mapOf(day to six(53))

    private fun fetcher(
        id: Int? = 11024,
        direct: suspend (Int) -> Map<LocalDate, SixTimes> = { yearData },
        proxy: suspend (Int) -> Map<LocalDate, SixTimes> = { proxyData },
    ) = CompositeDiyanetFetcher({ id }, direct, proxy, log = { _, _ -> })

    @Test
    fun `direkt liefert - Proxy wird nie gefragt`() = runBlocking {
        var proxyCalls = 0
        val result = fetcher(proxy = { proxyCalls++; proxyData }).fetch(settings)
        assertEquals(yearData, result.schedule)
        assertEquals(11024, result.locationId)
        assertEquals(0, proxyCalls)
    }

    @Test
    fun `direkt wirft - Proxy uebernimmt`() = runBlocking {
        val result = fetcher(direct = { error("HTML-Umbau") }).fetch(settings)
        assertEquals(proxyData, result.schedule)
        assertEquals(11024, result.locationId)
    }

    @Test
    fun `direkt leer - Proxy uebernimmt`() = runBlocking {
        val result = fetcher(direct = { emptyMap() }).fetch(settings)
        assertEquals(proxyData, result.schedule)
    }

    @Test
    fun `beide scheitern - leeres Ergebnis ohne ID`() = runBlocking {
        val result = fetcher(direct = { error("down") }, proxy = { error("down") }).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
    }

    @Test
    fun `keine ID aufloesbar - keine Abrufe`() = runBlocking {
        var calls = 0
        val result = fetcher(id = null, direct = { calls++; yearData }, proxy = { calls++; proxyData }).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
        assertEquals(0, calls)
    }

    @Test
    fun `ID-Aufloesung wirft - leeres Ergebnis statt Crash`() = runBlocking {
        val f = CompositeDiyanetFetcher({ error("Suche down") }, { yearData }, { proxyData }, log = { _, _ -> })
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
                    log = { _, _ -> },
                ).fetch(settings)
            }
        }
    }
}
