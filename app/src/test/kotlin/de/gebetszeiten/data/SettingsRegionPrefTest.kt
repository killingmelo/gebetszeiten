package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Charakterisiert die Naht, über die `AppSettings.region` in den DataStore
 * geht und wieder herauskommt (`regionToPref`/`regionFromPref`). Das
 * Repository selbst braucht einen Android-Context und bleibt hier außen vor;
 * die beiden reinen Funktionen sind genau dafür ausgelagert.
 *
 * Kernaussage: „unbekannt" hat im DataStore zwei Erscheinungsformen — ein
 * FEHLENDER Schlüssel (Bestandsnutzer, keine Migration) und ein LEERER Wert
 * (von uns geschrieben, weil DataStore kein null ablegt). Beide müssen als
 * derselbe Zustand `null` zurückkommen.
 */
class SettingsRegionPrefTest {

    @Test fun `Rundlauf erhaelt eine bekannte Region unveraendert`() {
        assertEquals("BAYERN", regionFromPref(regionToPref("BAYERN")))
        // Sonderzeichen und Leerzeichen INNERHALB des Namens bleiben stehen.
        assertEquals("K. MARAŞ", regionFromPref(regionToPref("K. MARAŞ")))
        // Gemischte Gross-/Kleinschreibung ist die ECHTE Form aus cities500
        // (admin1, z. B. „Yalova"). Ohne einen solchen Wert bliebe ein
        // versehentliches .uppercase() oder .trim() in der Naht unentdeckt —
        // die Regionen oben sind ohnehin schon durchgaengig gross.
        assertEquals("Yalova", regionFromPref(regionToPref("Yalova")))
        assertEquals("Bad Kissingen", regionFromPref(regionToPref("Bad Kissingen")))
    }

    @Test fun `unbekannte Region wird als leerer String geschrieben und kommt als null zurueck`() {
        assertEquals("", regionToPref(null))
        assertNull(regionFromPref(regionToPref(null)))
    }

    @Test fun `fehlender Schluessel ergibt null`() {
        // prefs[Keys.REGION] liefert null, wenn der Schlüssel nie geschrieben
        // wurde — der Zustand jedes Bestandsnutzers nach dem Update.
        assertNull(regionFromPref(null))
    }

    @Test fun `nur Leerraum zaehlt als unbekannt`() {
        assertNull(regionFromPref(""))
        assertNull(regionFromPref("   "))
        assertNull(regionFromPref("\t"))
    }
}
