package de.gebetszeiten.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der alte Schalter hiess „immer rechnen", der neue heisst „Luecken fuellen".
 * Wer ihn an hatte, bekommt kuenftig amtliche Zeiten, wo welche da sind - eine
 * Verhaltensaenderung, und die gehoert in eine benannte Migration statt in die
 * stille Umdeutung desselben Schluessels.
 */
class CalculationFallbackMigrationTest {

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
