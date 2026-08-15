package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyanetPlacesTest {

    // Echte Werte aus der Datenpruefung: Diyanet-Standort SAKARYA liegt auf
    // Adapazari, Serdivan 2,1 km entfernt.
    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)
    private val akyazi = DiyanetPlace(9800, "AKYAZI", "SAKARYA", "TR", 40.685, 30.62222)
    private val places = listOf(sakarya, akyazi)

    @Test fun `parst gueltige Zeilen`() {
        val parsed = parseDiyanetPlaces(
            sequenceOf(
                "9807\tSAKARYA\tSAKARYA\tTR\t40.78056\t30.40333",
                "9800\tAKYAZI\tSAKARYA\tTR\t40.685\t30.62222",
            ),
        )
        assertEquals(listOf(sakarya, akyazi), parsed)
    }

    @Test fun `ueberspringt defekte Zeilen statt zu werfen`() {
        val parsed = parseDiyanetPlaces(
            sequenceOf(
                "",
                "zu\tkurz",
                "keineZahl\tX\tY\tTR\t1.0\t2.0",
                "9807\tSAKARYA\tSAKARYA\tTR\tkeineKoordinate\t30.4",
                "9807\tSAKARYA\tSAKARYA\tTR\t40.78056\t30.40333",
            ),
        )
        assertEquals(listOf(sakarya), parsed)
    }

    @Test fun `nearest findet Serdivan zu Sakarya`() {
        // Serdivan laut cities.tsv
        val hit = DiyanetPlaces.nearest(places, 40.77376, 30.38006)
        assertEquals(sakarya, hit)
    }

    @Test fun `distanceKm Serdivan nach Sakarya rund 2 km`() {
        val km = DiyanetPlaces.distanceKm(sakarya, 40.77376, 30.38006)
        assertTrue("gemessen $km km", km in 1.5..2.5)
    }

    @Test fun `nearest liefert null jenseits der Schwelle`() {
        // Wien: weit weg von beiden
        assertNull(DiyanetPlaces.nearest(places, 48.2082, 16.3738))
    }

    @Test fun `nearest respektiert eine engere Schwelle`() {
        // Serdivan liegt 2,1 km entfernt: bei 1 km kein Treffer, bei 3 km schon.
        assertNull(DiyanetPlaces.nearest(places, 40.77376, 30.38006, maxKm = 1.0))
        assertEquals(sakarya, DiyanetPlaces.nearest(places, 40.77376, 30.38006, maxKm = 3.0))
    }

    @Test fun `nearest zaehlt eine Distanz exakt an der Schwelle noch als Treffer (kleiner-gleich, nicht kleiner)`() {
        // Bisher wurden nur test-eigene Schwellen (1.0, 3.0) geprueft, nie die
        // tatsaechlich ausgelieferte Konstante (Standard-maxKm = 25.0). Ziel
        // ist speziell die Grenze selbst: eine Implementierung, die versehentlich
        // `< maxKm` statt `<= maxKm` prueft, wuerde einen Treffer GENAU an der
        // Schwelle faelschlich verwerfen.
        //
        // Ziel- und Kandidatenpunkt liegen auf demselben Meridian (gleiche
        // Laenge) — dafuer reduziert sich die haversine-Formel exakt auf den
        // Erdradius mal Breitenwinkel, ohne Naeherung. Die Distanz wird nicht
        // geschaetzt, sondern direkt ueber distanceKm() derselben Produktions-
        // formel ausgelesen, die nearest() intern verwendet — kein zweiter,
        // moeglicherweise abweichender Rechenweg.
        val ziel = DiyanetPlace(1, "GRENZTEST", "GRENZTEST", "DE", 50.0, 10.0)
        val deltaLatGrad = Math.toDegrees(25.0 / 6371.0) // ~ die produktiv genutzte 25-km-Schwelle
        val kandidatLat = ziel.latitude + deltaLatGrad
        val distanz = DiyanetPlaces.distanceKm(ziel, kandidatLat, ziel.longitude)
        assertTrue("Konstruktion liegt nicht bei ~25 km: $distanz km", distanz in 24.9..25.1)

        // Exakt an der (selbst ausgelesenen) Distanz als maxKm: muss ein Treffer
        // sein. `<` statt `<=` würde hier durchfallen.
        assertEquals(
            ziel,
            DiyanetPlaces.nearest(listOf(ziel), kandidatLat, ziel.longitude, maxKm = distanz),
        )
        // Der naechste darstellbare Double darunter darf keinen Treffer mehr liefern —
        // die andere Seite der Grenze.
        assertNull(
            DiyanetPlaces.nearest(listOf(ziel), kandidatLat, ziel.longitude, maxKm = Math.nextDown(distanz)),
        )
    }

    @Test fun `displayName macht aus Schreiaufschrift lesbare Namen`() {
        assertEquals("Sakarya", sakarya.displayName())
        assertEquals("İstanbul", DiyanetPlace(9541, "İSTANBUL", "İSTANBUL", "TR", 41.0, 29.0).displayName())
        assertEquals("Mustafakemalpaşa", DiyanetPlace(1, "MUSTAFAKEMALPAŞA", "BURSA", "TR", 40.0, 28.0).displayName())
    }

    @Test fun `displayName verstuemmelt nicht-tuerkische Namen nicht`() {
        assertEquals("Berlin", DiyanetPlace(2, "BERLIN", "BERLIN", "DE", 52.5, 13.4).displayName())
        assertEquals("Mainz", DiyanetPlace(3, "MAINZ", "RHEINLAND-PFALZ", "DE", 50.0, 8.27).displayName())
    }

    @Test fun `leere Liste ist kein Absturz`() {
        assertNull(DiyanetPlaces.nearest(emptyList(), 40.0, 30.0))
    }
}
