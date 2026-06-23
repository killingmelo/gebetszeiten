package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryClearTest {
    private val now = 1_000_000L
    private val transition = now + 3 * 60 * 60 * 1000L // 3 h später
    private val short = 5 * 60 * 1000L

    @Test fun persistentAndAudibleShortens() {
        assertEquals(
            now + short,
            entryClearAtMillis(now, transition, persistent = true, audible = true, shortMillis = short),
        )
    }

    @Test fun persistentButSilentKeepsTransition() {
        // Still+dauerhaft postet ohnehin kein Eintritts-Banner; der reine Helfer
        // gibt dennoch die lange Frist zurück.
        assertEquals(
            transition,
            entryClearAtMillis(now, transition, persistent = true, audible = false, shortMillis = short),
        )
    }

    @Test fun withoutPersistentKeepsTransition() {
        assertEquals(transition, entryClearAtMillis(now, transition, persistent = false, audible = true, shortMillis = short))
        assertEquals(transition, entryClearAtMillis(now, transition, persistent = false, audible = false, shortMillis = short))
    }

    @Test fun shortFrameCappedAtTransition() {
        val soon = now + 60_000L // Übergang in 1 Min
        assertEquals(
            soon,
            entryClearAtMillis(now, soon, persistent = true, audible = true, shortMillis = short),
        )
    }
}
