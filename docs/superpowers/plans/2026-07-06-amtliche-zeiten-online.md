# Online-Flavor: on-demand amtliche Zeiten (PR 4) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Online-Flavor lädt amtliche Diyanet-Zeiten automatisch bei Standortwechsel und wenn der Cache zur Neige geht (< 7 Tage Zukunft) — und liefert nie mehr Zeiten eines veralteten Standorts.

**Architecture:** `OfficialTimesCache` bekommt einen Standort-Stempel (lat/lng des Fetches) — Lookups liefern nur bei passendem Stempel (~1 km). Pure Helfer (`CacheFreshness.kt`) entscheiden testbar über Refresh-Bedarf. `DiyanetProxyFetcher` nutzt für Deutschland direkt die `diyanetId` des gebündelten Nearest-Standorts (id-genau, konsistent zu Bundle+Footer), sonst wie bisher die Namens-Suche. `PrayerProvider.refreshOfficial` fetcht nur noch bei Bedarf.

**Tech Stack:** Kotlin, DataStore, HttpURLConnection (bestehend, nur online-Flavor).

**Scope:** PR 4 (letzter Teil) der Spec [2026-07-06-amtliche-zeiten-standard-design.md](../specs/2026-07-06-amtliche-zeiten-standard-design.md) §5.

## Global Constraints

- Netzwerkcode NUR unter `app/src/online/` (Offline-Flavor beweisbar netzfrei; `OfficialTimesCache`/`CacheFreshness` in main sind reine Daten-/Logikklassen ohne Netz).
- Fehlerfall unverändert: leerer/fehlgeschlagener Fetch → gebündelte Tabelle → Berechnung; NIE crashen.
- Freshness-Schwelle: **7 Tage** Zukunft; Standort-Stempel-Toleranz: **1.0 km** (Haversine via `OfficialLocations.haversineKm`? — die ist `internal` in core; stattdessen simple Grad-Näherung, siehe O1-Code).
- Beide Flavors + Tests + Lint müssen nach jedem Task grün sein: `:app:testOfflineDebugUnitTest :app:assembleOfflineDebug :app:assembleOnlineDebug`.
- Gradle über PowerShell mit `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- Commits einzeln pro Task, deutsch, `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## Verifizierter Ist-Stand

- `PrayerViewModel.reschedule()` ruft `PrayerProvider.refreshOfficial(app, value)` bei JEDEM App-Start (`ensureScheduled`) und jedem Settings-Save — aktuell fetcht das bei `useOnline=true` IMMER (kein Freshness-Check).
- `OfficialTimesCache` (main): DataStore `official_times`, Key `schedule`, eine Zeile/Tag; `putAll` ersetzt komplett; KEIN Standort-Bezug → Stadtwechsel + Fetch-Fehler = stale Zeiten der alten Stadt mit „amtlich"-Label (Altlast, wird hier behoben).
- `DiyanetProxyFetcher` (online): `search?q=<city>` → Namens-Match → `prayertimes?location_id=` (~31 Tage rollierend). Konstruktor ohne Context; `OfficialTimesProvider.fetcher(context)` (je Flavor) liefert ihn.
- `BundledOfficialSource` (main): interner nearest über `OfficialLocations.nearest` — `diyanetId` ist im Index vorhanden, wird aber nicht exponiert.
- Aufrufer von `OfficialTimesCache.get`: `PrayerProvider.daily` (Zeile ~26) und `MainActivity` Footer-produceState (Zeile ~277).

---

### Task O1: Cache-Standort-Stempel + pure Freshness-Helfer (TDD)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/official/CacheFreshness.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/official/CacheFreshnessTest.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (get-Aufruf), `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (Footer-produceState)

**Interfaces:**
- Produces: `fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean`; `fun needsRefresh(coveredUntil: LocalDate?, today: LocalDate, stampOk: Boolean, minFutureDays: Long = 7): Boolean`; Cache-API neu: `suspend fun get(date, lat, lng): SixTimes?`, `suspend fun putAll(schedule, lat, lng)`, `suspend fun coveredUntil(lat, lng): LocalDate?`, `suspend fun stampOk(lat, lng): Boolean`.

- [ ] **Step 1: Failing Tests schreiben** — `CacheFreshnessTest.kt`:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CacheFreshnessTest {

    @Test fun stampMatchesWithinOneKm() {
        // ~500 m nördlich von Nürnberg-Zentrum.
        assertTrue(stampMatches(49.4521, 11.0767, 49.4566, 11.0767))
    }

    @Test fun stampRejectsDifferentCity() {
        // Nürnberg vs. Fürth (~7 km).
        assertFalse(stampMatches(49.4521, 11.0767, 49.4759, 10.9886))
    }

    @Test fun stampRejectsMissing() {
        assertFalse(stampMatches(null, null, 49.4521, 11.0767))
    }

    @Test fun refreshWhenStampMismatch() {
        assertTrue(needsRefresh(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 6), stampOk = false))
    }

    @Test fun refreshWhenCoverageThin() {
        // Nur noch 3 Tage Zukunft abgedeckt.
        assertTrue(needsRefresh(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 6), stampOk = true))
    }

    @Test fun noRefreshWhenFreshAndMatching() {
        assertFalse(needsRefresh(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 6), stampOk = true))
    }

    @Test fun refreshWhenEmpty() {
        assertTrue(needsRefresh(null, LocalDate.of(2026, 7, 6), stampOk = true))
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen** (unresolved reference)

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest --tests "*CacheFreshnessTest" --console=plain`

- [ ] **Step 3: Implementierung** — `CacheFreshness.kt`:

```kotlin
package de.gebetszeiten.official

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos

/** ~1 km Toleranz: Cache gilt nur für den Standort, für den er geladen wurde.
 *  Grad-Näherung statt Haversine reicht hier (kleine Distanzen, grobe Schwelle). */
fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean {
    if (stampLat == null || stampLng == null) return false
    val dLatKm = abs(lat - stampLat) * 111.0
    val dLngKm = abs(lng - stampLng) * 111.0 * cos(Math.toRadians(lat))
    return dLatKm <= 1.0 && dLngKm <= 1.0
}

/** Neu laden, wenn der Cache nicht zum Standort passt oder weniger als
 *  [minFutureDays] Tage Zukunft ab [today] abdeckt (Proxy liefert ~31 Tage). */
fun needsRefresh(
    coveredUntil: LocalDate?,
    today: LocalDate,
    stampOk: Boolean,
    minFutureDays: Long = 7,
): Boolean {
    if (!stampOk) return true
    if (coveredUntil == null) return true
    return coveredUntil.isBefore(today.plusDays(minFutureDays))
}
```

- [ ] **Step 4: Tests grün**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest --tests "*CacheFreshnessTest" --console=plain`

- [ ] **Step 5: Cache um Stempel erweitern.** In `OfficialTimesCache.kt`: neue Keys + API-Umbau (kompletter neuer Klassen-Inhalt; parseLine bleibt identisch):

```kotlin
class OfficialTimesCache(private val context: Context) {

    private val key = stringPreferencesKey("schedule")
    private val stampLat = doublePreferencesKey("stamp_lat")
    private val stampLng = doublePreferencesKey("stamp_lng")

    /** Zeiten nur, wenn der Cache für (lat,lng) geladen wurde — sonst null,
     *  damit nie amtliche Zeiten eines alten Standorts angezeigt werden. */
    suspend fun get(date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return (prefs[key] ?: return null)
            .lineSequence().mapNotNull { parseLine(it) }.toMap()[date]
    }

    suspend fun stampOk(lat: Double, lng: Double): Boolean {
        val prefs = context.officialStore.data.first()
        return stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)
    }

    /** Letztes abgedecktes Datum — für den Freshness-Check; null wenn leer
     *  oder Stempel nicht passt. */
    suspend fun coveredUntil(lat: Double, lng: Double): LocalDate? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return (prefs[key] ?: return null)
            .lineSequence().mapNotNull { parseLine(it) }.maxOfOrNull { it.first }
    }

    suspend fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double) {
        if (schedule.isEmpty()) return
        val text = schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }
        context.officialStore.edit {
            it[key] = text
            it[stampLat] = lat
            it[stampLng] = lng
        }
    }

    private fun parseLine(line: String): Pair<LocalDate, SixTimes>? { /* unverändert */ }
}
```
(Import `doublePreferencesKey` ergänzen. Migration: Bestandscache ohne Stempel liefert ab jetzt null → wird beim nächsten Refresh neu geladen — gewollt.)

- [ ] **Step 6: Aufrufer anpassen.** `PrayerProvider.daily` Schritt 1:
```kotlin
        if (settings.useOnline) {
            OfficialTimesCache(context).get(date, settings.latitude, settings.longitude)
                ?.let { return it.toDaily(date, zone) }
        }
```
`PrayerProvider.refreshOfficial` (Kompilierbarkeit; Gate kommt in O3):
```kotlin
        OfficialTimesCache(context).putAll(fetcher.fetch(settings), settings.latitude, settings.longitude)
```
`MainActivity` Footer-produceState: `OfficialTimesCache(context).get(selectedDate, settings.latitude, settings.longitude) != null`.

- [ ] **Step 7: Alles bauen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOfflineDebug :app:assembleOnlineDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/de/gebetszeiten/official app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/test/kotlin/de/gebetszeiten/official/CacheFreshnessTest.kt
git commit -m "feat(online): Online-Cache mit Standort-Stempel - nie mehr stale Zeiten fremder Orte"
```

---

### Task O2: Fetcher nutzt gebündelte diyanetId (nearest), sonst Namens-Suche

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt` (nearest exponieren)
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt`
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` (Fetcher-Interface bekommt lat/lng? — NEIN: Signatur bleibt, `AppSettings` enthält lat/lng bereits)

**Interfaces:**
- Produces: `suspend fun BundledOfficialSource.nearestLocation(context, lat: Double, lng: Double): OfficialLocation?` (public); `DiyanetProxyFetcher(private val context: Context)`.

- [ ] **Step 1: nearest exponieren.** In `BundledOfficialSource`: die bestehende private nearest-Auflösung als public API herausziehen und intern nutzen:

```kotlin
    /** Nächstgelegener gebündelter Diyanet-Standort ≤ 25 km, oder null.
     *  Auch vom Online-Fetcher genutzt (liefert die exakte diyanetId). */
    suspend fun nearestLocation(context: Context, lat: Double, lng: Double): OfficialLocation? =
        OfficialLocations.nearest(allLocations(context), lat, lng)
```
`nearestCovering` ruft ab jetzt `nearestLocation(...)` statt direkt `OfficialLocations.nearest`.

- [ ] **Step 2: Fetcher umbauen.** `DiyanetProxyFetcher` bekommt Context und bevorzugt die Bundle-ID:

```kotlin
class DiyanetProxyFetcher(private val context: android.content.Context) : OfficialTimesFetcher {
    ...
    override suspend fun fetch(settings: AppSettings): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            try {
                // Deutschland: exakte diyanetId des gebündelten Nearest-Standorts
                // (id-genau, konsistent zu Offline-Tabelle und Footer). Sonst
                // wie bisher Namens-Suche über den Proxy.
                val locationId = BundledOfficialSource
                    .nearestLocation(context, settings.latitude, settings.longitude)?.diyanetId
                    ?: resolveLocationId(settings.city)
                    ?: return@withContext emptyMap()
                parseSchedule(httpGet("$base/prayertimes?location_id=$locationId"))
            } catch (e: Exception) {
                emptyMap()
            }
        }
```
(Import `de.gebetszeiten.core.prayertimes.officialtimes` nicht nötig — nur `.diyanetId`-Zugriff; `BundledOfficialSource`-Import ergänzen.)

- [ ] **Step 3: Provider verdrahten.** Online-`OfficialTimesProvider`:
```kotlin
    fun fetcher(context: Context): OfficialTimesFetcher = DiyanetProxyFetcher(context)
```

- [ ] **Step 4: Bauen (beide Flavors!)**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOfflineDebug :app:assembleOnlineDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt app/src/online/kotlin
git commit -m "feat(online): Fetch nutzt exakte diyanetId des Nearest-Standorts (DE), sonst Namenssuche"
```

---

### Task O3: Refresh-Gate — nur bei Standortwechsel oder dünner Abdeckung fetchen

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (`refreshOfficial`)

**Interfaces:**
- Consumes: `OfficialTimesCache.stampOk/coveredUntil/putAll` (O1), `needsRefresh` (O1).

- [ ] **Step 1: Gate einbauen** — `refreshOfficial` komplett:

```kotlin
    /** Online flavor + user opted in: refresh the official-times cache —
     *  aber nur bei Standortwechsel oder wenn weniger als 7 Tage Zukunft
     *  abgedeckt sind (Proxy liefert ~31 Tage rollierend). */
    suspend fun refreshOfficial(context: Context, settings: AppSettings) {
        if (!settings.useOnline || settings.useCalculated) return
        val cache = OfficialTimesCache(context)
        val stampOk = cache.stampOk(settings.latitude, settings.longitude)
        val coveredUntil = cache.coveredUntil(settings.latitude, settings.longitude)
        if (!de.gebetszeiten.official.needsRefresh(coveredUntil, LocalDate.now(zoneOf(context)), stampOk)) return
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        cache.putAll(fetcher.fetch(settings), settings.latitude, settings.longitude)
    }
```
`zoneOf(context)` existiert nicht — schlicht `LocalDate.now()` (Gerätezone) verwenden; Import `java.time.LocalDate` vorhanden. Import von `needsRefresh` als top-level: `import de.gebetszeiten.official.needsRefresh`.

- [ ] **Step 2: Bauen + Tests + Lint**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOnlineDebug :app:lintOfflineDebug --console=plain`
Expected: BUILD SUCCESSFUL, Lint grün.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt
git commit -m "feat(online): Refresh-Gate - Fetch nur bei Standortwechsel oder <7 Tagen Abdeckung"
```

---

### Task O4: Online-Emulator-Verifikation

**Files:** keine. Phone-AVD `Medium_Phone_API_36.1` (läuft ggf. schon, adb-Ziel emulator-5554). Der Emulator hat Internetzugang; es entstehen einzelne echte Proxy-Requests (gewollt, minimal).

- [ ] **Step 1: Online-Flavor bauen + installieren** (`applicationId de.gebetszeiten.online`):
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleOnlineDebug --console=plain -q
& $adb -s emulator-5554 install -r app\build\outputs\apk\online\debug\app-online-debug.apk
& $adb -s emulator-5554 shell pm clear de.gebetszeiten.online
& $adb -s emulator-5554 shell am start -n de.gebetszeiten.online/de.gebetszeiten.ui.MainActivity
```
(Activity-Pfad prüfen: online-Flavor hat applicationIdSuffix? Falls Start scheitert: `cmd package resolve-activity de.gebetszeiten.online`.)

- [ ] **Step 2: useOnline aktivieren.** Settings öffnen → Schalter „Amtliche Zeiten online laden" (settings_official_online) aktivieren. Danach App-Neustart (force-stop + start), damit `ensureScheduled` → refreshOfficial läuft.

- [ ] **Step 3: DE-Fall.** Default Nürnberg: Footer bleibt „Amtliche Diyanet-Zeiten · Nürnberg"; Zeiten unverändert 13:26 Dhuhr (Online-Cache und Bundle liefern dieselbe Quelle — Fetch lief mit diyanetId 11024). Screenshot.

- [ ] **Step 4: Weltweit-Fall.** Stadt „Istanbul" wählen (Suchfeld) → App refresht (Stempel-Mismatch → Fetch). Erwartung: Footer „Amtliche Diyanet-Zeiten · Istanbul" (Online-Cache-Treffer, Label = settings.city), plausible Istanbul-Zeiten. Screenshot. (Wenn der Fetch scheitert — Proxy down —, zeigt die App „Berechnung"; dann später erneut versuchen.)

- [ ] **Step 5: Stale-Schutz-Gegenprobe.** Flugmodus AN (`svc wifi disable` + `svc data disable` via adb shell), Stadt „Kairo" wählen → Fetch scheitert. Erwartung: Footer „Berechnung: Diyanet-Methode (offline)" — NICHT Istanbuls Zeiten (Stempel-Schutz!). Screenshot. Flugmodus wieder AUS.

- [ ] **Step 6: Aufräumen + Ergebnis.** Online-App ggf. deinstallieren oder auf Nürnberg zurücksetzen; Zusammenfassung mit Screenshots.

---

## Self-Review (durchgeführt)

- **Spec §5 komplett:** Stadtwechsel-Trigger (Stempel-Mismatch → needsRefresh, ausgelöst durch bestehendes save()→reschedule), Auto-Refresh < 7 Tage (O3), nearest per Koordinaten wo möglich (O2 via Bundle-diyanetId; Ausland Namens-Suche wie Spec vorsieht), Fehlerfall unverändert (leerer Fetch → Bundle → Berechnung; zusätzlich Stale-Schutz durch Stempel).
- **Platzhalter:** keine; parseLine-„unverändert"-Marker verweist auf exakt bestehenden Code im selben File.
- **Typkonsistenz:** `get(date, lat, lng)`/`putAll(schedule, lat, lng)`/`stampOk`/`coveredUntil` (O1) = Aufrufe in O2/O3; `nearestLocation(context, lat, lng): OfficialLocation?` (O2) mit `.diyanetId: Int` aus core.
