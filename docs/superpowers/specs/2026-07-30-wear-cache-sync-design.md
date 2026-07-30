# Wear-Cache-Sync — Design

Datum: 2026-07-30 · Status: vom Nutzer freigegeben (Abschnitte 1+2 einzeln bestätigt)
Folgephase aus `2026-07-29-online-first-weltweit-design.md` („Feste Folgephase").

## Ziel

Uhr und Handy zeigen außerhalb der DE-Bundle-Abdeckung identische amtliche
Diyanet-Zeiten. Das Handy repliziert seinen amtlichen Zeiten-Cache per
Wear Data Layer an die Uhr; die Uhr scrapt weiterhin NICHT selbst, bleibt
funktional standalone und liest den gesyncten Cache mit der Kette
**Sync-Cache → Bundle → Berechnung**.

## Getroffene Grundsatz-Entscheidungen (Nutzer, 2026-07-30)

1. **Data Layer akzeptiert:** `play-services-wearable` kommt in die
   Wear-App fest und ins Handy NUR als `onlineImplementation` — der
   offline-Phone-Flavor bleibt gms-frei. Der bisherige Kommentar
   „Fully standalone — kein Phone-Sync (FOSS, keine Play Services)" in
   `WearPrayer.kt` wird ehrlich angepasst (Uhr bleibt ohne Handy voll
   funktionsfähig).
2. **Ortswahl synct mit, Uhr-Picker als lokaler Override:** Ein neuer
   Handy-Ort wird auf der Uhr übernommen; eine spätere Wahl am
   Uhr-Picker bleibt bestehen, bis am Handy wieder ein ANDERER Ort
   gewählt wird.
3. **Mechanismus: DataItems** (`DataClient`), nicht MessageClient/
   ChannelClient — Zustands-Replikation mit garantierter Zustellung
   (kommt auch an, wenn die Uhr beim Schreiben offline war),
   last-value-wins, kein Polling.

## Architektur & Datenfluss

1. `PrayerProvider.refreshOfficial()` schreibt wie bisher den
   `OfficialTimesCache`. Direkt danach — nur bei NICHT-leerem
   Fetch-Ergebnis — ruft es einen flavor-spezifischen Hook am
   `OfficialTimesProvider`: offline = No-op, online = `WearCacheSync`.
2. `WearCacheSync` schreibt EIN DataItem unter Pfad `/official-times`:
   Zeiten-Text (gleiches Zeilenformat wie der Handy-Cache,
   ~22 KB/Jahr, DataItem-Limit 100 KB), Orts-Stempel (lat/lng),
   Stadtname. Bewusst KEIN volatiler Zeitstempel: DataItems feuern nur
   bei Inhaltsänderung — kein Sync-Spam bei unverändertem Cache.
   Kein Rück-Kanal Uhr→Handy.
3. Auf der Uhr empfängt ein `WearableListenerService` das DataItem,
   schreibt den neuen DataStore `WearOfficialCache` und stößt danach
   an: Tile-Update (`TileService.getUpdater().requestUpdate`),
   Complication-Update (`ComplicationDataSourceUpdateRequester`) und
   Neu-Planung der Vibrations-Alarme (`WearAlarmReceiver`-Pfad).
4. `WearPrayer.daily()`-Kette: `useCalculated` → Berechnung (wie
   heute); sonst Sync-Cache (stamp-geschützt) → `WearOfficialSource`
   (Bundle) → Berechnung.

## Geteilter Code

Das Zeilenformat (`"$date $fajr $sunrise $dhuhr $asr $maghrib $isha"`,
zeilenweise, sortiert) bekommt Serializer+Parser `ScheduleText` in
`core-prayertimes` (Package `officialtimes`). Handy-`OfficialTimesCache`
wird verhaltensgleich darauf umgestellt; der Uhr-Cache nutzt dasselbe —
das Format existiert genau einmal.

## Ort-Übernahme-Semantik (präzise)

`WearOfficialCache` speichert neben den Zeiten den ZULETZT ÜBERNOMMENEN
Handy-Ort (`synced_lat/lng/city`). Bei eintreffendem Payload:

- Payload-Ort ≠ zuletzt übernommener Ort → Uhr übernimmt ihn in
  `WearSettings` (Stadt + Koordinaten) UND merkt ihn als übernommen.
- Payload-Ort = zuletzt übernommener Ort → `WearSettings` unangetastet
  (ein Uhr-Override bleibt bestehen).
- Erst-Sync (noch kein gemerkter Ort) → übernehmen.
- „Gleich/ungleich" nutzt dieselbe Koordinaten-Toleranz wie
  `stampMatches` (kein exakter Double-Vergleich).

Der Sync-Cache liefert beim LESEN nur, wenn sein Stempel zur aktuellen
Uhr-Ortswahl passt (analog `stampMatches` am Handy) — nach einem
Uhr-Override greift für den Uhr-Ort automatisch wieder
Bundle/Berechnung.

## Fehlerverhalten

- Handy: `putDataItem` in try/catch; Fehler (kein gms, API) nur
  geloggt, Refresh unberührt. Leere Ergebnisse werden nie gepusht
  (letzter guter Stand bleibt auf der Uhr).
- Uhr: unlesbarer Payload → ignorieren, alter Cache bleibt. Veraltete
  Daten unkritisch: `get()` ist datumsgenau, sonst nächste Stufe.
- Uhr ohne Play Services / nie gekoppelt / offline-Phone-Flavor:
  Listener feuert nie → Verhalten exakt wie heute.

## Tests (reine JVM, kein Robolectric — Repo-Konvention)

- `core-prayertimes`: Round-trip `ScheduleText` (serialize ↔ parse;
  leere Map; kaputte Zeile wirft).
- `wear` (erstes Test-Source-Set des Moduls): Übernahme-/
  Override-Entscheidung als reine Funktion geschnitten
  (Payload + aktuelle Settings + zuletzt übernommener Ort →
  Entscheidungen), mit Fakes getestet; der ListenerService bleibt
  dünner IO-Wrapper ohne eigene Logik.
- `app` (online): Push-Hook per injizierbarem Lambda (Aufruf-Zählung:
  gepusht nur bei nicht-leerem Ergebnis), Muster wie
  `CompositeDiyanetFetcherTest`.

## Mitzunehmende Alt-Findings (aus dem Online-First-Review verschoben)

- `CompositeDiyanetFetcher.attempt/fetch`: `CancellationException`
  rethrow statt schlucken.
- `withTimeout(~25 s)` um `PrayerProvider.refreshOfficial()`
  (Broadcast-Budget im `PrayerAlarmReceiver`).
- Veraltete „~31 Tage"-Formulierungen in den ALTEN Spec-/Plan-Dokumenten
  bleiben unangetastet (historische Dokumente).

## Bewusst nicht dabei (YAGNI)

Kein Rück-Sync Uhr→Handy, kein Sync weiterer Einstellungen
(Vibration/Anzeige), keine Sync-Status-UI auf der Uhr, kein
Payload-Versionsfeld (falls je nötig: neuer Pfad `/official-times/v2`),
kein `setUrgent`-Feintuning, Uhr scrapt nicht selbst, keine
Bundle-Ausweitung auf weitere Länder.
