package de.gebetszeiten.wear

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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
 * **Jeder Test steht unter einer Zeitgrenze** ([ZEITGRENZE_MS]), und zwar
 * doppelt: der ganze Testkoerper, und zusaetzlich jede einzelne Warteschleife
 * ueber [warteBis]. Das ist kein Schmuck — in Fix-Runde 3 stand hier ein
 * `while (callCount.get() == 1) yield()` ohne Grenze, und unter genau der
 * Mutation, die der Test toeten sollte, HING der Testlauf endlos, statt rot
 * zu werden. Ein Fehler muss als Fehler erscheinen.
 *
 * Welche Mutation welcher Fall toetet (Fix-Runde 4 jeweils durch Einsetzen
 * belegt):
 *
 * 1. `zwei gleichzeitige Aufrufe ...` toetet **"keine Buendelung"**: ersetzt
 *    man in [SingleFlight.run] `inFlight ?: scope.async { block() }...` durch
 *    ein bedingungsloses `scope.async { block() }`, laeuft der Block zweimal.
 * 2. `nach Abschluss ist der Slot frei ...` toetet **"nie freigeben"**: faellt
 *    das `invokeOnCompletion { ... inFlight = null }` weg, bekommt der zweite
 *    Aufrufer stillschweigend das alte Ergebnis des ersten.
 * 3. `ein gecancelter Wartender ...` toetet **"Freigabe am Wartenden statt am
 *    Lauf"** — also genau den Zustand vor Fix-Runde 4 (`try/finally` um
 *    `await()`, das `inFlight` zuruecksetzt): dann gibt der ABGEBROCHENE
 *    Wartende den Slot frei, obwohl der gemeinsame Lauf noch laeuft, und der
 *    naechste Aufrufer startet einen zweiten parallelen Durchlauf. Derselbe
 *    Fall deckt mit ab, dass ein Abbruch die ANDEREN Wartenden nicht
 *    mitreisst.
 *
 * Nicht geprueft wird die EXAKTE Sperrkonkurrenz-Zeile in [SingleFlight.run]
 * (auf einem einzigen kooperativen Thread ohne Suspension innerhalb der
 * kritischen Abschnitte laesst sie sich nicht deterministisch erzwingen).
 * Getestet wird der AUSSEN sichtbare Vertrag: hoechstens ein laufender
 * Durchlauf, Freigabe erst mit seinem Ende, Abbruch-Isolation.
 */
class SingleFlightTest {

    @Test fun `zwei gleichzeitige Aufrufe fuehren den Block genau einmal aus`() = runBlocking {
        withTimeout(ZEITGRENZE_MS) {
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
            warteBis("a den Block betreten hat") { callCount.get() == 1 }

            val b = async { singleFlight.run(::block) }
            // b muss den Slot ERREICHT und sich an dasselbe Deferred gehaengt
            // haben, statt block() ein zweites Mal aufzurufen.
            ereignisschleifeLaufenLassen()
            assertEquals("b haette block() kein zweites Mal aufrufen duerfen", 1, callCount.get())

            gate.complete(42)
            assertEquals(42, a.await())
            assertEquals(42, b.await())
            assertEquals(1, callCount.get())
        }
    }

    @Test fun `nach Abschluss ist der Slot frei - der naechste Aufrufer startet neu`() = runBlocking {
        withTimeout(ZEITGRENZE_MS) {
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
    }

    @Test fun `ein gecancelter Wartender gibt den Slot nicht frei und reisst die anderen nicht mit`() = runBlocking {
        withTimeout(ZEITGRENZE_MS) {
            val callCount = AtomicInteger(0)
            val gate = CompletableDeferred<Int>()
            val singleFlight = SingleFlight<Int>(this)

            suspend fun block(): Int {
                callCount.incrementAndGet()
                return gate.await()
            }

            val a = async { singleFlight.run(::block) }
            warteBis("a den Block betreten hat") { callCount.get() == 1 }

            // ZWEITER Wartender, noch vor dem Abbruch: nur mit ihm laesst sich
            // ueberhaupt pruefen, dass ein Abbruch "die anderen nicht
            // mitreisst".
            val b = async { singleFlight.run(::block) }
            ereignisschleifeLaufenLassen()
            assertEquals("b haette sich an den laufenden Durchlauf haengen muessen", 1, callCount.get())

            // a wird abgebrochen, WAEHREND der gemeinsame Lauf noch am Tor
            // haengt.
            a.cancel()
            a.join()

            // Kernpruefung gegen "Freigabe am Wartenden": der Lauf laeuft noch
            // (das Tor ist zu), also muss der Slot noch belegt sein. Ein JETZT
            // eintreffender Aufrufer haengt sich an — er darf keinen zweiten
            // parallelen Durchlauf starten.
            val c = async { singleFlight.run(::block) }
            ereignisschleifeLaufenLassen()
            assertEquals(
                "der Abbruch von a haette den Slot nicht freigeben duerfen - der Lauf laeuft noch",
                1,
                callCount.get(),
            )

            gate.complete(7)
            assertEquals("b haette das gemeinsame Ergebnis bekommen muessen", 7, b.await())
            assertEquals("c haette das gemeinsame Ergebnis bekommen muessen", 7, c.await())

            // Erst mit dem ENDE des Laufs ist der Slot frei: der naechste
            // Aufrufer muss einen frischen Block-Lauf ausloesen, statt still
            // das alte (laengst wertlose) Ergebnis zu bekommen.
            val gate2 = CompletableDeferred<Int>()
            suspend fun block2(): Int {
                callCount.incrementAndGet()
                return gate2.await()
            }
            val d = async { singleFlight.run(::block2) }
            warteBis("d einen frischen Block-Lauf ausgeloest hat") { callCount.get() == 2 }
            gate2.complete(9)

            assertEquals(
                "der Aufruf nach dem Ende des Laufs haette einen frischen Block-Lauf sehen muessen",
                9,
                d.await(),
            )
            assertEquals(2, callCount.get())
        }
    }

    /**
     * Laesst den kooperativen Event-Loop mehrere Runden durchlaufen, damit
     * ALLES, was gerade eingereiht ist, auch tatsaechlich laufen konnte —
     * bevor geprueft wird, dass eben NICHTS Zusaetzliches gelaufen ist.
     *
     * Ein einzelnes `yield()` genuegt dafuer nicht: ein faelschlich zweiter
     * `scope.async { block() }` wird beim ersten `yield()` nur EINGEREIHT
     * (der Aufrufer landet danach im `await()`), sein `block()` liefe erst
     * eine Runde spaeter. Eine Pruefung nach nur einem `yield()` saehe die
     * Mutation also gar nicht. Vier Runden sind reichlich fuer die hier
     * hoechstens zwei verschraenkten Coroutinen — und harmlos, weil der
     * gruene Fall an einem `yield()` ueberhaupt nichts tut.
     */
    private suspend fun ereignisschleifeLaufenLassen(runden: Int = 4) {
        repeat(runden) { yield() }
    }

    /**
     * Wartet kooperativ, bis [bedingung] zutrifft — aber hoechstens
     * [ZEITGRENZE_MS]. Ohne diese Grenze wuerde eine Mutation, die die
     * Bedingung nie eintreten laesst, den Testlauf HAENGEN statt ihn rot zu
     * faerben (genau der Befund an der Vorgaengerfassung).
     */
    private suspend fun warteBis(was: String, bedingung: () -> Boolean) {
        try {
            withTimeout(ZEITGRENZE_MS) {
                while (!bedingung()) yield()
            }
        } catch (e: TimeoutCancellationException) {
            fail("Zeitgrenze (${ZEITGRENZE_MS} ms) beim Warten darauf, dass $was")
        }
    }

    private companion object {
        /**
         * Grosszuegig gegenueber jeder realen Ausfuehrung (alles hier ist
         * reine Speicher-Buchfuehrung auf einem Event-Loop, im gruenen Fall
         * Mikrosekunden) und trotzdem kurz genug, dass ein CI-Lauf bei einer
         * Regression nicht stehenbleibt.
         */
        const val ZEITGRENZE_MS = 5_000L
    }
}
