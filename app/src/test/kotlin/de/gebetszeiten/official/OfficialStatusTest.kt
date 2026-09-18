package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import de.gebetszeiten.prayer.TimesSourceBadge
import de.gebetszeiten.prayer.coverageWarning
import de.gebetszeiten.prayer.officialStatusText
import de.gebetszeiten.prayer.verificationLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OfficialStatusTest {

    private val now = 1_786_000_000_000L // fester Zeitpunkt, kein System.now

    @Test fun `amtlicher Status nennt Standort und Abdeckung`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(text, text.contains("Sakarya"))
        assertTrue(text, text.contains("9807"))
        assertTrue(text, text.contains("31.12.2026"))
    }

    @Test fun `Fehlergrund erscheint im Klartext`() {
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Kein Diyanet-Standort aufloesbar"),
            source = TimesSourceBadge.Calculated,
            canFetch = true,
        )
        assertTrue(text, text.contains("Kein Diyanet-Standort aufloesbar"))
    }

    @Test fun `ohne jeden Abruf wird das gesagt statt ein leerer Text`() {
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            source = TimesSourceBadge.Calculated,
            canFetch = true,
        )
        assertTrue(text, text.isNotBlank())
        assertTrue(text, text.contains("noch kein"))
    }

    @Test fun `mit Abdeckung aber ohne Versuchsdatensatz heisst es unbekannt statt noch kein Versuch`() {
        // Standort/Abdeckung belegen einen frueheren Erfolg, aber der
        // Versuchsdatensatz wurde von einem Abruf an einem anderen Ort
        // verdraengt (ein Datensatz fuer alle Orte, siehe Fix-Runde 1).
        // "noch kein Versuch" waere hier eine Falschaussage.
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), null, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(text, text.contains("unbekannt"))
        assertTrue(text, !text.contains("noch kein Versuch"))
    }

    @Test fun `Zeitstempel wird in der uebergebenen Zone formatiert`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
            zone = ZoneId.of("Europe/Istanbul"),
        )
        // Erwarteter Zeitstempel unabhaengig ausgerechnet (nicht mit dem
        // gleichen DateTimeFormatter erzeugt, den die Funktion selbst nutzt):
        // 1_786_000_000_000 ms = 2026-08-06T07:06:40Z = 2026-08-06T10:06:40
        // in Europe/Istanbul (fest UTC+3, keine Sommerzeit seit 2016).
        assertTrue(text, text.contains("Letzter Abruf: 06.08.2026, 10:06"))
    }

    // --- Fix-Runde 2 (finale Branch-Review): die Statuszeile muss dieselbe
    // Quelle nennen, die PrayerProvider.daily tatsaechlich anzeigt — nicht
    // den Diyanet-Standortnamen allein, der frueher unabhaengig von
    // calculationFillsGaps/useOnline/Bundle aus dem Cache-Stempel gelesen
    // wurde.

    @Test fun `F1a eigene Berechnung ueberschreibt einen veralteten Diyanet-Stempel`() {
        // Der Cache-Stempel zeigt noch einen erfolgreichen Diyanet-Abruf
        // (Sakarya, ID 9807) von VOR dem Umschalten auf "Eigene Berechnung":
        // dieses Beispiel haengt canFetch=false absichtlich an useOnline=false,
        // nicht am Notausgang - seit Aufgabe 15 schliesst ein eingeschalteter
        // Notausgang (calculationFillsGaps) canFetch nicht mehr aus (siehe
        // canFetchOfficial-KDoc). Die Zeile darf trotzdem nicht mehr
        // "amtliche Diyanet-Zeiten" behaupten, wenn die Klassifikation (die
        // den tatsaechlich genutzten Pfad widerspiegelt) Calculated liefert.
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Calculated,
            canFetch = false,
        )
        assertTrue(text, text.contains("Quelle: eigene Berechnung (Diyanet-Methode)"))
        assertTrue(text, !text.contains("Diyanet-Zeiten"))
        assertTrue(text, !text.contains("Sakarya"))
    }

    @Test fun `F1b Online-Schalter aus ueberschreibt ebenfalls den alten Diyanet-Stempel`() {
        // Gleiche Ausgangslage wie F1a, aber diesmal weil useOnline aus ist:
        // daily() ueberspringt dann Schritt 1 (Cache) komplett. Die
        // Klassifikation liefert hier ebenfalls Calculated (kein aktueller
        // Cache-Treffer, keine gebuendelte DE-Tabelle fuer diesen Ort) —
        // die Zeile muss das ehrlich wiedergeben statt den alten Stempel.
        // canFetch = false, weil useOnline aus ist.
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Calculated,
            canFetch = false,
        )
        assertTrue(text, text.contains("eigene Berechnung"))
        assertTrue(text, !text.contains("amtliche Diyanet-Zeiten"))
    }

    @Test fun `F1c gebuendelte DE-Tabelle behauptet keinen Abruf, der nie stattfand (offline-Flavor)`() {
        // Offline-Flavor: status hat NICHTS (kein locationId, keine
        // Abdeckung, kein Versuch) — die Quelle kommt ausschliesslich aus dem
        // Bundle, und im offline-Flavor existiert kein Fetcher ueberhaupt
        // (canFetch = false). "Letzter Abruf"/"Fehler" waeren hier eine
        // Aussage ueber ein Ereignis, das gar nicht stattfinden kann.
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            source = TimesSourceBadge.Bundled("Nürnberg"),
            canFetch = false,
        )
        assertTrue(text, text.contains("Quelle: amtliche Diyanet-Zeiten · Nürnberg (gebündelt)"))
        assertTrue(text, !text.contains("Abruf"))
        assertTrue(text, !text.contains("Fehler"))
        assertTrue(text, !text.contains("eigene Berechnung"))
    }

    @Test fun `F2 Official ohne Standort-ID im Status zeigt keine ID an`() {
        // Die ID kommt aus `status.locationId` (Cache-Stempel), nicht aus der
        // Klassifikation selbst — ein Stage-4-Namenstreffer etwa hat eine ID
        // im Cache, aber der Index (der den Anzeigenamen liefert) kennt den
        // Ort u. U. gar nicht in derselben Form. Ohne Stempel-ID darf der
        // Suffix nicht erscheinen.
        val text = officialStatusText(
            OfficialStatus(null, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(text, text.contains("Quelle: amtliche Diyanet-Zeiten · Sakarya"))
        assertTrue(text, !text.contains("(ID"))
    }

    // --- Fix-Runde 3 (Re-Review nach Fix-Runde 2): die Unterdrueckung von
    // "Letzter Abruf"/"Fehler" haengt jetzt an canFetch (ob es ueberhaupt
    // einen Abrufmechanismus gibt), nicht mehr an `source is Bundled`. Und
    // "Abgedeckt bis" (Abdeckung des ONLINE-CACHES) haengt an `source is
    // Official`, nicht mehr bedingungslos an `status.coveredUntil`.

    @Test fun `F-Runde3 Befund1 online-Flavor mit fehlgeschlagenem Abruf zeigt den Fehler auch wenn das Bundle traegt`() {
        // Online-Flavor, deutscher Ort: der Abruf scheitert wiederholt, die
        // gebuendelte DE-Tabelle traegt die Zeiten (Einordnung = Bundled).
        // canFetch = true (useOnline an, keine eigene Berechnung) — der
        // Fehlergrund MUSS erscheinen, sonst aendert sich die Statuszeile
        // trotz sichtbarem "Jetzt aktualisieren"-Knopf nie (die Stille, gegen
        // die dieser Umbau urspruenglich antrat).
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Zeitüberschreitung beim Abruf"),
            source = TimesSourceBadge.Bundled("Nürnberg"),
            canFetch = true,
        )
        assertTrue(text, text.contains("Fehler: Zeitüberschreitung beim Abruf"))
    }

    @Test fun `F-Runde3 Befund1 offline-Flavor unterdrueckt die Abruf-Zeilen bei Bundled weiterhin`() {
        // Gegenprobe: canFetch = false (offline-Flavor, kein Fetcher
        // existiert) — hier bleibt die Unterdrueckung richtig, auch wenn
        // (hypothetisch) ein Fehlergrund im Status stuende.
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Zeitüberschreitung beim Abruf"),
            source = TimesSourceBadge.Bundled("Nürnberg"),
            canFetch = false,
        )
        assertTrue(text, !text.contains("Fehler"))
        assertTrue(text, !text.contains("Abruf"))
    }

    @Test fun `F-Runde3 Befund2 Abgedeckt-bis nur bei Official, nicht bei Bundled mit demselben Stempel`() {
        // status.coveredUntil beschreibt die Abdeckung des ONLINE-CACHES.
        // Bei Bundled (die gebuendelte Tabelle traegt, der Cache ist z. B.
        // veraltet) darf ein vergangenes "Abgedeckt bis" nicht so aussehen,
        // als beschriebe es die gebuendelte Tabelle.
        val coveredUntil = LocalDate.of(2026, 12, 31)
        val bundledText = officialStatusText(
            OfficialStatus(9807, coveredUntil, now, null),
            source = TimesSourceBadge.Bundled("Nürnberg"),
            canFetch = true,
        )
        assertTrue(bundledText, !bundledText.contains("Abgedeckt bis"))

        val officialText = officialStatusText(
            OfficialStatus(9807, coveredUntil, now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(officialText, officialText.contains("Abgedeckt bis: 31.12.2026"))
    }

    // --- Task 12: die Gegenpruefung wird sichtbar --------------------------

    private fun verification(
        note: VerificationNote,
        confirmedBy: List<SourceId> = listOf(SourceId.PROXY_ABDUS),
        comparedDays: Int = 31,
    ) = Verification(
        note = note,
        chosen = SourceId.DIRECT,
        confirmedBy = confirmedBy,
        comparedDays = comparedDays,
        differingDays = 0,
        maxAbsMinutes = 0,
        firstDiff = null,
        checkedEpochMs = now,
    )

    @Test fun `Pruefnotiz steht zwischen Abdeckung und letztem Abruf`() {
        val text = officialStatusText(
            OfficialStatus(
                9807,
                LocalDate.of(2026, 12, 31),
                now,
                null,
                verification(VerificationNote.VERIFIED),
            ),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        val zeilen = text.lines()
        val abdeckung = zeilen.indexOfFirst { it.startsWith("Abgedeckt bis") }
        val pruefung = zeilen.indexOfFirst { it.startsWith("Gegenprüfung") }
        val abruf = zeilen.indexOfFirst { it.startsWith("Letzter Abruf") }
        assertTrue(text, abdeckung >= 0 && pruefung >= 0 && abruf >= 0)
        assertTrue(text, abdeckung < pruefung && pruefung < abruf)
        // Der Wortlaut kommt aus verificationLine (Task 8), nicht aus einem
        // zweiten Text.
        assertEquals(verificationLine(verification(VerificationNote.VERIFIED)), zeilen[pruefung])
    }

    @Test fun `ohne Pruefnotiz faellt die Zeile weg`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(text, !text.contains("Gegenprüfung"))
    }

    @Test fun `Note NONE ergibt keine Zeile`() {
        val text = officialStatusText(
            OfficialStatus(
                9807,
                LocalDate.of(2026, 12, 31),
                now,
                null,
                verification(VerificationNote.NONE, confirmedBy = emptyList(), comparedDays = 0),
            ),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
        )
        assertTrue(text, !text.contains("Gegenprüfung"))
    }

    @Test fun `Pruefnotiz erscheint weder bei Bundled noch bei Calculated`() {
        // Dieselbe Begruendung wie fuer "Abgedeckt bis": beides beschreibt den
        // ONLINE-CACHE. Traegt die gebuendelte Tabelle oder die Berechnung,
        // laese sich die Pruefnotiz als Aussage ueber DIESE lesen.
        val status = OfficialStatus(
            9807,
            LocalDate.of(2026, 12, 31),
            now,
            null,
            verification(VerificationNote.VERIFIED),
        )
        val bundled = officialStatusText(status, TimesSourceBadge.Bundled("Nürnberg"), canFetch = true)
        assertTrue(bundled, !bundled.contains("Gegenprüfung"))

        val calculated = officialStatusText(status, TimesSourceBadge.Calculated, canFetch = true)
        assertTrue(calculated, !calculated.contains("Gegenprüfung"))
    }

    // --- Task 19: die Reserve laeuft ab -----------------------------------
    // Der Aufrufer entscheidet die ANWENDBARKEIT (gibt es fuer diesen Ort
    // ueberhaupt eine gebuendelte Tabelle?) und uebergibt sonst `null`; der
    // WORTLAUT kommt aus `coverageWarning`.

    private val heute = LocalDate.of(2026, 11, 20)

    @Test fun `die Reservewarnung erscheint als letzte Zeile`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
            bundledCoverageEnd = LocalDate.of(2026, 12, 31),
            today = heute,
        )
        // Kein zweiter Wortlaut fuer dieselbe Sache.
        assertEquals(coverageWarning(LocalDate.of(2026, 12, 31), heute), text.lines().last())
    }

    @Test fun `ohne gebuendelte Tabelle fuer diesen Ort bleibt der Text unveraendert`() {
        // Ein Favorit in Istanbul: das Bundle deckt nur Deutschland ab, dort
        // war nie eine Reserve. Der Aufrufer uebergibt deshalb null.
        val status = OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null)
        val ohne = officialStatusText(status, TimesSourceBadge.Official("Istanbul", 2), canFetch = true)
        val mitNull = officialStatusText(
            status,
            source = TimesSourceBadge.Official("Istanbul", 2),
            canFetch = true,
            bundledCoverageEnd = null,
            today = heute,
        )
        assertEquals(ohne, mitNull)
        assertTrue(ohne, !ohne.contains("Offline-Reserve"))
    }

    @Test fun `ausserhalb der Frist bleibt der Text unveraendert`() {
        val status = OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null)
        val ohne = officialStatusText(status, TimesSourceBadge.Official("Sakarya", 2), canFetch = true)
        val frueh = officialStatusText(
            status,
            source = TimesSourceBadge.Official("Sakarya", 2),
            canFetch = true,
            bundledCoverageEnd = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2026, 9, 11), // 111 Tage Rest
        )
        assertEquals(ohne, frueh)
    }

    @Test fun `die Warnung haengt an der Reserve, nicht an der aktiven Quelle`() {
        // Auch wenn gerade der Online-Cache traegt (Official) oder gerechnet
        // wird (Calculated): die Reserve ist dieselbe, und genau sie geht zur
        // Neige. Die bestehenden Zeilen bleiben dabei, wie sie waren.
        val status = OfficialStatus(null, null, null, null)
        listOf(
            TimesSourceBadge.Official("Nürnberg", 1),
            TimesSourceBadge.Bundled("Nürnberg"),
            TimesSourceBadge.Calculated,
        ).forEach { source ->
            val ohne = officialStatusText(status, source, canFetch = false)
            val mit = officialStatusText(
                status,
                source = source,
                canFetch = false,
                bundledCoverageEnd = LocalDate.of(2026, 12, 31),
                today = heute,
            )
            assertEquals(ohne, mit.lines().dropLast(1).joinToString("\n"))
            assertTrue(mit, mit.lines().last().contains("31.12.2026"))
        }
    }
}
