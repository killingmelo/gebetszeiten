package de.gebetszeiten.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung der neuen Hauptansicht „Heute“ und des vierten Reiters,
 * quelltextlesend (Compose wird hier nicht getestet, kein Robolectric).
 *
 * Gewacht wird über das, was ein Umbau still verlieren könnte: die Ansicht
 * holt Titel und Liste aus [de.gebetszeiten.prayer.heroState] und
 * [de.gebetszeiten.prayer.dayRows] statt aus eigener Zeitlogik, Nebenzeilen
 * führen zu „Zeiten verstehen“ statt in einen Dialog, Zeitspannen laufen über
 * `timeRange` und es steht kein Halbgeviertstrich im Text.
 */
class HeuteWiringTest {

    private val activity = File("src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt")
    private val heute = File("src/main/kotlin/de/gebetszeiten/ui/HeuteScreen.kt")

    private fun ohneKommentare(f: File): String {
        assertTrue("${f.absolutePath} fehlt", f.isFile)
        return f.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
    }

    @Test fun `MainActivity kennt den vierten Reiter und keinen Erklaerungsdialog mehr`() {
        val text = ohneKommentare(activity)
        assertTrue("Tab.VERSTEHEN fehlt", text.contains("Tab.VERSTEHEN"))
        assertTrue("VerstehenScreen wird nicht angezeigt", text.contains("VerstehenScreen("))
        assertTrue("HeuteScreen wird nicht angezeigt", text.contains("HeuteScreen("))
        assertFalse("der Karāha-AlertDialog ist noch da", text.contains("AlertDialog("))
    }

    @Test fun `Antippen einer Nebenzeile springt in Zeiten verstehen`() {
        val text = ohneKommentare(activity).replace(Regex("""\s+"""), "")
        assertTrue(
            "onExplain setzt den Abschnitt nicht und wechselt nicht auf Tab.VERSTEHEN",
            text.contains("onExplain={explain=it;tab=Tab.VERSTEHEN}"),
        )
    }

    @Test fun `HeuteScreen nutzt die gemeinsame Zeitlogik`() {
        val text = ohneKommentare(heute)
        listOf("heroState(", "dayRows(", "onExplain(", "photoRes(", "timeRange(").forEach {
            assertTrue("HeuteScreen.kt enthaelt $it nicht", text.contains(it))
        }
    }

    @Test fun `noch steht auf Heute unabhaengig von der Restzeit-Einstellung`() {
        // Die Einstellung steht ab Werk auf „aus“; sie gilt nur für Widget,
        // Sperrbildschirm und Statusleiste. Die Hauptansicht beantwortet
        // „wie viel Zeit habe ich noch“ immer.
        val text = ohneKommentare(heute)
        assertFalse("HeuteScreen.kt liest countdownMode", text.contains("countdownMode"))
        assertFalse("HeuteScreen.kt liest COUNTDOWN_OFF", text.contains("COUNTDOWN_OFF"))
    }

    @Test fun `kein Gedanken- oder Halbgeviertstrich in HeuteScreen`() {
        val text = ohneKommentare(heute)
        assertFalse("HeuteScreen.kt enthaelt –", text.contains("–"))
        assertFalse("HeuteScreen.kt enthaelt —", text.contains("—"))
    }

    @Test fun `kleiner Bildschirm, Mitternacht, Ǧamāʿa und DMG-Titel sind verdrahtet`() {
        val text = ohneKommentare(heute)
        listOf(
            "compactPhotoHeightDp(configuration.screenWidthDp, screenHeightDp)",
            "heroGapDp(screenHeightDp)",
            "rolledSelectedDate(",
            "prayerStatusRes(",
            "R.string.status_jamaa_derived",
            "dmgTitle(stringResource(momentLabelRes(hero.moment)))",
        ).forEach { assertTrue("HeuteScreen.kt enthaelt $it nicht", text.contains(it)) }
        assertTrue("Foto ohne Hoehenwahl", text.contains("Modifier.height(heightDp.dp)"))
    }
}
