package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Zusammenfuehrung der vier alten Regler zu `countdownMode`.
 *
 * Das ist die einzige Stelle im Umbau, die NUTZERDATEN anfasst: wer heute die
 * genaue Anzeige auf dem Sperrbildschirm hat, muss sie danach haben, und wer
 * nie eine Restzeit wollte, darf nach dem Update keine bekommen. Deshalb
 * steht die Rechnung als reine Funktion da (Muster: `regionToPref`) und nicht
 * im DataStore-Flow, wo ohne Robolectric kein Test hinkommt.
 */
class CountdownModeMigrationTest {

    /** Ein Bestandsnutzer, der alles aus hatte — der haeufigste Fall. */
    @Test fun alleAltenSchluesselAusErgibtAus() {
        assertEquals(
            AppSettings.COUNTDOWN_OFF,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = AppSettings.COUNTDOWN_OFF,
                widgetCountdown = AppSettings.COUNTDOWN_OFF,
                showCountdown = false,
                remainingPrecision = AppSettings.COUNTDOWN_OFF,
            ),
        )
    }

    @Test fun genaueBenachrichtigungBleibtGenau() {
        assertEquals(
            AppSettings.PRECISION_EXACT,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = AppSettings.PRECISION_EXACT,
                widgetCountdown = AppSettings.COUNTDOWN_OFF,
                showCountdown = false,
                remainingPrecision = AppSettings.PRECISION_STEPS,
            ),
        )
    }

    @Test fun widgetAufStufenGewinntWennDieBenachrichtigungAusIst() {
        assertEquals(
            AppSettings.PRECISION_STEPS,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = AppSettings.COUNTDOWN_OFF,
                widgetCountdown = AppSettings.PRECISION_STEPS,
                showCountdown = false,
                remainingPrecision = AppSettings.PRECISION_STEPS,
            ),
        )
    }

    /**
     * Der teuerste Fehler, den diese Migration machen koennte:
     * `remainingPrecision` stand ab Werk auf STEPS und war damit auch bei
     * jedem gesetzt, der die Restzeit nie eingeschaltet hat. Wer den Wert
     * allein auswertet, schaltet all diesen Leuten die Anzeige ein — samt
     * rund 20 Weckvorgaengen je Gebetsintervall.
     */
    @Test fun werkseinstellungStufenOhneEingeschalteteAnzeigeBleibtAus() {
        assertEquals(
            AppSettings.COUNTDOWN_OFF,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = AppSettings.COUNTDOWN_OFF,
                widgetCountdown = AppSettings.COUNTDOWN_OFF,
                showCountdown = false,
                remainingPrecision = AppSettings.PRECISION_STEPS,
            ),
        )
    }

    @Test fun alteGlobaleAnzeigeMitGenauZaehltNurWennSieAnWar() {
        assertEquals(
            AppSettings.PRECISION_EXACT,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = null,
                widgetCountdown = null,
                showCountdown = true,
                remainingPrecision = AppSettings.PRECISION_EXACT,
            ),
        )
    }

    /**
     * Widerspruch zwischen zwei Flaechen: die Reihenfolge entscheidet, und
     * dieser Test haelt sie fest. Die Benachrichtigung kommt zuerst, weil sie
     * die sichtbarste Flaeche ist.
     */
    @Test fun beiWiderspruchGewinntDieBenachrichtigung() {
        assertEquals(
            AppSettings.PRECISION_STEPS,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = AppSettings.PRECISION_STEPS,
                widgetCountdown = AppSettings.PRECISION_EXACT,
                showCountdown = true,
                remainingPrecision = AppSettings.PRECISION_EXACT,
            ),
        )
    }

    /** Frische Installation: kein einziger alter Schluessel ist gesetzt. */
    @Test fun frischeInstallationBeginntAus() {
        assertEquals(
            AppSettings.COUNTDOWN_OFF,
            countdownModeFromPrefs(
                migrated = false,
                stored = null,
                notificationCountdown = null,
                widgetCountdown = null,
                showCountdown = null,
                remainingPrecision = null,
            ),
        )
    }

    /**
     * Ein bereits migrierter Stand wird nicht erneut migriert: sonst
     * ueberschriebe ein liegengebliebener alter Schluessel die Wahl, die der
     * Nutzer nach dem Update getroffen hat.
     */
    @Test fun einMigrierterStandBleibtUnangetastet() {
        assertEquals(
            AppSettings.COUNTDOWN_OFF,
            countdownModeFromPrefs(
                migrated = true,
                stored = AppSettings.COUNTDOWN_OFF,
                notificationCountdown = AppSettings.PRECISION_EXACT,
                widgetCountdown = AppSettings.PRECISION_EXACT,
                showCountdown = true,
                remainingPrecision = AppSettings.PRECISION_EXACT,
            ),
        )
        assertEquals(
            AppSettings.PRECISION_EXACT,
            countdownModeFromPrefs(
                migrated = true,
                stored = AppSettings.PRECISION_EXACT,
                notificationCountdown = null,
                widgetCountdown = null,
                showCountdown = null,
                remainingPrecision = null,
            ),
        )
    }

    /** Migriert, aber der neue Schluessel fehlt (abgebrochener Schreibvorgang):
     *  lieber aus als eine Anzeige, die niemand bestellt hat. */
    @Test fun migriertOhneWertIstAus() {
        assertEquals(
            AppSettings.COUNTDOWN_OFF,
            countdownModeFromPrefs(
                migrated = true,
                stored = null,
                notificationCountdown = AppSettings.PRECISION_STEPS,
                widgetCountdown = null,
                showCountdown = null,
                remainingPrecision = null,
            ),
        )
    }
}
