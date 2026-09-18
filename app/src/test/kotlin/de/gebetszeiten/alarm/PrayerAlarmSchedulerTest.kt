package de.gebetszeiten.alarm

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.prayer.NextPrayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Die Entscheidung, ob der naechste Gebets-Wecker gesetzt oder abbestellt
 * wird — aus `scheduleNext` herausgezogen (Aufgabe 13), weil `scheduleNext`
 * selbst wegen `Context`/`AlarmManager` ohne Robolectric nicht ausfuehrbar
 * ist, diese eine Entscheidung darin aber sehr wohl.
 *
 * Vor Aufgabe 13 kehrte `scheduleNext` bei `null` VOR jeder Abbestellung
 * zurueck: alte Wecker blieben stehen und feuerten auf veraltete Zeiten.
 * `mainAlarmTriggerAtMillis(null) == null` haelt die Gegenprobe fest: `null`
 * muss immer "abbestellen" bedeuten, nie "unangetastet lassen".
 */
class PrayerAlarmSchedulerTest {

    private val zeit = ZonedDateTime.of(2026, 9, 18, 5, 12, 0, 0, ZoneOffset.UTC)

    @Test fun ohneNaechstesGebetWirdAbbestellt() {
        assertNull(PrayerAlarmScheduler.mainAlarmTriggerAtMillis(null))
    }

    @Test fun mitNaechstemGebetWirdGenauDessenZeitpunktGesetzt() {
        val next = NextPrayer(Prayer.ISHA, zeit)
        assertEquals(
            zeit.toInstant().toEpochMilli(),
            PrayerAlarmScheduler.mainAlarmTriggerAtMillis(next),
        )
    }
}
