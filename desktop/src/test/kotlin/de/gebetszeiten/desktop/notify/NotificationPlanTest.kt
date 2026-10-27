package de.gebetszeiten.desktop.notify

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.HijriDay
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

    /** Nur Gebetserinnerungen, alle Hinweise aus (so testen die Grundfaelle). */
    private val plain = DesktopSettings(
        endWarningMinutes = 0, cumaHints = false, fastingHints = false,
        nafileHints = false, hijriHints = false, ramazanHints = false,
    )

    private fun daily(date: LocalDate, fajr: LocalTime = LocalTime.of(5, 0)): DailyPrayerTimes {
        fun t(l: LocalTime) = ZonedDateTime.of(date, l, zone)
        return DailyPrayerTimes(
            date, zone, t(fajr), t(LocalTime.of(6, 30)), t(LocalTime.of(13, 0)),
            t(LocalTime.of(15, 30)), t(LocalTime.of(18, 0)), t(LocalTime.of(19, 30)),
        )
    }

    private fun hints(ctx: DayContext, s: DesktopSettings) =
        notificationEvents(ctx, s).filter { it.kind == EventKind.HINT }

    @Test
    fun endWarningBeforeEachPrayerEnds() {
        val ctx = DayContext(times, next = daily(day.plusDays(1), LocalTime.of(4, 58)))
        val ev = notificationEvents(ctx, plain.copy(endWarningMinutes = 20))
            .filter { it.kind == EventKind.END_WARNING }
            .associateBy { it.prayer }
        assertEquals(at(6, 10), ev.getValue(Prayer.FAJR).at)
        assertEquals(at(15, 10), ev.getValue(Prayer.DHUHR).at)
        // Asr: Ende ist der Beginn der Isfirar-Karaha (18:00 - 40 = 17:20).
        assertEquals(at(17, 0), ev.getValue(Prayer.ASR).at)
        assertEquals(at(19, 10), ev.getValue(Prayer.MAGHRIB).at)
        assertEquals(ZonedDateTime.of(day.plusDays(1), LocalTime.of(4, 38), zone), ev.getValue(Prayer.ISHA).at)
        assertEquals("ʿIšāʾ endet um 04:58 (Faǧr)", ev.getValue(Prayer.ISHA).text)
        assertEquals(ToneCategory.WARNING, ev.getValue(Prayer.ASR).tone)
    }

    @Test
    fun ishaEndWarningNeedsTomorrow() {
        val ev = notificationEvents(DayContext(times), plain.copy(endWarningMinutes = 20))
        assertTrue(ev.none { it.kind == EventKind.END_WARNING && it.prayer == Prayer.ISHA })
    }

    @Test
    fun asrEndWarningReplacesIsfirarKaraha() {
        val ev = notificationEvents(times, plain.copy(endWarningMinutes = 15, karahaWarning = true))
        assertEquals(2, ev.count { it.kind == EventKind.KARAHA_WARNING })
        assertTrue(ev.any { it.kind == EventKind.END_WARNING && it.prayer == Prayer.ASR })
    }

    @Test
    fun cumaHintsThursdayEveningAndFriday() {
        val thursday = LocalDate.of(2026, 6, 11)
        val friday = thursday.plusDays(1)
        val s = plain.copy(cumaHints = true)
        val th = hints(DayContext(daily(thursday)), s).single()
        assertEquals(Occasion.CUMA_GECE, th.occasion)
        val fr = hints(DayContext(daily(friday)), s)
        assertEquals(listOf(Occasion.CUMA, Occasion.CUMA_NAMAZ), fr.map { it.occasion })
        assertEquals(ZonedDateTime.of(friday, LocalTime.of(12, 15), zone), fr[1].at)
        // Tageshinweis morgens nachholbar bis 45 Min vor Dhuhr.
        assertEquals(ZonedDateTime.of(friday, LocalTime.of(7, 15), zone), fr[0].at)
        assertEquals(ZonedDateTime.of(friday, LocalTime.of(12, 15), zone), fr[0].until)
    }

    @Test
    fun kandilOnlyWithDiyanetHijri() {
        val s = plain.copy(hijriHints = true)
        // 14 Şaban heute, morgen 15 Şaban: Berat-Nacht beginnt heute Abend.
        val berat = hints(DayContext(times, hijri = HijriDay(14, 8, 1448), hijriNext = HijriDay(15, 8, 1448)), s)
        assertEquals(Occasion.BERAT, berat.single().occasion)
        assertEquals(at(18, 10), berat.single().at)
        // Ohne Hicri-Datum: nichts, auch nicht geraten.
        assertTrue(hints(DayContext(times), s).isEmpty())
    }

    @Test
    fun regaibIsNightBeforeFirstFridayOfRecep() {
        val thursday = LocalDate.of(2026, 6, 11)
        assertEquals(Occasion.REGAIB, kandilTonight(thursday, HijriDay(3, 7, 1448)))
        assertEquals(null, kandilTonight(thursday, HijriDay(10, 7, 1448)))
        assertEquals(null, kandilTonight(thursday.plusDays(1), HijriDay(4, 7, 1448)))
    }

    @Test
    fun ramazanSahurAndIftarMergedIntoMaghrib() {
        val s = plain.copy(ramazanHints = true, sahurMinutes = 45, iftarLeadMinutes = 10)
        val ev = notificationEvents(DayContext(times, hijri = HijriDay(5, 9, 1448), hijriNext = HijriDay(6, 9, 1448)), s)
        val sahur = ev.single { it.occasion == Occasion.SAHUR }
        assertEquals(at(4, 15), sahur.at)
        assertEquals(at(5, 0), sahur.until)
        val maghrib = ev.filter { it.at == at(18, 0) }
        assertEquals(1, maghrib.size)
        assertEquals(Occasion.IFTAR, maghrib.single().occasion)
        assertEquals(at(17, 50), ev.single { it.title == "Ifṭār bald" }.at)
    }

    @Test
    fun fastingHintEveningBeforeMondayThursdayAndWhiteDays() {
        val s = plain.copy(fastingHints = true)
        val wednesday = LocalDate.of(2026, 6, 10)
        val th = hints(DayContext(daily(wednesday), next = daily(wednesday.plusDays(1))), s).single()
        assertEquals("Morgen: Donnerstag – Sunna-Fasten. Saḥūr bis 05:00.", th.text)
        assertEquals(at(19, 30), th.at)
        val both = hints(DayContext(daily(wednesday), hijriNext = HijriDay(13, 4, 1448)), s).single()
        assertEquals("Morgen: Donnerstag und al-Ayyām al-Bīḍ (13.) – Sunna-Fasten.", both.text)
        // Im Ramazan und an Bayram kein Sunnet-Fasten-Hinweis.
        assertTrue(hints(DayContext(daily(wednesday), hijriNext = HijriDay(14, 9, 1448)), s).isEmpty())
        assertTrue(hints(DayContext(daily(wednesday), hijriNext = HijriDay(10, 12, 1448)), s).isEmpty())
    }

    @Test
    fun specialDaysAndTodayLine() {
        assertEquals(Occasion.AREFE, specialDay(HijriDay(9, 12, 1448)))
        assertEquals(Occasion.BAYRAM_KURBAN, specialDay(HijriDay(11, 12, 1448)))
        assertEquals(null, specialDay(HijriDay(20, 5, 1448)))
        val friday = LocalDate.of(2026, 6, 12)
        assertEquals("Ramaḍān, 27. Tag · Ǧumʿa", todayLine(friday, HijriDay(27, 9, 1448), HijriDay(28, 9, 1448)))
        assertEquals(null, todayLine(day, HijriDay(20, 5, 1448), HijriDay(21, 5, 1448)))
    }

    @Test
    fun nafileHintsNeedNextFajrForTeheccud() {
        val s = plain.copy(nafileHints = true)
        assertEquals(listOf(Occasion.KUSLUK), hints(DayContext(times), s).map { it.occasion })
        val withNext = hints(DayContext(times, next = daily(day.plusDays(1))), s)
        assertEquals(listOf(Occasion.KUSLUK, Occasion.TEHECCUD), withNext.map { it.occasion })
    }

    @Test
    fun catchUpUntilDeadline() {
        val e = NotifyEvent(at(7, 15), EventKind.HINT, null, "Cuma", "x", false, Occasion.CUMA, until = at(12, 15))
        // Programm um 9:00 gestartet: lastRun == now.
        assertEquals(listOf(e), dueEvents(listOf(e), at(9, 0), at(9, 0)))
        assertTrue(dueEvents(listOf(e), at(12, 15), at(12, 15)).isEmpty())
    }

    @Test
    fun defaultRemindersFiveNoSunrise() {
        val ev = notificationEvents(times, plain.copy())
        assertEquals(5, ev.size)
        assertTrue(ev.all { it.kind == EventKind.PRAYER })
        assertFalse(ev.any { it.prayer == Prayer.SUNRISE })
        assertEquals(ev.sortedBy { it.at }, ev)
        val asr = ev.single { it.prayer == Prayer.ASR }
        assertEquals("ʿAṣr", asr.title)
        assertEquals("Es ist Zeit für ʿAṣr", asr.text)
        assertEquals(at(15, 30), asr.at)
        assertFalse(asr.playEzan)
    }

    @Test
    fun leadEventTenMinutesBefore() {
        val s = plain.copy(reminders = setOf(Prayer.ASR), leadMinutes = 10, ezanFile = "a.mp3")
        val lead = notificationEvents(times, s).single { it.kind == EventKind.LEAD }
        assertEquals(at(15, 20), lead.at)
        assertEquals("ʿAṣr", lead.title)
        assertEquals("ʿAṣr in 10 Min", lead.text)
        assertFalse(lead.playEzan)
    }

    @Test
    fun karahaWarningFifteenBeforeWindow() {
        val s = plain.copy(reminders = emptySet(), karahaWarning = true)
        val ev = notificationEvents(times, s)
        assertEquals(3, ev.size)
        val zeval = ev.single { it.at == at(12, 25) }
        assertEquals(EventKind.KARAHA_WARNING, zeval.kind)
        assertEquals("Karāha", zeval.title)
        assertEquals("⚠️ Karāha ab 12:40", zeval.text)
        assertEquals(null, zeval.prayer)
        assertFalse(zeval.playEzan)
    }

    @Test
    fun fajrUsesFajrEzanFlag() {
        val s = plain.copy(fajrEzanFile = "f.mp3")
        val ev = notificationEvents(times, s)
        assertTrue(ev.single { it.prayer == Prayer.FAJR }.playEzan)
        assertFalse(ev.single { it.prayer == Prayer.ASR }.playEzan)
        val s2 = plain.copy(ezanFile = "e.mp3")
        assertTrue(notificationEvents(times, s2).all { it.playEzan })
    }

    @Test
    fun missedDuringSleepIsDropped() {
        val e = NotifyEvent(at(13, 30), EventKind.PRAYER, Prayer.DHUHR, "Ẓuhr", "x", false)
        assertTrue(dueEvents(listOf(e), at(10, 0), at(15, 0)).isEmpty())
    }

    @Test
    fun justDueIsDelivered() {
        val now = at(13, 30)
        val e = NotifyEvent(now.minusSeconds(30), EventKind.PRAYER, Prayer.DHUHR, "Ẓuhr", "x", false)
        assertEquals(listOf(e), dueEvents(listOf(e), now.minusSeconds(60), now))
        // schon geliefert: lastRun == at
        assertTrue(dueEvents(listOf(e), e.at, now).isEmpty())
    }
}
