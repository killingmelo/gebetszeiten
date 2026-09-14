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
 * Uhr und Telefon fuer deutsche Orte: seit [de.gebetszeiten.wear.WearFetchProvider]
 * die `bundledLocationId` aus `locations-de.tsv` ausfuellt (Fix zu Aufgabe 6,
 * Fix-Runde 1 — die Uhr hat dieses Bundle laengst, `wear/build.gradle.kts`
 * bindet `shared-assets` ein), fragen BEIDE Geraete fuer einen deutschen Ort
 * ZUERST dieselbe Tabelle. Eine Abweichung fuer Deutschland ist damit
 * konstruktiv ausgeschlossen, nicht mehr nur zufaellig vermieden.
 *
 * **Warum dieser Test trotzdem bleibt.** Er bewacht nicht mehr den
 * Produktcode (der fragt jetzt dieselbe Quelle), sondern die DATENPIPELINE:
 * `locations-world.tsv` (der weltweite Index, Fallback fuer alles ausserhalb
 * von `locations-de.tsv` — Ausland, oder ein deutscher Ort, den das DE-Bundle
 * selbst nicht kennt) enthaelt nachweislich drei Fehler (siehe
 * [BEKANNTE_WELTINDEX_ABWEICHUNGEN] unten). Dieser Test haelt die Zahl der
 * bekannten Fehler SICHTBAR und WAECHST NICHT UNBEMERKT: ein vierter
 * abweichender Ort faellt durch, weil er nicht in der Ausnahmeliste steht;
 * verschwindet einer der drei bekannten (Pipeline-Korrektur), faellt die
 * Ausnahmeliste selbst durch, weil sie dann zu gross ist. Beides ist ein
 * Befund, keiner der beiden Faelle darf leise durchlaufen.
 *
 * Fuer JEDEN Eintrag aus `locations-de.tsv` (der bereits seine eigene
 * Diyanet-ID vom DE-Pipeline-Lauf trägt) wird im Weltindex der Ort an
 * PRAKTISCH DENSELBEN Koordinaten gesucht (`DiyanetPlaces.nearest`, dieselbe
 * Funktion, die die Uhr fuer die Koordinaten-Aufloesung im Weltindex-Fallback
 * nutzt) und dieselbe ID verlangt.
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

    @Test fun weltindexWeichtNurAnDenBekanntenDreiOrtenVonLocationsDeAb() {
        val gefunden = deLocations.mapNotNull { de ->
            val weltTreffer = DiyanetPlaces.nearest(worldPlaces, de.latitude, de.longitude, maxKm = 1.0)
            val abweichend = weltTreffer == null || weltTreffer.diyanetId != de.diyanetId
            if (!abweichend) return@mapNotNull null
            val beschreibung = if (weltTreffer == null) {
                "${de.name} (${de.diyanetId}): kein Weltindex-Treffer innerhalb von 1 km " +
                    "(${de.latitude}/${de.longitude})"
            } else {
                "${de.name}: Telefon-ID ${de.diyanetId} vs. Weltindex-ID ${weltTreffer.diyanetId} " +
                    "(Weltindex-Treffer: ${weltTreffer.name}/${weltTreffer.province})"
            }
            de.diyanetId to beschreibung
        }
        val gefundeneIds = gefunden.map { it.first }.toSet()
        val bekannteIds = BEKANNTE_WELTINDEX_ABWEICHUNGEN.map { it.deDiyanetId }.toSet()

        // Fall 1: ein NEUER, nicht dokumentierter Ort weicht ab — die
        // Ausnahmeliste ist zu klein geworden (oder ein bekannter Fehler hat
        // sich einen zusaetzlichen Ort "eingefangen").
        val unbekannt = gefunden.filter { (id, _) -> id !in bekannteIds }
        assertEquals(
            "Neue, nicht in BEKANNTE_WELTINDEX_ABWEICHUNGEN dokumentierte Abweichung(en) " +
                "zwischen locations-de.tsv und dem Weltindex:\n" +
                unbekannt.joinToString("\n") { it.second },
            emptyList<String>(),
            unbekannt.map { it.second },
        )

        // Fall 2: ein bekannter Fehler tritt NICHT MEHR auf (Pipeline
        // korrigiert, oder der Ort ist aus locations-de.tsv verschwunden) —
        // die Ausnahmeliste ist zu gross geworden und muss verkleinert
        // werden, sonst wuerde sie eine kuenftige, wirklich neue Abweichung
        // mit demselben Namensglueck verschleiern.
        val nichtMehrAbweichend = BEKANNTE_WELTINDEX_ABWEICHUNGEN.filter { it.deDiyanetId !in gefundeneIds }
        assertEquals(
            "BEKANNTE_WELTINDEX_ABWEICHUNGEN ist veraltet, diese Eintraege weichen nicht mehr " +
                "ab und muessen entfernt werden:\n" +
                nichtMehrAbweichend.joinToString("\n") { "${it.deName} (${it.deDiyanetId}): ${it.ursache}" },
            emptyList<BekannteWeltindexAbweichung>(),
            nichtMehrAbweichend,
        )
    }

    /**
     * Ort, den `locations-de.tsv` (Telefon) und `locations-world.tsv`
     * (Weltindex-Fallback der Uhr, siehe Klassen-KDoc) auf verschiedene
     * Diyanet-IDs abbilden — mit dokumentierter Ursache. Nur diese drei
     * duerfen als Abweichung durchgehen; jede andere laesst den Test oben
     * rot werden.
     */
    private data class BekannteWeltindexAbweichung(
        val deDiyanetId: Int,
        val deName: String,
        val ursache: String,
    )

    companion object {
        /**
         * Recherchiert (nicht behoben — `tools/diyanet-index/build_index.py`
         * ist ein eigenes Vorhaben mit eigenem Datenlauf, siehe
         * task-6-report.md) am 14.09.2026 gegen
         * `net-diyanet/src/main/assets/official/locations-world.tsv`.
         */
        private val BEKANNTE_WELTINDEX_ABWEICHUNGEN = listOf(
            BekannteWeltindexAbweichung(
                deDiyanetId = 10463,
                deName = "Neukirchen",
                ursache = "Der Weltindex fuehrt ZWEI Diyanet-Distrikte an EXAKT denselben " +
                    "Koordinaten (50.86906/9.34655): ID 10224 NEUKIRCHEN/NORDRHEIN-WESTFALEN " +
                    "und ID 10463 NEUKIRCHEN/SACHSEN. DiyanetPlaces.nearest trifft bei " +
                    "echtem Gleichstand den ersten in Dateireihenfolge (10224); das " +
                    "DE-Bundle traegt fuer diesen Ort aber 10463.",
            ),
            BekannteWeltindexAbweichung(
                deDiyanetId = 10489,
                deName = "Salzgitter",
                ursache = "Dasselbe Koordinaten-Duplikat: ID 11078 SALZGITTER und ID 10489 " +
                    "SALZGITTER BAD liegen beide auf (52.15705/10.4154). nearest() trifft " +
                    "zuerst auf 11078, das DE-Bundle nennt denselben Ort (10489) schlicht " +
                    "\"Salzgitter\".",
            ),
            BekannteWeltindexAbweichung(
                deDiyanetId = 11089,
                deName = "Halle (Saale)",
                ursache = "Der Weltindex-Eintrag ID 11089 HALLE(Saxony-Anhalt) ist FALSCH " +
                    "geokodiert: seine Koordinaten (52.06007/8.36083) sind die von Halle in " +
                    "Nordrhein-Westfalen (ID 10773 HALLE (NRW)) statt die des echten " +
                    "Halle/Saale in Sachsen-Anhalt (51.48158/11.97947) - rund 300 km daneben, " +
                    "deshalb kein Treffer innerhalb von 1 km.",
            ),
        )
    }
}
