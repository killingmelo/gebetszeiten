package de.gebetszeiten.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Entscheidung ueber alle drei Ketten-Wecker (Gebet, Vorlauf, Anzeige-
 * Stufe) — aus `scheduleNext` herausgezogen (Aufgabe 13, Fix-Runde 1), weil
 * `scheduleNext` selbst wegen `Context`/`AlarmManager` ohne Robolectric nicht
 * ausfuehrbar ist, diese Entscheidung darin aber sehr wohl.
 *
 * Erste Fassung dieser Aufgabe hatte nur den Gebets-Wecker allein
 * herausgezogen (`mainAlarmTriggerAtMillis`) und die drei Aufrufe in
 * `scheduleNext` unbedingt gelassen — richtig, aber nicht dagegen geschuetzt,
 * die drei Aufrufe stattdessen HINTER eine eigene Bedingung zu
 * verschachteln (dieselbe Fehlerklasse wie das fruehe `return` aus
 * Aufgabe 9, nur ueber Verschachtelung statt ueber einen fruehen
 * Rueckgabewert). `alarmPlan` macht diese Verschachtelung unmoeglich, statt
 * sie nur schwerer zu entdecken: `scheduleNext` hat danach ueberhaupt keine
 * eigene Verzweigung mehr, nur noch drei gleichfoermige Anwendungen des
 * bereits fertigen Plans.
 */
class PrayerAlarmSchedulerTest {

    private val now = 1_700_000_000_000L
    private val fajr = "FAJR"
    private val isha = "ISHA"

    @Test fun keineZeitenAlleDreiWeckerAbbestellt() {
        // Mutation, die dieser Test toetet: irgendein Feld faellt bei
        // fehlenden Zeiten auf einen erfundenen Wert zurueck statt auf
        // `null` — etwa `displayStepAtMillis` per `boundaries.minOrNull()
        // ?: irgendwas` statt ueber `nextDisplayBoundary`, oder
        // `preReminderAtMillis` haengt an `nextPrayerMillis` statt an
        // `upcomingPrayerMillis`.
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = null,
            upcomingPrayerName = null,
            upcomingPrayerMillis = null,
            reminderLeadMinutes = 15,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertNull("Gebets-Wecker", plan.prayerAtMillis)
        assertNull("Vorlauf-Wecker", plan.preReminderAtMillis)
        assertNull("Stufen-Wecker", plan.displayStepAtMillis)
    }

    @Test fun zeitenVorhandenVorlaufAusVorlaufBleibtAbbestelltDieAnderenNicht() {
        // Vorlauf "aus" heisst `reminderLeadMinutes == 0` — Mutation, die
        // dieser Test toetet: die `reminderLeadMinutes > 0`-Bedingung faellt
        // weg oder wird zu `>= 0`, dann bekaeme der Vorlauf-Wecker trotz
        // ausgeschaltetem Vorlauf eine Zeit.
        val upcomingMillis = now + 3 * 60 * 60 * 1000L // 3 h voraus
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = upcomingMillis,
            reminderLeadMinutes = 0,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = listOf(now + 30_000L),
            nowMillis = now,
        )
        assertNull("Vorlauf-Wecker bei reminderLeadMinutes=0", plan.preReminderAtMillis)
        assertEquals("Gebets-Wecker bleibt unberuehrt vom Vorlauf", now + 60_000L, plan.prayerAtMillis)
        assertEquals("Stufen-Wecker bleibt unberuehrt vom Vorlauf", now + 30_000L, plan.displayStepAtMillis)
    }

    @Test fun zeitenVorhandenRestzeitAnzeigeAusStufenWeckerBleibtAbbestelltDieAnderenNicht() {
        // "Restzeit-Anzeige aus" heisst: keine Grenzen kommen herein (der
        // Aufrufer hat sie gar nicht erst gesammelt). Mutation, die dieser
        // Test toetet: `displayStepAtMillis` faellt bei leerer Liste auf
        // etwas anderes als `null` zurueck (z. B. `nowMillis` selbst oder
        // den letzten bekannten Wert).
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = now + 3 * 60 * 60 * 1000L,
            reminderLeadMinutes = 15,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertNull("Stufen-Wecker ohne Grenzen", plan.displayStepAtMillis)
        assertEquals("Gebets-Wecker bleibt unberuehrt", now + 60_000L, plan.prayerAtMillis)
        assertEquals(
            "Vorlauf-Wecker bleibt unberuehrt",
            now + 3 * 60 * 60 * 1000L - 15 * 60_000L,
            plan.preReminderAtMillis,
        )
    }

    @Test fun vorlaufGesetztWennGebetInDenErinnerungenSteht() {
        // Mutation, die dieser Test toetet: die Mitgliedschaftspruefung
        // (`upcomingPrayerName in enabledReminders`) faellt weg — dann
        // bekaeme JEDES Gebet einen Vorlauf-Wecker, auch eines ohne
        // aktivierte Erinnerung.
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = now + 3 * 60 * 60 * 1000L,
            reminderLeadMinutes = 10,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertEquals(now + 3 * 60 * 60 * 1000L - 10 * 60_000L, plan.preReminderAtMillis)
    }

    @Test fun vorlaufAbbestelltWennGebetNichtInDenErinnerungenSteht() {
        // Isha ist NICHT in den aktivierten Erinnerungen — derselbe Test wie
        // oben, andere Erwartung. Mutation, die dieser Test toetet:
        // dieselbe wie oben, nur aus der anderen Richtung sichtbar.
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = now + 3 * 60 * 60 * 1000L,
            reminderLeadMinutes = 10,
            enabledReminders = setOf(fajr),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertNull(plan.preReminderAtMillis)
    }

    @Test fun vorlaufAbbestelltWennDerAusloeseZeitpunktSchonVorbeiWaere() {
        // Vorlauf 30 Min, Gebet aber schon in 10 Min: der Ausloesezeitpunkt
        // laege in der Vergangenheit. Mutation, die dieser Test toetet: die
        // `triggerAt > nowMillis`-Pruefung faellt weg — dann wuerde ein
        // Wecker auf einen vergangenen Zeitpunkt gesetzt (feuert sofort statt
        // gar nicht).
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 10 * 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = now + 10 * 60_000L,
            reminderLeadMinutes = 30,
            enabledReminders = setOf(isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertNull(plan.preReminderAtMillis)
    }

    @Test fun stufenWeckerNimmtDieKleinsteZukuenftigeGrenze() {
        // Mutation, die dieser Test toetet: `displayStepAtMillis` wird direkt
        // aus der Liste gebildet (z. B. `minOrNull()`) statt ueber
        // `nextDisplayBoundary`, das vergangene Grenzen herausfiltert.
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = now + 60_000L,
            reminderLeadMinutes = 0,
            enabledReminders = emptySet(),
            displayStepBoundaries = listOf(now - 10_000L, now + 5_000L, now + 20_000L),
            nowMillis = now,
        )
        // now - 10_000 liegt in der Vergangenheit und faellt raus; von den
        // beiden verbleibenden ist now + 5_000 die kleinere.
        assertEquals(now + 5_000L, plan.displayStepAtMillis)
    }
}
