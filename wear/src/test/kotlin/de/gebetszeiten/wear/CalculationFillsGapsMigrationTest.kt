package de.gebetszeiten.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der alte Schalter [WearSettings] hiess `useCalculated` und bedeutete
 * "immer rechnen, amtliche Zeiten ignorieren". Der neue heisst
 * `calculationFillsGaps` und bedeutet "rechnen, NUR wo amtliche Zeiten
 * fehlen" - dieselbe Bedeutungsaenderung wie am Telefon
 * (`calculationFillsGapsFromPrefs` in `SettingsRepository.kt`), deshalb
 * dieselbe Wahrheitstabelle.
 */
class CalculationFillsGapsMigrationTest {

    @Test fun `wer immer rechnen wollte, behaelt den Notausgang`() {
        assertTrue(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = true))
    }

    @Test fun `wer amtliche Zeiten wollte, bekommt keinen Notausgang`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = false))
    }

    @Test fun `frische Installation ist ab Werk aus`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = null))
    }

    @Test fun `nach der Migration gilt der neue Schluessel, nicht der alte`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = true, stored = false, legacyUseCalculated = true))
        assertTrue(calculationFillsGapsFromPrefs(migrated = true, stored = true, legacyUseCalculated = false))
    }
}
