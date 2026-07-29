# Online-First (amtliche Zeiten weltweit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die veröffentlichte App wird der Online-Flavor: amtliche Diyanet-Zeiten weltweit on demand — Jahresseite direkt von diyanet.gov.tr, Proxy als Fallback, aufgelöste Standort-ID persistiert.

**Architecture:** Flavor-/AppId-Tausch in Gradle (online übernimmt `de.gebetszeiten`). Neuer reiner Parser `DiyanetYearPageParser` (Port aus der Python-Pipeline) + dünner `DiyanetDirectFetcher`. Ein `CompositeDiyanetFetcher` (per Konstruktor-Injektion testbar) kettet Direkt→Proxy und liefert `FetchResult(schedule, locationId)`; die ID wird im `OfficialTimesCache` mitpersistiert. Spec: `docs/superpowers/specs/2026-07-29-online-first-weltweit-design.md`.

**Tech Stack:** Kotlin, Android Gradle (Flavors `offline`/`online`), Jetpack DataStore, JUnit4 (reine JVM-Unit-Tests, KEIN Robolectric), Coroutines.

## Global Constraints

- Gradle NUR über PowerShell mit `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (Bash hat kein JAVA_HOME).
- Alle Unit-Tests sind reine JVM-Tests. `android.util.Log` ist dort NICHT gemockt → Klassen mit Log-Aufrufen bekommen injizierbare `log`-Lambdas, Tests injizieren No-ops.
- Online-only-Code liegt in `app/src/online/kotlin/de/gebetszeiten/official/`, zugehörige Tests in `app/src/testOnline/kotlin/de/gebetszeiten/official/` (Source-Set neu anlegen, AGP erkennt es automatisch), Test-Ressourcen in `app/src/testOnline/resources/`.
- Deutsche Commit-Messages im Repo-Stil (`feat(online): …`), Umlaute im Body als ue/oe/ae vermeiden Encoding-Ärger.
- Kein Versionsbump in diesem Plan — Release ist ein separater chore danach.
- Testlauf-Kommandos immer mit `--console=plain`.

## File Structure

| Datei | Verantwortung |
|---|---|
| `app/build.gradle.kts` (modify) | Flavor-Tausch: online = `de.gebetszeiten`, offline = `.offline`-Suffix |
| `app/src/online/kotlin/de/gebetszeiten/official/DiyanetYearPageParser.kt` (create) | Reiner HTML→Zeiten-Parser der Jahresseite (tab-2), wirft bei Format-Bruch |
| `app/src/online/kotlin/de/gebetszeiten/official/DiyanetHttp.kt` (create) | Gemeinsamer `httpGet` (aus DiyanetProxyFetcher extrahiert, +UA, +readTimeout-Param) |
| `app/src/online/kotlin/de/gebetszeiten/official/DiyanetDirectFetcher.kt` (create) | Dünner IO-Wrapper: Jahresseite laden → Parser |
| `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt` (create) | Ketten-Logik Direkt→Proxy + ID-Auflösung (Bundle→Cache→Suche), implementiert `OfficialTimesFetcher` |
| `app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt` (modify) | Nur noch `fetchById` + `resolveLocationId`, kein Interface mehr, kein Context |
| `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt` (modify) | liefert `CompositeDiyanetFetcher.create(context)` |
| `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` (modify) | Interface auf `FetchResult` umstellen + `data class FetchResult` |
| `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt` (modify) | `putAll` mit `locationId`-Param, neu `cachedLocationId()` |
| `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (modify) | `refreshOfficial` nutzt `FetchResult` |
| `app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetYearPageParserTest.kt` (create) | Fixture-Tests (echtes HTML) + Negativtests |
| `app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt` (create) | Ketten-Tests mit Fake-Lambdas |
| `app/src/testOnline/resources/diyanet-11024.html` (create, Kopie) | Echte Nürnberg-Jahresseite aus `tools/diyanet-fetch/cache/11024.html` |
| `app/src/testOnline/resources/t507-2026.tsv` (create, Kopie) | Erwartungswerte: gebündelte Nürnberg-Tabelle (gleiche Quelle) |
| `PRIVACY.md` (modify) | Veröffentlichte Version = online; was genau übertragen wird |
| `playstore/CHECKLISTE.md` (modify) | Data-Safety-Antworten + Release-Kommando auf online umstellen |

---

### Task 1: Flavor-/AppId-Tausch

**Files:**
- Modify: `app/build.gradle.kts:29-41`

**Interfaces:**
- Consumes: —
- Produces: Build-Varianten: `onlineRelease` → applicationId `de.gebetszeiten` (Play-Listing), `offlineRelease` → `de.gebetszeiten.offline`. Alle späteren Tasks testen gegen `:app:testOnlineDebugUnitTest`.

- [ ] **Step 1: Flavor-Block tauschen**

In `app/build.gradle.kts` den `productFlavors`-Block ersetzen. Vorher:

```kotlin
    productFlavors {
        // Privacy-pure default: no INTERNET, fully offline calculation.
        create("offline") {
            dimension = "connectivity"
            isDefault = true
        }
        // Optional variant that can fetch exact official Diyanet times online.
        create("online") {
            dimension = "connectivity"
            applicationIdSuffix = ".online"
            versionNameSuffix = "-online"
        }
    }
```

Nachher:

```kotlin
    productFlavors {
        // Puristisch netzfreie Variante (kein INTERNET) — aus dem Quellcode
        // baubar, nicht mehr die veroeffentlichte App.
        create("offline") {
            dimension = "connectivity"
            isDefault = true
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
        }
        // Veroeffentlichte App (de.gebetszeiten): amtliche Diyanet-Zeiten
        // on demand, DE-Bundle + Berechnung als Offline-Fallback.
        create("online") {
            dimension = "connectivity"
        }
    }
```

- [ ] **Step 2: Beide Varianten bauen und applicationIds verifizieren**

Run (PowerShell):
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleOnlineDebug :app:assembleOfflineDebug --console=plain -q
Select-String -Path app\build\outputs\apk\online\debug\output-metadata.json -Pattern 'applicationId'
Select-String -Path app\build\outputs\apk\offline\debug\output-metadata.json -Pattern 'applicationId'
```
Expected: online → `"applicationId": "de.gebetszeiten"`, offline → `"applicationId": "de.gebetszeiten.offline"`.

- [ ] **Step 3: Beide Testsuiten laufen lassen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (kein Code hängt an der applicationId).

- [ ] **Step 4: Commit**

```powershell
git add app\build.gradle.kts
git commit -m "feat(flavors): online uebernimmt de.gebetszeiten, offline bekommt .offline-Suffix"
```

---

### Task 2: DiyanetYearPageParser (TDD mit echten Fixtures)

**Files:**
- Create: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetYearPageParser.kt`
- Create: `app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetYearPageParserTest.kt`
- Create (Kopien): `app/src/testOnline/resources/diyanet-11024.html`, `app/src/testOnline/resources/t507-2026.tsv`

**Interfaces:**
- Consumes: `SixTimes`, `parseOfficialTimes` aus `de.gebetszeiten.core.prayertimes.officialtimes` (core-Modul, bereits App-Dependency).
- Produces: `object DiyanetYearPageParser { fun parse(html: String): Map<LocalDate, SixTimes> }` — wirft `IllegalArgumentException` bei jedem Format-Bruch (Task 4 ruft es, Task 5 verlässt sich auf das Werfen).

- [ ] **Step 1: Fixtures kopieren**

```powershell
New-Item -ItemType Directory -Force app\src\testOnline\resources, app\src\testOnline\kotlin\de\gebetszeiten\official
Copy-Item tools\diyanet-fetch\cache\11024.html app\src\testOnline\resources\diyanet-11024.html
Copy-Item shared-assets\official\tables\t507-2026.tsv app\src\testOnline\resources\t507-2026.tsv
Copy-Item tools\diyanet-fetch\cache\9976.html app\src\testOnline\resources\diyanet-9976.html
Copy-Item shared-assets\official\tables\t000-2026.tsv app\src\testOnline\resources\t000-2026.tsv
```

(11024 = Diyanet-ID Nürnberg → Tabelle `t507`; 9976 = Espelkamp → `t000`, laut `shared-assets/official/locations-de.tsv`. Die TSVs wurden aus GENAU diesen Seiten generiert — perfekte Erwartungswerte, zwei unabhängige Standorte wie im Spec gefordert.)

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`app/src/testOnline/kotlin/de/gebetszeiten/official/DiyanetYearPageParserTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.parseOfficialTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DiyanetYearPageParserTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.bufferedReader(Charsets.UTF_8).readText()

    private fun assertPageMatchesBundledTable(pageResource: String, tableResource: String) {
        val expected = javaClass.getResourceAsStream("/$tableResource")!!
            .bufferedReader(Charsets.UTF_8).useLines { parseOfficialTimes(it) }
        assertEquals(expected, DiyanetYearPageParser.parse(resource(pageResource)))
    }

    @Test
    fun `parst die Nuernberg-Jahresseite identisch zur gebuendelten Tabelle`() {
        assertPageMatchesBundledTable("diyanet-11024.html", "t507-2026.tsv")
        // Stichprobe zur Lesbarkeit: 29.07.2026 amtlich verifiziert.
        val jul29 = DiyanetYearPageParser.parse(resource("diyanet-11024.html"))
            .getValue(LocalDate.of(2026, 7, 29))
        assertEquals(LocalTime.of(3, 52), jul29.fajr)
        assertEquals(LocalTime.of(22, 39), jul29.isha)
    }

    @Test
    fun `parst die Espelkamp-Jahresseite identisch zur gebuendelten Tabelle`() {
        assertPageMatchesBundledTable("diyanet-9976.html", "t000-2026.tsv")
    }

    @Test
    fun `wirft bei HTML ohne Jahres-Tabelle`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiyanetYearPageParser.parse("<html><body>Bakim calismasi</body></html>")
        }
    }

    @Test
    fun `wirft bei abgeschnittener Tabelle`() {
        val html = resource("diyanet-11024.html")
        val broken = html.substring(0, html.indexOf("id=\"tab-2\"") + 5000)
        assertThrows(IllegalArgumentException::class.java) {
            DiyanetYearPageParser.parse(broken)
        }
    }
}
```

Hinweis: Sollte `parseOfficialTimes` eine andere Signatur haben als `(Sequence<String>) -> Map<LocalDate, SixTimes>`, in `core-prayertimes` nachsehen (`OfficialTimesParser.kt` o. ä.) und den Aufruf anpassen — NICHT die core-Funktion ändern.

- [ ] **Step 3: Test laufen lassen — muss scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.official.DiyanetYearPageParserTest" --console=plain`
Expected: FAIL — `Unresolved reference: DiyanetYearPageParser` (Kompilierfehler zählt als roter Test).

- [ ] **Step 4: Parser implementieren**

`app/src/online/kotlin/de/gebetszeiten/official/DiyanetYearPageParser.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parst die Jahres-Tabelle (tab-2) einer Diyanet-Jahresseite
 * (namazvakitleri.diyanet.gov.tr/tr-TR/{id}) — 1:1-Port von
 * parse_year_table aus tools/diyanet-fetch/fetch_diyanet.py.
 * Wirft bei jedem Format-Bruch, damit der Aufrufer auf den
 * Proxy zurückfallen kann.
 */
object DiyanetYearPageParser {

    private val TR_MONTHS = mapOf(
        "Ocak" to 1, "Şubat" to 2, "Mart" to 3, "Nisan" to 4,
        "Mayıs" to 5, "Haziran" to 6, "Temmuz" to 7, "Ağustos" to 8,
        "Eylül" to 9, "Ekim" to 10, "Kasım" to 11, "Aralık" to 12,
    )
    private val CELL = Regex("<td>\\s*([^<]*?)\\s*</td>")
    private val DATE = Regex("(\\d{2}) (\\S+) (\\d{4})")
    private val TIME = Regex("\\d{2}:\\d{2}")

    fun parse(html: String): Map<LocalDate, SixTimes> {
        val start = html.indexOf("id=\"tab-2\"")
        require(start >= 0) { "Jahres-Tabelle (tab-2) nicht gefunden" }
        val end = html.indexOf("</table>", start)
        require(end >= 0) { "Tabellen-Ende fehlt" }
        val cells = CELL.findAll(html.substring(start, end))
            .map { unescape(it.groupValues[1]) }
            .toList()
        require(cells.isNotEmpty() && cells.size % 8 == 0) {
            "Zellenzahl ${cells.size} nicht durch 8 teilbar"
        }
        val out = LinkedHashMap<LocalDate, SixTimes>(cells.size / 8)
        for (i in cells.indices step 8) {
            val m = requireNotNull(DATE.find(cells[i])) { "Datum unlesbar: ${cells[i]}" }
            val month = requireNotNull(TR_MONTHS[m.groupValues[2]]) { "Monat unlesbar: ${cells[i]}" }
            val date = LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            val times = (2..7).map { off ->
                cells[i + off].also { require(TIME.matches(it)) { "Zeit unlesbar am $date: $it" } }
            }
            out[date] = SixTimes(
                fajr = LocalTime.parse(times[0]),
                sunrise = LocalTime.parse(times[1]),
                dhuhr = LocalTime.parse(times[2]),
                asr = LocalTime.parse(times[3]),
                maghrib = LocalTime.parse(times[4]),
                isha = LocalTime.parse(times[5]),
            )
        }
        return out
    }

    /** Minimal-Unescape — die Zellen enthalten nur Datum/Uhrzeiten/Monatsnamen. */
    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
```

- [ ] **Step 5: Test laufen lassen — muss grün sein**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.official.DiyanetYearPageParserTest" --console=plain`
Expected: PASS (3 Tests). Falls der Gleichheits-Test scheitert, Diff ansehen: vermutlich enthält die Seite mehr/weniger Tage als die Tabelle — dann Erwartung präzisieren (`expected.keys == parsed.keys` prüfen), NICHT den Parser aufweichen.

- [ ] **Step 6: Commit**

```powershell
git add app\src\online\kotlin\de\gebetszeiten\official\DiyanetYearPageParser.kt app\src\testOnline
git commit -m "feat(online): DiyanetYearPageParser - Jahresseiten-Parser (Port aus Python-Pipeline)"
```

---

### Task 3: Cache lernt die Diyanet-ID

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimesCache.kt`

**Interfaces:**
- Consumes: bestehendes `stampMatches(...)` (top-level in `CacheFreshness.kt`).
- Produces: `suspend fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int? = null)` und `suspend fun cachedLocationId(lat: Double, lng: Double): Int?` — Task 5 nutzt beide.

- [ ] **Step 1: Cache erweitern**

In `OfficialTimesCache.kt` — Import ergänzen:

```kotlin
import androidx.datastore.preferences.core.intPreferencesKey
```

Key-Deklaration ergänzen (unter `stampLng`):

```kotlin
    private val stampId = intPreferencesKey("stamp_diyanet_id")
```

Neue Methode (unter `freshness`):

```kotlin
    /** Diyanet-ID des letzten erfolgreichen Abrufs — nur bei Stempel-Match,
     *  damit nie die ID eines anderen Standorts wiederverwendet wird. */
    suspend fun cachedLocationId(lat: Double, lng: Double): Int? {
        val prefs = context.officialStore.data.first()
        if (!stampMatches(prefs[stampLat], prefs[stampLng], lat, lng)) return null
        return prefs[stampId]
    }
```

`putAll` ersetzen:

```kotlin
    suspend fun putAll(schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int? = null) {
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
            if (locationId != null) it[stampId] = locationId else it.remove(stampId)
        }
    }
```

Begründung, warum hier kein eigener Unit-Test: Die Klasse hängt fest am Context-gebundenen DataStore (kein Robolectric im Projekt); die Logik ist trivial (ein Key mehr + Stempel-Guard, der wortgleich in `get()` steht). Die Ketten-Logik, die die ID NUTZT, wird in Task 5 mit Fakes getestet.

- [ ] **Step 2: Beide Testsuiten laufen lassen (Default-Param bricht keine Aufrufer)**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app\src\main\kotlin\de\gebetszeiten\official\OfficialTimesCache.kt
git commit -m "feat(online): Cache persistiert Diyanet-ID des letzten Abrufs (stamp-geschuetzt)"
```

---

### Task 4: DiyanetHttp extrahieren + DiyanetDirectFetcher

**Files:**
- Create: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetHttp.kt`
- Create: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetDirectFetcher.kt`
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt` (nur: privates `httpGet` löschen, gemeinsames nutzen)

**Interfaces:**
- Consumes: `DiyanetYearPageParser.parse(html)` (Task 2).
- Produces: `internal fun httpGet(urlString: String, accept: String = "application/json", readTimeoutMs: Int = 10_000): String` und `class DiyanetDirectFetcher { suspend fun fetchYear(locationId: Int): Map<LocalDate, SixTimes> }` — Task 5 verdrahtet `fetchYear`.

- [ ] **Step 1: Gemeinsames httpGet anlegen**

`app/src/online/kotlin/de/gebetszeiten/official/DiyanetHttp.kt`:

```kotlin
package de.gebetszeiten.official

import java.net.HttpURLConnection
import java.net.URL

/** Schlanker GET mit Timeouts — gemeinsame Basis für Direkt- und Proxy-Abruf. */
internal fun httpGet(
    urlString: String,
    accept: String = "application/json",
    readTimeoutMs: Int = 10_000,
): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = readTimeoutMs
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "GebetszeitenApp (amtliche Zeiten, ~1 Abruf/Jahr)")
    }
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
```

In `DiyanetProxyFetcher.kt` die private Funktion `httpGet` (Zeilen 93–106) ersatzlos löschen — die Aufrufe treffen jetzt das top-level `httpGet`. Imports `java.net.HttpURLConnection`/`java.net.URL` dort entfernen.

- [ ] **Step 2: DiyanetDirectFetcher anlegen**

`app/src/online/kotlin/de/gebetszeiten/official/DiyanetDirectFetcher.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Lädt die amtliche Jahresseite direkt von namazvakitleri.diyanet.gov.tr
 * (~390 KB, deckt das ganze Jahr ab — daher großzügiger readTimeout).
 * Wirft bei Netz-/Formatfehlern; der Composite fällt dann auf den Proxy.
 */
class DiyanetDirectFetcher {
    suspend fun fetchYear(locationId: Int): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            DiyanetYearPageParser.parse(
                httpGet(
                    "https://namazvakitleri.diyanet.gov.tr/tr-TR/$locationId",
                    accept = "text/html",
                    readTimeoutMs = 20_000,
                ),
            )
        }
}
```

- [ ] **Step 3: Kompilieren + Suiten laufen lassen**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (fetchYear ist ein dünner IO-Wrapper ohne eigenen Unit-Test; Parser ist getestet, Kette folgt in Task 5).

- [ ] **Step 4: Commit**

```powershell
git add app\src\online\kotlin\de\gebetszeiten\official\DiyanetHttp.kt app\src\online\kotlin\de\gebetszeiten\official\DiyanetDirectFetcher.kt app\src\online\kotlin\de\gebetszeiten\official\DiyanetProxyFetcher.kt
git commit -m "feat(online): DiyanetDirectFetcher laedt Jahresseite direkt; httpGet extrahiert"
```

---

### Task 5: FetchResult + CompositeDiyanetFetcher (TDD) + Verdrahtung

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt`
- Create: `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt`
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/DiyanetProxyFetcher.kt`
- Modify: `app/src/online/kotlin/de/gebetszeiten/official/OfficialTimesProvider.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt:81-88` (`refreshOfficial`)
- Test: `app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt`

**Interfaces:**
- Consumes: `DiyanetDirectFetcher.fetchYear(Int)` (Task 4), `OfficialTimesCache.cachedLocationId`/`putAll(…, locationId)` (Task 3), `BundledOfficialSource.nearestLocation(context, lat, lng): OfficialLocation?` mit Feld `.diyanetId` (bestehend).
- Produces: `data class FetchResult(val schedule: Map<LocalDate, SixTimes>, val locationId: Int?)`; `OfficialTimesFetcher.fetch(settings): FetchResult`; `CompositeDiyanetFetcher(resolveId, direct, proxy, log)` + `CompositeDiyanetFetcher.create(context)`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`app/src/testOnline/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcherTest.kt`:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CompositeDiyanetFetcherTest {

    private val settings = AppSettings.DEFAULT
    private val day = LocalDate.of(2026, 7, 29)
    private fun six(fajrMinute: Int) = SixTimes(
        fajr = LocalTime.of(3, fajrMinute), sunrise = LocalTime.of(5, 36),
        dhuhr = LocalTime.of(13, 27), asr = LocalTime.of(17, 35),
        maghrib = LocalTime.of(21, 9), isha = LocalTime.of(22, 39),
    )
    private val yearData = mapOf(day to six(52))
    private val proxyData = mapOf(day to six(53))

    private fun fetcher(
        id: Int? = 11024,
        direct: suspend (Int) -> Map<LocalDate, SixTimes> = { yearData },
        proxy: suspend (Int) -> Map<LocalDate, SixTimes> = { proxyData },
    ) = CompositeDiyanetFetcher({ id }, direct, proxy, log = { _, _ -> })

    @Test
    fun `direkt liefert - Proxy wird nie gefragt`() = runBlocking {
        var proxyCalls = 0
        val result = fetcher(proxy = { proxyCalls++; proxyData }).fetch(settings)
        assertEquals(yearData, result.schedule)
        assertEquals(11024, result.locationId)
        assertEquals(0, proxyCalls)
    }

    @Test
    fun `direkt wirft - Proxy uebernimmt`() = runBlocking {
        val result = fetcher(direct = { error("HTML-Umbau") }).fetch(settings)
        assertEquals(proxyData, result.schedule)
        assertEquals(11024, result.locationId)
    }

    @Test
    fun `direkt leer - Proxy uebernimmt`() = runBlocking {
        val result = fetcher(direct = { emptyMap() }).fetch(settings)
        assertEquals(proxyData, result.schedule)
    }

    @Test
    fun `beide scheitern - leeres Ergebnis ohne ID`() = runBlocking {
        val result = fetcher(direct = { error("down") }, proxy = { error("down") }).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
    }

    @Test
    fun `keine ID aufloesbar - keine Abrufe`() = runBlocking {
        var calls = 0
        val result = fetcher(id = null, direct = { calls++; yearData }, proxy = { calls++; proxyData }).fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
        assertEquals(0, calls)
    }

    @Test
    fun `ID-Aufloesung wirft - leeres Ergebnis statt Crash`() = runBlocking {
        val f = CompositeDiyanetFetcher({ error("Suche down") }, { yearData }, { proxyData }, log = { _, _ -> })
        val result = f.fetch(settings)
        assertEquals(emptyMap<LocalDate, SixTimes>(), result.schedule)
        assertNull(result.locationId)
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss scheitern**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "de.gebetszeiten.official.CompositeDiyanetFetcherTest" --console=plain`
Expected: FAIL — `Unresolved reference: CompositeDiyanetFetcher` / `FetchResult`.

- [ ] **Step 3: Interface + Composite + Refactor implementieren**

**3a** — `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` komplett ersetzen:

```kotlin
package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import java.time.LocalDate

/**
 * Fetches exact official Diyanet times for a location. Only the `online`
 * product flavor provides a real implementation; the `offline` flavor supplies
 * none (see the flavor-specific [OfficialTimesProvider]).
 */
interface OfficialTimesFetcher {
    /** Zeiten für so viele Tage, wie die Quelle hergibt, plus die Diyanet-ID,
     *  mit der sie geholt wurden (wird für Folge-Refreshes persistiert). */
    suspend fun fetch(settings: AppSettings): FetchResult
}

data class FetchResult(
    val schedule: Map<LocalDate, SixTimes>,
    val locationId: Int?,
)
```

**3b** — `DiyanetProxyFetcher.kt`: Klassensignatur und `fetch` ersetzen. Die Klasse implementiert KEIN Interface mehr und braucht keinen Context (Auflösung übernimmt der Composite). Kopfzeile neu:

```kotlin
class DiyanetProxyFetcher {

    private val base = "https://prayertimes.api.abdus.dev/api/diyanet"

    /** 31-Tage-Fenster für eine bekannte Diyanet-ID — Fallback-Quelle. */
    suspend fun fetchById(locationId: Int): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            parseSchedule(httpGet("$base/prayertimes?location_id=$locationId"))
        }
```

Die bisherige `override suspend fun fetch(settings…)`-Methode (inkl. try/catch und `BundledOfficialSource`-Aufruf) ersatzlos löschen. `resolveLocationId` von `private` auf öffentlich stellen (Sichtbarkeitsmodifier einfach weglassen) — der Composite ruft sie. `normalize` und `parseSchedule` bleiben `private`. Nicht mehr genutzte Imports entfernen (`AppSettings`, `android.content.Context`-Konstruktorparameter, `BundledOfficialSource`-Bezüge). KDoc der Klasse kürzen auf: Proxy-Quelle (`prayertimes.api.abdus.dev`, 1:1-Scrape von Diyanet) für 31-Tage-Fenster + Namenssuche.

**3c** — `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt`:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Amtliche Zeiten mit Quellen-Kette: Diyanet-Jahresseite direkt (ganzes
 * Jahr), bei Fehler/leer der Community-Proxy (31 Tage). Die Standort-ID
 * kommt aus dem DE-Bundle (id-genau), sonst aus dem Cache (letzter
 * erfolgreicher Abruf am selben Ort), sonst per Proxy-Namenssuche.
 * Wirft nie — leeres Ergebnis heißt: Aufrufer bleibt bei Bundle/Berechnung.
 */
class CompositeDiyanetFetcher(
    private val resolveId: suspend (AppSettings) -> Int?,
    private val direct: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val proxy: suspend (Int) -> Map<LocalDate, SixTimes>,
    private val log: (String, Exception) -> Unit = { msg, e ->
        android.util.Log.w("DiyanetFetch", msg, e)
    },
) : OfficialTimesFetcher {

    override suspend fun fetch(settings: AppSettings): FetchResult {
        val id = try {
            resolveId(settings)
        } catch (e: Exception) {
            log("Standort-Aufloesung fehlgeschlagen", e)
            null
        } ?: return FetchResult(emptyMap(), null)
        val schedule = attempt("Direktabruf", id, direct)
            .ifEmpty { attempt("Proxy-Abruf", id, proxy) }
        return FetchResult(schedule, id.takeIf { schedule.isNotEmpty() })
    }

    private suspend fun attempt(
        label: String,
        id: Int,
        source: suspend (Int) -> Map<LocalDate, SixTimes>,
    ): Map<LocalDate, SixTimes> = try {
        source(id)
    } catch (e: Exception) {
        log("$label fehlgeschlagen (id=$id)", e)
        emptyMap()
    }

    companion object {
        fun create(context: Context): CompositeDiyanetFetcher {
            val proxyFetcher = DiyanetProxyFetcher()
            return CompositeDiyanetFetcher(
                resolveId = { settings ->
                    BundledOfficialSource.nearestLocation(context, settings.latitude, settings.longitude)?.diyanetId
                        ?: OfficialTimesCache(context).cachedLocationId(settings.latitude, settings.longitude)
                        ?: withContext(Dispatchers.IO) { proxyFetcher.resolveLocationId(settings.city) }
                },
                direct = DiyanetDirectFetcher()::fetchYear,
                proxy = proxyFetcher::fetchById,
            )
        }
    }
}
```

(Falls `OfficialLocation.diyanetId` anders heißt — in `core-prayertimes` `OfficialLocation` nachsehen; der bisherige Proxy-Code nutzte exakt `?.diyanetId`.)

**3d** — `OfficialTimesProvider.kt` (online): Rumpf ersetzen:

```kotlin
/** Online flavor: official times come from Diyanet (direct, proxy fallback). */
object OfficialTimesProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher = CompositeDiyanetFetcher.create(context)
}
```

**3e** — `PrayerProvider.refreshOfficial`: die letzten zwei Zeilen ersetzen:

```kotlin
        val fetcher = OfficialTimesProvider.fetcher(context) ?: return
        val result = fetcher.fetch(settings)
        cache.putAll(result.schedule, settings.latitude, settings.longitude, result.locationId)
```

- [ ] **Step 4: Tests laufen lassen — Composite grün, dann beide Suiten**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL (offline kompiliert weiter, weil `FetchResult` im main-Sourceset liegt und der offline-Provider `null` liefert).

- [ ] **Step 5: Commit**

```powershell
git add app\src\main\kotlin\de\gebetszeiten\official\OfficialTimes.kt app\src\main\kotlin\de\gebetszeiten\prayer\PrayerProvider.kt app\src\online\kotlin\de\gebetszeiten\official app\src\testOnline\kotlin\de\gebetszeiten\official\CompositeDiyanetFetcherTest.kt
git commit -m "feat(online): Composite-Kette Direkt-vor-Proxy, FetchResult persistiert Diyanet-ID"
```

---

### Task 6: Compliance-Doku + Abschluss-Verifikation

**Files:**
- Modify: `PRIVACY.md`
- Modify: `playstore/CHECKLISTE.md`

**Interfaces:**
- Consumes: Verhalten aus Tasks 1–5 (was genau übertragen wird).
- Produces: aktualisierte, wahrheitsgemäße Nutzer-Doku; verifizierter Gesamtbau.

- [ ] **Step 1: PRIVACY.md umschreiben**

Beide Sprachabschnitte ersetzen (Kopfzeile, Kontakt und Quellcode-Links bleiben). Deutsch:

```markdown
## Deutsch

Diese App erhebt, speichert und teilt **keine personenbezogenen Daten** und
enthält **keine Werbung, kein Tracking, keine Analyse-Dienste, keine Konten**.

- **Kein GPS:** Der Gebetsort wird manuell aus einer eingebauten Städteliste
  gewählt und nur lokal gespeichert.
- **Internet nur für amtliche Zeiten:** Die App lädt die offiziellen
  Diyanet-Gebetszeiten für den gewählten Ort — primär direkt von
  `namazvakitleri.diyanet.gov.tr` (Jahres-Tabelle, typischerweise ein Abruf
  pro Jahr und Ort), ersatzweise vom Community-Proxy
  `prayertimes.api.abdus.dev`. Übermittelt wird dabei ausschließlich die
  **Diyanet-Standort-Kennung** des gewählten Ortes (bei erstmaliger Auswahl
  eines Ortes außerhalb Deutschlands einmalig der **Städtename** zur
  Auflösung). Keine Koordinaten, keine Geräte-Kennungen.
- **Abschaltbar:** In den Einstellungen lässt sich der Online-Abgleich
  deaktivieren; für Deutschland sind amtliche Tabellen offline gebündelt,
  sonst rechnet die App lokal.
- **Wear OS:** Die Watch-App bleibt vollständig offline.

**Offline-Variante:** Aus dem Quellcode ist weiterhin eine Variante ohne
jede Internet-Berechtigung baubar (`de.gebetszeiten.offline`).
```

Englisch:

```markdown
## English

This app does **not collect, store, or share any personal data** and contains
**no ads, no tracking, no analytics, and no accounts**.

- **No GPS:** The prayer location is chosen manually from a bundled city list
  and stored only locally on the device.
- **Internet only for official times:** The app downloads the official
  Diyanet prayer times for the chosen location — primarily directly from
  `namazvakitleri.diyanet.gov.tr` (full-year table, typically one request per
  year and location), with the community proxy `prayertimes.api.abdus.dev`
  as fallback. The only data transmitted is the **Diyanet location id** of
  the chosen place (plus, once, the **city name** when a location outside
  Germany is first selected, to resolve its id). No coordinates, no device
  identifiers.
- **Can be turned off:** The online sync can be disabled in the settings;
  official tables for Germany are bundled offline, elsewhere the app
  calculates locally.
- **Wear OS:** The watch app remains fully offline.

**Offline variant:** A variant without any Internet permission can still be
built from source (`de.gebetszeiten.offline`).
```

Die bisherige Behauptung „Die im Play Store veröffentlichte Version enthält diese Variante nicht" MUSS raus — sie wäre nach dem Umbau falsch.

- [ ] **Step 2: CHECKLISTE.md anpassen**

1. Kopfblock + Tabelle: Release-Kommando auf `.\gradlew.bat :app:bundleOnlineRelease :wear:bundleRelease` und Pfad `app/build/outputs/bundle/onlineRelease/app-online-release.aab` umstellen (Versionsangaben v0.1.14/15 sind ohnehin veraltet — auf „aktueller Stand laut git tag" umformulieren).
2. Abschnitt „4. Formulare" — Data-Safety-Antworten ersetzen durch:

```markdown
**Datensicherheit (Data Safety) — Stand Online-First:**
- „Erhebt oder teilt deine App Nutzerdaten?" → **Ja** (Datenerhebung im
  Play-Sinn = Übertragung vom Gerät).
- Datentyp: **Standort → ungefährer Standort** (manuell gewählter Ort als
  Diyanet-Standort-Kennung/Städtename an diyanet.gov.tr bzw. Fallback-Proxy).
- Zweck: **App-Funktionen** (amtliche Gebetszeiten). Erhebung **optional**
  (in den Einstellungen abschaltbar). **Keine Weitergabe** zu Werbe-/
  Analysezwecken, **kein Verkauf**, Übertragung **verschlüsselt (HTTPS)**,
  Daten **nicht mit Nutzern verknüpft** (keine Konten/Kennungen),
  **Löschung entfällt** (es wird nichts serverseitig gespeichert, was der
  App zuordenbar wäre — Antwort: Daten werden nicht gespeichert).
- Abschnitt 5: Upload-Datei ist ab jetzt das **online**-Bundle.
```

3. In Abschnitt „8. Jaehrliches Zeiten-Update" einen Satz ergänzen: Das Bundle bleibt DE-Fallback + Wear-Quelle; die Phone-App holt sich das neue Jahr online selbst.

- [ ] **Step 3: Gesamt-Verifikation**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest :core-prayertimes:test :wear:testDebugUnitTest :app:bundleOnlineRelease --console=plain -q`
Expected: BUILD SUCCESSFUL — alle Suiten grün, Release-Bundle (inkl. lintVital/R8) baut durch. Danach prüfen:
```powershell
Get-Item app\build\outputs\bundle\onlineRelease\app-online-release.aab
```
Expected: Datei existiert, frischer Zeitstempel.

- [ ] **Step 4: Commit**

```powershell
git add PRIVACY.md playstore\CHECKLISTE.md
git commit -m "docs: PRIVACY + Play-Checkliste auf Online-First umgestellt (Data Safety, Release-Pfad)"
```

---

## Nicht Teil dieses Plans

- Versionsbump + Release-Tag (separater chore nach Review).
- Wear-Cache-Sync (verbindliche Folgephase, eigene Spec — siehe Spec Abschnitt „Feste Folgephase").
- Manuelle Play-Console-Schritte (Data-Safety-Formular, Upload) — nur der Nutzer.
