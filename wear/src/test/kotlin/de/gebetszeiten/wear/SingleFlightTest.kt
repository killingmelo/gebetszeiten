package de.gebetszeiten.wear

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [SingleFlight] rein auf JVM-Ebene, ohne Android, ohne Context — kein
 * Robolectric noetig, genau wie `CompositeDiyanetFetcherTest` (Muster:
 * `die Quellen laufen nebenlaeufig - keine wartet auf die vorige`, dort mit
 * `delay`/Markierungslisten statt Stoppuhr).
 *
 * ALLE Tests laufen in `runBlocking { ... }` OHNE eigenen Dispatcher, also
 * auf einem einzigen kooperativen Event-Loop: eine mit `async {}` gestartete
 * Coroutine laeuft erst an einer SUSPENSION des Aufrufers weiter (`yield()`,
 * `delay()`, `await()` auf ein noch offenes `CompletableDeferred`). Das macht
 * die Interleaving-Reihenfolge deterministisch, ganz ohne Stoppuhr — dieselbe
 * Technik wie im net-diyanet-Vorbild.
 *
 * Nicht geprueft wird die EXAKTE Sperrkonkurrenz-Zeile in [SingleFlight.run]
 * (`Mutex.lock()` suspendiert nur, wenn der Lock in dem Moment WIRKLICH
 * belegt ist — auf einem einzigen kooperativen Thread ohne Suspension
 * innerhalb der kritischen Abschnitte kommt das praktisch nie vor). Getestet
 * wird stattdessen der AUSSEN sichtbare Vertrag, den `NonCancellable` in
 * [SingleFlight.run] garantiert: ein abgebrochener Aufrufer darf weder die
 * anderen mitreissen noch den Slot dauerhaft blockieren.
 */
class SingleFlightTest {

    @Test fun `zwei gleichzeitige Aufrufe fuehren den Block genau einmal aus`() = runBlocking {
        val callCount = AtomicInteger(0)
        val gate = CompletableDeferred<Int>()
        val singleFlight = SingleFlight<Int>(this)

        suspend fun block(): Int {
            callCount.incrementAndGet()
            return gate.await()
        }

        val a = async { singleFlight.run(::block) }
        // a muss im Block angekommen sein (haengt am Tor), bevor b startet —
        // sonst koennte b zufaellig zuerst dran sein und die Rollen waeren
        // nur vertauscht, nicht die Buendelung ungeprueft.
        while (callCount.get() == 0) yield()

        val b = async { singleFlight.run(::block) }
        // b muss den Slot ERREICHT und sich an dasselbe Deferred gehaengt
        // haben, statt block() ein zweites Mal aufzurufen.
        yield()
        assertEquals("b haette block() kein zweites Mal aufrufen duerfen", 1, callCount.get())

        gate.complete(42)
        assertEquals(42, a.await())
        assertEquals(42, b.await())
        assertEquals(1, callCount.get())
    }

    @Test fun `nach Abschluss ist der Slot frei - der naechste Aufrufer startet neu`() = runBlocking {
        val callCount = AtomicInteger(0)
        val singleFlight = SingleFlight<Int>(this)

        val erstesErgebnis = singleFlight.run { callCount.incrementAndGet(); 1 }
        val zweitesErgebnis = singleFlight.run { callCount.incrementAndGet(); 2 }

        assertEquals(1, erstesErgebnis)
        assertEquals(2, zweitesErgebnis)
        assertEquals(
            "der zweite Aufruf haette einen frischen Block-Lauf sehen muessen, nicht das alte Ergebnis",
            2,
            callCount.get(),
        )
    }

    @Test fun `ein gecancelter Aufrufer laesst den Slot nicht dauerhaft belegt zurueck`() = runBlocking {
        val callCount = AtomicInteger(0)
        val gate = CompletableDeferred<Int>()
        val singleFlight = SingleFlight<Int>(this)

        suspend fun block(): Int {
            callCount.incrementAndGet()
            return gate.await()
        }

        val a = async { singleFlight.run(::block) }
        while (callCount.get() == 0) yield() // a haengt im Block, am Tor.

        // a selbst wird abgebrochen, WAEHREND er auf das geteilte Ergebnis
        // wartet — der Fall aus Fix-Runde 3: ohne `NonCancellable` beim
        // Aufraeumen koennte der Slot danach auf einem laengst nutzlosen
        // Deferred sitzen bleiben.
        a.cancel()
        a.join()

        // Der eigentliche Lauf haengt weiter im SingleFlight-eigenen Scope
        // (unabhaengig von a) und schliesst jetzt ab — ohne dass noch
        // jemand darauf wartet.
        gate.complete(7)

        // Slot muss trotzdem frei sein: ein neuer Aufruf ruft den Block
        // ERNEUT auf. Ohne die Absicherung bekaeme dieser Aufruf hier still
        // das alte Ergebnis (7) zurueck, OHNE dass block2() je liefe.
        val gate2 = CompletableDeferred<Int>()
        suspend fun block2(): Int {
            callCount.incrementAndGet()
            return gate2.await()
        }
        val c = async { singleFlight.run(::block2) }
        while (callCount.get() == 1) yield()
        gate2.complete(9)

        assertEquals(
            "der Aufruf nach dem Abbruch haette einen frischen Block-Lauf sehen muessen",
            9,
            c.await(),
        )
        assertEquals(2, callCount.get())
    }
}
