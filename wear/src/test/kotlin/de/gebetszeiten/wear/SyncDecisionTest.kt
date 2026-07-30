package de.gebetszeiten.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncDecisionTest {

    private fun payload(lat: Double = 41.0082, lng: Double = 28.9784) = SyncDecision.Payload(
        scheduleText = "2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36",
        lat = lat,
        lng = lng,
        city = "Istanbul",
    )

    @Test
    fun `gueltiger Payload wird geparst`() {
        val schedule = SyncDecision.parse(payload())
        assertEquals(setOf(LocalDate.of(2026, 7, 30)), schedule!!.keys)
    }

    @Test
    fun `leerer oder unlesbarer Payload wird verworfen`() {
        assertNull(SyncDecision.parse(payload().copy(scheduleText = "")))
        assertNull(SyncDecision.parse(payload().copy(scheduleText = "voelliger unsinn")))
    }

    @Test
    fun `Erst-Sync uebernimmt den Handy-Ort`() {
        assertTrue(SyncDecision.shouldAdoptLocation(payload(), syncedLat = null, syncedLng = null))
    }

    @Test
    fun `gleicher Handy-Ort wie zuletzt - Uhr-Override bleibt`() {
        assertFalse(SyncDecision.shouldAdoptLocation(payload(), syncedLat = 41.0082, syncedLng = 28.9784))
    }

    @Test
    fun `neuer Handy-Ort wird uebernommen (ausserhalb 1-km-Toleranz)`() {
        assertTrue(SyncDecision.shouldAdoptLocation(payload(lat = 49.4521, lng = 11.0767), syncedLat = 41.0082, syncedLng = 28.9784))
    }

    @Test
    fun `noch nie gesynct - bestehendes DataItem wird nachgeholt`() {
        assertTrue(SyncDecision.shouldReplay(null, null))
        assertTrue(SyncDecision.shouldReplay(41.0082, null))
        assertTrue(SyncDecision.shouldReplay(null, 28.9784))
    }

    @Test
    fun `bereits gesynct - kein Nachholen beim App-Start`() {
        assertFalse(SyncDecision.shouldReplay(41.0082, 28.9784))
    }
}
