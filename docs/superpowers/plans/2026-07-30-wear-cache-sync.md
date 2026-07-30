# Wear-Cache-Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Handy repliziert seinen amtlichen Zeiten-Cache per Wear Data Layer (DataItem `/official-times`) an die Uhr; die Uhr liest mit der Kette Sync-Cache → Bundle → Berechnung und übernimmt die Handy-Ortswahl (Uhr-Picker als lokaler Override).

**Architecture:** Geteiltes Zeilenformat + Sync-Vertrag wandern nach `core-prayertimes` (`ScheduleText`, `stampMatches`, `WearSyncContract`). Am Handy pusht ein `WearCacheSync` (nur online-Flavor, per Lambda testbar) nach jedem nicht-leeren Refresh; auf der Uhr materialisiert ein `WearableListenerService` das DataItem in den neuen `WearOfficialCache` (reine Entscheidungslogik in `SyncDecision`, JVM-getestet). Mitgenommen aus dem Online-First-Review: CancellationException-Rethrow im Composite + `withTimeout(25 s)` um `refreshOfficial`. Spec: `docs/superpowers/specs/2026-07-30-wear-cache-sync-design.md`.

**Tech Stack:** Kotlin, Wear Data Layer (`play-services-wearable`), Jetpack DataStore, JUnit4 (reine JVM-Tests), Coroutines.

## Global Constraints

- Gradle NUR über PowerShell mit `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (Bash hat kein JAVA_HOME), immer `--console=plain`.
- Alle Unit-Tests sind reine JVM-Tests, KEIN Robolectric. `android.util.Log` ist nicht gemockt → getestete Klassen bekommen injizierbare `log`-Lambdas.
- `play-services-wearable` am Phone NUR als `"onlineImplementation"` — der offline-Flavor bleibt gms-frei. In der Wear-App als normale `implementation`.
- Wear bekommt sein ERSTES Test-Source-Set: `wear/src/test/kotlin/de/gebetszeiten/wear/`.
- DataItem-Pfad/Schlüssel existieren genau einmal: `WearSyncContract` in core-prayertimes; Phone und Uhr importieren ihn.
- Deutsche Commit-Messages im Repo-Stil, Umlaute im Body als ue/oe/ae.
- Kein Versionsbump (Release ist ein separater chore).
- Die Uhr bleibt funktional standalone: ohne gms/Handy exakt heutiges Verhalten.

## File Structure

| Datei | Verantwortung |
|---|---|
| `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/ScheduleText.kt` (create) | Zeilenformat serialize/parse (tolerant), geteilt Phone+Wear |
| `core-prayertimes/.../officialtimes/CacheStamp.kt` (create) | `stampMatches` (aus app/CacheFreshness.kt hierher verschoben) |
| `core-prayertimes/.../officialtimes/WearSyncContract.kt` (create) | DataItem-Pfad + DataMap-Schlüssel |
| `core-prayertimes/src/test/.../officialtimes/ScheduleTextTest.kt` (create) | Round-trip + Toleranz-Tests |
| `core-prayertimes/src/test/.../officialtimes/CacheStampTest.kt` (create) | 1-km-Toleranz-Tests |
| `app/src/main/kotlin/de/gebetszeiten/official/CacheFreshness.kt` (modify) | `stampMatches` raus (nur noch `needsRefresh`) |
| `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt` (modify) | nutzt `ScheduleText` + core-`stampMatches` |
| `gradle/libs.versions.toml` (modify) | `play-services-wearable` |
| `app/build.gradle.kts` (modify) | `"onlineImplementation"(libs.play.services.wearable)` |
| `app/src/online/kotlin/de/gebetszeiten/official/WearCacheSync.kt` (create) | Push als DataItem, injizierbares put-Lambda |
| `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` (modify) | `syncToWear` → `WearCacheSync` |
| `app/src/offline/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` (modify) | `syncToWear` No-op |
| `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (modify) | Hook-Aufruf + `withTimeout(25 s)` |
| `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt` (modify) | CancellationException-Rethrow |
| `app/src/testOnline/.../WearCacheSyncTest.kt` (create) | Push-Logik mit Fake-Lambda |
| `app/src/testOnline/.../CompositeDiyanetFetcherTest.kt` (modify) | Rethrow-Test |
| `wear/build.gradle.kts` (modify) | gms + junit |
| `wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialCache.kt` (create) | gesyncter Cache + synced-Ort |
| `wear/src/main/kotlin/de/gebetszeiten/wear/SyncDecision.kt` (create) | reine Payload-/Übernahme-Logik |
| `wear/src/main/kotlin/de/gebetszeiten/wear/WearSyncListenerService.kt` (create) | dünner Empfänger + Consumer-Refresh |
| `wear/src/main/kotlin/de/gebetszeiten/wear/WearPrayer.kt` (modify) | Kette Sync-Cache → Bundle → Berechnung, KDoc ehrlich |
| `wear/src/main/AndroidManifest.xml` (modify) | Listener-Service registrieren |
| `wear/src/test/kotlin/de/gebetszeiten/wear/SyncDecisionTest.kt` (create) | Übernahme-/Payload-Tests |
| `PRIVACY.md` (modify) | Wear-Satz ehrlich (Sync vom Handy, selbst kein Internet) |

---

### Task 1: Geteilter Code nach core-prayertimes (TDD)

**Files:**
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/ScheduleText.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/CacheStamp.kt`
- Create: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/WearSyncContract.kt`
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/ScheduleTextTest.kt`
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/CacheStampTest.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/CacheFreshness.kt` (stampMatches löschen)
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt` (ScheduleText + Import)

**Interfaces:**
- Consumes: `SixTimes` (core, bestehend).
- Produces: `ScheduleText.serialize(Map<LocalDate, SixTimes>): String`, `ScheduleText.parse(String): Map<LocalDate, SixTimes>` (tolerant, überspringt kaputte Zeilen), `fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean` (Package `de.gebetszeiten.core.prayertimes.officialtimes`), `object WearSyncContract { PATH, KEY_SCHEDULE, KEY_LAT, KEY_LNG, KEY_CITY }` — Tasks 2/4/5 importieren alles.

- [ ] **Step 1: Fehlschlagende Tests schreiben**

`core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/ScheduleTextTest.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTextTest {

    private val day = LocalDate.of(2026, 7, 30)
    private val times = SixTimes(
        fajr = LocalTime.of(3, 54), sunrise = LocalTime.of(5, 38),
        dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 34),
        maghrib = LocalTime.of(21, 7), isha = LocalTime.of(22, 36),
    )

    @Test
    fun `round-trip erhaelt alle Tage sortiert`() {
        val schedule = mapOf(day.plusDays(1) to times, day to times)
        val text = ScheduleText.serialize(schedule)
        assertEquals(schedule, ScheduleText.parse(text))
        assertEquals(listOf(day, day.plusDays(1)), text.lines().map { LocalDate.parse(it.substringBefore(" ")) })
    }

    @Test
    fun `serialisiert im bisherigen Cache-Zeilenformat`() {
        assertEquals(
            "2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36",
            ScheduleText.serialize(mapOf(day to times)),
        )
    }

    @Test
    fun `leere Map ergibt leeren Text und zurueck`() {
        assertEquals("", ScheduleText.serialize(emptyMap()))
        assertEquals(emptyMap<LocalDate, SixTimes>(), ScheduleText.parse(""))
    }

    @Test
    fun `kaputte Zeilen werden uebersprungen`() {
        val text = "kaputt\n2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36\n2026-07-31 xx"
        assertEquals(mapOf(day to times), ScheduleText.parse(text))
    }
}
```

`core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/CacheStampTest.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheStampTest {

    @Test
    fun `gleicher Punkt passt, fehlender Stempel nicht`() {
        assertTrue(stampMatches(49.4521, 11.0767, 49.4521, 11.0767))
        assertFalse(stampMatches(null, 11.0767, 49.4521, 11.0767))
        assertFalse(stampMatches(49.4521, null, 49.4521, 11.0767))
    }

    @Test
    fun `1 km Toleranz - knapp drunter passt, deutlich drueber nicht`() {
        // 0.008 Grad Breite ~ 0.9 km; 0.02 Grad ~ 2.2 km.
        assertTrue(stampMatches(49.4521, 11.0767, 49.4601, 11.0767))
        assertFalse(stampMatches(49.4521, 11.0767, 49.4721, 11.0767))
    }
}
```

- [ ] **Step 2: Tests laufen lassen — müssen scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test --tests "de.gebetszeiten.core.prayertimes.officialtimes.*" --console=plain`
Expected: FAIL — `Unresolved reference: ScheduleText` / `stampMatches` (Kompilierfehler = roter Test).

- [ ] **Step 3: Implementieren**

`ScheduleText.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate
import java.time.LocalTime

/**
 * Kompaktes Zeilenformat des amtlichen Zeiten-Caches — eine Zeile pro Tag:
 * "YYYY-MM-DD fajr sunrise dhuhr asr maghrib isha". Geteilt zwischen
 * Phone-Cache und Wear-Sync, damit das Format genau einmal existiert.
 */
object ScheduleText {

    fun serialize(schedule: Map<LocalDate, SixTimes>): String =
        schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }

    /** Tolerant: unlesbare Zeilen werden übersprungen (wie der bisherige
     *  Cache-Parser) — ein komplett kaputter Payload ergibt eine leere Map. */
    fun parse(text: String): Map<LocalDate, SixTimes> =
        text.lineSequence().mapNotNull { parseLine(it) }.toMap()

    private fun parseLine(line: String): Pair<LocalDate, SixTimes>? {
        val p = line.trim().split(" ")
        if (p.size != 7) return null
        return try {
            LocalDate.parse(p[0]) to SixTimes(
                fajr = LocalTime.parse(p[1]),
                sunrise = LocalTime.parse(p[2]),
                dhuhr = LocalTime.parse(p[3]),
                asr = LocalTime.parse(p[4]),
                maghrib = LocalTime.parse(p[5]),
                isha = LocalTime.parse(p[6]),
            )
        } catch (e: Exception) {
            null
        }
    }
}
```

`CacheStamp.kt` (Körper wortgleich aus `app/.../CacheFreshness.kt` verschoben):

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import kotlin.math.abs
import kotlin.math.cos

/** ~1 km Toleranz: Cache gilt nur für den Standort, für den er geladen wurde.
 *  Grad-Näherung statt Haversine reicht hier (kleine Distanzen, grobe Schwelle).
 *  Geteilt: Stempel-Guard am Phone UND Übernahme-Vergleich auf der Uhr. */
fun stampMatches(stampLat: Double?, stampLng: Double?, lat: Double, lng: Double): Boolean {
    if (stampLat == null || stampLng == null) return false
    val dLatKm = abs(lat - stampLat) * 111.0
    val dLngKm = abs(lng - stampLng) * 111.0 * cos(Math.toRadians(lat))
    return dLatKm <= 1.0 && dLngKm <= 1.0
}
```

`WearSyncContract.kt`:

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

/** Vertrag des Phone→Wear-Syncs: DataItem-Pfad + DataMap-Schlüssel.
 *  Liegt in core, damit Phone (Sender) und Uhr (Empfänger) exakt
 *  dieselben Konstanten nutzen. */
object WearSyncContract {
    const val PATH = "/official-times"
    const val KEY_SCHEDULE = "schedule"
    const val KEY_LAT = "lat"
    const val KEY_LNG = "lng"
    const val KEY_CITY = "city"
}
```

- [ ] **Step 4: App auf den geteilten Code umstellen**

In `app/src/main/kotlin/de/gebetszeiten/official/CacheFreshness.kt`: die Funktion `stampMatches` (Zeilen 7–14) samt der Imports `kotlin.math.abs`/`kotlin.math.cos` ersatzlos löschen — `needsRefresh` bleibt unverändert.

In `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt`:

1. Imports ergänzen:
```kotlin
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
```
(`java.time.LocalTime`-Import entfällt, wenn `parseLine` weg ist.)

2. In `get(...)` den Rumpf-Teil
```kotlin
        return (prefs[key] ?: return null)
            .lineSequence().mapNotNull { parseLine(it) }.toMap()[date]
```
ersetzen durch:
```kotlin
        return ScheduleText.parse(prefs[key] ?: return null)[date]
```

3. In `freshness(...)` die Zeile
```kotlin
        val until = prefs[key]?.lineSequence()?.mapNotNull { parseLine(it) }?.maxOfOrNull { it.first }
```
ersetzen durch:
```kotlin
        val until = prefs[key]?.let { ScheduleText.parse(it).keys.maxOrNull() }
```

4. In `putAll(...)` den `text`-Block
```kotlin
        val text = schedule.entries
            .sortedBy { it.key }
            .joinToString("\n") { (d, t) ->
                "$d ${t.fajr} ${t.sunrise} ${t.dhuhr} ${t.asr} ${t.maghrib} ${t.isha}"
            }
```
ersetzen durch:
```kotlin
        val text = ScheduleText.serialize(schedule)
```

5. Die private Funktion `parseLine` ersatzlos löschen.

Danach prüfen, dass NIEMAND sonst das alte app-`stampMatches` referenziert:
Run (Bash): `grep -rn "stampMatches" app/src wear/src core-prayertimes/src`
Expected: Treffer nur noch in `core-prayertimes` (Definition + Tests) und `OfficialTimesCache.kt` (Import + 3 Aufrufe).

- [ ] **Step 5: Alle betroffenen Suiten laufen lassen — grün**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (Verhaltensgleichheit: Format und Toleranz sind wortgleich verschoben).

- [ ] **Step 6: Commit**

```powershell
git add core-prayertimes app\src\main\kotlin\de\gebetszeiten\official\CacheFreshness.kt app\src\main\kotlin\de\gebetszeiten\official\OfficialTimesCache.kt
git commit -m "refactor(core): ScheduleText, stampMatches und WearSyncContract geteilt in core-prayertimes"
```

---

### Task 2: Phone-Push — WearCacheSync + Hook (TDD)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (dependencies-Block)
- Create: `app/src/online/kotlin/de/gebetszeiten/official/WearCacheSync.kt`
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt`
- Modify: `app/src/offline/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt:82-90` (`refreshOfficial`)
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/WearCacheSyncTest.kt`

**Interfaces:**
- Consumes: `ScheduleText.serialize`, `WearSyncContract` (Task 1); `FetchResult`, `AppSettings` (bestehend).
- Produces: `class WearCacheSync(put, log)` mit `suspend fun push(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, city: String)` (wirft nie außer CancellationException) + `WearCacheSync.create(context)`; `OfficialTimesProvider.syncToWear(context, schedule, settings)` in BEIDEN Flavors (offline No-op) — Task 3 zeigt den finalen `refreshOfficial` inkl. dieses Aufrufs.

- [ ] **Step 1: Abhängigkeit anlegen**

In `gradle/libs.versions.toml` unter `[versions]` nach `wear = "1.3.0"` ergänzen:

```toml
playServicesWearable = "19.0.0"
```

Unter `[libraries]` nach dem Block `# Wear OS` ergänzen:

```toml
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
```

In `app/build.gradle.kts` im `dependencies {}`-Block nach `implementation(libs.androidx.datastore.preferences)` ergänzen:

```kotlin
    // Wear-Sync: nur der online-Flavor pusht den amtlichen Cache zur Uhr —
    // der offline-Flavor bleibt gms-frei.
    "onlineImplementation"(libs.play.services.wearable)
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`app/src/testOnline/kotlin/de/gebetszeiten/official/WearCacheSyncTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WearCacheSyncTest {

    private val day = LocalDate.of(2026, 7, 30)
    private val schedule = mapOf(
        day to SixTimes(
            fajr = LocalTime.of(3, 54), sunrise = LocalTime.of(5, 38),
            dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 34),
            maghrib = LocalTime.of(21, 7), isha = LocalTime.of(22, 36),
        ),
    )

    @Test
    fun `pusht serialisierten Text mit Ort und Stadt`() = runBlocking {
        val calls = mutableListOf<List<Any>>()
        val sync = WearCacheSync(
            put = { text, lat, lng, city -> calls.add(listOf(text, lat, lng, city)) },
            log = { _, _ -> },
        )
        sync.push(schedule, 41.0082, 28.9784, "Istanbul")
        assertEquals(1, calls.size)
        assertEquals(
            listOf<Any>("2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36", 41.0082, 28.9784, "Istanbul"),
            calls.single(),
        )
    }

    @Test
    fun `leerer Zeitplan wird nie gepusht`() = runBlocking {
        var calls = 0
        WearCacheSync(put = { _, _, _, _ -> calls++ }, log = { _, _ -> })
            .push(emptyMap(), 41.0, 28.9, "Istanbul")
        assertEquals(0, calls)
    }

    @Test
    fun `Push-Fehler wird geschluckt und geloggt`() = runBlocking {
        var logged = 0
        WearCacheSync(put = { _, _, _, _ -> error("kein gms") }, log = { _, _ -> logged++ })
            .push(schedule, 41.0, 28.9, "Istanbul")
        assertEquals(1, logged)
    }

    @Test
    fun `CancellationException wird durchgereicht`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                WearCacheSync(put = { _, _, _, _ -> throw CancellationException("abbruch") }, log = { _, _ -> })
                    .push(schedule, 41.0, 28.9, "Istanbul")
            }
        }
    }
}
```

- [ ] **Step 3: Test laufen lassen — muss scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.official.WearCacheSyncTest" --console=plain`
Expected: FAIL — `Unresolved reference: WearCacheSync`.

- [ ] **Step 4: WearCacheSync implementieren**

`app/src/online/kotlin/de/gebetszeiten/official/WearCacheSync.kt`:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Repliziert den amtlichen Zeiten-Cache als DataItem an die Uhr
 * (Zustands-Sync mit garantierter Zustellung, last-value-wins).
 * Wirft nie — ein fehlgeschlagener Sync ist folgenlos, der nächste
 * Refresh versucht es erneut. Leere Zeitpläne werden nie gepusht,
 * damit der letzte gute Stand auf der Uhr bleibt.
 */
class WearCacheSync(
    private val put: suspend (schedule: String, lat: Double, lng: Double, city: String) -> Unit,
    private val log: (String, Exception) -> Unit = { msg, e ->
        android.util.Log.w("WearCacheSync", msg, e)
    },
) {

    suspend fun push(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, city: String) {
        if (schedule.isEmpty()) return
        try {
            put(ScheduleText.serialize(schedule), lat, lng, city)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Wear-Sync fehlgeschlagen", e)
        }
    }

    companion object {
        fun create(context: Context) = WearCacheSync(
            put = { schedule, lat, lng, city ->
                val request = PutDataMapRequest.create(WearSyncContract.PATH).apply {
                    dataMap.putString(WearSyncContract.KEY_SCHEDULE, schedule)
                    dataMap.putDouble(WearSyncContract.KEY_LAT, lat)
                    dataMap.putDouble(WearSyncContract.KEY_LNG, lng)
                    dataMap.putString(WearSyncContract.KEY_CITY, city)
                }.asPutDataRequest()
                withContext(Dispatchers.IO) {
                    Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                }
            },
        )
    }
}
```

- [ ] **Step 5: Hook in beiden Flavors + Aufruf im PrayerProvider**

`app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` komplett ersetzen:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/** Online flavor: official times come from Diyanet (direct, proxy fallback). */
object OfficialTimesProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher = CompositeDiyanetFetcher.create(context)

    /** Frisch geholte amtliche Zeiten zur Uhr replizieren (wirft nie). */
    suspend fun syncToWear(context: Context, schedule: Map<LocalDate, SixTimes>, settings: AppSettings) =
        WearCacheSync.create(context).push(schedule, settings.latitude, settings.longitude, settings.city)
}
```

`app/src/offline/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` komplett ersetzen:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/** Offline flavor: no online source — the app is provably network-free. */
object OfficialTimesProvider {
    const val isOnline = false
    fun fetcher(context: Context): OfficialTimesFetcher? = null

    /** Offline gibt es nichts zu syncen (kein Online-Cache, kein gms). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun syncToWear(context: Context, schedule: Map<LocalDate, SixTimes>, settings: AppSettings) = Unit
}
```

In `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` in `refreshOfficial` nach der `cache.putAll(...)`-Zeile ergänzen:

```kotlin
        OfficialTimesProvider.syncToWear(context, result.schedule, settings)
```

(`WearCacheSync.push` gated selbst auf nicht-leer; Task 3 legt `withTimeout` um Fetch+Put+Sync.)

- [ ] **Step 6: Tests laufen lassen — grün, beide Flavors**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (offline kompiliert: No-op-Hook ohne gms).

- [ ] **Step 7: Commit**

```powershell
git add gradle\libs.versions.toml app\build.gradle.kts app\src\online\kotlin\de\gebetszeiten\official\WearCacheSync.kt app\src\online\kotlin\de\gebetszeiten\official\OfficialTimesProvider.kt app\src\offline\kotlin\de\gebetszeiten\official\OfficialTimesProvider.kt app\src\main\kotlin\de\gebetszeiten\prayer\PrayerProvider.kt app\src\testOnline\kotlin\de\gebetszeiten\official\WearCacheSyncTest.kt
git commit -m "feat(online): WearCacheSync pusht amtlichen Cache als DataItem zur Uhr"
```

---

### Task 3: Alt-Findings — CancellationException-Rethrow + withTimeout (TDD)

**Files:**
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (`refreshOfficial`)
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt` (ergänzen)

**Interfaces:**
- Consumes: `OfficialTimesProvider.syncToWear` (Task 2).
- Produces: `refreshOfficial` bricht nach 25 s ab (nur Timeout wird geschluckt, echte Cancellation propagiert) — kein neues API.

- [ ] **Step 1: Fehlschlagenden Test ergänzen**

In `app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt` Imports ergänzen (`kotlinx.coroutines.CancellationException`, `org.junit.Assert.assertThrows`) und am Ende der Klasse ergänzen:

```kotlin
    @Test
    fun `CancellationException wird durchgereicht statt geschluckt`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                fetcher(direct = { throw CancellationException("abbruch") }).fetch(settings)
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                CompositeDiyanetFetcher(
                    { throw CancellationException("abbruch") },
                    { yearData },
                    { proxyData },
                    log = { _, _ -> },
                ).fetch(settings)
            }
        }
    }
```

- [ ] **Step 2: Test laufen lassen — muss scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.official.CompositeDiyanetFetcherTest" --console=plain`
Expected: FAIL — heute schluckt `catch (e: Exception)` die CancellationException (fetch liefert Proxy-Daten bzw. leeres Ergebnis statt zu werfen).

- [ ] **Step 3: Rethrow implementieren**

In `CompositeDiyanetFetcher.kt` Import ergänzen: `import kotlinx.coroutines.CancellationException`.

In `fetch(...)` den `resolveId`-try/catch ersetzen durch:

```kotlin
        val id = try {
            resolveId(settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Standort-Aufloesung fehlgeschlagen", e)
            null
        } ?: return FetchResult(emptyMap(), null)
```

In `attempt(...)` den try/catch ersetzen durch:

```kotlin
    ): Map<LocalDate, SixTimes> = try {
        source(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("$label fehlgeschlagen (id=$id)", e)
        emptyMap()
    }
```

- [ ] **Step 4: withTimeout um den Refresh**

In `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` Imports ergänzen:

```kotlin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
```

Den Rumpf von `refreshOfficial` ab der `fetcher`-Zeile ersetzen, sodass die Methode so endet:

```kotlin
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        // Broadcast-Budget (~10-30 s im Alarm-Receiver): der Refresh darf den
        // Empfaenger nicht laenger blockieren — naechster Anlauf beim
        // folgenden Gebet. Nur der Timeout wird geschluckt; echte
        // Cancellation propagiert (Composite reicht sie durch).
        try {
            withTimeout(25_000) {
                val result = fetcher.fetch(settings)
                cache.putAll(result.schedule, settings.latitude, settings.longitude, result.locationId)
                OfficialTimesProvider.syncToWear(context, result.schedule, settings)
            }
        } catch (e: TimeoutCancellationException) {
            android.util.Log.w("PrayerProvider", "refreshOfficial abgebrochen (Timeout)", e)
        }
```

- [ ] **Step 5: Tests laufen lassen — grün, beide Flavors**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app\src\online\kotlin\de\gebetszeiten\official\CompositeDiyanetFetcher.kt app\src\main\kotlin\de\gebetszeiten\prayer\PrayerProvider.kt app\src\testOnline\kotlin\de\gebetszeiten\official\CompositeDiyanetFetcherTest.kt
git commit -m "fix(online): CancellationException durchreichen, refreshOfficial mit 25s-Timeout"
```

---

### Task 4: Uhr — WearOfficialCache + SyncDecision (TDD, erstes wear-Test-Source-Set)

**Files:**
- Modify: `wear/build.gradle.kts`
- Create: `wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialCache.kt`
- Create: `wear/src/main/kotlin/de/gebetszeiten/wear/SyncDecision.kt`
- Test: `wear/src/test/kotlin/de/gebetszeiten/wear/SyncDecisionTest.kt`

**Interfaces:**
- Consumes: `ScheduleText`, `stampMatches` (Task 1, core).
- Produces: `WearOfficialCache.get(context, date, lat, lng): SixTimes?`, `.syncedLocation(context): Pair<Double, Double>?`, `.store(context, scheduleText, lat, lng, adoptedLocation)`; `SyncDecision.Payload(scheduleText, lat, lng, city)`, `SyncDecision.parse(payload): Map<LocalDate, SixTimes>?` (null = verwerfen), `SyncDecision.shouldAdoptLocation(payload, syncedLat, syncedLng): Boolean` — Task 5 verdrahtet beides.

- [ ] **Step 1: Abhängigkeiten**

In `wear/build.gradle.kts` im `dependencies {}`-Block nach `implementation(libs.androidx.datastore.preferences)` ergänzen:

```kotlin
    // Phone-Sync: amtlicher Zeiten-Cache kommt als DataItem vom Handy.
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`wear/src/test/kotlin/de/gebetszeiten/wear/SyncDecisionTest.kt` (Verzeichnis neu anlegen):

```kotlin
package de.gebetszeiten.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncDecisionTest {

    private fun payload(lat: Double = 41.0082, lng: Double = 28.9784) = SyncDecision.Payload(
        scheduleText = "2026-07-30 03:54 05:38 13:27 17:34 21:07 22:36",
        lat = lat,
        lng = lng,
        city = "Istanbul",
    )

    @Test
    fun `gueltiger Payload wird geparst`() {
        val schedule = SyncDecision.parse(payload())
        assertEquals(setOf(LocalDate.of(2026, 7, 30)), schedule!!.keys)
    }

    @Test
    fun `leerer oder unlesbarer Payload wird verworfen`() {
        assertNull(SyncDecision.parse(payload().copy(scheduleText = "")))
        assertNull(SyncDecision.parse(payload().copy(scheduleText = "voelliger unsinn")))
    }

    @Test
    fun `Erst-Sync uebernimmt den Handy-Ort`() {
        assertTrue(SyncDecision.shouldAdoptLocation(payload(), syncedLat = null, syncedLng = null))
    }

    @Test
    fun `gleicher Handy-Ort wie zuletzt - Uhr-Override bleibt`() {
        assertFalse(SyncDecision.shouldAdoptLocation(payload(), syncedLat = 41.0082, syncedLng = 28.9784))
    }

    @Test
    fun `neuer Handy-Ort wird uebernommen (ausserhalb 1-km-Toleranz)`() {
        assertTrue(SyncDecision.shouldAdoptLocation(payload(lat = 49.4521, lng = 11.0767), syncedLat = 41.0082, syncedLng = 28.9784))
    }
}
```

- [ ] **Step 3: Test laufen lassen — muss scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:testDebugUnitTest --tests "de.gebetszeiten.wear.SyncDecisionTest" --console=plain`
Expected: FAIL — `Unresolved reference: SyncDecision` (Kompilierfehler = roter Test).

- [ ] **Step 4: SyncDecision + WearOfficialCache implementieren**

`wear/src/main/kotlin/de/gebetszeiten/wear/SyncDecision.kt`:

```kotlin
package de.gebetszeiten.wear

import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import java.time.LocalDate

/**
 * Reine Entscheidungslogik des Phone→Wear-Syncs — JVM-testbar; der
 * ListenerService bleibt ein dünner IO-Wrapper ohne eigene Logik.
 */
object SyncDecision {

    data class Payload(
        val scheduleText: String,
        val lat: Double,
        val lng: Double,
        val city: String,
    )

    /** Unlesbarer/leerer Payload → null: verwerfen, alter Cache bleibt. */
    fun parse(payload: Payload): Map<LocalDate, SixTimes>? =
        ScheduleText.parse(payload.scheduleText).ifEmpty { null }

    /** Übernehmen bei Erst-Sync oder ANDEREM Handy-Ort als zuletzt
     *  übernommen (gleiche 1-km-Toleranz wie der Cache-Stempel); sonst
     *  bleibt ein Uhr-Picker-Override bestehen. */
    fun shouldAdoptLocation(payload: Payload, syncedLat: Double?, syncedLng: Double?): Boolean =
        !stampMatches(syncedLat, syncedLng, payload.lat, payload.lng)
}
```

`wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialCache.kt`:

```kotlin
package de.gebetszeiten.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.officialSyncStore: DataStore<Preferences> by preferencesDataStore(name = "wear_official_cache")

/**
 * Vom Handy gesyncte amtliche Zeiten. Stempel (stamp_*) = Ort, für den
 * die Zeiten gelten; synced_* = zuletzt ÜBERNOMMENER Handy-Ort (für die
 * Override-Semantik: nur ein NEUER Handy-Ort überschreibt die Uhr-Wahl).
 */
object WearOfficialCache {

    private val SCHEDULE = stringPreferencesKey("schedule")
    private val STAMP_LAT = doublePreferencesKey("stamp_lat")
    private val STAMP_LNG = doublePreferencesKey("stamp_lng")
    private val SYNCED_LAT = doublePreferencesKey("synced_lat")
    private val SYNCED_LNG = doublePreferencesKey("synced_lng")

    /** Zeiten nur, wenn der Sync-Stempel zur aktuellen Uhr-Ortswahl passt —
     *  nach einem Uhr-Override greift automatisch wieder Bundle/Berechnung. */
    suspend fun get(context: Context, date: LocalDate, lat: Double, lng: Double): SixTimes? {
        val prefs = context.officialSyncStore.data.first()
        if (!stampMatches(prefs[STAMP_LAT], prefs[STAMP_LNG], lat, lng)) return null
        return ScheduleText.parse(prefs[SCHEDULE] ?: return null)[date]
    }

    suspend fun syncedLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.officialSyncStore.data.first()
        val lat = prefs[SYNCED_LAT] ?: return null
        val lng = prefs[SYNCED_LNG] ?: return null
        return lat to lng
    }

    suspend fun store(context: Context, scheduleText: String, lat: Double, lng: Double, adoptedLocation: Boolean) {
        context.officialSyncStore.edit {
            it[SCHEDULE] = scheduleText
            it[STAMP_LAT] = lat
            it[STAMP_LNG] = lng
            if (adoptedLocation) {
                it[SYNCED_LAT] = lat
                it[SYNCED_LNG] = lng
            }
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen — grün**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:testDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (5 Tests).

- [ ] **Step 6: Commit**

```powershell
git add wear\build.gradle.kts wear\src\main\kotlin\de\gebetszeiten\wear\WearOfficialCache.kt wear\src\main\kotlin\de\gebetszeiten\wear\SyncDecision.kt wear\src\test
git commit -m "feat(wear): WearOfficialCache + SyncDecision - gesyncter Cache mit Ort-Uebernahme-Logik"
```

---

### Task 5: Uhr — ListenerService, Kette, Manifest

**Files:**
- Create: `wear/src/main/kotlin/de/gebetszeiten/wear/WearSyncListenerService.kt`
- Modify: `wear/src/main/AndroidManifest.xml`
- Modify: `wear/src/main/kotlin/de/gebetszeiten/wear/WearPrayer.kt`

**Interfaces:**
- Consumes: `WearOfficialCache`, `SyncDecision` (Task 4); `WearSyncContract` (Task 1); `WearSettings.save`, `WearVibration.reschedule`, `PrayerTileService`, `PrayerComplicationService` (bestehend).
- Produces: laufender Empfangspfad; `WearPrayer.daily`-Kette Sync-Cache → Bundle → Berechnung.

- [ ] **Step 1: ListenerService anlegen**

`wear/src/main/kotlin/de/gebetszeiten/wear/WearSyncListenerService.kt`:

```kotlin
package de.gebetszeiten.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import de.gebetszeiten.core.prayertimes.officialtimes.WearSyncContract
import kotlinx.coroutines.runBlocking

/**
 * Empfängt den amtlichen Zeiten-Cache vom Handy (DataItem
 * [WearSyncContract.PATH]), materialisiert ihn in [WearOfficialCache]
 * und stößt Tile, Complication und Vibrations-Kette an. Dünner
 * IO-Wrapper — die Logik steckt in [SyncDecision] (JVM-getestet).
 * runBlocking ist hier ok: onDataChanged läuft auf einem
 * Binder-Hintergrund-Thread (gleiches Muster wie CityPickerActivity).
 */
class WearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearSyncContract.PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val payload = SyncDecision.Payload(
                scheduleText = map.getString(WearSyncContract.KEY_SCHEDULE) ?: continue,
                lat = map.getDouble(WearSyncContract.KEY_LAT),
                lng = map.getDouble(WearSyncContract.KEY_LNG),
                city = map.getString(WearSyncContract.KEY_CITY) ?: continue,
            )
            runBlocking { apply(payload) }
        }
    }

    private suspend fun apply(payload: SyncDecision.Payload) {
        if (SyncDecision.parse(payload) == null) return
        val synced = WearOfficialCache.syncedLocation(applicationContext)
        val adopt = SyncDecision.shouldAdoptLocation(payload, synced?.first, synced?.second)
        WearOfficialCache.store(applicationContext, payload.scheduleText, payload.lat, payload.lng, adopt)
        if (adopt) WearSettings.save(applicationContext, payload.city, payload.lat, payload.lng)
        // Neue Zeiten/neuer Ort: Vibrations-Kette neu armieren, Tile und
        // Complication einmalig auffrischen (danach wieder Zero-Wakeup).
        WearVibration.reschedule(applicationContext)
        TileService.getUpdater(this).requestUpdate(PrayerTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, PrayerComplicationService::class.java))
            .requestUpdateAll()
    }
}
```

- [ ] **Step 2: Manifest registrieren**

In `wear/src/main/AndroidManifest.xml` vor `</application>` (nach dem Complication-Service) ergänzen:

```xml
        <!-- Phone-Sync: amtlicher Zeiten-Cache kommt als DataItem vom Handy.
             exported=true ist fuer WearableListenerService Pflicht; gms
             erzwingt die BIND_LISTENER-Signatur-Permission. -->
        <service
            android:name=".WearSyncListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                <data
                    android:scheme="wear"
                    android:host="*"
                    android:path="/official-times" />
            </intent-filter>
        </service>
```

(Der Pfad im Manifest MUSS wortgleich `WearSyncContract.PATH` sein — Literal, weil das Manifest keine Konstanten kennt.)

- [ ] **Step 3: Kette in WearPrayer erweitern + KDoc ehrlich machen**

In `wear/src/main/kotlin/de/gebetszeiten/wear/WearPrayer.kt`:

1. Objekt-KDoc (Zeilen 12–17) ersetzen durch:

```kotlin
/**
 * Shared prayer-time helpers for the watch. Prioritätskette wie am Phone:
 * vom Handy gesyncte amtliche Zeiten (WearOfficialCache) → gebündelte
 * Diyanet-Tabellen (nearest ≤ 25 km) → Berechnung. Die Uhr geht selbst
 * nie ins Netz und läuft ohne Handy voll weiter; der Sync ist rein
 * empfangend (Play-Services Data Layer).
 */
```

2. `daily(...)`-Kommentar und -Rumpf (Zeilen 48–61) ersetzen durch:

```kotlin
    /** Sync-Cache → amtliche Tabelle → Berechnung — dieselbe
     *  Prioritätslogik wie PrayerProvider.daily am Phone. */
    private suspend fun daily(
        context: Context,
        location: GeoLocation,
        date: LocalDate,
        zone: ZoneId,
    ): DailyPrayerTimes {
        if (!WearSettings.useCalculated(context)) {
            WearOfficialCache.get(context, date, location.latitude, location.longitude)
                ?.let { return it.toDaily(date, zone) }
            WearOfficialSource.get(context, location.latitude, location.longitude, date)
                ?.let { return it.toDaily(date, zone) }
        }
        return DiyanetPrayerTimesCalculator.calculate(location, date, zone)
    }
```

- [ ] **Step 4: Bauen + alle Suiten**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleDebug :wear:testDebugUnitTest :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add wear\src\main\kotlin\de\gebetszeiten\wear\WearSyncListenerService.kt wear\src\main\AndroidManifest.xml wear\src\main\kotlin\de\gebetszeiten\wear\WearPrayer.kt
git commit -m "feat(wear): Sync-Empfaenger + Kette Sync-Cache vor Bundle vor Berechnung"
```

---

### Task 6: PRIVACY-Ehrlichkeit + Gesamt-Verifikation

**Files:**
- Modify: `PRIVACY.md` (nur der Wear-Punkt, beide Sprachen)

**Interfaces:**
- Consumes: Verhalten aus Tasks 1–5.
- Produces: wahrheitsgemäße Doku; verifizierter Gesamtbau beider Module.

- [ ] **Step 1: PRIVACY.md Wear-Punkt ersetzen**

Im deutschen Abschnitt den Punkt `- **Wear OS:** Die Watch-App bleibt vollständig offline.` ersetzen durch:

```markdown
- **Wear OS:** Die Watch-App geht selbst nie ins Internet. Sie erhält die
  amtlichen Zeiten und den gewählten Ort vom gekoppelten Handy
  (Play-Services-Gerätesync) und funktioniert ohne Handy vollständig
  weiter (eingebaute Tabellen bzw. lokale Berechnung).
```

Im englischen Abschnitt den Punkt `- **Wear OS:** The watch app remains fully offline.` ersetzen durch:

```markdown
- **Wear OS:** The watch app itself never connects to the Internet. It
  receives the official times and the chosen place from the paired phone
  (Play Services device sync) and keeps working fully without a phone
  (bundled tables or local calculation).
```

(Falls der Wortlaut der bestehenden Punkte minimal abweicht: den jeweiligen Wear-Punkt ERSETZEN, Rest der Datei unangetastet. Beide Sprachabschnitte müssen parallel bleiben.)

- [ ] **Step 2: Gesamt-Verifikation**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest :core-prayertimes:test :wear:testDebugUnitTest :app:bundleOnlineRelease :wear:bundleRelease --console=plain -q`
Expected: BUILD SUCCESSFUL — alle Suiten grün, beide Release-Bundles (inkl. R8) bauen durch. Danach prüfen:

```powershell
Get-Item app\build\outputs\bundle\onlineRelease\app-online-release.aab, wear\build\outputs\bundle\release\wear-release.aab
```
Expected: beide Dateien mit frischem Zeitstempel.

- [ ] **Step 3: Commit**

```powershell
git add PRIVACY.md
git commit -m "docs: PRIVACY - Wear-Punkt ehrlich (Sync vom Handy, Uhr selbst ohne Internet)"
```

---

## Nicht Teil dieses Plans

- Versionsbump + Release-Tag (separater chore nach Review; Wear braucht dann erstmals seit v0.1.13 einen Bump).
- Sync weiterer Einstellungen (Vibration/Anzeige), Rück-Sync Uhr→Handy, Sync-Status-UI (Spec-YAGNI).
- Emulator-/Geräte-E2E des Data Layers (kein Wear-Emulator-Paar im Test-Setup; Empfangs-Logik ist JVM-getestet, der Rest sind dünne gms-Wrapper).
- Veraltete „~31 Tage"-Formulierungen in ALTEN Spec-/Plan-Dokumenten (historisch, bleiben).
