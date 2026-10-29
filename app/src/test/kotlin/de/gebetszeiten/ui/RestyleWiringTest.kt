package de.gebetszeiten.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Monat, Qibla, Einstellungen und Ersteinrichtung im Stil von „Heute“ —
 * quelltextlesend wie [SettingsLayoutTest], weil Compose hier nicht getestet
 * wird. Geprüft wird, was beim nächsten Umbau unbemerkt zurückkäme: die
 * Farbfläche für „heute“, das Fehlerrot am Kompass, Karten statt Haarlinien,
 * der Strich für fehlende Zeiten und der fehlende Abschnitt „Über“.
 */
class RestyleWiringTest {

    private fun quelltext(name: String): String {
        val f = File("src/main/kotlin/de/gebetszeiten/ui/$name")
        assertTrue("${f.absolutePath} fehlt", f.isFile)
        return f.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    private fun stringWert(name: String): String =
        File("src/main/res/values/strings.xml").readText()
            .substringAfter("""<string name="$name">""", missingDelimiterValue = "")
            .substringBefore("</string>")

    /** Der Rumpf der Funktion [name] bis zur nächsten Funktionsdeklaration. */
    private fun funktion(text: String, name: String): String {
        val start = text.indexOf("fun $name(")
        assertTrue("$name nicht gefunden", start >= 0)
        val ende = text.indexOf("\nfun ", start + 1).let { a ->
            listOf(a, text.indexOf("\nprivate fun ", start + 1), text.indexOf("\ninternal fun ", start + 1))
                .filter { it > 0 }.minOrNull() ?: text.length
        }
        return text.substring(start, ende)
    }

    @Test fun `Monat-Datum bricht bei grosser Schrift nicht um`() {
        val text = quelltext("MonatScreen.kt").replace(Regex("""\s+"""), " ")
        assertTrue(
            "Datumszelle ohne gedeckelte Schrift und eine Zeile",
            Regex("""dateLabel\(row\.date\), Modifier\.weight\(DATE_WEIGHT\),.*?cappedSp\(14, TIME_SCALE_CAP, fontScale\).*?maxLines = 1, softWrap = false""")
                .containsMatchIn(text),
        )
    }

    @Test fun `Monat markiert heute ohne Farbflaeche`() {
        val text = quelltext("MonatScreen.kt")
        assertFalse("MonatScreen.kt nutzt noch primaryContainer", text.contains("primaryContainer"))
        assertTrue("MonatScreen.kt zieht keine Haarlinien", text.contains("hairlineBelow("))
    }

    @Test fun `Monat zeigt fuer fehlende Zeiten keinen Strich`() {
        val text = quelltext("MonatScreen.kt")
        assertFalse("MonatScreen.kt zeigt noch \"-\" für Tage ohne Zeit", text.contains("\"-\""))
        assertTrue(
            "die Zeilenbeschreibung nennt fehlende Zeiten nicht (month_no_time)",
            text.contains("R.string.month_no_time"),
        )
    }

    @Test fun `Qibla ohne Fehlerrot`() {
        val text = quelltext("QiblaScreen.kt")
        assertFalse("QiblaScreen.kt nutzt noch colorScheme.error", text.contains("colorScheme.error"))
        assertTrue("die Nordmarke ist nicht gold", text.contains("t.gold"))
    }

    @Test fun `Einstellungsabschnitte sind keine Karten`() {
        val text = quelltext("SettingsSheet.kt")
        val sektion = funktion(text, "SettingsSection")
        assertFalse("SettingsSection baut noch eine Card", Regex("""\bCard\(""").containsMatchIn(sektion))
        assertFalse("SettingsSheet.kt importiert noch Card", text.contains("import androidx.compose.material3.Card\n"))
        assertTrue("SettingsSection setzt keine Haarlinie", sektion.contains("hairline"))
    }

    @Test fun `Ueber nennt Fotografen und Schriftlizenzen`() {
        val text = quelltext("SettingsSheet.kt")
        assertTrue("SettingsSheet.kt zeigt PHOTO_CREDITS nicht", text.contains("PHOTO_CREDITS"))
        assertTrue("SettingsSheet.kt nutzt photo_credit nicht", text.contains("R.string.photo_credit"))
        assertTrue("kein Abschnitt „Über“", text.contains("R.string.settings_section_about"))
        listOf("Noto Serif Display", "Inter", "Noto Naskh Arabic").forEach {
            assertTrue("Schrift $it fehlt im Abschnitt „Über“", text.contains("\"$it\""))
        }
        assertTrue(
            "die Lizenz ist nicht genannt",
            stringWert("about_font_license").contains("SIL Open Font License 1.1"),
        )
    }

    @Test fun `der Restzeit-Regler sagt, wofuer er gilt`() {
        // In der App steht die Restzeit immer; der Regler gilt nur noch für
        // Widget, Sperrbildschirm und Statusleiste.
        val wert = stringWert("settings_remaining_all")
        assertFalse("settings_remaining_all behauptet noch „Überall“", wert.contains("Überall"))
        assertTrue("settings_remaining_all nennt das Widget nicht: $wert", wert.contains("Widget"))
    }

    @Test fun `alle Auswahl-Chips laufen ueber SelectChip`() {
        listOf("SettingsSheet.kt", "Onboarding.kt", "OngoingPreviewUi.kt").forEach {
            assertFalse("$it baut noch einen eigenen FilterChip", quelltext(it).contains("FilterChip("))
        }
        val chip = quelltext("SelectChip.kt")
        assertTrue("der gewählte Chip trägt kein Häkchen", chip.contains("R.drawable.ic_check"))
        assertTrue("der gewählte Chip hat keinen Akzentrand", chip.contains("selectedBorderColor = t.accent"))
    }

    @Test fun `Nord-Buchstabe nicht in Gold`() {
        // Gold erreicht als Text nur 3,5:1; nur die Marke darf gold sein.
        assertFalse(quelltext("QiblaScreen.kt").contains("northLetter = t.gold"))
    }

    @Test fun `der letzte Abschnitt hat keine Haarlinie`() {
        assertTrue(
            quelltext("SettingsSheet.kt")
                .contains("SettingsSection(stringResource(R.string.settings_section_about), divider = false)"),
        )
    }

    @Test fun `das Einstellungsblatt liegt auf dem Grund`() {
        val text = quelltext("MainActivity.kt").replace(Regex("""\s+"""), "")
        val blatt = text.substringAfter("ModalBottomSheet(", missingDelimiterValue = "").substringBefore("LocationSettings(")
        assertTrue("ModalBottomSheet hat keinen eigenen Hintergrund", blatt.contains("containerColor="))
        assertTrue("skipPartiallyExpanded ging verloren", blatt.contains("skipPartiallyExpanded=true"))
    }
}
