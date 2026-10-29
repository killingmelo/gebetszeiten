package de.gebetszeiten.prayer

import de.gebetszeiten.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verdrahtung „Wert dieses Gebets“, quelltextlesend wie
 * `NotificationStyleWiringTest` und `SettingsLayoutTest` (kein Robolectric,
 * Compose wird nicht gerendert). Geprueft wird: der Satz erscheint nur in der
 * Meldung zur Gebetszeit (nicht im Vorlauf, nicht in der Daueranzeige) und nur
 * bei eingeschalteter Einstellung; die Einstellung wird gespeichert und steht
 * unter „Erinnerungen“; „Zeiten verstehen“ liest dieselben Texte.
 * Arbeitsverzeichnis ist das Modul `app`.
 */
class PrayerVirtueWiringTest {

    private fun quelltext(pfad: String): String {
        val datei = File("src/main/kotlin/de/gebetszeiten/$pfad")
        assertTrue("${datei.absolutePath} fehlt", datei.isFile)
        return datei.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    /** Der Rumpf ab der ersten `{` nach [marke], per Klammerzaehlung. */
    private fun rumpf(text: String, marke: String): String {
        val start = text.indexOf(marke)
        assertTrue("$marke nicht gefunden", start >= 0)
        val offen = text.indexOf('{', start)
        var tiefe = 0
        for (i in offen until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(offen, i)
                }
            }
        }
        throw AssertionError("$marke ist nicht geschlossen")
    }

    @Test
    fun `Meldung zur Gebetszeit zeigt den Satz nur bei eingeschalteter Einstellung`() {
        val notifier = quelltext("notify/PrayerNotifier.kt")
        assertEquals("virtueFor( genau einmal erwartet", 1, Regex("""\bvirtueFor\(""").findAll(notifier).count())
        val eintritt = rumpf(notifier, "fun notifyPrayer(")
        assertTrue(
            "notifyPrayer fragt virtueFor( nicht hinter showVirtue ab",
            Regex("""if \(showVirtue\)[^\n]*virtueFor\(""").containsMatchIn(eintritt),
        )
        assertTrue("notifyPrayer hat keinen Parameter showVirtue", notifier.contains("showVirtue: Boolean"))
        listOf("fun notifyPre(", "fun updateOngoing(").forEach { marke ->
            assertFalse("$marke zeigt den Wert-Satz", rumpf(notifier, marke).contains("virtue", ignoreCase = true))
        }
        val receiver = quelltext("alarm/PrayerAlarmReceiver.kt")
        assertTrue(
            "Receiver reicht showPrayerVirtue nicht weiter",
            receiver.contains("showVirtue = settings.showPrayerVirtue"),
        )
    }

    @Test
    fun `Einstellung ist ab Werk an und wird gespeichert`() {
        assertTrue(AppSettings(latitude = 0.0, longitude = 0.0, city = "").showPrayerVirtue)
        assertTrue(AppSettings.DEFAULT.showPrayerVirtue)
        val repo = quelltext("data/SettingsRepository.kt")
        assertTrue(repo.contains("""booleanPreferencesKey("show_prayer_virtue")"""))
        assertTrue(repo.contains("prefs[Keys.SHOW_PRAYER_VIRTUE] ?: AppSettings.DEFAULT.showPrayerVirtue"))
        assertTrue(repo.contains("prefs[Keys.SHOW_PRAYER_VIRTUE] = value.showPrayerVirtue"))
    }

    @Test
    fun `Schalter steht unter Erinnerungen mit Hinweis`() {
        val sheet = quelltext("ui/SettingsSheet.kt")
        val erinnerungen = rumpf(sheet, "SettingsSection(stringResource(R.string.settings_section_reminders))")
        assertTrue(
            "Schalter fehlt unter Erinnerungen",
            erinnerungen.contains(
                "ToggleRow(stringResource(R.string.settings_prayer_virtue), settings.showPrayerVirtue)",
            ),
        )
        assertTrue("Hinweis fehlt", erinnerungen.contains("R.string.settings_prayer_virtue_hint"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(
            strings.contains("""<string name="settings_prayer_virtue">Wert des Gebets in der Gebets-Meldung</string>"""),
        )
        assertTrue(
            strings.contains(
                """<string name="settings_prayer_virtue_hint">Ein kurzer Satz aus dem Hadith, """ +
                    """sichtbar beim Aufklappen der Meldung zur Gebetszeit.</string>""",
            ),
        )
    }

    @Test
    fun `Zeiten verstehen zeigt alle Texte unter eigener Ueberschrift`() {
        val screen = quelltext("ui/verstehen/VerstehenScreen.kt")
        assertTrue("PRAYER_VIRTUES wird nicht gelesen", screen.contains("PRAYER_VIRTUES"))
        assertTrue("Ueberschrift fehlt", screen.contains("\"Wert dieses Gebets\""))
        val block = rumpf(screen, "private fun VirtueBlock(")
        assertTrue("Ueberschrift ist keine heading()", block.contains("heading()"))
        assertTrue("Ueberschrift nicht in labelSmall", block.contains("typography.labelSmall"))
        assertTrue("Texte nicht in bodyMedium", block.contains("typography.bodyMedium"))
    }
}
