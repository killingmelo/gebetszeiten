package de.gebetszeiten.net

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.parseDiyanetPlaces
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Uhr und Telefon loesen denselben Ort auf verschiedenen Wegen auf: das
 * Telefon bevorzugt `locations-de.tsv` (`shared-assets/official/`, gepflegt
 * von der DE-Pipeline), die Uhr hat dieses Bundle nicht und faellt auf den
 * WELTWEITEN Index `locations-world.tsv` (dieses Modul) zurueck — genau der
 * Weg, den [de.gebetszeiten.wear.WearFetchProvider] beschreibt: das
 * `bundledLocationId`-Feld ist auf der Uhr immer `null`.
 *
 * Loesten die beiden Indizes fuer denselben Punkt auf VERSCHIEDENE
 * Diyanet-IDs auf, zeigten Uhr und Telefon fuer denselben Ort verschiedene
 * amtliche Zeiten — und mangels gemeinsamer Anzeige wuerde das niemand
 * bemerken. Dieser Test ist der Abgleich.
 *
 * Fuer JEDEN Eintrag aus `locations-de.tsv` (der bereits seine eigene
 * Diyanet-ID vom DE-Pipeline-Lauf trägt) wird im Weltindex der Ort an
 * PRAKTISCH DENSELBEN Koordinaten gesucht (`DiyanetPlaces.nearest`, dieselbe
 * Funktion, die die Uhr fuer die Koordinaten-Aufloesung nutzt) und dieselbe
 * ID verlangt.
 *
 * **Schwelle: 1 km.** Beide Indizes stammen aus DERSELBEN Diyanet-Quelle;
 * live geprueft (11.09.2026) stimmen Nuernberg (49.45421/11.07752, ID 11024)
 * und Berlin (52.52437/13.41053, ID 11002) in BEIDEN Dateien auf alle fuenf
 * Nachkommastellen exakt ueberein — 0 km Abweichung, nicht nur "nah". 1 km
 * laesst Rundungsrauschen zwischen den beiden Pipeline-Laeufen zu, ist aber
 * um Groessenordnungen enger als der Abstand zum naechsten ANDEREN
 * Diyanet-Standort (typischerweise zweistellige km) — ein falscher Treffer
 * kann diese Schwelle nicht zufaellig unterlaufen.
 */
class PhoneWatchLocationIdConsistencyTest {

    private val deLocations: List<OfficialLocation> by lazy {
        File("../shared-assets/official/locations-de.tsv").useLines { parseOfficialLocations(it) }
    }

    private val worldPlaces: List<DiyanetPlace> by lazy {
        File("src/main/assets/official/locations-world.tsv").useLines { parseDiyanetPlaces(it) }
    }

    @Test fun beideIndizesSindNichtLeer() {
        // Stolperdraht gegen einen Test, der nur deshalb "gruen" ist, weil
        // eine der beiden Dateien nicht gefunden wurde.
        assertTrue("locations-de.tsv liefert keine Eintraege", deLocations.isNotEmpty())
        assertTrue("locations-world.tsv liefert keine Eintraege", worldPlaces.isNotEmpty())
    }

    @Test fun telefonUndUhrLoesenJedenDeutschenOrtAufDieselbeDiyanetIdAuf() {
        val abweichungen = deLocations.mapNotNull { de ->
            val weltTreffer = DiyanetPlaces.nearest(worldPlaces, de.latitude, de.longitude, maxKm = 1.0)
            when {
                weltTreffer == null ->
                    "${de.name} (${de.diyanetId}): kein Weltindex-Treffer innerhalb von 1 km " +
                        "(${de.latitude}/${de.longitude})"
                weltTreffer.diyanetId != de.diyanetId ->
                    "${de.name}: Telefon-ID ${de.diyanetId} vs. Uhr-ID ${weltTreffer.diyanetId} " +
                        "(Weltindex-Treffer: ${weltTreffer.name}/${weltTreffer.province})"
                else -> null
            }
        }
        assertEquals(
            "Telefon (locations-de.tsv) und Uhr (locations-world.tsv) loesen " +
                "${abweichungen.size} von ${deLocations.size} Orten auf UNTERSCHIEDLICHE " +
                "Diyanet-IDs auf:\n" + abweichungen.joinToString("\n"),
            emptyList<String>(),
            abweichungen,
        )
    }
}
