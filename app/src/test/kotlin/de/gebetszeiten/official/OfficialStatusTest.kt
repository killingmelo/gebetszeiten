package de.gebetszeiten.official

import de.gebetszeiten.prayer.TimesSourceBadge
import de.gebetszeiten.prayer.officialStatusText
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
        )
        assertTrue(text, text.contains("Sakarya"))
        assertTrue(text, text.contains("9807"))
        assertTrue(text, text.contains("31.12.2026"))
    }

    @Test fun `Fehlergrund erscheint im Klartext`() {
        val text = officialStatusText(
            OfficialStatus(null, null, now, "Kein Diyanet-Standort aufloesbar"),
            source = TimesSourceBadge.Calculated,
        )
        assertTrue(text, text.contains("Kein Diyanet-Standort aufloesbar"))
    }

    @Test fun `ohne jeden Abruf wird das gesagt statt ein leerer Text`() {
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            source = TimesSourceBadge.Calculated,
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
        )
        assertTrue(text, text.contains("unbekannt"))
        assertTrue(text, !text.contains("noch kein Versuch"))
    }

    @Test fun `Zeitstempel wird in der uebergebenen Zone formatiert`() {
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Official("Sakarya", 2),
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
    // useCalculated/useOnline/Bundle aus dem Cache-Stempel gelesen wurde.

    @Test fun `F1a eigene Berechnung ueberschreibt einen veralteten Diyanet-Stempel`() {
        // Der Cache-Stempel zeigt noch einen erfolgreichen Diyanet-Abruf
        // (Sakarya, ID 9807) von VOR dem Umschalten auf "Eigene Berechnung":
        // refreshOfficial() kehrt fuer useCalculated fruehzeitig zurueck und
        // laesst Cache und Stempel unangetastet. Die Zeile darf trotzdem
        // nicht mehr "amtliche Diyanet-Zeiten" behaupten, wenn die
        // Klassifikation (die den tatsaechlich genutzten Pfad widerspiegelt)
        // Calculated liefert.
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Calculated,
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
        val text = officialStatusText(
            OfficialStatus(9807, LocalDate.of(2026, 12, 31), now, null),
            source = TimesSourceBadge.Calculated,
        )
        assertTrue(text, text.contains("eigene Berechnung"))
        assertTrue(text, !text.contains("amtliche Diyanet-Zeiten"))
    }

    @Test fun `F1c gebuendelte DE-Tabelle behauptet keinen Abruf, der nie stattfand`() {
        // Offline-Flavor (oder DE-Ort vor dem ersten Online-Fetch): status
        // hat NICHTS (kein locationId, keine Abdeckung, kein Versuch) — die
        // Quelle kommt ausschliesslich aus dem Bundle. "Letzter Abruf"/
        // "Fehler" waeren hier eine Aussage ueber ein Ereignis, das im
        // offline-Flavor gar nicht existieren kann.
        val text = officialStatusText(
            OfficialStatus(null, null, null, null),
            source = TimesSourceBadge.Bundled("Nürnberg"),
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
        )
        assertTrue(text, text.contains("Quelle: amtliche Diyanet-Zeiten · Sakarya"))
        assertTrue(text, !text.contains("(ID"))
    }
}
