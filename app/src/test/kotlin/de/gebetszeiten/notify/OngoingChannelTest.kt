package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Restzeit in der Statusleiste: Android zeigt Symbole stiller Meldungen
 * (Wichtigkeit LOW) dort nicht an. Mit Restzeit braucht die Dauerbenachrichtigung
 * deshalb einen eigenen Kanal auf DEFAULT; ohne Restzeit bleibt sie still.
 */
class OngoingChannelTest {
    @Test fun `mit Restzeit ein anderer Kanal als ohne`() {
        assertNotEquals(PrayerNotifier.ongoingChannelId(countdown = true), PrayerNotifier.ongoingChannelId(countdown = false))
        // Der alte stille Kanal bleibt für Nutzer ohne Restzeit unverändert.
        assertEquals("next_prayer", PrayerNotifier.ongoingChannelId(countdown = false))
    }

    @Test fun `der Restzeit-Kanal ist sichtbar, aber ohne Ton`() {
        val src = File("src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt").readText()
        val block = src.substringAfter("val ongoingStatus = NotificationChannel(").substringBefore("createNotificationChannel(ongoingStatus)")
        assertTrue("Restzeit-Kanal nicht auf DEFAULT", block.contains("IMPORTANCE_DEFAULT"))
        assertTrue("Restzeit-Kanal mit Ton", block.contains("setSound(null, null)"))
        assertTrue("Restzeit-Kanal vibriert", block.contains("enableVibration(false)"))
        // Die Meldung selbst bleibt still und meldet sich nur einmal.
        assertTrue(src.contains("Builder(context, ongoingChannelId(countdown))"))
    }
}
