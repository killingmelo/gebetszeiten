package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Verifiziert die Pipeline-Ausgabe: Index konsistent, Tabellen vollständig
 *  und monoton, Nürnberg reproduziert die amtliche Phase-1-Referenz. */
class OfficialAssetsIntegrityTest {

    private val assets = File("../shared-assets/official")
    private val locations by lazy {
        File(assets, "locations-de.tsv").useLines { parseOfficialLocations(it) }
    }

    /** Anfang, Ende und Tageszahl einer gebündelten Tabelle. */
    private data class Span(val first: LocalDate, val last: LocalDate, val days: Int)

    /**
     * Die Fenster aller gebündelten Tabellen — ohne die Zeiten zu parsen.
     *
     * Seit die Jahrgänge zusammengeführt statt ersetzt werden, deckt NICHT
     * mehr jeder Ort dasselbe Fenster ab: Orte, die Diyanet 2026 noch nicht
     * führte, fangen erst 2027 an. Die Aussagen über Fenster brauchen deshalb
     * alle 947 Tabellen, und dafür genügt Spalte 1. Sie alle vollständig
     * geparst im Speicher zu halten wäre Verschwendung;
     * `everyReferencedTableExistsCompleteAndOrdered` tut das eine nach der
     * anderen.
     */
    private val spans: Map<String, Span> by lazy {
        locations.map { it.tableRef }.distinct().associateWith { ref ->
            val f = File(assets, "tables/$ref.tsv")
            assertTrue("$ref fehlt (${f.absolutePath})", f.isFile)
            val dates = f.useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .map { LocalDate.parse(it.substringBefore('\t')) }
                    .toList()
            }
            assertTrue("$ref ist leer", dates.isNotEmpty())
            Span(dates.min(), dates.max(), dates.size)
        }
    }

    /** coverage.tsv: eine Zeile, zwei Spalten — der Zeitraum, den JEDER
     *  gebündelte Ort abdeckt. Vom Pipeline-Skript geschrieben, nicht von Hand
     *  gepflegt. Einzelne Orte dürfen mehr haben (siehe `spans`). */
    private val coverage: Pair<LocalDate, LocalDate> by lazy {
        val f = File(assets, "coverage.tsv")
        assertTrue("coverage.tsv fehlt (${f.absolutePath}) — Pipeline laufen lassen", f.isFile)
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

    /**
     * Deckt coverage.tsv wirklich das ab, was in den Tabellen steht? Sonst
     * wären die Stolperdrähte unten nur Behauptungen über zwei Zahlen.
     *
     * Hier stand einmal `assertEquals(first.year, last.year)` — „coverage.tsv
     * umspannt mehr als ein Kalenderjahr". Das galt, solange die Pipeline auf
     * ein Kalenderjahr beschnitt und jeder Ort dasselbe Fenster hatte. Seit
     * die Jahrgänge zusammengeführt werden, stimmt beides nicht mehr.
     *
     * Was die Zusage eigentlich schützte — coverage.tsv und die Tabellen
     * erzählen dasselbe — steht jetzt ausdrücklich da: coverage.tsv ist der
     * SCHNITT über alle Tabellen, also der spätestmögliche Anfang und das
     * früheste Ende. Wäre es der Verband, verspräche die Datei für die
     * knappsten Orte etwas, das sie nicht halten.
     */
    @Test fun coverageMatchesTheBundledTables() {
        val (first, last) = coverage
        assertTrue("coverage.tsv: $first liegt nach $last", !first.isAfter(last))
        assertEquals(
            "coverage.tsv nennt $first als Anfang, aber der knappste Ort fängt später an",
            spans.values.maxOf { it.first },
            first,
        )
        assertEquals(
            "coverage.tsv nennt $last als Ende, aber der knappste Ort hört früher auf",
            spans.values.minOf { it.last },
            last,
        )
    }

    /**
     * Nur EIN Jahrgang darf in `tables/` liegen — sonst wächst das Bundle mit
     * jedem Lauf, und eine liegengebliebene Tabelle sieht aus wie eine gültige.
     *
     * `fetch_diyanet.py` vergibt die Kennungen (`t000`, `t001`, …) bei jedem
     * Lauf NEU, nach Inhalt und in Fundreihenfolge, und schreibt
     * `locations-de.tsv` komplett neu. Nach einem neuen Lauf bedeutet `t005`
     * also im Zweifel einen anderen Ort als vorher.
     *
     * Seit dem rollierenden Fenster trägt der Dateiname statt eines
     * Kalenderjahrs die LAUF-KENNUNG (`t000-20260919.tsv`, das Abrufdatum),
     * und `tableRef` im Index trägt sie mit. Index und Tabellen stammen
     * dadurch zwangsläufig aus demselben Lauf — ein alter Jahrgang kann nicht
     * mehr unter neuer Kennung ausgeliefert werden. Auffallen soll er
     * trotzdem: das Skript warnt am Ende seines Laufs, aber eine Warnung im
     * Terminal liest, wer sie ohnehin beachtet hätte. Deshalb hier ein
     * Build-Fehler.
     */
    @Test fun onlyOneVintageIsBundled() {
        val names = File(assets, "tables").listFiles().orEmpty().map { it.name }.sorted()
        val pattern = Regex("""^t\d{3}-(\d+)\.tsv$""")
        val fremd = names.filterNot(pattern::matches)
        assertEquals(
            "Dateien in shared-assets/official/tables, die kein t###-<lauf>.tsv sind: $fremd",
            emptyList<String>(),
            fremd,
        )
        val vintages = names.mapNotNull { pattern.find(it)?.groupValues?.get(1) }.distinct().sorted()
        assertEquals(
            "Mehr als ein Jahrgang in shared-assets/official/tables: $vintages. " +
                "Die Kennungen werden bei jedem Pipeline-Lauf neu vergeben — ein " +
                "liegengebliebener Jahrgang ist totes Gewicht im APK und im " +
                "Zweifel die Tabelle eines FREMDEN Orts. " +
                "Zu tun: git rm shared-assets/official/tables/t*-<alte Kennung>.tsv",
            1,
            vintages.size,
        )
        assertEquals(
            "Der gebuendelte Jahrgang passt nicht zu den tableRefs in locations-de.tsv",
            setOf(vintages.single()),
            locations.map { it.tableRef.substringAfterLast('-') }.toSet(),
        )
    }

    /**
     * Jede Tabelle: vorhanden, ohne Loch, mindestens so weit wie coverage.tsv
     * verspricht, und jede Zeile aufsteigend.
     *
     * „Ohne Loch" ist die Prüfung, die beim Zusammenführen zweier Jahrgänge
     * zählt. Ein Ort, den nur der neuere Lauf kennt, hat ein KÜRZERES Fenster
     * — das ist erlaubt. Ein Ort mit einem Loch MITTEN drin wäre etwas
     * anderes: die App schlägt je Tag nach und zeigt für den fehlenden Tag
     * einen Hinweis, während der Tag davor und danach Zeiten haben. Das
     * bemerkt niemand beim Prüfen und jeder beim Benutzen.
     */
    @Test fun everyReferencedTableExistsCompleteAndOrdered() {
        val (first, last) = coverage
        spans.forEach { (ref, span) ->
            assertEquals(
                "$ref hat ein Loch zwischen ${span.first} und ${span.last}",
                ChronoUnit.DAYS.between(span.first, span.last) + 1,
                span.days.toLong(),
            )
            assertTrue(
                "$ref beginnt erst am ${span.first}, coverage.tsv verspricht ab $first",
                !span.first.isAfter(first),
            )
            assertTrue(
                "$ref endet schon am ${span.last}, coverage.tsv verspricht bis $last",
                !span.last.isBefore(last),
            )
            val table = File(assets, "tables/$ref.tsv").useLines { parseOfficialTimes(it) }
            assertEquals("$ref: ${span.days} Zeilen, aber ${table.size} Tage", span.days, table.size)
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
        val table = File(assets, "tables/${nbg.tableRef}.tsv").useLines { parseOfficialTimes(it) }
        val t = table.getValue(LocalDate.of(2027, 6, 7))
        assertEquals(LocalTime.of(3, 33), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 46), t.isha)
    }

    /**
     * Der andere Stolperdraht — der, der gefehlt hat.
     *
     * `CoverageAssetTest` und `bundledYearCoversTheNextTwoMonths` prüfen beide
     * nur die ZWEITE Spalte von `coverage.tsv`, das Ende der Abdeckung. Der
     * Anfang wurde nirgends geprüft, und so blieb am 19.09.2026 alles grün,
     * während das Bundle erst am 01.01.2027 begann: 104 Tage, an denen die
     * offline-Variante überall gar keine Zeiten zeigte und die online-Variante
     * beim ersten Start ohne Netz nichts hatte. Bemerkt hat das niemand, weil
     * die eigene Berechnung stillschweigend einsprang — bis sie das nicht mehr
     * tat.
     *
     * Eine Reserve, die erst in der Zukunft beginnt, ist keine Reserve.
     */
    @Test fun coverageHasAlreadyBegun() {
        val today = LocalDate.now()
        val ohneHeute = locations.filter { spans.getValue(it.tableRef).first.isAfter(today) }

        // Die Ankerorte. Sie stehen seit dem ersten Jahrgang im Bundle, also
        // gibt es fuer sie keinen legitimen Grund, heute keine Reserve zu
        // haben. Waere der Anfang des GANZEN Bundles wieder in die Zukunft
        // gerutscht, fielen zuerst sie — und zwar unabhaengig davon, wie die
        // Zahl unten sich entwickelt.
        listOf("Nürnberg", "Berlin").forEach { name ->
            val span = spans.getValue(locations.first { it.name == name }.tableRef)
            assertTrue(
                "$name hat heute ($today) keine amtliche Reserve: seine Tabelle geht " +
                    "${span.first} bis ${span.last}.",
                !span.first.isAfter(today) && !span.last.isBefore(today),
            )
        }

        // Und die Obergrenze fuer alle uebrigen. 326 Orte kamen erst mit dem
        // Jahrgang 2027 dazu; fuer sie gibt es 2026 nichts zu buendeln, weil
        // die Jahresseite den Rest des laufenden Jahres nicht mehr hergibt.
        // Diese Zahl darf SINKEN (ab 2027 auf null) und niemals steigen: ein
        // Anstieg hiesse, dass wieder Tage weggeworfen wurden.
        assertTrue(
            "${ohneHeute.size} Orte haben heute ($today) keine amtliche Reserve, " +
                "erlaubt sind hoechstens 326 (die mit dem Jahrgang 2027 neu " +
                "hinzugekommenen). Zuletzt betroffen z.B. " +
                ohneHeute.take(5).joinToString { it.name } + ".\n" +
                "Wahrscheinlichste Ursache: ein Jahrgang wurde ERSETZT statt " +
                "ergaenzt. Die Diyanet-Jahresseite liefert 31 Tage ab heute und das " +
                "NAECHSTE Kalenderjahr — die Tage dazwischen bekommt man nie wieder. " +
                "Zu tun: tools/diyanet-fetch/merge_bundles.py, siehe dessen Kopf.",
            ohneHeute.size <= 326,
        )
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
                "  1. tools/diyanet-fetch/cache/ löschen — sonst wird das alte Fenster re-emittiert\n" +
                "  2. python tools/diyanet-fetch/fetch_diyanet.py\n" +
                "  3. git rm shared-assets/official/tables/t*-<alte Kennung>.tsv, dann\n" +
                "     git add shared-assets/official (Tabellen, locations-de.tsv, coverage.tsv)\n" +
                "  4. App- UND Wear-Update mit erhöhtem versionCode veröffentlichen",
            !last.isBefore(today.plusMonths(2)),
        )
    }
}
