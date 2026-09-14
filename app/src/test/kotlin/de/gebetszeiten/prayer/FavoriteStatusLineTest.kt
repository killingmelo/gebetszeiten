package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.MIN_FUTURE_DAYS
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import de.gebetszeiten.official.OfficialStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Eine Zeile je Favorit: sie beantwortet „kann ich mich darauf verlassen?",
 * ohne dass der Nutzer hinschalten muss. Drei Lagen muessen auf einen Blick
 * unterscheidbar sein — versorgt, keine Zeiten, Abdeckung laeuft ab — und
 * keine Zahl darf etwas anderes behaupten, als sie ist.
 */
class FavoriteStatusLineTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val zone = ZoneId.of("Europe/Istanbul")

    // 1_786_000_000_000 ms = 2026-08-06T07:06:40Z = 10:06 in Europe/Istanbul
    // (fest UTC+3, keine Sommerzeit seit 2016) — unabhaengig ausgerechnet,
    // nicht mit demselben Formatter erzeugt, den die Funktion nutzt.
    private val attempt = 1_786_000_000_000L

    private fun verification(note: VerificationNote) = Verification(
        note = note,
        chosen = SourceId.DIRECT,
        confirmedBy = listOf(SourceId.PROXY_ABDUS),
        comparedDays = 31,
        differingDays = 2,
        maxAbsMinutes = 1,
        firstDiff = null,
        checkedEpochMs = attempt,
    )

    private fun line(status: OfficialStatus, name: String = "Nürnberg") =
        favoriteStatusLine(name, status, today, zone)

    @Test fun `versorgt nennt das Enddatum und die Gegenpruefung`() {
        val status = OfficialStatus(
            locationId = 9807,
            coveredUntil = LocalDate.of(2027, 7, 4),
            lastAttemptEpochMs = attempt,
            lastError = null,
            verification = verification(VerificationNote.VERIFIED),
        )
        assertEquals("Nürnberg · bis 04.07.2027 · bestätigt", line(status))
    }

    @Test fun `ohne Zeiten und mit Fehler nennt den Fehler samt Zeitpunkt`() {
        val status = OfficialStatus(
            locationId = null,
            coveredUntil = null,
            lastAttemptEpochMs = attempt,
            lastError = "Kein Netz",
            verification = null,
        )
        assertEquals("Istanbul · noch keine Zeiten · Kein Netz (06.08., 10:06)", line(status, "Istanbul"))
    }

    @Test fun `ohne Zeiten und ohne Versuch sagt das statt einen Fehler zu erfinden`() {
        val status = OfficialStatus(null, null, null, null, null)
        assertEquals("Istanbul · noch keine Zeiten · noch kein Versuch", line(status, "Istanbul"))
    }

    @Test fun `ohne Zeiten, aber mit gelungenem Versuch, wird kein Fehler behauptet`() {
        // Das Fenster zwischen putAll und recordAttempt, oder ein Abruf, der
        // nichts lieferte, ohne zu werfen: ein Versuch liegt vor, ein Fehler
        // nicht.
        val status = OfficialStatus(null, null, attempt, null, null)
        assertEquals("Istanbul · noch keine Zeiten · letzter Versuch 06.08., 10:06", line(status, "Istanbul"))
    }

    @Test fun `knappe Abdeckung nennt die verbleibenden Tage`() {
        val status = OfficialStatus(
            locationId = 9807,
            coveredUntil = today.plusDays(12),
            lastAttemptEpochMs = attempt,
            lastError = null,
            verification = verification(VerificationNote.UNVERIFIED_SINGLE),
        )
        assertEquals("Regensburg · nur noch 12 Tage · unbestätigt", line(status, "Regensburg"))
    }

    @Test fun `genau an der Auffrisch-Schwelle gilt der Ort noch als versorgt`() {
        // DIESELBE Schwelle, an der die App selbst auffrischt: `needsRefresh`
        // nennt die Abdeckung duenn, sobald `coveredUntil` VOR
        // `today + minFutureDays` liegt. Genau auf der Schwelle also nicht.
        // Eine Zeile, die frueher warnt als die App nachlaedt, waere Laerm.
        val gerade = OfficialStatus(9807, today.plusDays(MIN_FUTURE_DAYS), attempt, null, null)
        assertEquals("Nürnberg · bis 09.11.2026", line(gerade))

        val knapp = OfficialStatus(9807, today.plusDays(MIN_FUTURE_DAYS - 1), attempt, null, null)
        assertEquals("Nürnberg · nur noch ${MIN_FUTURE_DAYS - 1} Tage", line(knapp))
    }

    @Test fun `ein einziger Tag steht im Singular, der letzte Tag heisst heute`() {
        assertEquals(
            "Nürnberg · nur noch 1 Tag",
            line(OfficialStatus(9807, today.plusDays(1), attempt, null, null)),
        )
        assertEquals(
            "Nürnberg · nur noch heute",
            line(OfficialStatus(9807, today, attempt, null, null)),
        )
    }

    @Test fun `abgelaufene Abdeckung wird nicht als Restzeit ausgegeben`() {
        // Eine negative Tageszahl waere eine Zahl, die etwas anderes
        // behauptet, als sie ist.
        val status = OfficialStatus(9807, today.minusDays(3), attempt, null, verification(VerificationNote.VERIFIED))
        assertEquals("Nürnberg · Abdeckung abgelaufen", line(status))
    }

    @Test fun `bei abgelaufener Abdeckung faellt das Guetewort weg`() {
        // „Bestätigt" beschriebe Zeiten, die es nicht mehr gibt. Am letzten
        // abgedeckten Tag gilt es noch, einen Tag spaeter nicht mehr — die
        // Notiz im Kopf bleibt davon unberuehrt, sie wird nur nicht mehr
        // behauptet.
        for (note in VerificationNote.values()) {
            val v = verification(note)
            val heute = OfficialStatus(9807, today, attempt, null, v)
            val gestern = OfficialStatus(9807, today.minusDays(1), attempt, null, v)

            assertEquals(note.name, "Nürnberg · Abdeckung abgelaufen", line(gestern))
            // Gegenprobe: am letzten abgedeckten Tag steht das Kurzwort noch
            // da (bei NONE nie) — sonst bliebe dieser Test auch dann gruen,
            // wenn das Kurzwort ueberall verschwunden waere.
            val amLetztenTag = line(heute)
            val kurzwort = amLetztenTag.removePrefix("Nürnberg · nur noch heute")
            assertEquals(note.name, note == VerificationNote.NONE, kurzwort.isEmpty())
            assertEquals(note.name, "Nürnberg · nur noch heute$kurzwort", amLetztenTag)
        }
    }

    @Test fun `jede Note bekommt ihr Kurzwort, NONE und null keines`() {
        fun kurzwort(note: VerificationNote?): String {
            val v = note?.let { verification(it) }
            val status = OfficialStatus(9807, LocalDate.of(2027, 7, 4), attempt, null, v)
            return line(status).removePrefix("Nürnberg · bis 04.07.2027")
        }
        assertEquals(" · bestätigt", kurzwort(VerificationNote.VERIFIED))
        assertEquals(" · kleine Abweichung", kurzwort(VerificationNote.DRIFT))
        assertEquals(" · aus Kontrollquellen", kurzwort(VerificationNote.GAP_FILLED))
        assertEquals(" · geprüfter Kontrollstand", kurzwort(VerificationNote.CONFLICT_OVERRIDDEN))
        assertEquals(" · Quellen uneinig", kurzwort(VerificationNote.CONFLICT_UNRESOLVED))
        assertEquals(" · unbestätigt", kurzwort(VerificationNote.UNVERIFIED_SINGLE))
        assertEquals("", kurzwort(VerificationNote.NONE))
        assertEquals("", kurzwort(null))
    }

    @Test fun `ein Fehler an einem versorgten Ort verdraengt die Abdeckung nicht`() {
        // Der letzte Abruf scheiterte, die Zeiten von vorher gelten aber
        // weiter — das ist die Lage, in der die App zwei Monate erfolglos
        // bleiben darf, ohne dass jemand etwas merkt. Die Zeile sagt zuerst,
        // was zaehlt: der Ort ist versorgt.
        val status = OfficialStatus(
            9807,
            LocalDate.of(2027, 7, 4),
            attempt,
            "Kein Netz",
            verification(VerificationNote.VERIFIED),
        )
        assertEquals("Nürnberg · bis 04.07.2027 · bestätigt", line(status))
    }
}
