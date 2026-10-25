package de.gebetszeiten.desktop.notify

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.desktop.data.DesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationPlanTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 6, 10)

    private fun at(h: Int, m: Int, s: Int = 0): ZonedDateTime =
        ZonedDateTime.of(day, LocalTime.of(h, m, s), zone)

    private val times = DailyPrayerTimes(
        day, zone, at(5, 0), at(6, 30), at(13, 0), at(15, 30), at(18, 0), at(19, 30),
    )

    @Test
    fun defaultRemindersFiveNoSunrise() {
        val ev = notificationEvents(times, DesktopSettings())
        assertEquals(5, ev.size)
        assertTrue(ev.all { it.kind == EventKind.PRAYER })
        assertFalse(ev.any { it.prayer == Prayer.SUNRISE })
        assertEquals(ev.sortedBy { it.at }, ev)
        val asr = ev.single { it.prayer == Prayer.ASR }
        assertEquals("Asr", asr.title)
        assertEquals("Es ist Zeit für Asr", asr.text)
        assertEquals(at(15, 30), asr.at)
        assertFalse(asr.playEzan)
    }

    @Test
    fun leadEventTenMinutesBefore() {
        val s = DesktopSettings(reminders = setOf(Prayer.ASR), leadMinutes = 10, ezanFile = "a.mp3")
        val lead = notificationEvents(times, s).single { it.kind == EventKind.LEAD }
        assertEquals(at(15, 20), lead.at)
        assertEquals("Asr", lead.title)
        assertEquals("Asr in 10 Min", lead.text)
        assertFalse(lead.playEzan)
    }

    @Test
    fun karahaWarningFifteenBeforeWindow() {
        val s = DesktopSettings(reminders = emptySet(), karahaWarning = true)
        val ev = notificationEvents(times, s)
        assertEquals(3, ev.size)
        val zeval = ev.single { it.at == at(12, 25) }
        assertEquals(EventKind.KARAHA_WARNING, zeval.kind)
        assertEquals("Karaha", zeval.title)
        assertEquals("⚠️ Karaha ab 12:40", zeval.text)
        assertEquals(null, zeval.prayer)
        assertFalse(zeval.playEzan)
    }

    @Test
    fun fajrUsesFajrEzanFlag() {
        val s = DesktopSettings(fajrEzanFile = "f.mp3")
        val ev = notificationEvents(times, s)
        assertTrue(ev.single { it.prayer == Prayer.FAJR }.playEzan)
        assertFalse(ev.single { it.prayer == Prayer.ASR }.playEzan)
        val s2 = DesktopSettings(ezanFile = "e.mp3")
        assertTrue(notificationEvents(times, s2).all { it.playEzan })
    }

    @Test
    fun missedDuringSleepIsDropped() {
        val e = NotifyEvent(at(13, 30), EventKind.PRAYER, Prayer.DHUHR, "Dhuhr", "x", false)
        assertTrue(dueEvents(listOf(e), at(10, 0), at(15, 0)).isEmpty())
    }

    @Test
    fun justDueIsDelivered() {
        val now = at(13, 30)
        val e = NotifyEvent(now.minusSeconds(30), EventKind.PRAYER, Prayer.DHUHR, "Dhuhr", "x", false)
        assertEquals(listOf(e), dueEvents(listOf(e), now.minusSeconds(60), now))
        // schon geliefert: lastRun == at
        assertTrue(dueEvents(listOf(e), e.at, now).isEmpty())
    }
}
