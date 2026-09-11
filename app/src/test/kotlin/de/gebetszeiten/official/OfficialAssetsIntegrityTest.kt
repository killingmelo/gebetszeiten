package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Verifiziert die Pipeline-Ausgabe: Index konsistent, Tabellen vollständig
 *  und monoton, Nürnberg reproduziert die amtliche Phase-1-Referenz. */
class OfficialAssetsIntegrityTest {

    private val assets = File("../shared-assets/official")
    private val locations by lazy {
        File(assets, "locations-de.tsv").useLines { parseOfficialLocations(it) }
    }

    /** coverage.tsv: eine Zeile, zwei Spalten — erster und letzter abgedeckter
     *  Tag. Vom Pipeline-Skript geschrieben, nicht von Hand gepflegt. */
    private val coverage: Pair<LocalDate, LocalDate> by lazy {
        val f = File(assets, "coverage.tsv")
        assertTrue("coverage.tsv fehlt (${f.absolutePath}) — Pipeline mit --year laufen lassen", f.isFile)
        val c = f.readLines().first { it.isNotBlank() }.trim().split('\t')
        assertEquals("coverage.tsv: zwei Spalten erwartet, gelesen ${c.size}", 2, c.size)
        LocalDate.parse(c[0]) to LocalDate.parse(c[1])
    }

    @Test fun indexIsSubstantialAndInGermanBounds() {
        assertTrue("nur ${locations.size} Standorte", locations.size >= 500)
        locations.forEach {
            assertTrue("${it.name}: lat ${it.latitude}", it.latitude in 47.0..55.5)
            assertTrue("${it.name}: lng ${it.longitude}", it.longitude in 5.5..15.5)
        }
        assertTrue(locations.any { it.name == "Nürnberg" })
        assertTrue(locations.any { it.name == "Berlin" })
    }

    /** Deckt coverage.tsv wirklich das ab, was in den Tabellen steht? Sonst
     *  waere der Stolperdraht unten nur eine Behauptung über eine Zahl. */
    @Test fun coverageMatchesTheBundledTables() {
        val (first, last) = coverage
        assertEquals("coverage.tsv umspannt mehr als ein Kalenderjahr", first.year, last.year)
        val ref = locations.first().tableRef
        val table = File(assets, "tables/$ref-${first.year}.tsv").useLines { parseOfficialTimes(it) }
        assertEquals("erster Tag laut coverage.tsv fehlt in $ref", first, table.keys.minOrNull())
        assertEquals("letzter Tag laut coverage.tsv fehlt in $ref", last, table.keys.maxOrNull())
    }

    /**
     * Nur EIN Jahrgang darf in `tables/` liegen — sonst zeigt die App falsche
     * Gebetszeiten, die richtig aussehen.
     *
     * `fetch_diyanet.py` vergibt die Kennungen (`t000`, `t001`, …) bei jedem
     * Lauf NEU, nach Inhalt und in Fundreihenfolge, und schreibt
     * `locations-de.tsv` komplett neu. Nach einem Lauf fuer ein neues Jahr
     * bedeutet `t005` also im Zweifel einen anderen Ort als vorher.
     *
     * `BundledOfficialSource` baut den Dateinamen aus dem Jahr des GESUCHTEN
     * Datums (`tables/${'$'}{loc.tableRef}-${'$'}{date.year}.tsv`). Bleibt ein alter
     * Jahrgang liegen, findet eine Suche fuer ein Datum jenes Jahres unter der
     * NEUEN Kennung die Tabelle eines FREMDEN Orts — und liefert sie aus.
     *
     * Das Skript warnt davor am Ende seines Laufs. Eine Warnung im Terminal
     * ist aber kein Schutz: sie liest, wer sie ohnehin beachtet haette.
     * Deshalb hier ein Build-Fehler.
     */
    @Test fun onlyOneVintageIsBundled() {
        val vintages = File(assets, "tables").listFiles()
            .orEmpty()
            .mapNotNull { Regex("""^t\d{3}-(\d{4})\.tsv$""").find(it.name)?.groupValues?.get(1) }
            .distinct()
            .sorted()
        assertEquals(
            "Mehr als ein Jahrgang in shared-assets/official/tables: $vintages. " +
                "Die Kennungen werden bei jedem Pipeline-Lauf neu vergeben — ein " +
                "liegengebliebener Jahrgang liefert die Zeiten eines FREMDEN Orts. " +
                "Zu tun: git rm shared-assets/official/tables/t*-<altes Jahr>.tsv",
            1,
            vintages.size,
        )
        assertEquals(
            "Der gebuendelte Jahrgang passt nicht zu coverage.tsv",
            coverage.first.year.toString(),
            vintages.single(),
        )
    }

    @Test fun everyReferencedTableExistsCompleteAndOrdered() {
        val year = coverage.first.year
        locations.map { it.tableRef }.distinct().forEach { ref ->
            val f = File(assets, "tables/$ref-$year.tsv")
            assertTrue("$ref fehlt", f.isFile)
            val table = f.useLines { parseOfficialTimes(it) }
            assertEquals("$ref unvollständig", if (year % 4 == 0) 366 else 365, table.size)
            table.forEach { (date, t) ->
                val ordered = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
                assertEquals("$ref $date nicht aufsteigend", ordered.sorted(), ordered)
            }
        }
    }

    /**
     * Die eine Zeile, die nicht aus der Pipeline stammt, sondern von Hand gegen
     * die Quelle geprüft wurde. Alle anderen Tests hier prüfen die Ausgabe
     * gegen sich selbst — dieser prüft sie gegen die Wirklichkeit.
     *
     * Stand 11.09.2026 abgelesen auf `namazvakitleri.diyanet.gov.tr/tr-TR/11024`
     * (Nürnberg), Jahrestabelle, Zeile `07 Haziran 2027 Pazartesi`:
     * `03:33 05:04 13:20 17:36 21:25 22:46`.
     *
     * **Nach jedem Pipeline-Lauf neu ablesen und hier eintragen** — Datum,
     * Jahrgang und Werte. Eine Referenz, die man mitwandern lässt, ohne sie
     * nachzuschlagen, prüft nichts mehr.
     */
    @Test fun nuernbergReproducesTheHandCheckedReference() {
        val nbg = locations.first { it.name == "Nürnberg" }
        val table = File(assets, "tables/${nbg.tableRef}-2027.tsv").useLines { parseOfficialTimes(it) }
        val t = table.getValue(LocalDate.of(2027, 6, 7))
        assertEquals(LocalTime.of(3, 33), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 46), t.isha)
    }

    /**
     * Der Stolperdraht. Seit „online zuerst" ist das Bundle nur noch Reserve —
     * und Reserven laufen lautlos ab. Zwei Monate vor dem letzten abgedeckten
     * Tag wird dieser Test rot. Absichtlich hier und nicht als Banner in der
     * App: rot im lokalen Build trifft den, der etwas dagegen tun kann.
     *
     * Er ist rot, bis die Pipeline für das Folgejahr gelaufen ist. Das ist
     * kein Defekt, sondern der Zweck.
     */
    @Test fun bundledYearCoversTheNextTwoMonths() {
        val last = coverage.second
        val today = LocalDate.now()
        assertTrue(
            "Die gebündelten amtlichen Zeiten enden am $last, das ist weniger als zwei " +
                "Monate hin (heute $today). Ab dem ${last.plusDays(1)} fällt jeder Ort " +
                "ohne Netz still auf die eigene Berechnung zurück, und die Wear-App hat " +
                "gar keine amtlichen Zeiten mehr.\n" +
                "Zu tun (playstore/CHECKLISTE.md, Abschnitt 8):\n" +
                "  1. tools/diyanet-fetch/cache/ löschen — sonst wird das alte Jahr re-emittiert\n" +
                "  2. python tools/diyanet-fetch/fetch_diyanet.py --year ${last.year + 1}\n" +
                "  3. git add shared-assets/official (Tabellen, locations-de.tsv, coverage.tsv)\n" +
                "  4. App- UND Wear-Update mit erhöhtem versionCode veröffentlichen",
            !last.isBefore(today.plusMonths(2)),
        )
    }
}
