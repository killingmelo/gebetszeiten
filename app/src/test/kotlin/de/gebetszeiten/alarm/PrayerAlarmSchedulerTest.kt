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
 *
 * Fix-Runde 2: die ersten sieben Faelle variierten `nextPrayerMillis` und
 * `upcomingPrayerMillis` nie gegenlaeufig (entweder beide `null` oder beide
 * gesetzt) - eine versehentliche Kopplung der beiden Parameter waere damit
 * unentdeckt geblieben, obwohl `alarmPlan` sie als unabhaengig behandeln
 * soll. Die beiden neuen Faelle unten schliessen diese Luecke.
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

    @Test fun gebetVorhandenVorlaufZielFehltGebetsWeckerBleibtUnabhaengigVomVorlaufZiel() {
        // `nextPrayerMillis` und `upcomingPrayerMillis` gegenlaeufig gesetzt:
        // das Gebet ist da, das Vorlauf-Ziel (naechstes ECHTES Gebet, ohne
        // Sonnenaufgang) fehlt. In der Praxis waere das ungewoehnlich (beide
        // haengen ueblicherweise an denselben Zeiten), aber `alarmPlan`
        // bekommt sie als zwei UNABHAENGIGE Parameter - und darf sich auch
        // dann korrekt verhalten, wenn der Aufrufer sie einmal nicht im
        // Gleichschritt liefert. Mutation, die dieser Test toetet:
        // `prayerAtMillis = if (upcomingPrayerMillis == null) null else
        // nextPrayerMillis` - eine versehentliche Kopplung des Gebets-
        // Weckers an das Vorlauf-Ziel. Mit `upcomingPrayerMillis == null`
        // wuerde diese Mutation den Gebets-Wecker faelschlich abbestellen,
        // obwohl eine echte Gebetszeit vorliegt.
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = now + 60_000L,
            upcomingPrayerName = null,
            upcomingPrayerMillis = null,
            reminderLeadMinutes = 15,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertEquals("Gebets-Wecker bleibt gesetzt, obwohl das Vorlauf-Ziel fehlt", now + 60_000L, plan.prayerAtMillis)
        assertNull("Vorlauf-Wecker ohne Vorlauf-Ziel", plan.preReminderAtMillis)
    }

    @Test fun vorlaufZielVorhandenGebetFehltVorlaufWeckerBleibtUnabhaengigVomGebet() {
        // Die umgekehrte Kombination: das Vorlauf-Ziel ist da, das Gebet
        // (`nextPrayerMillis`) fehlt. Mutation, die dieser Test toetet: eine
        // symmetrische Kopplung in die andere Richtung, etwa
        // `preReminderAtMillis = if (nextPrayerMillis == null) null else
        // <die eigentliche Vorlauf-Rechnung>` - mit `nextPrayerMillis ==
        // null` wuerde diese Mutation den Vorlauf-Wecker faelschlich
        // abbestellen, obwohl ein echtes Vorlauf-Ziel vorliegt.
        val upcomingMillis = now + 3 * 60 * 60 * 1000L
        val plan = PrayerAlarmScheduler.alarmPlan(
            nextPrayerMillis = null,
            upcomingPrayerName = isha,
            upcomingPrayerMillis = upcomingMillis,
            reminderLeadMinutes = 15,
            enabledReminders = setOf(fajr, isha),
            displayStepBoundaries = emptyList(),
            nowMillis = now,
        )
        assertNull("Gebets-Wecker bleibt abbestellt, obwohl ein Vorlauf-Ziel vorliegt", plan.prayerAtMillis)
        assertEquals(
            "Vorlauf-Wecker bleibt gesetzt, obwohl das Gebet fehlt",
            upcomingMillis - 15 * 60_000L,
            plan.preReminderAtMillis,
        )
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
