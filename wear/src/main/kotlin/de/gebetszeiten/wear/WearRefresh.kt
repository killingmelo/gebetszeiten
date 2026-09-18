package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Letzte Rettungsleine des [refreshScope]. Sie sollte nie greifen — jeder
 * Pfad in [doRefresh] und [launchWearRefresh] faengt seine Ausnahmen selbst
 * ab. Aber ein unbehandelter Fehler in einer Coroutine geht auf Android an
 * den Default-Handler, und der beendet den PROZESS: eine `IOException` aus
 * dem DataStore duerfte die Uhr nicht abstuerzen lassen, nur weil niemand
 * hinsieht. `SupervisorJob` leistet das ausdruecklich NICHT — es haelt nur
 * Geschwister-Coroutines am Leben, faengt aber keine Ausnahme ab.
 */
private val refreshExceptionHandler = CoroutineExceptionHandler { _, e ->
    android.util.Log.e("WearRefresh", "unbehandelter Fehler im Auffrisch-Scope", e)
}

/**
 * Eigener, langlebiger Scope fuer den Netzabruf — unabhaengig vom Scope des
 * jeweiligen Aufrufers. `MainActivity` haelt selbst einen langlebigen
 * `MainScope`, aber `PrayerTileService`/`PrayerComplicationService` sind
 * GEBUNDENE Dienste: das System loest sie Sekunden nach der Antwort wieder,
 * ihr eigener Scope waere fuer einen bis zu 25 s laufenden Netzabruf zu
 * kurzlebig (Fix-Runde 3, Important: ohne diesen datei-eigenen Scope stirbt
 * die Neuzeichnung mit dem Service, bevor `await()` je zurueckkehrt — siehe
 * [launchWearRefresh]). `SupervisorJob`, damit ein Fehlschlag EINES Abrufs
 * nicht den Scope fuer alle folgenden Aufrufe mit umbringt;
 * [refreshExceptionHandler] als zusaetzliche Rettungsleine fuer den Fall,
 * dass doch einmal etwas an allen `catch`-Zweigen vorbeikommt.
 */
private val refreshScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + refreshExceptionHandler)

/**
 * Buendelt gleichzeitige Aufrufe von [refreshWearOfficial] — die Uhr hat
 * DREI unabhaengige Ausloeser (Activity, Kachel, Komplikation), anders als
 * das Telefon mit seinem einzelnen Alarm-Receiver. Treffen zwei davon
 * gleichzeitig ein (z. B. Handgelenk-Heben zeigt Kachel UND Komplikation
 * neu, genau beim faelligen Gebetsuebergang), bekommt der zweite Aufrufer
 * dasselbe Ergebnis, statt einen eigenen Lauf zu starten — aus potenziell
 * drei gleichzeitigen Ablaeufen (neun HTTP-Anfragen ueber die
 * Bluetooth-Strecke der Uhr) wird hoechstens einer (drei Anfragen).
 *
 * Reine Buendelungs-Mechanik in [SingleFlight] (herausgezogen und dort
 * eigenstaendig getestet, Fix-Runde 3) — hier nur die Verdrahtung mit
 * [refreshScope].
 *
 * Bewusst EIN gemeinsamer Slot fuer die ganze Funktion, nicht einer je Ort:
 * die Uhr hat ohnehin nur den einen aktiven Ort ([WearSettings.location]),
 * ein zweiter Aufruf waehrend eines laufenden waere fuer denselben Ort
 * gedacht. Trifft ein `force`-Aufruf auf einen bereits laufenden
 * unforcierten, wird trotzdem nur der laufende abgewartet — `force` ist
 * (Stand dieser Aufgabe) an keinem Uhr-Knopf verdrahtet, dieser Fall also
 * praktisch nicht erreichbar; sollte er es werden, verdient er eine eigene
 * Entscheidung, kein stillschweigendes Downgrade hier.
 */
private val refreshSingleFlight = SingleFlight<Boolean>(refreshScope)

/**
 * Die Uhr ruft ihre amtlichen Zeiten selbst ab, statt nur auf den Sync vom
 * Handy zu warten (siehe [WearOfficialCache]). Muster:
 * `PrayerProvider.refreshOfficial` am Telefon — aber nur fuer den EINEN
 * gewaehlten Uhr-Ort ([WearSettings.location]), kein Favoritenreigen: die
 * Uhr fuehrt keine eigene Favoritenliste.
 *
 * Dieselbe Wiederholungs-Bremse wie am Telefon ([needsRefresh] ueber
 * [chooseTarget]) verhindert, dass Kachel und Komplikation bei jedem
 * Zeichnen erneut abrufen.
 *
 * **Wirft nie.** Jeder Fehlerpfad in [doRefresh] endet in `false` — auch
 * eine `IOException` aus dem DataStore oder aus dem Einlesen der
 * Ortstabellen. Das ist keine Kosmetik: alle drei Aufrufer starten diese
 * Funktion in einem `launch` ohne eigenen Aufrufer-`try`, und durch die
 * Buendelung ([refreshSingleFlight]) traefe EINE entkommene Ausnahme
 * gleich ALLE wartenden Aufrufer. Einzige Ausnahme ist
 * `CancellationException` — ein echter Abbruch ist kein Fehler und muss
 * weiterlaufen duerfen.
 *
 * **Muss NICHT-BLOCKIEREND aufgerufen werden.** `withTimeout(25_000)` unten
 * kann einen echten Netzabruf bis zu 25 s laufen lassen. `PrayerTileService`
 * und `PrayerComplicationService` werden vom System auf dem HAUPT-THREAD
 * aufgerufen (ANR-Schwelle 5 s) — sie rufen diese Funktion deshalb NIE
 * direkt auf, sondern ueber [launchWearRefresh] (fire-and-forget im
 * datei-eigenen [refreshScope], siehe dort — NICHT im eigenen Service-Scope,
 * der stirbt, bevor `await()` zurueckkehrt). `MainActivity` geht BEIDE Wege
 * (Fix-Runde 4, Important 1): [launchWearRefresh] traegt den Abruf samt
 * Anstoessen an Kachel, Komplikation und Vibrationskette im langlebigen
 * Scope, und daneben haengt sich `onStart` per `scope.launch {
 * withContext(Dispatchers.IO) { refreshWearOfficial(...) } }` als zweiter
 * Aufrufer an denselben Lauf — nur, um den eigenen Bildschirm neu zu
 * zeichnen, den sonst niemand erreicht. Auch das nicht-blockierend und
 * ausserhalb des synchronen `onStart`-Pfads; wird dieser zweite Aufrufer
 * abgebrochen ("niemand schaut mehr hin"), laeuft der Abruf unbeeinflusst
 * im geteilten [refreshScope] weiter (siehe [SingleFlight]).
 *
 * Offline-Flavor: [WearFetchProvider.isOnline] ist `false` — sofortige
 * Rueckkehr, ohne dass hier auch nur eine DataStore geoeffnet wird.
 *
 * Laeuft dagegen AUCH, wenn der Notausgang ([WearSettings.calculationFillsGaps])
 * eingeschaltet ist — bis Task 16 (unter dem alten Namen `useCalculated`,
 * "immer rechnen") stand hier ein fruehes `return false`, weil ein Abruf
 * dessen Ergebnis nie angezeigt haette. Seit der Schalter nur noch LUECKEN
 * fuellt, will ein Nutzer, der ihn einschaltet, amtliche Zeiten WEITERHIN —
 * ein Abruf ist also GENAUSO sinnvoll wie ohne (dieselbe Begruendung wie
 * `canFetchOfficial()`/`PrayerProvider.refreshOfficial` am Telefon).
 *
 * [force] (der "Jetzt aktualisieren"-Knopf, falls die Uhr einen bekommt)
 * durchbricht die Bremse fuer den aktiven Ort — dieselbe Semantik wie am
 * Telefon.
 *
 * @return `true` nur, wenn tatsaechlich neue Zeiten abgelegt wurden (also
 *   ein Neuzeichnen lohnt) — `false` bei jedem fruehen Ausstieg, wenn kein
 *   Ort faellig war, oder wenn der Abruf fehlschlug/leer blieb/dem Timeout
 *   unterlag.
 */
suspend fun refreshWearOfficial(context: Context, force: Boolean = false): Boolean {
    // Reine Konstante des Flavors, kein DataStore, kein Asset — das einzige,
    // was hier VOR der Buendelungs-Huelle stehen darf, ohne einen eigenen
    // `try` zu brauchen.
    if (!WearFetchProvider.isOnline) return false

    return refreshSingleFlight.run { doRefresh(context, force) }
}

/**
 * Startet [refreshWearOfficial] fire-and-forget im datei-eigenen,
 * langlebigen [refreshScope] und ruft bei Erfolg [notifyWearOfficialRefreshed]
 * auf — fuer `PrayerTileService`/`PrayerComplicationService`, die selbst
 * KEINEN fuer einen Netzabruf hinreichend langlebigen Scope halten
 * (Begruendung an [refreshScope]), UND fuer `MainActivity.onStart` (seit
 * Fix-Runde 3, Important 1 — siehe dort: ein `scope.launch` im
 * Activity-gebundenen `MainScope` wird abgebrochen, sobald `onDestroy`
 * laeuft, und riss die Fortsetzung mit sich, obwohl der Abruf selbst dank
 * [SingleFlight] im [refreshScope] ueberlebte und die Zeiten schon ablegte).
 *
 * Nicht `context.lifecycleScope` oder ein per-Service-Feld: genau DAS war
 * der Fix-Runde-3-Befund (Aufgabe 6) — ein `scope.launch {...}` im
 * Service-eigenen Scope wird abgebrochen, sobald das System den Dienst kurz
 * nach der Antwort wieder loest, und das Neuzeichnen feuert dann nur noch,
 * wenn der Abruf zufaellig schneller war als der Dienst lebte — also fast
 * nie im Zielfall (langsames Netz), fast immer nur dann, wenn die Bremse
 * ohnehin `false` geliefert haette.
 *
 * Ruft bei Erfolg NICHT mehr einen aufruferspezifischen `onUpdated`-Lambda
 * auf (Fix-Runde 2, Important 1 — vorher stiess `PrayerTileService` nur die
 * Kachel an und `PrayerComplicationService` nur die Komplikation).
 * [notifyWearOfficialRefreshed] stoesst jetzt IMMER beide Oberflaechen an,
 * unabhaengig davon, wer den Abruf ausgeloest hat.
 */
fun launchWearRefresh(context: Context) {
    refreshScope.launch {
        try {
            if (refreshWearOfficial(context)) {
                notifyWearOfficialRefreshed(context)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("WearRefresh", "Auffrischen/Neuzeichnen fehlgeschlagen", e)
        }
    }
}

/**
 * Alles, was passieren muss, damit weder Kachel noch Komplikation auf einem
 * veralteten Stand einfrieren, nachdem sich etwas geaendert haben KANN, das
 * eine naechste Zeit betrifft.
 *
 * **Die App (Activity) gehoert ausdruecklich NICHT dazu.** Die Uhr hat VIER
 * Oberflaechen, die heilen muessen — App, Kachel, Komplikation und die
 * Vibrationskette —, aber diese Funktion erreicht nur drei davon:
 * [WearVibration.reschedule] plus [notifyWearSurfaces] (Kachel und
 * Komplikation). Fuer den Bildschirm gibt es keinen Anstoss von aussen; die
 * Activity zeichnet sich selbst neu (`MainActivity.refresh()`), und JEDER
 * Aufrufer, der aus einer sichtbaren Activity heraus laeuft, muss das
 * zusaetzlich selbst tun. Genau diese Zeile fehlte bis Fix-Runde 4 in
 * `MainActivity.onStart` (dort Important 1): Kachel und Komplikation heilten
 * nach einem gelungenen Abruf, der Bildschirm davor nicht.
 *
 * Vier Aufrufer, alle gleichrangig behandelt statt die Heilung von Hand
 * nachzubauen:
 * - [launchWearRefresh] (ein erfolgreicher eigener Abruf — Kachel,
 *   Komplikation, seit Fix-Runde 3 auch `MainActivity.onStart`; dort seit
 *   Fix-Runde 4 zusaetzlich ein eigener, an denselben Lauf gehaengter
 *   Aufruf von [refreshWearOfficial], nur fuer das Neuzeichnen des
 *   Bildschirms),
 * - `WearSyncApplier.apply` (ein neuer Stand vom Handy, Fix-Runde 3,
 *   Important 2 — vorher drei Zeilen von Hand, OHNE `try`),
 * - `WearAlarmReceiver.onReceive` (Boot/Update/Uhrzeit-/Zeitzonenwechsel,
 *   Fix-Runde 3, Important 3 — vorher NUR [WearVibration.reschedule], ohne
 *   die beiden Oberflaechen anzustossen),
 * - der Notausgang-Toggle in `MainActivity` (seit Fix-Runde 4, Important 3 —
 *   vorher schrieb er [WearVibration.reschedule] und [notifyWearSurfaces]
 *   einzeln und ohne `try` von Hand, im `MainScope` ohne
 *   `CoroutineExceptionHandler`: eine `IOException` aus dem DataStore liess
 *   dort das Neuzeichnen ausfallen UND die App abstuerzen). Er ruft KEIN
 *   [refreshWearOfficial] davor — die lokale Berechnung loest den Leerfall
 *   sofort auf, ein Netzabruf ist dafuer nicht noetig.
 *
 * [WearVibration.reschedule] und [notifyWearSurfaces] laufen in GETRENNTEN
 * `try`-Bloecken (Fix-Runde 2, Important 2): ein Fehlschlag beim
 * Neubewerten der Vibrationskette (z. B. eine `IOException` beim
 * DataStore-Lesen) darf das Neuzeichnen nicht verhindern — die Zeiten sind
 * zu diesem Zeitpunkt bereits abgelegt, nur GENAU DAS wuerde sonst
 * verloren gehen, wenn `reschedule` vor dem Neuzeichnen stuende und warf.
 *
 * Faengt jede Ausnahme selbst ab, statt sich auf einen Aufrufer-`try` zu
 * verlassen: nicht alle vier Aufrufer haben einen fuer Netzabrufe gedachten
 * Rettungs-Scope wie [refreshScope] (`WearSyncApplier.apply` etwa laeuft per
 * `runBlocking` auf einem Binder-Thread, der Notausgang-Toggle im blanken
 * `MainScope` der Activity — beide ohne `CoroutineExceptionHandler`), und
 * `notifyWearSurfaces` ruft fremden Code (Tiles-/Komplikations-Framework),
 * der werfen kann, wenn das Ziel gerade nicht erreichbar ist.
 */
suspend fun notifyWearOfficialRefreshed(context: Context) {
    try {
        WearVibration.reschedule(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("WearRefresh", "Vibrationskette konnte nicht neu bewertet werden", e)
    }
    try {
        notifyWearSurfaces(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("WearRefresh", "Neuzeichnen (Komplikation/Kachel) fehlgeschlagen", e)
    }
}

/**
 * Stoesst Komplikation UND Kachel GEMEINSAM neu an — nie nur eine der
 * beiden (Fix-Runde 1/2, Important 1). Zwei Aufrufer:
 * [notifyWearOfficialRefreshed] (der Regelweg, mit Vibrationskette und
 * `try`-Kapselung) und `CityPickerActivity.pick`, das die Vibrationskette
 * unmittelbar davor selbst neu bestellt.
 *
 * Genau EINE Ausnahme von "nie nur eine der beiden", bewusst nicht ueber
 * diese Funktion: der Modus-Umschalter (Uhrzeit ⇄ Restzeit) in
 * `MainActivity` fordert allein die Komplikation neu an. Die Kachel zeigt
 * nie die Restzeit, ein Anstoss waere dort Arbeit ohne sichtbare Wirkung —
 * keine vergessene Handschrift, sondern die einzige Stelle, an der genau
 * eine Oberflaeche richtig ist.
 *
 * Erreicht die vierte Oberflaeche NICHT: den App-Bildschirm. Siehe
 * [notifyWearOfficialRefreshed].
 *
 * Kachel und Komplikation stossen sich damit potenziell GEGENSEITIG an:
 * `onTileRequest` ruft am Ende [launchWearRefresh], dessen Erfolg wiederum
 * diese Funktion aufruft und damit auch die Komplikation neu anfordert —
 * und umgekehrt. Das schwingt nicht, weil beide Anfragen letztlich wieder
 * bei [refreshWearOfficial] landen, und dessen Wiederholungs-Bremse
 * (`chooseTarget`/`needsRefresh`) nach einem bereits erfolgreichen Abruf
 * `false` liefert — der zweite, gegenseitig ausgeloeste Anlauf bricht dort
 * fruehzeitig ab, OHNE diese Funktion je ein zweites Mal zu erreichen. Die
 * Schleifensicherheit haengt damit ALLEIN an dieser Bremse; sie selbst
 * bremst hier nichts.
 */
fun notifyWearSurfaces(context: Context) {
    androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
        .create(context, android.content.ComponentName(context, PrayerComplicationService::class.java))
        .requestUpdateAll()
    androidx.wear.tiles.TileService.getUpdater(context).requestUpdate(PrayerTileService::class.java)
}

private suspend fun doRefresh(context: Context, force: Boolean): Boolean {
    val now = System.currentTimeMillis()
    // Der Ort, unter dem ein Fehlschlag verbucht wird. Wird erst gesetzt,
    // sobald er ueberhaupt bekannt ist: faellt die Ausnahme davor (beim
    // Lesen der Einstellungen oder beim Oeffnen des Fetchers), gibt es
    // keinen Ort, unter dem `recordAttempt` etwas festhalten koennte — dann
    // bleibt es beim Protokolleintrag.
    var versuchsOrt: Pair<Double, Double>? = null
    return try {
        val fetcher = WearFetchProvider.fetcher(context) ?: return false

        val location = WearSettings.location(context)
        val active = location.latitude to location.longitude
        versuchsOrt = active
        val today = LocalDate.now()

        // Bei `force` wird `dueOrder` nicht einmal gelesen — dieselbe Abkuerzung
        // wie in `PrayerProvider.refreshOfficial`: ein DataStore-Read weniger im
        // Klick-Pfad, `chooseTarget` liefert bei `force` ohnehin immer
        // [activeCoords].
        val target = chooseTarget(
            due = if (force) emptyList() else WearOfficialCache.dueOrder(context, active, today),
            activeCoords = active,
            force = force,
            today = today,
            nowEpochMs = now,
        ) ?: return false
        versuchsOrt = target
        val (targetLat, targetLng) = target

        // Dasselbe Budget wie am Telefon (~25 s). Laeuft seit Fix-Runde 2
        // NIE mehr auf dem Haupt-Thread eines Zeichenpfads — siehe KDoc an
        // [refreshWearOfficial]/[launchWearRefresh].
        withTimeout(25_000) {
            val city = WearSettings.city(context)
            val preferredId = WearOfficialCache.cachedLocationId(context, targetLat, targetLng)
            val result = fetcher.fetch(targetLat, targetLng, city, preferredId)
            if (result.schedule.isEmpty()) {
                WearOfficialCache.recordAttempt(context, "Kein Ergebnis von den amtlichen Quellen", now, targetLat, targetLng)
                return@withTimeout false
            }
            WearOfficialCache.put(context, result.schedule, targetLat, targetLng, result.locationId)
            // error = null: ein gelungener Abruf ist kein Fehler, auch wenn
            // nicht ALLE drei Quellen antworteten — dieselbe Begruendung wie
            // in `PrayerProvider.refreshOfficial`.
            WearOfficialCache.recordAttempt(context, null, now, targetLat, targetLng)
            true
        }
    } catch (e: TimeoutCancellationException) {
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Timeout)", e)
        verbucheFehlschlag(context, "Zeitüberschreitung beim Abruf", now, versuchsOrt)
        false
    } catch (e: CancellationException) {
        // Ein ECHTER Abbruch (z. B. der Aufrufer selbst wird abgebrochen)
        // muss weiterlaufen, nicht als Fehler protokolliert werden — sonst
        // saehe ein normaler App-Wechsel wie ein gescheiterter Abruf aus.
        // `TimeoutCancellationException` (oben) ist der einzige Fall, den
        // WIR selbst ausloesen und deshalb auch selbst deuten duerfen. Sie
        // MUSS deshalb vor diesem Zweig stehen: sie ist eine Unterklasse von
        // `CancellationException`, ein `catch` faengt immer den ersten
        // passenden Zweig, und die Reihenfolge ist hier der einzige
        // Unterschied zwischen "Timeout korrekt verbucht" und "Timeout still
        // als Abbruch durchgereicht". (Kotlin meldet eine ungluecklich
        // sortierte `catch`-Kette NICHT — anders als Java gibt es hier keinen
        // "unreachable catch"-Fehler, der uns vor dem Vertauschen schuetzen
        // wuerde.)
        throw e
    } catch (e: Exception) {
        // Alles andere (z. B. eine IOException aus dem DataStore oder beim
        // Einlesen der Ortstabellen) darf NICHT unbehandelt bis zum Aufrufer
        // durchschlagen: die drei Aufrufer starten diese Funktion in einem
        // `launch`, und weil mehrere Aufrufer ueber `SingleFlight` dasselbe
        // Ergebnis teilen, traefe eine entkommene Ausnahme sie ALLE — auf
        // Android heisst unbehandelt im Zweifel Prozessabsturz.
        // `SupervisorJob` an `refreshScope` schuetzt davor NICHT — das haelt
        // nur Geschwister-Coroutines am Leben, faengt aber keine Ausnahme ab.
        android.util.Log.w("WearRefresh", "refreshWearOfficial abgebrochen (Fehler)", e)
        verbucheFehlschlag(context, "Fehler beim Abruf: ${e.message}", now, versuchsOrt)
        false
    }
}

/**
 * `recordAttempt` aus einem `catch`-Zweig heraus — selbst ein
 * DataStore-Schreibvorgang, der aus demselben Grund scheitern kann wie das,
 * was uns ueberhaupt erst hierher gebracht hat (volle Platte, defekte
 * Datei). Ein Fehler beim Verbuchen des Fehlers darf den `catch`-Zweig nicht
 * sprengen; er wuerde sonst genau die Ausnahme ersetzen, die wir gerade
 * abgefangen haben, und waere damit wieder unbehandelt.
 *
 * [ort] `null` heisst: die Ausnahme fiel, bevor ueberhaupt feststand, um
 * welchen Ort es geht — dann gibt es nichts zu verbuchen (die Bremse
 * greift beim naechsten Versuch eben nicht, was hier das kleinere Uebel
 * ist: ohne Ort waere jeder Eintrag geraten).
 */
private suspend fun verbucheFehlschlag(
    context: Context,
    fehler: String,
    nowEpochMs: Long,
    ort: Pair<Double, Double>?,
) {
    if (ort == null) return
    try {
        WearOfficialCache.recordAttempt(context, fehler, nowEpochMs, ort.first, ort.second)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("WearRefresh", "Fehlschlag liess sich nicht verbuchen", e)
    }
}
