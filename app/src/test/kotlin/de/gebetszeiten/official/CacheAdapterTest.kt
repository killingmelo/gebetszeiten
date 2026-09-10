package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Die reinen Haelften von [OfficialTimesCache]: die Umformung Kopf → Status
 * ([statusOf]) und der Kopf eines gelungenen Abrufs ([refreshedHeader]).
 *
 * Sie stehen ausserhalb der Klasse, weil die Klasse einen `Context` braucht
 * und dieses Projekt kein Robolectric hat — was drinnen liegt, ist
 * ungeprueft. Genau hier war die Luecke: ein weggelassenes Prueferzeugnis
 * und eine uebernommene alte Pruefnotiz haben eine Pruefrunde ueberlebt.
 */
class CacheAdapterTest {

    private val notiz = Verification(
        note = VerificationNote.VERIFIED,
        chosen = SourceId.DIRECT,
        confirmedBy = listOf(SourceId.PROXY_ABDUS),
        comparedDays = 31,
        differingDays = 0,
        maxAbsMinutes = 0,
        firstDiff = null,
        checkedEpochMs = 1_786_000_000_000L,
    )

    private fun header(
        locationId: Int? = 9807,
        firstDate: LocalDate? = LocalDate.of(2026, 9, 6),
        lastDate: LocalDate? = LocalDate.of(2027, 7, 4),
        updatedEpochMs: Long = 1_000L,
        lastAttemptEpochMs: Long? = 2_000L,
        lastError: String? = null,
        verification: Verification? = null,
    ) = CacheHeader(
        latitude = 49.4521,
        longitude = 11.0767,
        locationId = locationId,
        firstDate = firstDate,
        lastDate = lastDate,
        updatedEpochMs = updatedEpochMs,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastError = lastError,
        verification = verification,
    )

    @Test fun `statusOf reicht jedes Feld durch, auch die Pruefnotiz`() {
        val status = statusOf(
            header(lastAttemptEpochMs = 2_000L, lastError = "Kein Netz", verification = notiz),
        )

        assertEquals(9807, status.locationId)
        // Die Abdeckung ist der LETZTE Tag des Zeitplans, nicht der erste.
        assertEquals(LocalDate.of(2027, 7, 4), status.coveredUntil)
        assertEquals(2_000L, status.lastAttemptEpochMs)
        assertEquals("Kein Netz", status.lastError)
        // Ohne sie zeigte kein Favorit je ein Kurzwort — und die Statuszeile
        // des aktiven Orts keine Gegenpruefung.
        assertEquals(notiz, status.verification)
    }

    @Test fun `statusOf macht aus keinem Eintrag einen leeren Status, nicht null`() {
        val status = statusOf(null)

        assertNull(status.locationId)
        assertNull(status.coveredUntil)
        assertNull(status.lastAttemptEpochMs)
        assertNull(status.lastError)
        assertNull(status.verification)
    }

    @Test fun `refreshedHeader ersetzt die Pruefnotiz und uebernimmt das Versuchsprotokoll`() {
        val alt = header(
            lastAttemptEpochMs = 2_000L,
            lastError = "Kein Netz",
            verification = notiz.copy(note = VerificationNote.CONFLICT_UNRESOLVED),
        )
        val neu = notiz.copy(checkedEpochMs = 9_000L)

        val kopf = refreshedHeader(
            existing = alt,
            lat = 49.4521,
            lng = 11.0767,
            locationId = 9807,
            updatedEpochMs = 5_000L,
            verification = neu,
        )

        // Das Zeugnis gehoert zum NEUEN Zeitplan. Bliebe das alte kleben,
        // stuende „bestätigt" ueber Zeiten, die diese Pruefung nie gesehen
        // hat.
        assertEquals(neu, kopf.verification)
        // Das Versuchsprotokoll gehoert zum ORT und bleibt.
        assertEquals(2_000L, kopf.lastAttemptEpochMs)
        assertEquals("Kein Netz", kopf.lastError)
        assertEquals(5_000L, kopf.updatedEpochMs)
        // Abdeckung leitet `CacheStore` aus dem Zeitplan ab.
        assertNull(kopf.firstDate)
        assertNull(kopf.lastDate)
    }

    @Test fun `refreshedHeader ohne Zeugnis laesst keines stehen`() {
        val alt = header(verification = notiz)

        val kopf = refreshedHeader(
            existing = alt,
            lat = 49.4521,
            lng = 11.0767,
            locationId = 9807,
            updatedEpochMs = 5_000L,
            verification = null,
        )

        assertNull(kopf.verification)
    }

    @Test fun `refreshedHeader ohne vorhandenen Eintrag erfindet kein Versuchsprotokoll`() {
        val kopf = refreshedHeader(
            existing = null,
            lat = 49.4521,
            lng = 11.0767,
            locationId = 9807,
            updatedEpochMs = 5_000L,
            verification = notiz,
        )

        assertNull(kopf.lastAttemptEpochMs)
        assertNull(kopf.lastError)
        assertEquals(notiz, kopf.verification)
        assertEquals(9807, kopf.locationId)
    }
}
