package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AppSettings.canFetchOfficial()` — die Konjunktion, die vorher als
 * `useOnline && !useCalculated` an drei/vier Stellen einzeln stand
 * (`SettingsSheet.canFetch`, dessen Knopf-Sichtbarkeit, `HeuteContent` und
 * `MonatScreen` in Aufgabe 11). Extrahiert, weil genau dieses
 * Wiederholungsmuster im offline-Flavor schon einmal ein vergessenes viertes
 * Mal hatte (siehe [UseOnlineFlavorClampTest]).
 */
class CanFetchOfficialTest {

    private fun settings(useOnline: Boolean, useCalculated: Boolean): AppSettings =
        AppSettings.DEFAULT.copy(useOnline = useOnline, useCalculated = useCalculated)

    @Test fun onlineUndNichtBerechnetErgibtWahr() {
        assertTrue(settings(useOnline = true, useCalculated = false).canFetchOfficial())
    }

    @Test fun onlineAberBerechnungErzwungenErgibtFalsch() {
        assertFalse(settings(useOnline = true, useCalculated = true).canFetchOfficial())
    }

    @Test fun nichtOnlineErgibtFalschUnabhaengigVonBerechnung() {
        assertFalse(settings(useOnline = false, useCalculated = false).canFetchOfficial())
        assertFalse(settings(useOnline = false, useCalculated = true).canFetchOfficial())
    }
}
