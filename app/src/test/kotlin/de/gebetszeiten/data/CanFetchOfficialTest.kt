package de.gebetszeiten.data

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
 *
 * Seit Aufgabe 15 entfaellt die zweite Bedingung: der Notausgang
 * (`calculationFillsGaps`) heisst nicht mehr "amtliche Zeiten ignorieren",
 * sondern "Luecken fuellen" — ein Abruf bleibt also sinnvoll, egal wie er
 * steht. `canFetchOfficial()` haengt jetzt ausschliesslich an [AppSettings.useOnline].
 */
class CanFetchOfficialTest {

    private fun settings(useOnline: Boolean, calculationFillsGaps: Boolean): AppSettings =
        AppSettings.DEFAULT.copy(useOnline = useOnline, calculationFillsGaps = calculationFillsGaps)

    @Test fun onlineUndNichtBerechnetErgibtWahr() {
        assertTrue(settings(useOnline = true, calculationFillsGaps = false).canFetchOfficial())
    }

    @Test fun onlineUndNotausgangAnErgibtEbenfallsWahr() {
        // Das ist die Verhaltensaenderung aus Aufgabe 15: wer den Notausgang
        // eingeschaltet hat, will amtliche Zeiten weiterhin - ein Abruf ist
        // fuer ihn genauso sinnvoll wie fuer jeden anderen Online-Nutzer.
        assertTrue(settings(useOnline = true, calculationFillsGaps = true).canFetchOfficial())
    }

    @Test fun nichtOnlineErgibtFalschUnabhaengigVonBerechnung() {
        assertFalse(settings(useOnline = false, calculationFillsGaps = false).canFetchOfficial())
        assertFalse(settings(useOnline = false, calculationFillsGaps = true).canFetchOfficial())
    }
}
