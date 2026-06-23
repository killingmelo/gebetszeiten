# Amtliche Diyanet-Zeiten für Nürnberg — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Für Nürnberg die amtlichen Diyanet-Zeiten 2026 aus einem gebündelten Offline-Asset anzeigen statt der lokalen Berechnung; ab 2027 / für andere Städte automatisch auf die Berechnung zurückfallen.

**Architecture:** Ein neues `BundledOfficialSource`-Objekt lädt amtliche Jahrestabellen aus `assets/official/` (lazy, `Dispatchers.IO`-gecacht, analog zu `Cities`). `PrayerProvider.daily(...)` konsultiert die Quellen in der Reihenfolge Online-Cache → gebündelte amtliche Tabelle → Berechnung. Der Footer zeigt dynamisch die tatsächliche Quelle an.

**Tech Stack:** Kotlin, Android (minSdk 26, JVM 17), JUnit4 (`libs.junit`), java.time, Jetpack Compose (nur Footer-Anpassung).

## Global Constraints

- **Offline-Flavor bleibt netzfrei:** `BundledOfficialSource` liest ausschließlich lokale Assets — keine `INTERNET`-Nutzung, kein Netzwerkcode im Main-Source-Set.
- **Kotlin / JVM 17 / minSdk 26.**
- **Bestehende Muster folgen:** Singleton-`object`, `@Volatile`-Cache + `withContext(Dispatchers.IO)` wie in `de.gebetszeiten.data.Cities`.
- **Diyanet-Mapping (fix):** `imsak→fajr`, `gunes→sunrise`, `ogle→dhuhr`, `ikindi→asr`, `aksam→maghrib`, `yatsi→isha`.
- **Strings** in `app/src/main/res/values/strings.xml`, deutsch.
- **Asset-Format:** TSV, kein Header, eine Zeile pro Tag: `date\tfajr\tsunrise\tdhuhr\tasr\tmaghrib\tisha` mit ISO-Werten (`yyyy-MM-dd`, `HH:mm`).
- **Test-Kommandos** laufen auf dem `offline`-Flavor: `./gradlew :app:testOfflineDebugUnitTest`.

---

### Task 1: Geteilte Text-Normalisierung extrahieren

Grund: `Cities.normalize` und der neue `BundledOfficialSource` brauchen dieselbe Akzent-/Umlaut-/Türkisch-Normalisierung. DRY → in ein gemeinsames `object` ziehen.

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/data/TextNormalize.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/data/Cities.kt` (private `normalize` entfernen, `TextNormalize.normalize` nutzen)
- Modify: `app/build.gradle.kts` (JUnit-Test-Abhängigkeit ergänzen)
- Test: `app/src/test/kotlin/de/gebetszeiten/data/TextNormalizeTest.kt`

**Interfaces:**
- Produces: `de.gebetszeiten.data.TextNormalize.normalize(s: String): String` — lower-case, Diakritika entfernt, türkische/deutsche Sonderzeichen → ASCII (`ı→i, ş→s, ğ→g, ç→c, ö→o, ü→u, ß→s`).

- [ ] **Step 1: JUnit-Abhängigkeit zum app-Modul hinzufügen**

In `app/build.gradle.kts`, im `dependencies { ... }`-Block am Ende ergänzen:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: Failing test schreiben**

`app/src/test/kotlin/de/gebetszeiten/data/TextNormalizeTest.kt`:

```kotlin
package de.gebetszeiten.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizeTest {
    @Test fun stripsUmlautsAndCase() {
        assertEquals("nurnberg", TextNormalize.normalize("Nürnberg"))
        assertEquals("nurnberg", TextNormalize.normalize("  NÜRNBERG "))
    }

    @Test fun stripsTurkishLetters() {
        assertEquals("istanbul", TextNormalize.normalize("İstanbul"))
        assertEquals("sisli", TextNormalize.normalize("Şişli"))
    }
}
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.data.TextNormalizeTest"`
Expected: FAIL (Kompilierfehler: `TextNormalize` existiert nicht).

- [ ] **Step 4: `TextNormalize` implementieren**

`app/src/main/kotlin/de/gebetszeiten/data/TextNormalize.kt`:

```kotlin
package de.gebetszeiten.data

import java.text.Normalizer

/** Akzent-/Umlaut-/Türkisch-insensitive Normalisierung (lower-case → ASCII).
 *  Gemeinsam genutzt von [Cities] und der amtlichen Zeiten-Quelle. */
object TextNormalize {
    fun normalize(s: String): String {
        val lower = s.trim().lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ç', 'c')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ß', 's')
    }
}
```

- [ ] **Step 5: `Cities` auf `TextNormalize` umstellen**

In `app/src/main/kotlin/de/gebetszeiten/data/Cities.kt` die private Funktion `normalize` (Zeilen 36–48) löschen und alle Aufrufe `normalize(...)` durch `TextNormalize.normalize(...)` ersetzen (3 Aufrufstellen: `search`-Query plus die beiden Feld-Normalisierungen in `load`). Den jetzt ungenutzten Import `import java.text.Normalizer` aus `Cities.kt` entfernen.

- [ ] **Step 6: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.data.TextNormalizeTest"`
Expected: PASS

- [ ] **Step 7: Kompilierung des Hauptcodes prüfen**

Run: `./gradlew :app:compileOfflineDebugKotlin`
Expected: BUILD SUCCESSFUL (Cities nutzt jetzt TextNormalize).

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/de/gebetszeiten/data/TextNormalize.kt app/src/main/kotlin/de/gebetszeiten/data/Cities.kt app/src/test/kotlin/de/gebetszeiten/data/TextNormalizeTest.kt
git commit -m "refactor: shared TextNormalize extrahiert (Cities) + JUnit fuer app-Modul"
```

---

### Task 2: Reiner Parser `parseOfficialTimes`

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTables.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/official/OfficialTablesTest.kt`

**Interfaces:**
- Consumes: `de.gebetszeiten.official.SixTimes` (existiert bereits in `OfficialTimes.kt`).
- Produces: `de.gebetszeiten.official.parseOfficialTimes(lines: Sequence<String>): Map<LocalDate, SixTimes>` — Top-Level-Funktion. Eine Zeile `date\tfajr\tsunrise\tdhuhr\tasr\tmaghrib\tisha`; defekte/leere Zeilen werden übersprungen.

- [ ] **Step 1: Failing test schreiben**

`app/src/test/kotlin/de/gebetszeiten/official/OfficialTablesTest.kt`:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OfficialTablesTest {

    private val sample = sequenceOf(
        "2026-01-01\t06:15\t08:05\t12:24\t14:13\t16:33\t18:11",
        "",                                   // leere Zeile ignorieren
        "kaputt\t06:15",                      // defekte Zeile ignorieren
        "2026-06-07\t03:35\t05:04\t13:20\t17:36\t21:25\t22:45",
    )

    @Test fun parsesValidRowsAndMapsColumns() {
        val map = parseOfficialTimes(sample)
        val jan1 = map.getValue(LocalDate.of(2026, 1, 1))
        assertEquals(LocalTime.of(6, 15), jan1.fajr)     // imsak
        assertEquals(LocalTime.of(8, 5), jan1.sunrise)   // gunes
        assertEquals(LocalTime.of(12, 24), jan1.dhuhr)   // ogle
        assertEquals(LocalTime.of(14, 13), jan1.asr)     // ikindi
        assertEquals(LocalTime.of(16, 33), jan1.maghrib) // aksam
        assertEquals(LocalTime.of(18, 11), jan1.isha)    // yatsi
    }

    @Test fun skipsBrokenLines() {
        val map = parseOfficialTimes(sample)
        assertEquals(2, map.size)
    }

    @Test fun missingDateReturnsNull() {
        val map = parseOfficialTimes(sample)
        assertNull(map[LocalDate.of(2027, 1, 1)])
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.official.OfficialTablesTest"`
Expected: FAIL (Kompilierfehler: `parseOfficialTimes` existiert nicht).

- [ ] **Step 3: Parser implementieren**

`app/src/main/kotlin/de/gebetszeiten/official/OfficialTables.kt`:

```kotlin
package de.gebetszeiten.official

import java.time.LocalDate
import java.time.LocalTime

/**
 * Parst eine amtliche Tagestabelle (TSV ohne Header):
 *   date  fajr  sunrise  dhuhr  asr  maghrib  isha   (Tab-getrennt, ISO-Werte)
 * Defekte oder leere Zeilen werden übersprungen.
 */
fun parseOfficialTimes(lines: Sequence<String>): Map<LocalDate, SixTimes> =
    lines.mapNotNull { parseLine(it) }.toMap()

private fun parseLine(line: String): Pair<LocalDate, SixTimes>? {
    val p = line.trim().split('\t')
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
```

- [ ] **Step 4: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.official.OfficialTablesTest"`
Expected: PASS (3 Tests grün)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/official/OfficialTables.kt app/src/test/kotlin/de/gebetszeiten/official/OfficialTablesTest.kt
git commit -m "feat: parseOfficialTimes - reiner TSV-Parser fuer amtliche Zeiten"
```

---

### Task 3: Amtliche Nürnberg-Tabelle 2026 als Asset bündeln

Erzeugt das Asset aus der vorhandenen Diyanet-CSV (`core-prayertimes/src/test/resources/diyanet_nurnberg_2026.csv`). Deren Spaltenreihenfolge `date,imsak,gunes,ogle,ikindi,aksam,yatsi` entspricht **exakt** der Zielreihenfolge → Transformation = Header entfernen + Komma→Tab.

**Files:**
- Create: `app/src/main/assets/official/nuernberg-2026.tsv` (generiert)
- Test: `app/src/test/kotlin/de/gebetszeiten/official/Nuernberg2026AssetTest.kt`

**Interfaces:**
- Consumes: `parseOfficialTimes` (Task 2).

- [ ] **Step 1: Asset erzeugen**

```bash
mkdir -p app/src/main/assets/official
tail -n +2 core-prayertimes/src/test/resources/diyanet_nurnberg_2026.csv | tr ',' '\t' > app/src/main/assets/official/nuernberg-2026.tsv
```

- [ ] **Step 2: Zeilenzahl prüfen (2026 = kein Schaltjahr → 365 Zeilen)**

Run: `wc -l < app/src/main/assets/official/nuernberg-2026.tsv`
Expected: `365`

- [ ] **Step 3: Failing test schreiben (Asset gegen amtliche Referenzwerte)**

Die Referenzwerte stammen aus dem bestehenden `DiyanetPrayerTimesCalculatorTest` (offizielle Diyanet-Seite, Nürnberg, 7. Juni 2026: İmsak 03:35 · Güneş 05:04 · Öğle 13:20 · İkindi 17:36 · Akşam 21:25 · Yatsı 22:45).

`app/src/test/kotlin/de/gebetszeiten/official/Nuernberg2026AssetTest.kt`:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Liest das committete Asset über seinen Repo-Pfad (Unit-Test-CWD = app-Modul). */
class Nuernberg2026AssetTest {

    private val table: Map<LocalDate, SixTimes> by lazy {
        File("src/main/assets/official/nuernberg-2026.tsv")
            .useLines { parseOfficialTimes(it) }
    }

    @Test fun coversWholeYear2026() {
        assertEquals(365, table.size)
        assertEquals(LocalDate.of(2026, 1, 1), table.keys.min())
        assertEquals(LocalDate.of(2026, 12, 31), table.keys.max())
    }

    @Test fun matchesOfficialReferenceForJune7() {
        val t = table.getValue(LocalDate.of(2026, 6, 7))
        assertEquals(LocalTime.of(3, 35), t.fajr)
        assertEquals(LocalTime.of(5, 4), t.sunrise)
        assertEquals(LocalTime.of(13, 20), t.dhuhr)
        assertEquals(LocalTime.of(17, 36), t.asr)
        assertEquals(LocalTime.of(21, 25), t.maghrib)
        assertEquals(LocalTime.of(22, 45), t.isha)
    }
}
```

- [ ] **Step 4: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.official.Nuernberg2026AssetTest"`
Expected: PASS. Falls `matchesOfficialReferenceForJune7` fehlschlägt, stimmt eine Quell-Annahme nicht — STOP und Werte in der CSV gegen die Referenz prüfen (nicht den Test „anpassen").

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/official/nuernberg-2026.tsv app/src/test/kotlin/de/gebetszeiten/official/Nuernberg2026AssetTest.kt
git commit -m "feat: amtliche Diyanet-Tabelle Nuernberg 2026 als Offline-Asset"
```

---

### Task 4: `BundledOfficialSource` — Registry + Asset-Lader

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/official/BundledOfficialSourceTest.kt`

**Interfaces:**
- Consumes: `TextNormalize.normalize` (Task 1), `parseOfficialTimes` (Task 2), Asset aus Task 3, `SixTimes`.
- Produces:
  - `BundledOfficialSource.assetPathsFor(city: String): List<String>` — reine Registry-Auflösung (testbar ohne `Context`).
  - `suspend fun BundledOfficialSource.get(context: Context, city: String, date: LocalDate): SixTimes?`
  - `suspend fun BundledOfficialSource.covers(context: Context, city: String, date: LocalDate): Boolean`

- [ ] **Step 1: Failing test schreiben (reine Registry-Logik)**

`app/src/test/kotlin/de/gebetszeiten/official/BundledOfficialSourceTest.kt`:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOfficialSourceTest {

    @Test fun resolvesNurnbergRegardlessOfSpelling() {
        assertEquals(
            listOf("official/nuernberg-2026.tsv"),
            BundledOfficialSource.assetPathsFor("Nürnberg"),
        )
        assertEquals(
            listOf("official/nuernberg-2026.tsv"),
            BundledOfficialSource.assetPathsFor("  NÜRNBERG "),
        )
    }

    @Test fun unknownCityHasNoTable() {
        assertTrue(BundledOfficialSource.assetPathsFor("Berlin").isEmpty())
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.official.BundledOfficialSourceTest"`
Expected: FAIL (Kompilierfehler: `BundledOfficialSource` existiert nicht).

- [ ] **Step 3: `BundledOfficialSource` implementieren**

`app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt`:

```kotlin
package de.gebetszeiten.official

import android.content.Context
import de.gebetszeiten.data.TextNormalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Amtliche Diyanet-Zeiten aus gebündelten Offline-Tabellen (assets/official/).
 * Verfügbar in beiden Flavors, rein lokal — keine Netzwerknutzung.
 *
 * Aktuell abgedeckt: Nürnberg 2026. Für nicht abgedeckte Städte/Jahre liefert
 * [get] null, sodass der Aufrufer auf die Berechnung zurückfällt.
 */
object BundledOfficialSource {

    /** Normalisierter Stadtname → Asset-Pfade (eine Datei je Jahr). */
    private val TABLES: Map<String, List<String>> = mapOf(
        "nurnberg" to listOf("official/nuernberg-2026.tsv"),
    )

    @Volatile
    private var cache: Map<String, Map<LocalDate, SixTimes>> = emptyMap()

    /** Reine Registry-Auflösung (ohne Context, testbar). */
    fun assetPathsFor(city: String): List<String> =
        TABLES[TextNormalize.normalize(city)] ?: emptyList()

    suspend fun get(context: Context, city: String, date: LocalDate): SixTimes? {
        for (path in assetPathsFor(city)) {
            table(context, path)[date]?.let { return it }
        }
        return null
    }

    suspend fun covers(context: Context, city: String, date: LocalDate): Boolean =
        get(context, city, date) != null

    private suspend fun table(context: Context, path: String): Map<LocalDate, SixTimes> {
        cache[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[path] ?: load(context, path).also { cache = cache + (path to it) }
        }
    }

    private fun load(context: Context, path: String): Map<LocalDate, SixTimes> =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines {
            parseOfficialTimes(it)
        }
}
```

- [ ] **Step 4: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.official.BundledOfficialSourceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt app/src/test/kotlin/de/gebetszeiten/official/BundledOfficialSourceTest.kt
git commit -m "feat: BundledOfficialSource - amtliche Zeiten aus gebuendelten Assets"
```

---

### Task 5: `PrayerProvider.daily` an die amtliche Quelle anschließen

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt:20-25`

**Interfaces:**
- Consumes: `BundledOfficialSource.get` (Task 4).

- [ ] **Step 1: `daily(...)` erweitern**

In `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` den Import ergänzen:

```kotlin
import de.gebetszeiten.official.BundledOfficialSource
```

und die Methode `daily` ersetzen durch:

```kotlin
    suspend fun daily(context: Context, settings: AppSettings, date: LocalDate, zone: ZoneId): DailyPrayerTimes {
        // 1) Online-Cache (frischste Quelle, nur wenn aktiviert).
        if (settings.useOnline) {
            OfficialTimesCache(context).get(date)?.let { return it.toDaily(date, zone) }
        }
        // 2) Gebündelte amtliche Tabelle (offline, z. B. Nürnberg 2026).
        BundledOfficialSource.get(context, settings.city, date)?.let { return it.toDaily(date, zone) }
        // 3) Fallback: Berechnung.
        return PrayerSchedule.forDate(settings, date, zone)
    }
```

- [ ] **Step 2: Kompilierung + gesamte Unit-Tests prüfen**

Run: `./gradlew :app:compileOfflineDebugKotlin :app:testOfflineDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 3: Manuelle Verifikation (Build + App)**

Run: `./gradlew :app:assembleOfflineDebug`
Expected: BUILD SUCCESSFUL. App auf einem Gerät/Emulator starten, Ort = Nürnberg, ein Datum in 2026 wählen. Erwartet: Dhuhr 7. Juni 2026 = **13:20**, Maghrib = **21:25** (amtliche Werte). Ein Datum in 2027 wählen → Zeiten weichen leicht ab (Berechnung).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt
git commit -m "feat: PrayerProvider bevorzugt amtliche Zeiten (Nuernberg) vor Berechnung"
```

---

### Task 6: Dynamisches Quellen-Label im Footer

Footer zeigt die tatsächliche Quelle des angezeigten Tages statt fix „Berechnung".

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (zwei neue Strings)
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/DataCredit.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt:228-233` (Footer-Text dynamisch)
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/DataCreditTest.kt`

**Interfaces:**
- Produces: `de.gebetszeiten.prayer.dataCreditRes(official: Boolean): Int` — gibt die passende String-Ressource zurück.

- [ ] **Step 1: Strings ergänzen**

In `app/src/main/res/values/strings.xml` neben dem bestehenden `data_credit` einfügen:

```xml
    <string name="data_credit_official">Amtliche Diyanet-Zeiten · Nürnberg</string>
    <string name="data_credit_calculated">Berechnung: Diyanet-Methode (offline)</string>
```

(Der bestehende `data_credit`-String bleibt für Kompatibilität erhalten, wird aber im Screen nicht mehr verwendet.)

- [ ] **Step 2: Failing test schreiben**

`app/src/test/kotlin/de/gebetszeiten/prayer/DataCreditTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import de.gebetszeiten.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DataCreditTest {
    @Test fun officialPicksOfficialString() {
        assertEquals(R.string.data_credit_official, dataCreditRes(official = true))
    }
    @Test fun calculatedPicksCalculatedString() {
        assertEquals(R.string.data_credit_calculated, dataCreditRes(official = false))
    }
}
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.DataCreditTest"`
Expected: FAIL (Kompilierfehler: `dataCreditRes` existiert nicht).

- [ ] **Step 4: `dataCreditRes` implementieren**

`app/src/main/kotlin/de/gebetszeiten/prayer/DataCredit.kt`:

```kotlin
package de.gebetszeiten.prayer

import androidx.annotation.StringRes
import de.gebetszeiten.R

/** Footer-Label: amtliche vs. berechnete Quelle des angezeigten Tages. */
@StringRes
fun dataCreditRes(official: Boolean): Int =
    if (official) R.string.data_credit_official else R.string.data_credit_calculated
```

- [ ] **Step 5: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.DataCreditTest"`
Expected: PASS

- [ ] **Step 6: Footer im Screen dynamisch machen**

In `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt`, in `PrayerScreen`, vor dem `Scaffold` (nach der `dayInfo`-`produceState`-Deklaration) den Quellen-Status ermitteln:

```kotlin
    val officialSource by produceState(false, settings, selectedDate) {
        value = de.gebetszeiten.official.BundledOfficialSource.covers(context, settings.city, selectedDate) ||
            (settings.useOnline && de.gebetszeiten.official.OfficialTimesCache(context).get(selectedDate) != null)
    }
```

Dann den festen Footer-`Text` (aktuell `context.getString(R.string.data_credit)`) ersetzen durch:

```kotlin
            Text(
                text = context.getString(de.gebetszeiten.prayer.dataCreditRes(officialSource)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
```

- [ ] **Step 7: Build + alle Tests**

Run: `./gradlew :app:compileOfflineDebugKotlin :app:testOfflineDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 8: Manuelle Verifikation**

App starten, Nürnberg + Datum 2026 → Footer „Amtliche Diyanet-Zeiten · Nürnberg". Datum 2027 oder andere Stadt → „Berechnung: Diyanet-Methode (offline)".

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/kotlin/de/gebetszeiten/prayer/DataCredit.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/test/kotlin/de/gebetszeiten/prayer/DataCreditTest.kt
git commit -m "feat: Footer zeigt amtliche vs. berechnete Quelle dynamisch"
```

---

## Verifikation gesamt

- [ ] `./gradlew :app:testOfflineDebugUnitTest` — alle Unit-Tests grün
- [ ] `./gradlew :app:assembleOfflineDebug` — Release-/Debug-Build des Offline-Flavors erfolgreich
- [ ] Manuell: Nürnberg 2026 zeigt amtliche Zeiten (Dhuhr 7.6. = 13:20) + Footer „Amtliche Diyanet-Zeiten · Nürnberg"; 2027 fällt auf Berechnung zurück
- [ ] Offline-Flavor enthält weiterhin keine `INTERNET`-Permission (unverändert)
