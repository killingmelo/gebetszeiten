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
 */
class DisplayAlarmNeedTest {

    private val aus = AppSettings.DEFAULT.copy(
        persistentNotification = false,
        widgetCountdown = AppSettings.COUNTDOWN_OFF,
        notificationCountdown = AppSettings.COUNTDOWN_OFF,
    )

    @Test fun ohneJedeRestzeitanzeigeKeineKette() {
        assertFalse(aus.needsDisplayStepAlarms())
    }

    @Test fun widgetMitStufenBrauchtDieKette() {
        assertTrue(aus.copy(widgetCountdown = AppSettings.PRECISION_STEPS).needsDisplayStepAlarms())
    }

    @Test fun widgetMitGenauerAnzeigeBrauchtSieNicht() {
        // Das Widget zeichnet EXACT ohne Zutun der App; ein Symbol hat es nicht.
        assertFalse(aus.copy(widgetCountdown = AppSettings.PRECISION_EXACT).needsDisplayStepAlarms())
    }

    @Test fun benachrichtigungMitStufenBrauchtDieKette() {
        assertTrue(
            aus.copy(
                persistentNotification = true,
                notificationCountdown = AppSettings.PRECISION_STEPS,
            ).needsDisplayStepAlarms(),
        )
    }

    @Test fun benachrichtigungMitGenauerAnzeigeBrauchtDieKetteEbenfalls() {
        // Ohne sie friere das Statusleisten-Symbol zwischen zwei Gebeten ein.
        assertTrue(
            aus.copy(
                persistentNotification = true,
                notificationCountdown = AppSettings.PRECISION_EXACT,
            ).needsDisplayStepAlarms(),
        )
    }

    @Test fun abgeschalteteBenachrichtigungZaehltNichtMit() {
        assertFalse(
            aus.copy(
                persistentNotification = false,
                notificationCountdown = AppSettings.PRECISION_EXACT,
            ).needsDisplayStepAlarms(),
        )
        assertFalse(
            aus.copy(
                persistentNotification = false,
                notificationCountdown = AppSettings.PRECISION_STEPS,
            ).needsDisplayStepAlarms(),
        )
    }
}
