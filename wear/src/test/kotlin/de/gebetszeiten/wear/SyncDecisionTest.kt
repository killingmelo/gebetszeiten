package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
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

    @Test fun `ein frischerer eigener Stand schlaegt den Sync`() {
        assertFalse(SyncDecision.syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = 2_000L))
    }

    @Test fun `ohne eigenen Stand gewinnt der Sync`() {
        assertTrue(SyncDecision.syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = null))
    }

    @Test fun `bei gleichem Stand gewinnt der Sync - er ist billiger als ein Abruf`() {
        assertTrue(SyncDecision.syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = 1_000L))
    }

    private fun kopf(lat: Double, lng: Double, updatedEpochMs: Long) = CacheEntry(
        header = CacheHeader(
            latitude = lat,
            longitude = lng,
            locationId = null,
            firstDate = null,
            lastDate = null,
            updatedEpochMs = updatedEpochMs,
            lastAttemptEpochMs = null,
            lastError = null,
        ),
        schedule = emptyMap(),
    )

    @Test
    fun `eigener Zeitstempel wird am Sync-Ort nachgeschlagen - nicht am aktiven Uhr-Ort`() {
        // Nuernberg (Sync-Ort) hat einen AELTEREN eigenen Stand als Istanbul
        // (angenommen der aktive Uhr-Ort). Ein Vergleich mit Istanbuls
        // Zeitstempel waere Aepfel gegen Birnen (siehe Brief) - hier muss
        // Nuernbergs eigener Wert herauskommen, nicht Istanbuls hoeherer.
        val nuernberg = kopf(49.4521, 11.0767, updatedEpochMs = 5_000L)
        val istanbul = kopf(41.0082, 28.9784, updatedEpochMs = 9_000L)
        val entries = CacheStore.split(CacheStore.serialize(listOf(nuernberg, istanbul)))

        assertEquals(5_000L, SyncDecision.ownUpdatedEpochMs(entries, 49.4521, 11.0767))
    }

    @Test
    fun `noch nie eigenstaendig abgerufener Ort liefert keinen eigenen Zeitstempel`() {
        assertNull(SyncDecision.ownUpdatedEpochMs(emptyList(), 49.4521, 11.0767))
    }

    // Fix-Runde 1: die reine Funktion `syncWins` war von Anfang an richtig
    // und getestet, aber `WearSyncApplier` rief sie mit
    // `System.currentTimeMillis()` beim EMPFANG auf statt mit dem
    // TATSAECHLICHEN Abrufzeitpunkt des Telefons — das machte den Vergleich
    // strukturell wertlos (der Empfangszeitpunkt liegt IMMER nach jedem
    // zuvor persistierten eigenen Zeitstempel). `shouldApply` ist die volle
    // Verdrahtungs-Entscheidung inklusive des Datenzeitpunkts aus dem
    // Payload, damit genau DIESE Verwechslung mit plain JUnit auffaellt.

    @Test
    fun `Sync mit aelterem Datenzeitpunkt verliert gegen einen frischeren eigenen Stand`() {
        val syncPayload = payload().copy(updatedEpochMs = 1_000L)
        assertFalse(SyncDecision.shouldApply(syncPayload, ownUpdatedEpochMs = 2_000L))
    }

    @Test
    fun `Sync mit frischerem Datenzeitpunkt gewinnt gegen einen aelteren eigenen Stand`() {
        val syncPayload = payload().copy(updatedEpochMs = 2_000L)
        assertTrue(SyncDecision.shouldApply(syncPayload, ownUpdatedEpochMs = 1_000L))
    }

    @Test
    fun `Payload ohne Telefon-Zeitstempel (aeltere App-Version) - der Sync gewinnt immer`() {
        val syncPayload = payload().copy(updatedEpochMs = null)
        // Selbst ein VIEL frischerer eigener Stand darf hier nicht bremsen:
        // ohne WearSyncContract.KEY_UPDATED gibt es nichts, das man ehrlich
        // vergleichen koennte - das ist der Zustand von vor dieser
        // Zusicherung, kein Rueckschritt.
        assertTrue(SyncDecision.shouldApply(syncPayload, ownUpdatedEpochMs = Long.MAX_VALUE))
    }
}
