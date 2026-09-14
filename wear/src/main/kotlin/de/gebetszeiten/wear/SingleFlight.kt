package de.gebetszeiten.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

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

    /**
     * Schuetzt [inFlight]. Ein schlichter Monitor, KEIN `Mutex`: der
     * kritische Abschnitt suspendiert nie (er liest/schreibt ein Feld und
     * startet hoechstens ein `async`), und die Freigabe haengt seit
     * Fix-Runde 4 an `invokeOnCompletion` — einem NICHT suspendierbaren
     * Rueckruf, der einen `Mutex` gar nicht nehmen koennte, ohne dafuer
     * eine eigene Coroutine zu starten. Genau diese Coroutine waere das
     * naechste Loch: zwischen "Lauf fertig" und "Slot geraeumt" laege ein
     * Zeitfenster, in dem ein neuer Aufrufer das ABGESCHLOSSENE [Deferred]
     * bekaeme und dessen altes Ergebnis zurueckgereicht bekaeme. Der
     * Monitor raeumt synchron im Rueckruf und kennt dieses Fenster nicht.
     * `synchronized` ist wiedereintrittsfaehig, der Rueckruf darf also
     * (bei einem bereits abgeschlossenen Lauf) auch aus dem Abschnitt
     * heraus feuern, der ihn gerade registriert.
     */
    private val lock = Any()

    /** Der gerade laufende Durchlauf, falls einer laeuft. */
    private var inFlight: Deferred<T>? = null

    /**
     * Fuehrt [block] aus — oder, falls schon einer laeuft, wartet auf DESSEN
     * Ergebnis, ohne [block] ein zweites Mal aufzurufen.
     *
     * **Die Freigabe des Slots haengt am LAUF, nicht am Wartenden.** Vor
     * Fix-Runde 4 raeumte ein `finally` im Wartenden auf — dann gab ein
     * ABGEBROCHENER Wartender den Slot frei, waehrend der gemeinsame Lauf
     * noch lief, und der naechste Aufrufer startete einen ZWEITEN parallelen
     * Durchlauf. Die Zusage oben ("hoechstens ein laufender Durchlauf")
     * hielt damit nicht. `invokeOnCompletion` bindet das Aufraeumen
     * stattdessen an das Ende des Laufs selbst: solange er laeuft, haengen
     * sich neue Aufrufer daran; sobald er endet (Erfolg, Fehler ODER
     * Abbruch des Laufs), ist der Slot frei, und der naechste Aufrufer
     * startet frisch.
     *
     * Damit braucht [run] auch kein `withContext(NonCancellable)` mehr:
     * es gibt keinen Aufraeum-Abschnitt im Wartenden, den ein Abbruch
     * ueberspringen koennte. Ein abgebrochener Wartender bricht nur noch
     * sein eigenes `await()` ab — der Lauf und alle anderen Wartenden
     * bleiben unberuehrt, weil `scope.async` am [scope] des Konstruktors
     * haengt und nicht am Aufrufer.
     */
    suspend fun run(block: suspend () -> T): T {
        val deferred = synchronized(lock) {
            inFlight ?: scope.async { block() }.also { gestartet ->
                inFlight = gestartet
                gestartet.invokeOnCompletion {
                    synchronized(lock) { if (inFlight === gestartet) inFlight = null }
                }
            }
        }
        return deferred.await()
    }
}
