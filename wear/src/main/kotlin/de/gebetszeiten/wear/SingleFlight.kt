package de.gebetszeiten.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Buendelt gleichzeitige Aufrufe von [run] auf HOECHSTENS einen laufenden
 * Durchlauf von [block] — ein zweiter Aufrufer, der eintrifft, waehrend
 * schon einer laeuft, bekommt dasselbe Ergebnis, statt einen eigenen Lauf
 * zu starten. Reine Nebenlaeufigkeits-Buchfuehrung: kein Android, kein
 * Context, kein Wissen darueber, was [block] tut — herausgezogen aus
 * `WearRefresh.kt` (Fix-Runde 3), wo genau dieses Muster gebraucht wird
 * (drei unabhaengige Ausloeser — Activity, Kachel, Komplikation — koennen
 * beim selben Gebetsuebergang gleichzeitig eintreffen) und wo es vorher nur
 * inline stand, ungetestet unter dem Vorwand "das ist Context-Verdrahtung,
 * also ausserhalb der Testgrenze". Das stimmt fuer DIESEN Teil nicht: hier
 * steckt eigene Entscheidungslogik (wer wartet auf wen, wer raeumt auf),
 * und genau die ist unten getestet ([SingleFlightTest]).
 *
 * [scope] ist bewusst KEIN Parameter von [run], sondern des Konstruktors:
 * der geteilte Lauf muss laenger leben duerfen als jeder einzelne Aufrufer
 * (`PrayerTileService`/`PrayerComplicationService` sind gebundene Dienste,
 * die das System Sekunden nach der Antwort wieder loesen — ihr eigener
 * Scope waere fuer einen 25-s-Netzabruf zu kurzlebig). [scope] muss deshalb
 * unabhaengig von jedem einzelnen Aufrufer sein; in `WearRefresh.kt` ist das
 * ein datei-eigener, langlebiger `CoroutineScope(SupervisorJob() +
 * Dispatchers.IO)`.
 */
internal class SingleFlight<T>(private val scope: CoroutineScope) {

    /** Schuetzt [inFlight] — reine Buchhaltung, keine Suspendierung ausser
     *  bei echter Sperrkonkurrenz (siehe [run]). */
    private val lock = Mutex()

    /** Der gerade laufende Durchlauf, falls einer laeuft. */
    private var inFlight: Deferred<T>? = null

    /**
     * Fuehrt [block] aus — oder, falls schon einer laeuft, wartet auf DESSEN
     * Ergebnis, ohne [block] ein zweites Mal aufzurufen.
     *
     * **Das Aufraeumen laeuft unter [NonCancellable].** Wird DIESER Aufrufer
     * abgebrochen, waehrend er auf [inFlight] wartet (`deferred.await()`
     * wirft `CancellationException`), muss das `finally` trotzdem
     * [inFlight] zuruecksetzen koennen — sonst bliebe ein Slot mit einem
     * bereits ABGESCHLOSSENEN [Deferred] stehen, und jeder kuenftige
     * Aufrufer bekaeme dessen altes, laengst ueberholtes Ergebnis zurueck,
     * OHNE dass [block] je wieder liefe (ein Leck, das sich nicht von
     * selbst heilt — anders als ein einzelner uebersprungener Durchlauf).
     * `Mutex.lock()` hat einen unkonkurrierten Schnellpfad, der sogar fuer
     * einen bereits abgebrochenen Aufrufer durchlaeuft; ist der Lock aber
     * gerade BELEGT, suspendiert `lock()` und ein abgebrochener Aufrufer
     * bekaeme dort sofort die `CancellationException` statt zu warten — das
     * Aufraeumen faende dann gar nicht statt. `withContext(NonCancellable)`
     * schaltet genau das ab: dieser eine Codeabschnitt laeuft immer zu
     * Ende, egal wie sehr der Aufrufer schon abgebrochen ist.
     *
     * Der eigentliche Durchlauf selbst (`scope.async { block() }`) ist von
     * dieser Fallunterscheidung unberuehrt: er haengt am [scope] aus dem
     * Konstruktor, nicht am Aufrufer, und laeuft unabhaengig von jedem
     * einzelnen `run`-Aufruf weiter, auch wenn DESSEN Aufrufer abgebrochen
     * wird.
     */
    suspend fun run(block: suspend () -> T): T {
        val deferred = lock.withLock {
            inFlight ?: scope.async { block() }.also { inFlight = it }
        }
        try {
            return deferred.await()
        } finally {
            withContext(NonCancellable) {
                lock.withLock { if (inFlight === deferred) inFlight = null }
            }
        }
    }
}
