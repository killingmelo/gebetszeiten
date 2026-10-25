# Gebetszeiten in der Windows-Taskleiste — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Windows-11-Programm, das naechstes Gebet + Restzeit (mit Karaha) als Feld in der Taskleiste zeigt, mit Tray-Symbol, optionalen Benachrichtigungen und Ezan — gespeist aus denselben amtlichen Diyanet-Zeiten und derselben Logik wie die Handy-App.

**Architecture:** Reine Anzeige- und Cache-Logik, die heute im `app`-Modul liegt, wandert nach `core-prayertimes`; die App delegiert dorthin (ihre Tests beweisen die Gleichheit). Der plattformneutrale Diyanet-Abruf zieht aus dem Android-Modul `net-diyanet` in ein neues Kotlin/JVM-Modul `net-diyanet-core`. Das neue Modul `desktop` (Kotlin/JVM, Swing + JNA) verdrahtet beides duenn mit Dateien in `%APPDATA%` und haengt ein randloses Fenster per `SetParent` in die Taskleiste.

**Tech Stack:** Kotlin 2.2.0/JVM 21, Swing, JNA `net.java.dev.jna:jna-platform:5.14.0`, MP3 via `com.googlecode.soundlibs:jlayer:1.0.1.4`, `org.json:json:20240303`, kotlinx-coroutines 1.9.0, JUnit 4.13.2, `jpackage` (JDK 21 per Gradle-Toolchain).

**Spec:** `docs/superpowers/specs/2026-10-25-desktop-taskleiste-design.md`

## Global Constraints

- Nur amtliche Diyanet-Zeiten. Der Desktop hat **keine** Eigenberechnung: der Zweig `DaySource.CALCULATION` liefert `null` (Entscheidung Claude; der Notausgang ist fuer v1 nicht verlangt). Ohne Zeiten: Hinweis „Keine Zeiten", nie eine Zahl.
- Texte exakt wie die Handy-App: Stufen aus `remainingStepShort` („2+ Std", „20+ Min", „7 Min", letzte Minute leer → „jetzt"), Karaha „⚠️ Karaha ab HH:mm" / „⚠️ Karaha bis HH:mm", Gebetsnamen „Fajr", „Sonnenaufgang", „Dhuhr", „Asr", „Maghrib", „Isha".
- Sonnenaufgang ist kein Gebet und wird als „naechstes Gebet" uebersprungen (wie `PrayerProvider.nextPrayer`).
- Kein Sekundentakt: neu gezeichnet wird nur, wenn sich der Text aendert.
- App und Wear duerfen ihr Verhalten nicht aendern. Nach jeder Aufgabe, die sie beruehrt, muessen `:core-prayertimes:test`, `:net-diyanet:testDebugUnitTest`, `:app:testOnlineDebugUnitTest`, `:app:testOfflineDebugUnitTest` und `:wear:testOnlineDebugUnitTest` gruen sein.
- Gradle immer so (PowerShell 5.1, kein `&&`): `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain <tasks>`
- Quelldateien nie ueber `Get-Content`/`Set-Content` bearbeiten (zerstoert UTF-8) — Edit-Werkzeug benutzen.
- Nur Windows 11, nur der PC des Nutzers: unsigniertes Programmverzeichnis, keine Auto-Updates.
- Daten in `%APPDATA%\Gebetszeiten\` (`settings.properties`, `cache.txt`).
- Kommentare und Commit-Nachrichten auf Deutsch, im Stil des Repos (Umlaute in Code-Kommentaren als ae/oe/ue wie im Bestand).

## Review Focus

1. **Tageswechsel nach Isha**: nach Isha muss Fajr von *morgen* stehen (Restzeit ueber Mitternacht), nicht „Keine Zeiten" — Test in Task 5.
2. **Ruhezustand / Aufwachen**: nach Stunden Schlaf muss der Text sofort stimmen und keine Benachrichtigung von vor dem Schlaf nachtraeglich feuern (aelter als 2 Min → verwerfen) — Test in Task 6.
3. **Explorer-Neustart / Taskleiste weg**: das Feld haengt sich selbst wieder ein, das Tray-Symbol bleibt — Waechter in Task 7, manuelle Pruefung dort.
4. **Erster Start ohne Netz**: in Deutschland greift die gebuendelte Tabelle, sonst „Keine Zeiten" statt Absturz — Test in Task 4.
5. **Zeitumstellung (letzter Oktobersonntag)**: Restzeit ueber die Umstellung stimmt, weil mit `ZonedDateTime` gerechnet wird — Test in Task 5.

---

### Task 1: Reine Anzeige-Logik nach `core-prayertimes`

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/display/RemainingSteps.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/display/StepBoundaries.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/display/PrayerLabels.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/display/KarahaText.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/display/NextPrayer.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/RemainingLabel.kt` (Koerper delegieren)
- Modify: `app/src/main/kotlin/de/gebetszeiten/alarm/DisplayStepBoundaries.kt` (delegieren, Konstanten aus core)
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/KarahaDisplay.kt` (Text aus core)
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/display/DisplayTest.kt`

**Interfaces:**
- Produces (Paket `de.gebetszeiten.core.prayertimes.display`):
  - `fun remainingStepShort(remaining: Duration): String`
  - `fun remainingStepLabel(remaining: Duration): String`
  - `fun isUrgent(remaining: Duration): Boolean`
  - `const val BOUNDARY_SETTLE_MS = 500L`, `const val BOUNDARY_MIN_LEAD_MS = 1_000L`
  - `fun displayStepBoundaries(targetMs: Long, nowMs: Long): List<Long>`, `fun nextDisplayBoundary(boundaries: List<Long>, nowMs: Long): Long?`
  - `fun Prayer.germanName(): String`
  - `data class KarahaLine(val text: String, val active: Boolean)` und `fun karahaLine(status: Karaha.Status): KarahaLine?`
  - `data class UpcomingPrayer(val prayer: Prayer, val time: ZonedDateTime)` und `fun nextPrayer(today: DailyPrayerTimes, tomorrow: DailyPrayerTimes?, now: ZonedDateTime): UpcomingPrayer?` (Sonnenaufgang uebersprungen)
- Die App behaelt ihre bisherigen Namen (`de.gebetszeiten.prayer.remainingStepShort`, `KarahaLine` usw.) als duenne Weiterleitungen bzw. `typealias KarahaLine = de.gebetszeiten.core.prayertimes.display.KarahaLine` — keine Aufrufstelle in der App aendert sich.

- [ ] **Step 1: Failing tests schreiben**

```kotlin
@Test fun steps() {
    assertEquals("2+ Std", remainingStepShort(Duration.ofMinutes(150)))
    assertEquals("20+ Min", remainingStepShort(Duration.ofMinutes(27)))
    assertEquals("7 Min", remainingStepShort(Duration.ofMinutes(7)))
    assertEquals("", remainingStepShort(Duration.ofSeconds(30)))
    assertEquals("jetzt", remainingStepLabel(Duration.ofSeconds(-5)))
    assertTrue(isUrgent(Duration.ZERO)); assertFalse(isUrgent(Duration.ofMinutes(10)))
}
@Test fun karahaTexts() {
    val w = Karaha.Window(Karaha.Kind.ZEVAL, at(13, 1), at(13, 21))
    assertEquals(KarahaLine("⚠️ Karaha bis 13:21", true), karahaLine(Karaha.Status.Active(w)))
    assertEquals(KarahaLine("⚠️ Karaha ab 13:01", false), karahaLine(Karaha.Status.Soon(w)))
    assertNull(karahaLine(Karaha.Status.None))
}
@Test fun nextPrayerSkipsSunrise()      // now zwischen Fajr und Sonnenaufgang → DHUHR
@Test fun nextPrayerAfterIshaIsTomorrowsFajr()  // now nach Isha → tomorrow.fajr
@Test fun nextPrayerAfterIshaWithoutTomorrowIsNull()
@Test fun germanNames()  // ASR → "Asr", SUNRISE → "Sonnenaufgang"
```
Die bestehenden App-Tests zu `displayStepBoundaries` werden als Vorlage fuer zwei Faelle in `DisplayTest` uebernommen (gleiche Erwartungswerte).

- [ ] **Step 2: Laufen lassen, muss scheitern** — `.\gradlew.bat --console=plain :core-prayertimes:test` → FAIL (unresolved reference).
- [ ] **Step 3: Implementieren** — Koerper aus `RemainingLabel.kt`, `DisplayStepBoundaries.kt` und `KarahaDisplay.kt` (Format `HH:mm`) uebernehmen; `nextPrayer` wie `PrayerProvider.next`/`nextPrayer`, aber rein. App-Dateien leiten weiter; KDoc-Begruendungen wandern mit nach core, in der App bleibt ein Einzeiler-Verweis.
- [ ] **Step 4: Alle Suiten aus „Global Constraints" laufen lassen** — Expected: BUILD SUCCESSFUL, 0 Fehler.
- [ ] **Step 5: Commit** — `refactor(core): Anzeige-Logik (Stufen, Karaha-Text, naechstes Gebet) liegt in core`

---

### Task 2: Cache-Kopf-Bausteine nach `core-prayertimes`

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/CacheHeaders.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt` (`refreshedHeader`/`newHeader`/Versuchs-Transformation delegieren)
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/CacheHeadersTest.kt`

**Interfaces:**
- Produces:
  - `fun refreshedHeader(existing: CacheHeader?, lat: Double, lng: Double, locationId: Int?, updatedEpochMs: Long, verification: Verification?): CacheHeader`
  - `fun CacheStore.withAttempt(entries: List<RawEntry>, error: String?, nowEpochMs: Long, lat: Double, lng: Double, pinnedCoords: List<Pair<Double, Double>>, maxUnpinned: Int): List<RawEntry>` — genau die Transformation, die heute in `OfficialTimesCache.recordAttempt` im `update { }` steht.
- Die App behaelt `internal fun refreshedHeader(...)` als Weiterleitung, damit ihre Tests unveraendert kompilieren.

- [ ] **Step 1: Failing tests** — `refreshedHeaderKeepsAttemptAndError` (existing mit `lastAttemptEpochMs=5`, `lastError="x"` → beide uebernommen, `firstDate`/`lastDate` null), `withAttemptUpdatesExisting`, `withAttemptCreatesEmptyEntryForUnknownPlace` (neuer Eintrag mit leerem Zeitplan und `lastError`).
- [ ] **Step 2: `:core-prayertimes:test`** → FAIL.
- [ ] **Step 3: Implementieren** (Koerper aus `OfficialTimesCache.kt` verschieben).
- [ ] **Step 4: Alle Suiten** → gruen.
- [ ] **Step 5: Commit** — `refactor(core): Cache-Kopf und Abrufversuch als reine Funktionen in core`

---

### Task 3: Plattformneutraler Diyanet-Abruf als Modul `net-diyanet-core`

**Files:**
- Create: `net-diyanet-core/build.gradle.kts` (Plugin `kotlin-jvm`, Java 17 wie `core-prayertimes`; deps: `api(project(":core-prayertimes"))`, `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")`, `implementation("org.json:json:20240303")`, `testImplementation(libs.junit)`)
- Modify: `settings.gradle.kts` (`include(":net-diyanet-core")`)
- Move (Paket bleibt `de.gebetszeiten.net`): `DiyanetHttp.kt`, `DiyanetYearPageParser.kt`, `DiyanetDirectFetcher.kt`, `DiyanetProxyFetcher.kt`, `EzanVaktiFetcher.kt`, `CompositeDiyanetFetcher.kt` → `net-diyanet-core/src/main/kotlin/de/gebetszeiten/net/`
- Modify: `CompositeDiyanetFetcher.kt` — `log`-Default wird `{ _, _ -> }`; `companion object` behaelt nur `fun create(nearestPlace: suspend (Double, Double) -> DiyanetPlace?, bundledLocationId: suspend (Double, Double) -> Int?, log: (String, Exception) -> Unit = { _, _ -> }): CompositeDiyanetFetcher`; `resolveLocationIdChain` und `fetchErrorText` werden `public`.
- Create: `net-diyanet/src/main/kotlin/de/gebetszeiten/net/AndroidFetcherFactory.kt` — `fun CompositeDiyanetFetcher.Companion.create(context: Context, bundledLocationId: suspend (Double, Double) -> Int?): CompositeDiyanetFetcher` (ruft die JVM-Fabrik mit `DiyanetPlaceIndex.nearest(context, …)` und `android.util.Log.w("DiyanetFetch", …)` — Verhalten identisch zu heute).
- Modify: `net-diyanet/build.gradle.kts` (`api(project(":net-diyanet-core"))`)
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` und `wear/src/online/kotlin/de/gebetszeiten/wear/WearFetchProvider.kt` — je **nur** `import de.gebetszeiten.net.create` ergaenzen.
- Move Tests: alle unter `net-diyanet/src/test/...` **ausser** `WorldIndexIntegrityTest` und `PhoneWatchLocationIdConsistencyTest` (die lesen Asset-Dateien per Pfad) → `net-diyanet-core/src/test/kotlin/de/gebetszeiten/net/`.

**Interfaces:**
- Produces: `CompositeDiyanetFetcher.create(nearestPlace, bundledLocationId, log)` (JVM), `DiyanetYearPageParser`, `DiyanetDirectFetcher`, `DiyanetProxyFetcher`, `EzanVaktiFetcher` — alle in `de.gebetszeiten.net`, ohne Android.

- [ ] **Step 1: Neuer Test** in `net-diyanet-core`: `jvmFactoryUsesNearestPlaceInChain` — `create(nearestPlace = { _, _ -> DiyanetPlace(9541, …) }, bundledLocationId = { _, _ -> null })`; da Netz im Test nicht erlaubt ist, statt `create` die Kette pruefen: `resolveLocationIdChain(bundledId = null, indexPlace = place, cachedId = null, searchByName = { error("kein Netz") })` liefert `9541`.
- [ ] **Step 2: `:net-diyanet-core:test`** → FAIL (Modul fehlt).
- [ ] **Step 3: Verschieben per `git mv`**, Fabriken wie oben, Build-Dateien.
- [ ] **Step 4: `:net-diyanet-core:test` plus alle Suiten** → gruen; zusaetzlich `:app:assembleOnlineDebug :wear:assembleOnlineDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `refactor(net): Diyanet-Abruf ohne Android in eigenem Modul net-diyanet-core`

---

### Task 4: Modul `desktop` — Einstellungen, Cache, Quellen, Auffrischung

**Files:**
- Create: `desktop/build.gradle.kts` — Plugins `kotlin-jvm`, `application` (`mainClass = "de.gebetszeiten.desktop.MainKt"`); Toolchain `languageVersion = 21`; deps: `project(":core-prayertimes")`, `project(":net-diyanet-core")`, coroutines 1.9.0, `net.java.dev.jna:jna-platform:5.14.0`, `com.googlecode.soundlibs:jlayer:1.0.1.4`, `testImplementation(libs.junit)`. Ressourcen: `sourceSets.main.resources.srcDir(rootProject.file("shared-assets"))` und `srcDir(rootProject.file("net-diyanet/src/main/assets"))` (also `official/locations-de.tsv`, `official/tables/*.tsv`, `official/locations-world.tsv` im Klassenpfad).
- Modify: `settings.gradle.kts` (`include(":desktop")`); falls die Toolchain 21 nicht lokal vorhanden ist, in `pluginManagement`/`plugins` `id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"`.
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/DesktopSettings.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/SettingsStore.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/DesktopCache.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/BundledTables.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/TimesSource.kt`
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/data/DataTest.kt`

**Interfaces:**
- Produces:
  - `data class DesktopSettings(val placeName: String? = null, val latitude: Double? = null, val longitude: Double? = null, val diyanetId: Int? = null, val reminders: Set<Prayer> = setOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA), val leadMinutes: Int = 0, val karahaWarning: Boolean = false, val showKaraha: Boolean = true, val ezanFile: String? = null, val fajrEzanFile: String? = null, val ezanVolume: Int = 70, val taskbarField: Boolean = true, val autostart: Boolean = true)`
  - `class SettingsStore(dir: Path) { fun load(): DesktopSettings; fun save(s: DesktopSettings) }` — `settings.properties`, UTF-8, unbekannte/kaputte Werte → Default.
  - `class DesktopCache(file: Path) { fun get(date: LocalDate, lat: Double, lng: Double): SixTimes?; fun cachedLocationId(lat: Double, lng: Double): Int?; fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int?, verification: Verification?); fun recordAttempt(error: String?, nowEpochMs: Long, lat: Double, lng: Double); fun dueOrder(active: Pair<Double, Double>, today: LocalDate): List<DueLocation> }` — alles ueber `CacheStore`/`refreshedHeader`/`withAttempt` aus Task 2; `pinnedCoords = emptyList()`, `maxUnpinned = 5`; Schreiben atomar (temp-Datei + `Files.move(ATOMIC_MOVE)`).
  - `class BundledTables(open: (String) -> InputStream?) { fun get(lat: Double, lng: Double, date: LocalDate): SixTimes? }` — `OfficialLocations.nearest` ueber `official/locations-de.tsv`, Tabelle `official/tables/${tableRef}.tsv`, `parseOfficialTimes`; fehlende Ressource → `null`.
  - `class TimesSource(cache: DesktopCache, bundled: BundledTables) { fun daily(s: DesktopSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes? }` — Reihenfolge `daySourceOrder(useOnline = true, calculationFillsGaps = false)`, `CALCULATION` → `null`; kein Ort gesetzt → `null`.
  - `class Refresher(cache: DesktopCache, fetcher: OfficialTimesFetcher, clock: () -> Long) { suspend fun refresh(s: DesktopSettings, force: Boolean) }` — wie `PrayerProvider.refreshOfficial` fuer genau einen Ort: `chooseTarget(cache.dueOrder(...), …)`, `withTimeout(25_000)`, Erfolg → `putAll` + `recordAttempt(null)`, Fehlschlag → `recordAttempt(Fehlertext aus candidates via fetchErrorText)`.
  - Fetcher-Erzeugung in Task 10: `CompositeDiyanetFetcher.create(nearestPlace = { lat, lng -> DiyanetPlaces.nearest(worldIndex, lat, lng) }, bundledLocationId = { lat, lng -> OfficialLocations.nearest(deLocations, lat, lng)?.diyanetId })`.

- [ ] **Step 1: Failing tests**
  - `settingsRoundTrip` (alle Felder), `brokenSettingsFileGivesDefaults`
  - `cachePutThenGet` (Temp-Datei), `recordAttemptWithoutScheduleKeepsNoTimes`
  - `cacheBeatsBundle` (gleicher Tag in beiden, unterschiedliche Fajr → Cache gewinnt)
  - `bundleUsedWhenCacheEmpty` (Nuernberg 49.45/11.08, Datum innerhalb der Bundle-Abdeckung → nicht null) — *Review Focus 4*
  - `noPlaceGivesNull`, `farAwayPlaceWithoutCacheGivesNull` (z. B. Tokio)
  - `refresherFailureRecordsError` (Fake-Fetcher wirft → Kopf hat `lastError`, `get` liefert null)
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: `:desktop:test`** → gruen.
- [ ] **Step 5: Commit** — `feat(desktop): Modul mit Einstellungen, Zeiten-Cache und amtlicher Quellenkette`

---

### Task 5: Anzeigezustand der Taskleiste (rein)

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/display/DisplayState.kt`
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/display/DisplayStateTest.kt`

**Interfaces:**
- Consumes: `nextPrayer`, `remainingStepLabel`, `isUrgent`, `karahaLine`, `germanName`, `displayStepBoundaries`, `nextDisplayBoundary` (Task 1); `Karaha.windows/status/boundaries`.
- Produces:
  - `enum class Tone { NORMAL, URGENT, KARAHA_SOON, KARAHA_ACTIVE, NO_TIMES }`
  - `data class DisplayState(val line1: String, val line2: String, val tone: Tone, val tooltip: String, val nextChangeAt: ZonedDateTime?)`
  - `fun displayState(now: ZonedDateTime, today: DailyPrayerTimes?, tomorrow: DailyPrayerTimes?, showKaraha: Boolean): DisplayState`

Regeln: `line1` = Gebetsname; `line2` = `remainingStepLabel(rest)`, ausser Karaha `Soon`/`Active` (und `showKaraha`) → `karahaLine.text`. `tone`: Karaha vor Dringlichkeit. `tooltip` = „Noch 20+ Min bis Asr" (bzw. „Asr jetzt" in der letzten Minute) plus „ · ⚠️ Karaha ab 13:01", wenn aktiv. Ohne Zeiten: `line1 = "Keine Zeiten"`, `line2 = ""`, `tone = NO_TIMES`, `tooltip = "Keine amtlichen Zeiten fuer diesen Ort"` (Umlaut im UI-Text: „für"), `nextChangeAt = null`. `nextChangeAt` = fruehester Zeitpunkt > now aus: Gebetszeit, `nextDisplayBoundary(displayStepBoundaries(...))`, `Karaha.boundaries`, naechste Mitternacht.

- [ ] **Step 1: Failing tests**
  - `asrIn27Minutes` → `("Asr", "noch 20+ Min", NORMAL, "Noch 20+ Min bis Asr")`
  - `urgentLastTenMinutes` → `"noch 7 Min"`, `URGENT`
  - `lastMinuteSaysJetzt`
  - `zevalSoonReplacesLine2` → `"⚠️ Karaha ab 13:01"`, `KARAHA_SOON`, line1 bleibt `"Dhuhr"`
  - `isfirarActive` → `"⚠️ Karaha bis <maghrib>"`, `KARAHA_ACTIVE`
  - `karahaHiddenWhenSwitchedOff`
  - `afterIshaShowsTomorrowsFajr` — *Review Focus 1*
  - `noTimes`
  - `nextChangeIsNextStepBoundary` (rest 27 Min → naechste Aenderung bei rest 20 Min + 500 ms)
  - `dstDayRemainingIsWallClockCorrect` — 25.10.2026 Europe/Berlin, now 01:30, Fajr 06:00 → Restzeit 5 h 30 min real (`"noch 5+ Std"`) — *Review Focus 5*
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: `:desktop:test`** → gruen.
- [ ] **Step 5: Commit** — `feat(desktop): Anzeigezustand fuer die Taskleiste aus der gemeinsamen Logik`

---

### Task 6: Benachrichtigungsplan (rein)

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/notify/NotificationPlan.kt`
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/notify/NotificationPlanTest.kt`

**Interfaces:**
- Produces:
  - `enum class EventKind { PRAYER, LEAD, KARAHA_WARNING }`
  - `data class NotifyEvent(val at: ZonedDateTime, val kind: EventKind, val prayer: Prayer?, val title: String, val text: String, val playEzan: Boolean)`
  - `fun notificationEvents(times: DailyPrayerTimes, s: DesktopSettings): List<NotifyEvent>` — sortiert; `PRAYER` je Gebet in `s.reminders` (nie SUNRISE), Titel „Asr", Text „Es ist Zeit fuer Asr" („für"), `playEzan = s.ezanFile != null || (prayer == FAJR && s.fajrEzanFile != null)`; `LEAD` bei `leadMinutes > 0` um `time - leadMinutes`, Text „Asr in 10 Min", nie Ezan; `KARAHA_WARNING` bei `karahaWarning` um `window.start - Karaha.WARN_LEAD_MIN`, Text = `karahaLine(Soon).text`.
  - `fun dueEvents(events: List<NotifyEvent>, lastRun: ZonedDateTime, now: ZonedDateTime): List<NotifyEvent>` — Ereignisse mit `lastRun < at <= now`, aber nur solche mit `at >= now - 2 Min` (Verpasstes durch Schlaf wird verworfen).

- [ ] **Step 1: Failing tests** — `defaultRemindersFiveNoSunrise`, `leadEventTenMinutesBefore`, `karahaWarningFifteenBeforeWindow`, `fajrUsesFajrEzanFlag`, `missedDuringSleepIsDropped` (lastRun 10:00, now 15:00, Event 13:30 → leer) — *Review Focus 2*, `justDueIsDelivered` (Event now-30 s → geliefert).
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: `:desktop:test`** → gruen.
- [ ] **Step 5: Commit** — `feat(desktop): Benachrichtigungsplan mit Vorlauf, Karaha-Warnung und Schlafschutz`

---

### Task 7: Feld in der Taskleiste (Win32)

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/taskbar/TaskbarGeometry.kt` (rein)
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/taskbar/Win32Taskbar.kt` (JNA)
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/taskbar/TaskbarField.kt` (Swing)
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/taskbar/TaskbarGeometryTest.kt`

**Interfaces:**
- Produces:
  - `data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)`
  - `fun fieldBox(taskbar: Box, trayNotify: Box, width: Int, gap: Int = 8): Box?` — Feld rechtsbuendig links von `trayNotify`, volle Hoehe der Leiste, Koordinaten **relativ zur Taskleiste** (fuer `SetParent`); `null`, wenn die Leiste nicht unten liegt (Hoehe > Breite) oder zu wenig Platz ist.
  - `object Win32Taskbar { fun taskbar(): HWND?; fun trayNotify(taskbar: HWND): HWND?; fun rect(h: HWND): Box; fun attach(child: HWND, parent: HWND); fun isLightTheme(): Boolean; fun dpiScale(h: HWND): Double }` — `FindWindow("Shell_TrayWnd", null)`, `FindWindowEx(taskbar, null, "TrayNotifyWnd", null)`, `SetParent` + Stil `WS_CHILD|WS_VISIBLE` statt `WS_POPUP`, Theme aus `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\SystemUsesLightTheme`, `GetDpiForWindow`.
  - `class TaskbarField(onClick: () -> Unit) { fun show(state: DisplayState); fun reattachIfNeeded(); fun hide() }` — `JWindow` ohne Rahmen, zweizeilig rechtsbuendig, Segoe UI 9 pt × DPI, Farben: Text weiss (dunkel) / `#1B1B1B` (hell), `URGENT` `#FF6B5A`, `KARAHA_SOON` `#F2B233`, `KARAHA_ACTIVE` `#FF8A3D`, `NO_TIMES` grau `#9A9A9A`; Breite aus dem breiteren der beiden Texte + 8 px. Prozess per `SetProcessDpiAwarenessContext(PER_MONITOR_AWARE_V2)` beim Start.

Als Kindfenster der Taskleiste verschwindet das Feld automatisch mit ihr (Vollbild, automatisch ausblenden).

- [ ] **Step 1: Failing tests** — `fieldSitsLeftOfTray` (taskbar `0,1392,2560,1440`, tray `2300,1392,2560,1440`, width 120 → `Box(2172, 0, 2292, 48)`), `verticalTaskbarGivesNull`, `notEnoughSpaceGivesNull`.
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.** `reattachIfNeeded()`: wenn `IsWindow(parent)` falsch oder `GetParent(child)` nicht die aktuelle `Shell_TrayWnd` ist (Explorer-Neustart), neu suchen und einhaengen; findet sich keine Taskleiste, Feld verstecken (Tray uebernimmt) — *Review Focus 3*. Aufgerufen vom 60-s-Waechter aus Task 10.
- [ ] **Step 4: `:desktop:test`** → gruen; dazu manuell: `.\gradlew.bat --console=plain :desktop:run` mit einem Demo-`DisplayState` (Hilfs-`main` in `TaskbarField.kt` hinter `if (args.contains("--demo-field"))`) → Feld sitzt links vom Infobereich, Hell/Dunkel stimmt, Explorer im Task-Manager neu starten → Feld ist binnen 60 s zurueck. Screenshot an den Nutzer.
- [ ] **Step 5: Commit** — `feat(desktop): Feld in der Taskleiste per SetParent, mit Wiedereinhaengen`

---

### Task 8: Tray-Symbol, Toasts und Ezan

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/ui/Tray.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/notify/EzanPlayer.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/notify/FocusState.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/ui/TodayPopup.kt`
- Create: `desktop/src/main/resources/icon.png` (16/32 px, schlichter Halbmond, AMOLED-Stil der App) und dasselbe als `desktop/src/main/resources/icon.ico` (16/32/48 px, fuer `jpackage` in Task 10)
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/notify/FocusStateTest.kt`

**Interfaces:**
- Consumes: `DisplayState` (Task 5), `NotifyEvent` (Task 6), `DesktopSettings` (Task 4).
- Produces:
  - `class Tray(onToday: () -> Unit, onSettings: () -> Unit, onToggleField: () -> Unit, onStopEzan: () -> Unit, onQuit: () -> Unit) { fun update(state: DisplayState); fun toast(e: NotifyEvent) }` — `java.awt.SystemTray`/`TrayIcon`, Tooltip = `state.tooltip`, Menue „Heutige Zeiten", „Einstellungen", „Feld in der Taskleiste" (Haken), „Ezan stoppen" (nur aktiv waehrend Wiedergabe), „Beenden"; Toast ueber `TrayIcon.displayMessage(title, text, NONE)`.
  - `class EzanPlayer { fun play(file: Path, volumePercent: Int); fun stop(); val isPlaying: Boolean }` — `.mp3` ueber JLayer `AdvancedPlayer` auf eigenem Thread, `.wav` ueber `javax.sound.sampled.Clip` mit `MASTER_GAIN`; Datei fehlt/kaputt → still, kein Absturz; neuer `play` stoppt den alten. Ohne Ezan-Datei bei `PRAYER`: `Toolkit.beep()` nur, wenn kein Toast-Ton kommt — Toasts von Windows haben eigenen Ton, also kein Extra-Ton.
  - `fun quietFromState(quns: Int): Boolean` — reine Abbildung von `SHQueryUserNotificationState` (`QUNS_BUSY=2`, `QUNS_RUNNING_D3D_FULL_SCREEN=3`, `QUNS_PRESENTATION_MODE=4`, `QUNS_QUIET_TIME=6` → `true`; sonst `false`); `object FocusState { fun quiet(): Boolean }` fragt per JNA (`shell32`) ab. Ist `quiet()`, spielt kein Ezan; der Toast wird trotzdem gesendet (Windows legt ihn ins Benachrichtigungscenter).
  - `class TodayPopup { fun showAbove(anchor: Rectangle, times: DailyPrayerTimes?, place: String?, karaha: List<Karaha.Window>, source: String) }` — kleines randloses Fenster: sechs Zeiten (naechstes Gebet hervorgehoben), Ort, Karaha-Fenster „İşrak 07:43–08:28" usw., Zeile „Diyanet · Stand 25.10.2026"; schliesst bei Fokusverlust.

- [ ] **Step 1: Failing test** — `quietStates` (2,3,4,6 → true; 1,5,7 → false).
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: `:desktop:test`** → gruen; manuell: Demo-Toast und eine kurze Test-MP3 (vom Nutzer gewaehlt) abspielen und ueber das Menue stoppen.
- [ ] **Step 5: Commit** — `feat(desktop): Tray-Symbol, Tageszeiten-Popup, Toasts und stoppbarer Ezan`

---

### Task 9: Einstellungsfenster, Ortssuche, Autostart

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/ui/SettingsWindow.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/PlaceSearch.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/data/Autostart.kt`
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/data/PlaceSearchTest.kt`

**Interfaces:**
- Produces:
  - `fun searchPlaces(all: List<DiyanetPlace>, query: String, limit: Int = 20): List<DiyanetPlace>` — Vergleich ueber `TextNormalize` aus core (tuerkische Zeichen, Gross/klein), Treffer am Wortanfang vor Treffern mittendrin, dann alphabetisch; Anzeige ueber `DiyanetPlace.displayName()`; Query < 2 Zeichen → leer.
  - `object Autostart { fun set(enabled: Boolean, exe: Path); fun isEnabled(): Boolean }` — `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, Wert `Gebetszeiten` = `"<exe>"` (via `Advapi32Util`); nur wirksam, wenn das Programm als gepackte `.exe` laeuft (`ProcessHandle.current().info().command()` endet auf `Gebetszeiten.exe`), sonst nichts tun.
  - `class SettingsWindow(store: SettingsStore, places: List<DiyanetPlace>, onSaved: (DesktopSettings) -> Unit)` — Felder laut Spec Abschnitt 5: Ortssuche (Liste unter dem Suchfeld; Auswahl setzt `placeName`, `latitude`, `longitude`, `diyanetId`), fuenf Haken fuer Gebete, Vorlauf (0/5/10/15/20/30 Min), Karaha anzeigen, Karaha-Warnung, Ezan-Datei + „Fajr eigene Datei" (Dateiauswahl `*.mp3;*.wav`), Lautstaerke-Regler, „Probe hoeren", Feld in der Taskleiste, Autostart. Speichern loest sofort `Refresher.refresh(force = true)` aus (Task 10).

- [ ] **Step 1: Failing tests** — `findsNuernbergWithoutUmlaut` („nurnberg" → enthaelt „Nürnberg"), `turkishDotlessI` („isparta" findet „Isparta"/„ISPARTA"), `prefixBeforeInfix`, `shortQueryEmpty`.
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: `:desktop:test`** → gruen; manuell: Ort „Nürnberg" waehlen, speichern, `settings.properties` pruefen.
- [ ] **Step 5: Commit** — `feat(desktop): Einstellungen mit Diyanet-Ortssuche und Autostart`

---

### Task 10: Zusammenbau, Taktgeber, Paketierung

**Files:**
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/Main.kt`
- Create: `desktop/src/main/kotlin/de/gebetszeiten/desktop/Ticker.kt`
- Modify: `desktop/build.gradle.kts` (Task `packageApp`)
- Create: `desktop/README.md` (Bauen, Starten, wo die Daten liegen, bekannte Grenze „Windows-Update kann das Feld verschieben")
- Test: `desktop/src/test/kotlin/de/gebetszeiten/desktop/TickerTest.kt`

**Interfaces:**
- Consumes: alles aus Task 4–9.
- Produces:
  - `class Ticker(clock: () -> ZonedDateTime, scheduler: ScheduledExecutorService, onTick: () -> Unit)` — plant den naechsten Lauf auf `min(state.nextChangeAt, naechstes NotifyEvent.at)`; zusaetzlich ein Waechter alle 60 s, der `onTick` ausloest, wenn die Wanduhr um mehr als 90 s vom erwarteten Wert abweicht (Schlaf, Uhr gestellt, Zeitzone) und `TaskbarField.reattachIfNeeded()` ruft. `fun nextRunAt(now: ZonedDateTime, nextChange: ZonedDateTime?, nextEvent: ZonedDateTime?): ZonedDateTime` ist rein und getestet.
  - `main`: Einzelinstanz per Lock-Datei in `%APPDATA%\Gebetszeiten\lock` (zweiter Start beendet sich still); laedt Einstellungen; ohne Ort oeffnet sich beim ersten Start das Einstellungsfenster; startet Tray, Feld, Ticker; `Refresher.refresh(force = false)` beim Start, nach jedem Gebet und taeglich nach Mitternacht (auf `Dispatchers.IO`); Swing-Aenderungen nur auf dem EDT; Feld nur neu zeichnen, wenn sich `DisplayState` aendert.
  - Gradle-Task `packageApp`: `installDist`, dann `jpackage --type app-image --name Gebetszeiten --input build/install/desktop/lib --main-jar desktop.jar --main-class de.gebetszeiten.desktop.MainKt --icon src/main/resources/icon.ico --dest build/package --java-options "-Dfile.encoding=UTF-8"`; `jpackage` aus `javaToolchains.launcherFor { languageVersion = 21 }`. Ergebnis: `desktop/build/package/Gebetszeiten/Gebetszeiten.exe` (kein MSI — das braeuchte WiX).

- [ ] **Step 1: Failing test** — `nextRunIsEarlierOfChangeAndEvent`, `nullsFallBackToOneMinute`.
- [ ] **Step 2: `:desktop:test`** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Verifizieren**
  - Alle Suiten aus „Global Constraints" plus `:desktop:test` → gruen.
  - `.\gradlew.bat --console=plain :desktop:packageApp` → `desktop\build\package\Gebetszeiten\Gebetszeiten.exe` existiert.
  - Exe starten, Nürnberg waehlen: Feld zeigt dasselbe wie die Handy-Benachrichtigung zur selben Zeit; Tooltip, Popup, Einstellungen funktionieren; nach Neuanmeldung startet es von selbst. Screenshot der Taskleiste an den Nutzer.
- [ ] **Step 5: Commit** — `feat(desktop): Zusammenbau, Taktgeber und Windows-Paket`

---

## Entscheidungen von Claude in diesem Plan (zur Pruefung durch den Nutzer)

- Swing statt Compose for Desktop: kleiner, kein zusaetzliches Gradle-Plugin, fuer ein Feld + zwei kleine Fenster genug.
- Toasts ueber `TrayIcon.displayMessage` (erscheinen unter Windows 11 als normale Benachrichtigung, Absender „Java"/Programmname) statt einer WinRT-Bruecke.
- Paket als Programmordner mit `Gebetszeiten.exe` statt MSI-Installer (MSI braeuchte zusaetzlich das WiX-Toolset).
- Keine Eigenberechnung im Desktop, auch nicht als Notausgang.
- Je eine Import-Zeile in `app` und `wear`, damit beide die Android-Fabrik des verschobenen Abrufs finden — keine Verhaltensaenderung.
