package de.gebetszeiten.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wer braucht die Anzeige-Weckkette (`PrayerAlarmScheduler.scheduleDisplayStep`)?
 *
 * Seit das Statusleisten-Symbol an dieser Kette haengt, ist die Frage nicht
 * mehr „wer zeigt Stufen", sondern „wessen Anzeige muss die App selbst
 * weiterstellen". Die Dauerbenachrichtigung gehoert deshalb auch im
 * EXACT-Modus dazu: der Systemzaehler zeichnet zwar den Text, aber nicht das
 * Symbol.
 *
 * Seit Task 16 stellt EINE Einstellung (`countdownMode`) alle Flaechen. Die
 * Asymmetrie zwischen Widget und Benachrichtigung bleibt trotzdem — sie kam
 * nie von der Einstellung, sondern vom Symbol.
 */
class DisplayAlarmNeedTest {

    private val aus = AppSettings.DEFAULT.copy(
        persistentNotification = false,
        countdownMode = AppSettings.COUNTDOWN_OFF,
    )

    @Test fun ohneJedeRestzeitanzeigeKeineKette() {
        assertFalse(aus.needsDisplayStepAlarms())
    }

    @Test fun stufenBrauchenDieKetteSchonWegenDesWidgets() {
        assertTrue(aus.copy(countdownMode = AppSettings.PRECISION_STEPS).needsDisplayStepAlarms())
    }

    @Test fun genauOhneDauerbenachrichtigungBrauchtSieNicht() {
        // Das Widget zeichnet EXACT ohne Zutun der App; ein Symbol hat es
        // nicht, und ohne Dauerbenachrichtigung gibt es auch sonst keins.
        assertFalse(aus.copy(countdownMode = AppSettings.PRECISION_EXACT).needsDisplayStepAlarms())
    }

    @Test fun benachrichtigungMitStufenBrauchtDieKette() {
        assertTrue(
            aus.copy(
                persistentNotification = true,
                countdownMode = AppSettings.PRECISION_STEPS,
            ).needsDisplayStepAlarms(),
        )
    }

    @Test fun benachrichtigungMitGenauerAnzeigeBrauchtDieKetteEbenfalls() {
        // Ohne sie friere das Statusleisten-Symbol zwischen zwei Gebeten ein.
        assertTrue(
            aus.copy(
                persistentNotification = true,
                countdownMode = AppSettings.PRECISION_EXACT,
            ).needsDisplayStepAlarms(),
        )
    }

    @Test fun abgeschalteteBenachrichtigungZaehltNichtMit() {
        // EXACT kostet ohne Dauerbenachrichtigung nichts: das Widget kommt
        // damit allein zurecht.
        assertFalse(
            aus.copy(
                persistentNotification = false,
                countdownMode = AppSettings.PRECISION_EXACT,
            ).needsDisplayStepAlarms(),
        )
        assertFalse(
            aus.copy(
                persistentNotification = false,
                countdownMode = AppSettings.PRECISION_EXACT,
            ).notificationNeedsStepAlarms(),
        )
        assertFalse(
            aus.copy(
                persistentNotification = false,
                countdownMode = AppSettings.PRECISION_STEPS,
            ).notificationNeedsStepAlarms(),
        )
    }

    @Test fun benachrichtigungOhneCountdownBrauchtKeineKette() {
        // Der Werkszustand, sobald jemand nur die Dauerbenachrichtigung
        // einschaltet: der Countdown steht ab Werk auf OFF. Ohne diesen
        // Test bliebe eine Bedingung, die schlicht `persistentNotification`
        // prueft, unentdeckt — und der Nutzer bekaeme ~20 Weckvorgaenge je
        // Gebetsintervall fuer eine voellig statische Zeile.
        assertFalse(
            aus.copy(
                persistentNotification = true,
                countdownMode = AppSettings.COUNTDOWN_OFF,
            ).needsDisplayStepAlarms(),
        )
    }

    // --- Die beiden Teil-Praedikate einzeln ---
    //
    // `scheduleDisplayStep` fragt nicht nur, OB die Kette laeuft, sondern
    // auch, WELCHE Ziele Grenzen bekommen — und benutzt dafuer genau diese
    // zwei. Waeren sie dort noch einmal ausgeschrieben, koennten die beiden
    // Stellen auseinanderlaufen: die Kette liefe, das Ziel fehlte,
    // `boundaries` bliebe leer, der Alarm wuerde abbestellt, und das Symbol
    // froere zwischen zwei Gebeten ein — ohne dass ein Test es merkt.

    @Test fun dasWidgetBrauchtDieKetteNurFuerStufen() {
        assertTrue(aus.copy(countdownMode = AppSettings.PRECISION_STEPS).widgetNeedsStepAlarms())
        // EXACT zeichnet der Systemzaehler; das Widget hat kein Symbol.
        assertFalse(aus.copy(countdownMode = AppSettings.PRECISION_EXACT).widgetNeedsStepAlarms())
        assertFalse(aus.widgetNeedsStepAlarms())
        // Die Dauerbenachrichtigung aendert am Widget nichts.
        assertFalse(
            aus.copy(
                persistentNotification = true,
                countdownMode = AppSettings.PRECISION_EXACT,
            ).widgetNeedsStepAlarms(),
        )
    }

    @Test fun dieBenachrichtigungBrauchtSieInBeidenModi() {
        val an = aus.copy(persistentNotification = true)
        assertTrue(an.copy(countdownMode = AppSettings.PRECISION_STEPS).notificationNeedsStepAlarms())
        // Der Unterschied zum Widget: hier haengt das Symbol dran.
        assertTrue(an.copy(countdownMode = AppSettings.PRECISION_EXACT).notificationNeedsStepAlarms())
        assertFalse(an.copy(countdownMode = AppSettings.COUNTDOWN_OFF).notificationNeedsStepAlarms())
        // Ohne Dauerbenachrichtigung gibt es kein Symbol, das stehenbleiben
        // koennte.
        assertFalse(
            aus.copy(
                persistentNotification = false,
                countdownMode = AppSettings.PRECISION_EXACT,
            ).notificationNeedsStepAlarms(),
        )
    }
}
