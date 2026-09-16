package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Flavor-Klemmung von `useOnline` (Muster: `countdownModeFromPrefs`).
 *
 * Vorbestehender Fehler, den die unabhaengige Pruefung von Aufgabe 10 belegt
 * hat: `SettingsRepository` korrigierte in seiner Migration nur ein
 * gespeichertes `false` auf den Flavor-Default, nicht ein gespeichertes
 * `true`. Ein Bestandsnutzer des Offline-Builds konnte also `use_online =
 * true` gespeichert haben — dort gibt es aber gar keinen Abrufmechanismus
 * (`OfficialTimesProvider.fetcher` liefert dort `null`). Die App zeigte
 * dann „Jetzt aktualisieren" und Abruf-Zeilen fuer einen Abruf, den es in
 * diesem Flavor nie gibt.
 *
 * Jeder Fall hier haelt fest: entfernt man die Klemmung (`&&
 * isOnlineFlavor`) und liefert nur `migratedUseOnline` zurueck, muss
 * mindestens einer dieser Tests sterben.
 */
class UseOnlineFlavorClampTest {

    /** Der eigentliche Fehlerfall: gespeichertes `true` im Offline-Flavor
     *  muss auf `false` geklemmt werden — unabhaengig davon, ob/wie migriert
     *  wurde. Ohne die Klemmung (reines `migratedUseOnline`) waere das hier
     *  `true` und der Test stuerbe. */
    @Test fun gespeichertesTrueImOfflineFlavorWirdFalse() {
        assertEquals(false, useOnlineFromPrefs(migratedUseOnline = true, isOnlineFlavor = false))
    }

    @Test fun gespeichertesFalseImOfflineFlavorBleibtFalse() {
        assertEquals(false, useOnlineFromPrefs(migratedUseOnline = false, isOnlineFlavor = false))
    }

    @Test fun gespeichertesTrueImOnlineFlavorBleibtTrue() {
        assertEquals(true, useOnlineFromPrefs(migratedUseOnline = true, isOnlineFlavor = true))
    }

    @Test fun gespeichertesFalseImOnlineFlavorBleibtFalse() {
        assertEquals(false, useOnlineFromPrefs(migratedUseOnline = false, isOnlineFlavor = true))
    }
}
