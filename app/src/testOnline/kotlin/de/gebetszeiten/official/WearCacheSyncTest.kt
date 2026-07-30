package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WearCacheSyncTest {

    private val day = LocalDate.of(2026, 7, 30)
    private val schedule = mapOf(
        day to SixTimes(
            fajr = LocalTime.of(3, 54), sunrise = LocalTime.of(5, 38),
            dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 34),
            maghrib = LocalTime.of(21, 7), isha = LocalTime.of(22, 36),
        ),
    )

    @Test
    fun `pusht serialisierten Text mit Ort und Stadt`() = runBlocking {
        val calls = mutableListOf<List<Any>>()
        val sync = WearCacheSync(
            put = { text, lat, lng, city -> calls.add(listOf(text, lat, lng, city)) },
            log = { _, _ -> },
        )
        sync.push(schedule, 41.0082, 28.9784, "Istanbul")
        assertEquals(1, calls.size)
        assertEquals(
            listOf<Any>("2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36", 41.0082, 28.9784, "Istanbul"),
            calls.single(),
        )
    }

    @Test
    fun `leerer Zeitplan wird nie gepusht`() = runBlocking {
        var calls = 0
        WearCacheSync(put = { _, _, _, _ -> calls++ }, log = { _, _ -> })
            .push(emptyMap(), 41.0, 28.9, "Istanbul")
        assertEquals(0, calls)
    }

    @Test
    fun `Push-Fehler wird geschluckt und geloggt`() = runBlocking {
        var logged = 0
        WearCacheSync(put = { _, _, _, _ -> error("kein gms") }, log = { _, _ -> logged++ })
            .push(schedule, 41.0, 28.9, "Istanbul")
        assertEquals(1, logged)
    }

    @Test
    fun `CancellationException wird durchgereicht`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                WearCacheSync(put = { _, _, _, _ -> throw CancellationException("abbruch") }, log = { _, _ -> })
                    .push(schedule, 41.0, 28.9, "Istanbul")
            }
        }
    }
}
