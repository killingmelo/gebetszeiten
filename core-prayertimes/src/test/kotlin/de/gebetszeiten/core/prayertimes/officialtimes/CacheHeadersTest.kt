package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheHeadersTest {

    // Nuernberg
    private val lat = 49.4521
    private val lng = 11.0767

    private fun header(
        lastAttemptEpochMs: Long? = 1_000L,
        lastError: String? = null,
        lastDate: java.time.LocalDate? = null,
    ) = CacheHeader(
        latitude = lat,
        longitude = lng,
        locationId = 42,
        firstDate = null,
        lastDate = lastDate,
        updatedEpochMs = 1_000L,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastError = lastError,
        verification = null,
    )

    @Test
    fun `refreshedHeader uebernimmt Versuch und Fehler und laesst Datumsgrenzen leer`() {
        val existing = header(lastAttemptEpochMs = 5L, lastError = "x")

        val result = refreshedHeader(
            existing = existing,
            lat = lat,
            lng = lng,
            locationId = 7,
            updatedEpochMs = 9_000L,
            verification = null,
        )

        assertEquals(5L, result.lastAttemptEpochMs)
        assertEquals("x", result.lastError)
        assertEquals(7, result.locationId)
        assertEquals(9_000L, result.updatedEpochMs)
        assertNull(result.firstDate)
        assertNull(result.lastDate)
    }

    @Test
    fun `withAttempt aktualisiert den vorhandenen Eintrag und laesst den Rumpf unberuehrt`() {
        val entries = listOf(RawEntry(header(lastDate = java.time.LocalDate.of(2026, 9, 6)), "BODY"))

        val result = CacheStore.withAttempt(
            entries = entries,
            error = "kaputt",
            nowEpochMs = 5_000L,
            lat = lat,
            lng = lng,
            pinnedCoords = emptyList(),
            maxUnpinned = 5,
        )

        assertEquals(1, result.size)
        assertEquals(5_000L, result[0].header.lastAttemptEpochMs)
        assertEquals("kaputt", result[0].header.lastError)
        assertEquals("BODY", result[0].body)
        assertEquals(1_000L, result[0].header.updatedEpochMs)
    }

    @Test
    fun `withAttempt legt fuer einen unbekannten Ort einen leeren Eintrag mit Fehler an`() {
        val result = CacheStore.withAttempt(
            entries = emptyList(),
            error = "kein Netz",
            nowEpochMs = 5_000L,
            lat = lat,
            lng = lng,
            pinnedCoords = emptyList(),
            maxUnpinned = 5,
        )

        assertEquals(1, result.size)
        val added = result[0]
        assertEquals("kein Netz", added.header.lastError)
        assertEquals(5_000L, added.header.lastAttemptEpochMs)
        assertEquals(5_000L, added.header.updatedEpochMs)
        assertNull(added.header.locationId)
        assertNull(added.header.lastDate)
        assertTrue(added.body.isEmpty())
    }
}
