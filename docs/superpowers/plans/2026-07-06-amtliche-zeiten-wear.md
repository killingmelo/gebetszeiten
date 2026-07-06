# Amtliche Diyanet-Zeiten auf der Watch (PR 3) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Wear-App zeigt dieselben amtlichen Diyanet-Zeiten wie das Phone (gebündelte Tabellen, Nearest ≤ 25 km) statt eigener Berechnung; Berechnung nur per Toggle oder Fallback.

**Architecture:** `SixTimes` + `parseOfficialTimes` wandern vom app-Modul ins pure `core-prayertimes` (Paket `officialtimes`, neben `OfficialLocations`). Die generierten Assets ziehen von `app/src/main/assets/official/` in ein geteiltes Repo-Verzeichnis `shared-assets/official/`, das app UND wear per Gradle `assets.srcDir` einbinden (keine Duplikate). Wear bekommt einen dünnen Context-Adapter `WearOfficialSource` (Spiegel von `BundledOfficialSource`), `WearPrayer` wird suspend und fragt amtlich-zuerst, `WearSettings` + Watch-UI bekommen den `useCalculated`-Toggle.

**Tech Stack:** Kotlin (core pur / Android app+wear), Gradle sourceSets, DataStore, Wear Tiles/Complications.

**Scope:** PR 3 der Spec [2026-07-06-amtliche-zeiten-standard-design.md](../specs/2026-07-06-amtliche-zeiten-standard-design.md) §6. PR 4 (Online) folgt separat.

## Global Constraints

- KEIN Phone↔Watch-Sync, keine Play Services, kein Netzwerkcode (FOSS/offline).
- Wear bündelt die Tabellen über das geteilte Verzeichnis — Watch-AAB wächst um ~2,6 MB (Nutzer-Entscheidung aus dem Design-Review, bewusst akzeptiert).
- Distanz-Schwelle bleibt 25.0 km (Default von `OfficialLocations.nearest` — nirgends anders hart codieren).
- Beide App-Flavors (`assembleOfflineDebug` + `assembleOnlineDebug`) UND `:wear:assembleDebug` müssen nach jedem Task kompilieren; app-Tests + core-Tests grün.
- Gradle über PowerShell mit `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- `wear/build.gradle.kts` enthält eine absichtlich uncommittete Versionsanhebung (0.1.12) des Nutzers — beim Editieren erhalten, beim Stagen die Versionszeilen MIT committen ist jetzt ok (der Nutzer wollte 0.1.12 für dieses Release), aber nichts daran ändern.
- Commits einzeln pro Task, deutsch, mit `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## Verifizierter Ist-Stand

- `SixTimes` (+ `toDaily`) in `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` (Datei enthält auch `OfficialTimesFetcher`, der `AppSettings` importiert → Fetcher BLEIBT im app-Modul). `parseOfficialTimes` in `app/.../official/OfficialTables.kt`.
- Verwender von `SixTimes`/`parseOfficialTimes` im app-Modul (per Grep zu verifizieren): `BundledOfficialSource.kt`, `OfficialTimesCache.kt`, `OfficialTimes.kt` (Fetcher-Interface), `PrayerProvider.kt` (nur indirekt via toDaily), online-Flavor `app/src/online/.../DiyanetProxyFetcher.kt`, Tests `OfficialAssetsIntegrityTest.kt` (+ evtl. Parser-Tests aus Phase 1).
- Wear: `WearPrayer` (pur, kein Context, sync), Consumer: `MainActivity.buildState()` (suspend), `PrayerTileService` + `PrayerComplicationService` (`runBlocking`-Muster), `WearVibration` (~Zeile 44). `WearSettings` (DataStore `wear_location`, Snapshot-Muster). Watch-Stadtauswahl = kuratierte `WearCities`-Liste (66 Städte, DE/AT/CH/TR — Auslands-Städte fallen automatisch auf Berechnung zurück, ≤25-km-Regel).
- Wear-AVD `WearGebetszeiten` existiert (Memory `emulator-ui-verification`).

---

### Task 1: `SixTimes` + `parseOfficialTimes` nach core-prayertimes verschieben

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/SixTimes.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/OfficialTables.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` (SixTimes raus, Import rein)
- Delete: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTables.kt`
- Modify: alle Importstellen (grep-getrieben, siehe Step 3)
- Test: bestehende Tests laufen weiter; Parser-Tests ziehen ggf. mit nach core

**Interfaces:**
- Produces: `de.gebetszeiten.core.prayertimes.officialtimes.SixTimes` (Felder/`toDaily` unverändert), `de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes(lines: Sequence<String>): Map<LocalDate, SixTimes>` — von Task 3 (Wear-Adapter) konsumiert.

- [ ] **Step 1: Neue core-Dateien anlegen.** `SixTimes.kt` = exakte Kopie der Klasse aus `OfficialTimes.kt` (inkl. KDoc + `toDaily`), nur Package-Zeile `package de.gebetszeiten.core.prayertimes.officialtimes` und Import `de.gebetszeiten.core.prayertimes.DailyPrayerTimes`. `OfficialTables.kt` = exakte Kopie von app `OfficialTables.kt` mit neuem Package.

- [ ] **Step 2: App-Seite umbauen.** In `OfficialTimes.kt` die `SixTimes`-Klasse löschen, dafür `import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes` (Datei behält `OfficialTimesFetcher`). App-`OfficialTables.kt` löschen (`git rm`).

- [ ] **Step 3: Alle Verweise umstellen.** Finden per:
```powershell
Get-ChildItem app\src -Recurse -Filter *.kt | Select-String "de.gebetszeiten.official.SixTimes|parseOfficialTimes|de.gebetszeiten.official.OfficialTables" -List
# UND unqualifizierte Nutzung im selben Package (official/): SixTimes / parseOfficialTimes
Get-ChildItem app\src -Recurse -Filter *.kt | Select-String "SixTimes|parseOfficialTimes" -List
```
In jeder Trefferdatei (main + online-Flavor + test) den Import `de.gebetszeiten.core.prayertimes.officialtimes.SixTimes` bzw. `...officialtimes.parseOfficialTimes` ergänzen. Gibt es app-Tests, die NUR den Parser testen (Phase-1-Erbe), die Testdatei nach `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/` verschieben (Package anpassen, Test-Ressourcen mitnehmen falls vorhanden).

- [ ] **Step 4: Beide Flavors + alle Tests bauen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test :app:testOfflineDebugUnitTest :app:assembleOfflineDebug :app:assembleOnlineDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add -A core-prayertimes/src app/src
git commit -m "refactor(core): SixTimes + parseOfficialTimes ins geteilte core-Modul (fuer Wear)"
```

---

### Task 2: Geteiltes Asset-Verzeichnis `shared-assets/official/`

**Files:**
- Move: `app/src/main/assets/official/` → `shared-assets/official/` (git mv; 621 Tabellen + locations-de.tsv)
- Modify: `app/build.gradle.kts` (+`sourceSets`-Block), `wear/build.gradle.kts` (+`sourceSets`-Block)
- Modify: `tools/diyanet-fetch/fetch_diyanet.py` (OUT_DIR), `tools/diyanet-fetch/README.md` (Pfad)
- Modify: `app/src/test/kotlin/de/gebetszeiten/official/OfficialAssetsIntegrityTest.kt` (Repo-Pfad)

**Interfaces:**
- Produces: Assets liegen zur Laufzeit in app UND wear unter `assets/official/...` (unveränderte Runtime-Pfade — `BundledOfficialSource` braucht KEINE Änderung).

- [ ] **Step 1: Verzeichnis verschieben**

```powershell
git mv app/src/main/assets/official shared-assets/official
```
(git mv legt `shared-assets/` an. Prüfen: `git status --porcelain | Select-Object -First 3` zeigt Renames `R`.)

- [ ] **Step 2: Gradle einbinden.** In `app/build.gradle.kts` innerhalb `android { ... }` (z. B. nach `compileOptions`):

```kotlin
    sourceSets {
        getByName("main") {
            // Amtliche Diyanet-Tabellen werden mit dem wear-Modul geteilt.
            assets.srcDir(rootProject.file("shared-assets"))
        }
    }
```
Identischen Block in `wear/build.gradle.kts` einfügen (gleiche Stelle, gleicher Kommentar).

- [ ] **Step 3: Pipeline-Ausgabepfad.** In `fetch_diyanet.py`:

```python
OUT_DIR = REPO / "shared-assets" / "official"
```
(ersetzt `ASSETS / "official"`; `ASSETS` bleibt für cities.tsv/city-aliases.tsv). In `tools/diyanet-fetch/README.md` den Pfad `app/src/main/assets/official/` → `shared-assets/official/` korrigieren (2 Stellen).

- [ ] **Step 4: Integritätstest-Pfad.** In `OfficialAssetsIntegrityTest.kt`:

```kotlin
    private val assets = File("../shared-assets/official")
```
(Test-CWD ist das app-Modul.)

- [ ] **Step 5: Bauen + Tests + Wear**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:assembleOfflineDebug :wear:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. Zusatzprüfung, dass die Assets wirklich in BEIDEN APKs stecken:
```powershell
$jbr="C:\Program Files\Android\Android Studio\jbr\bin\jar.exe"
& $jbr tf app\build\outputs\apk\offline\debug\app-offline-debug.apk | Select-String "official/locations-de.tsv"
& $jbr tf wear\build\outputs\apk\debug\wear-debug.apk | Select-String "official/locations-de.tsv"
```
Expected: je 1 Treffer `assets/official/locations-de.tsv`.

- [ ] **Step 6: Commit**

```powershell
git add -A shared-assets app/build.gradle.kts wear/build.gradle.kts tools/diyanet-fetch app/src/test app/src/main/assets
git commit -m "build: amtliche Tabellen in shared-assets/ - app+wear teilen ein Asset-Verzeichnis"
```

---

### Task 3: `WearOfficialSource` + `useCalculated` in WearSettings

**Files:**
- Create: `wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialSource.kt`
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/WearSettings.kt`

**Interfaces:**
- Consumes: `OfficialLocations`, `parseOfficialLocations`, `parseOfficialTimes`, `SixTimes` (core, Task 1).
- Produces (Task 4 konsumiert): `suspend fun WearOfficialSource.get(context, lat: Double, lng: Double, date: LocalDate): SixTimes?`; `WearSettings.Snapshot.useCalculated: Boolean` + `suspend fun useCalculated(context): Boolean` + `suspend fun saveUseCalculated(context, value: Boolean)`.

- [ ] **Step 1: Adapter anlegen** — `WearOfficialSource.kt` (dünner Spiegel des app-Adapters, gleiche Cache-Idiomatik):

```kotlin
package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocation
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialLocations
import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus den geteilten Offline-Tabellen (assets/official/),
 * identisch zum Phone: nächstgelegener Diyanet-Standort ≤ 25 km, sonst null
 * (→ Aufrufer rechnet selbst). Dünner Context-Adapter um die pure core-Logik.
 */
object WearOfficialSource {

    private const val LOCATIONS_ASSET = "official/locations-de.tsv"

    @Volatile private var locations: List<OfficialLocation>? = null
    @Volatile private var tables: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    suspend fun get(context: Context, lat: Double, lng: Double, date: LocalDate): SixTimes? {
        val loc = OfficialLocations.nearest(allLocations(context), lat, lng) ?: return null
        return table(context, "official/tables/${loc.tableRef}-${date.year}.tsv")[date]
    }

    private suspend fun allLocations(context: Context): List<OfficialLocation> {
        locations?.let { return it }
        return withContext(Dispatchers.IO) {
            locations ?: load(context).also { locations = it }
        }
    }

    private fun load(context: Context): List<OfficialLocation> = try {
        context.assets.open(LOCATIONS_ASSET).bufferedReader(Charsets.UTF_8).useLines {
            parseOfficialLocations(it)
        }
    } catch (e: FileNotFoundException) {
        emptyList()
    }

    private suspend fun table(context: Context, path: String): Map<LocalDate, SixTimes> {
        tables[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            tables[path] ?: loadTable(context, path).also { tables = tables + (path to it) }
        }
    }

    private fun loadTable(context: Context, path: String): Map<LocalDate, SixTimes> = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { parseOfficialTimes(it) }
    } catch (e: FileNotFoundException) {
        emptyMap()
    }
}
```

- [ ] **Step 2: WearSettings erweitern.** Neuer Key nach `VIBRATE`:
```kotlin
    private val USE_CALCULATED = booleanPreferencesKey("use_calculated")
```
`Snapshot` um `val useCalculated: Boolean` erweitern (letztes Feld) und in `snapshot()` mit `prefs[USE_CALCULATED] ?: false` füllen. Dazu:
```kotlin
    /** Immer die lokale Berechnung nutzen statt amtlicher Diyanet-Tabellen. */
    suspend fun useCalculated(context: Context): Boolean =
        context.locationStore.data.first()[USE_CALCULATED] ?: false

    suspend fun saveUseCalculated(context: Context, value: Boolean) {
        context.locationStore.edit { it[USE_CALCULATED] = value }
    }
```

- [ ] **Step 3: Bauen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialSource.kt wear/src/main/kotlin/de/gebetszeiten/wear/WearSettings.kt
git commit -m "feat(wear): WearOfficialSource-Adapter + useCalculated-Setting"
```

---

### Task 4: `WearPrayer` amtlich-zuerst (suspend) + Consumer umstellen

**Files:**
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/WearPrayer.kt` (kompletter Ersatz)
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/MainActivity.kt` (buildState, 3 Aufrufe)
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/PrayerTileService.kt`, `PrayerComplicationService.kt`, `WearVibration.kt` (je 1 Aufruf)

**Interfaces:**
- Consumes: `WearOfficialSource.get`, `WearSettings.useCalculated` (Task 3).
- Produces: `suspend fun WearPrayer.today(context, location, zone)`, `suspend fun next(context, location, zone, now)`, `suspend fun upcoming(context, location, zone, now, count)` — Rückgabetypen unverändert.

- [ ] **Step 1: WearPrayer ersetzen** (neuer Datei-Inhalt; Doc-Kommentar aktualisiert):

```kotlin
package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared prayer-time helpers for the watch. Bevorzugt die gebündelten
 * amtlichen Diyanet-Tabellen (identisch zum Phone, nearest ≤ 25 km);
 * Berechnung nur bei useCalculated-Toggle oder fehlender Abdeckung.
 * Fully standalone — kein Phone-Sync (FOSS, keine Play Services).
 */
object WearPrayer {

    suspend fun today(context: Context, location: GeoLocation, zone: ZoneId): DailyPrayerTimes =
        daily(context, location, ZonedDateTime.now(zone).toLocalDate(), zone)

    /** Next prayer strictly after [now], rolling into tomorrow if needed.
     *  Sunrise is just the end of Fajr's window, not a prayer → skipped. */
    suspend fun next(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
    ): Pair<Prayer, ZonedDateTime> = upcoming(context, location, zone, now, count = 1).first()

    /** The next [count] prayers after [now], across the day boundary, skipping
     *  sunrise. Used to build a self-switching tile timeline for the whole day. */
    suspend fun upcoming(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
        count: Int,
    ): List<Pair<Prayer, ZonedDateTime>> {
        val today = daily(context, location, now.toLocalDate(), zone)
        val tomorrow = daily(context, location, now.toLocalDate().plusDays(1), zone)
        return (today.ordered() + tomorrow.ordered())
            .filter { it.first != Prayer.SUNRISE && it.second.isAfter(now) }
            .take(count)
    }

    /** Amtliche Tabelle (nearest Diyanet-Standort) vor Berechnung — dieselbe
     *  Prioritätslogik wie PrayerProvider.daily am Phone (ohne Online-Stufe). */
    private suspend fun daily(
        context: Context,
        location: GeoLocation,
        date: LocalDate,
        zone: ZoneId,
    ): DailyPrayerTimes {
        if (!WearSettings.useCalculated(context)) {
            WearOfficialSource.get(context, location.latitude, location.longitude, date)
                ?.let { return it.toDaily(date, zone) }
        }
        return DiyanetPrayerTimesCalculator.calculate(location, date, zone)
    }
}
```
WICHTIG vor dem Schreiben: prüfen, wie `GeoLocation` seine Felder nennt (`latitude/longitude` oder `lat/lng`) — `Get-Content core-prayertimes\src\main\kotlin\de\gebetszeiten\core\prayertimes\Model.kt | Select-String -Context 2 "GeoLocation"` — und den Aufruf entsprechend anpassen.

- [ ] **Step 2: Consumer umstellen.** Alle Aufrufer bekommen `applicationContext`/`context` als erstes Argument; sync-Kontexte wrappen bereits mit `runBlocking` (Tile/Complication) — die `WearPrayer`-Aufrufe DORT mit in den bestehenden `runBlocking`-Block ziehen bzw. einen umschließenden `runBlocking { }` je Methode verwenden, exakt dem vorhandenen Muster folgend. `MainActivity.buildState()` ist suspend → direkt aufrufen. `WearVibration` analog seiner bestehenden Coroutine-Struktur.

- [ ] **Step 3: Bauen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add wear/src/main/kotlin
git commit -m "feat(wear): amtliche Zeiten auch auf der Watch (amtlich-zuerst in WearPrayer)"
```

---

### Task 5: Watch-UI-Toggle „Eigene Berechnung"

**Files:**
- Modify: `wear/src/main/res/values/strings.xml`
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/MainActivity.kt` (Settings-Bereich)

**Interfaces:**
- Consumes: `WearSettings.useCalculated/saveUseCalculated` (Task 3).

- [ ] **Step 1: String ergänzen** (wear strings.xml, bei den Settings-Strings):

```xml
    <string name="settings_use_calculated">Eigene Berechnung</string>
```

- [ ] **Step 2: Toggle einbauen.** In der Wear-`MainActivity` den bestehenden Einstellungs-Schaltern folgen: per Grep `SHOW_REMAINING|showRemaining|saveShowRemaining|vibrate` die Toggle-UI finden (Switch/Chip-Zeilen) und EXAKT im selben Muster einen dritten Schalter „Eigene Berechnung" ergänzen, der `WearSettings.useCalculated`/`saveUseCalculated` liest/schreibt und danach denselben Refresh auslöst wie die bestehenden Schalter (gleicher Callback/refresh()-Aufruf). Snapshot-Feld `useCalculated` nutzen, wo der Screen den Snapshot bereits lädt.

- [ ] **Step 3: Bauen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add wear/src/main
git commit -m "feat(wear): Toggle Eigene Berechnung in den Watch-Einstellungen"
```

---

### Task 6: Wear-Emulator-Verifikation

**Files:** keine. AVD `WearGebetszeiten` (Boot dauert auf Wear länger, ~2–4 min). adb-Flow siehe Memory `emulator-ui-verification`; bei ZWEI laufenden Emulatoren adb-Ziel mit `-s emulator-XXXX` wählen (`adb devices`).

- [ ] **Step 1: Wear-Emulator starten + installieren**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd WearGebetszeiten -no-snapshot-save -no-boot-anim  # im Hintergrund
# nach Boot (sys.boot_completed=1):
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleDebug --console=plain -q
& $adb -s <wear-serial> install -r wear\build\outputs\apk\debug\wear-debug.apk
& $adb -s <wear-serial> shell am start -n de.gebetszeiten/de.gebetszeiten.wear.MainActivity
```
(Launcher-Activity-Name vorher via `aapt`/Manifest prüfen, falls Start fehlschlägt.)

- [ ] **Step 2: Amtliche Zeiten prüfen.** Screenshot des Hauptscreens (Default Nürnberg). Erwartung: Die angezeigten Zeiten entsprechen der amtlichen Tabelle (z. B. Dhuhr **13:26** am 6. Juli 2026 — Wanduhrwert aus t496; Achtung: läuft der Wear-AVD auf UTC, erscheinen berechnete Zeiten −2h, amtliche Tabellenwerte aber unverändert als Wanduhrzeit → gerade DAS unterscheidet amtlich von berechnet).

- [ ] **Step 3: Toggle prüfen.** In den Watch-Einstellungen „Eigene Berechnung" aktivieren → Zeiten wechseln auf berechnete Werte (bei UTC-AVD deutlich sichtbar, z. B. Dhuhr ~11:25); deaktivieren → zurück auf amtlich. Screenshots.

- [ ] **Step 4: Phone-Parität.** Phone-Emulator (Medium_Phone): App öffnen, Nürnberg — dieselben amtlichen Zeiten wie die Watch (13:26). Screenshot-Paar dokumentieren.

- [ ] **Step 5: Abschluss.** Ergebnisse zusammenfassen; falls Fixes nötig waren, committen.

---

## Self-Review (durchgeführt)

- **Spec §6 komplett:** geteilte Assets ohne Duplikate (Task 2), geteilter Lookup-Code via core (Task 1, Adapter Task 3), WearPrayer amtlich-zuerst (Task 4), useCalculated auf der Watch (Task 3+5), Parität verifiziert (Task 6). Wear-Paritätstest als JVM-Test entfällt bewusst (Context-gebunden) — Emulator-Paritätsprüfung Task 6 Step 4 ersetzt ihn; die pure Logik (Parser/Nearest) ist bereits in core getestet.
- **Platzhalter:** keine; wo der Implementer Bestandsmuster spiegeln muss (Tile/Complication-runBlocking, Settings-Toggle), ist das Suchmuster konkret benannt.
- **Typkonsistenz:** `WearOfficialSource.get(context, lat, lng, date)` (Task 3) = Aufruf in Task 4; `Snapshot.useCalculated` (Task 3) = Nutzung Task 5; core-Paketpfad `de.gebetszeiten.core.prayertimes.officialtimes` durchgängig.
