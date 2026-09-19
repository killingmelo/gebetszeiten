package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Warum gerade nichts ankommt — die Entscheidung, die `canPost` frueher nicht
 * treffen konnte.
 *
 * `canPost` fragte nur `checkSelfPermission`. Wer die Benachrichtigungen der
 * App im System abschaltet oder einen Kanal auf „Keine" stellt, behaelt die
 * Berechtigung — `notify()` verpufft trotzdem. Das war doppelt teuer: der
 * Nutzer bekam nichts UND erfuhr nichts, und der Merker `pause_notice_shown`
 * behauptete hinterher „gemeldet", sodass die Meldung nie wiederkam.
 */
class NotificationBlockTest {

    private fun block(
        hasPermission: Boolean = true,
        appEnabled: Boolean = true,
        ongoing: Int = 2,
        entry: Int = 2,
    ) = notificationBlock(hasPermission, appEnabled, ongoing, entry)

    @Test fun `alles in Ordnung`() {
        assertEquals(NotificationBlock.NONE, block())
    }

    @Test fun `ohne Erlaubnis`() {
        assertEquals(NotificationBlock.NO_PERMISSION, block(hasPermission = false))
    }

    @Test fun `App im System abgeschaltet`() {
        assertEquals(NotificationBlock.APP_DISABLED, block(appEnabled = false))
    }

    @Test fun `vor Android 13 sieht checkSelfPermission den Ausfall nicht`() {
        // DER Fall, wegen dem es diese Funktion gibt. Vor Android 13 gibt es
        // keine Laufzeit-Erlaubnis, `hasPermission` ist dort immer true — und
        // der alte `canPost` sagte deshalb „geht", waehrend der Nutzer die
        // Benachrichtigungen laengst abgeschaltet hatte.
        assertEquals(NotificationBlock.APP_DISABLED, block(hasPermission = true, appEnabled = false))
    }

    @Test fun `Dauerzeilen-Kanal auf Keine`() {
        assertEquals(NotificationBlock.CHANNEL_DISABLED, block(ongoing = 0))
    }

    @Test fun `auch der Eintritts-Kanal zaehlt`() {
        // Sonst bliebe „die Dauerzeile steht ja" eine Ausrede dafuer, dass
        // die Gebets-Meldungen ausfallen.
        assertEquals(NotificationBlock.CHANNEL_DISABLED, block(entry = 0))
    }

    @Test fun `die Reihenfolge ist die Behebungsreihenfolge`() {
        // Wer zwei Huerden hat, bekommt die genannt, die er zuerst wegraeumen
        // muss: ohne Erlaubnis nuetzt der schoenste Kanal nichts.
        assertEquals(
            NotificationBlock.NO_PERMISSION,
            block(hasPermission = false, appEnabled = false, ongoing = 0, entry = 0),
        )
        assertEquals(
            NotificationBlock.APP_DISABLED,
            block(appEnabled = false, ongoing = 0),
        )
    }

    @Test fun `ein noch nicht angelegter Kanal ist kein Ausfall`() {
        // `IMPORTANCE_UNSPECIFIED` ist -1000 und kommt vor dem ersten
        // createNotificationChannel vor. Wuerde es als „abgeschaltet" gelten,
        // meldete die App beim allerersten Start faelschlich einen Ausfall.
        assertEquals(NotificationBlock.NONE, block(ongoing = -1000, entry = -1000))
    }

    @Test fun `jeder Hinderungsgrund hat einen Satz und einen Knopf, NONE keines`() {
        // Ein Zweig ohne Wortlaut waere genau der stille Ausfall, den dieser
        // Typ verhindern soll.
        NotificationBlock.values().forEach { b ->
            if (b == NotificationBlock.NONE) {
                assertNull(notificationBlockText(b))
                assertNull(notificationBlockAction(b))
            } else {
                assertNotNull("$b ohne Satz", notificationBlockText(b))
                assertNotNull("$b ohne Knopf", notificationBlockAction(b))
                assertTrue("$b hat einen leeren Satz", notificationBlockText(b)!!.isNotBlank())
            }
        }
    }

    @Test fun `nur bei fehlender Erlaubnis hilft der Systemdialog`() {
        // In den anderen Faellen IST die Erlaubnis erteilt — ein zweiter
        // Dialog kaeme gar nicht mehr, der Weg fuehrt in die
        // Systemeinstellungen. Der Knopf muss das unterscheiden.
        assertEquals("Erlauben", notificationBlockAction(NotificationBlock.NO_PERMISSION))
        assertEquals("Einstellungen öffnen", notificationBlockAction(NotificationBlock.APP_DISABLED))
        assertEquals("Einstellungen öffnen", notificationBlockAction(NotificationBlock.CHANNEL_DISABLED))
    }
}
