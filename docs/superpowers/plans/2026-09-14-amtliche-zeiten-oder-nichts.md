# Amtliche Zeiten oder nichts — Umsetzungsplan

> **Fuer agentische Arbeiter:** ERFORDERLICHE UNTER-FAEHIGKEIT: `superpowers:subagent-driven-development`
> (empfohlen) oder `superpowers:executing-plans`, um diesen Plan Aufgabe fuer Aufgabe umzusetzen.
> Schritte nutzen Checkbox-Syntax (`- [ ]`).

**Ziel:** Die App zeigt nie wieder eine Gebetszeit, fuer die sie nicht buergen kann — und die
Uhr versorgt sich selbst, damit diese Regel sie nicht leert.

**Architektur:** Die Quellenreihenfolge zieht aus dem `Context`-Rumpf von `PrayerProvider.daily`
in eine reine Funktion `daySourceOrder` in `core-prayertimes`, die Telefon und Uhr teilen.
`daily()` wird nullable; `null` heisst „keine Zeiten unter den aktuellen Einstellungen". Die
Netzschicht zieht aus `app/src/online` in ein neues Modul `:net-diyanet`, das `app` und `wear`
je als `onlineImplementation` einbinden.

**Technik:** Kotlin, Jetpack Compose, Glance (Widget), Wear ProtoLayout (Kachel/Komplikation),
DataStore Preferences, JUnit4. Keine Robolectric. Testabhaengigkeiten: `junit`, `org.json`.

**Entwurf:** `docs/superpowers/specs/2026-09-14-amtliche-zeiten-oder-nichts-design.md`

## Globale Randbedingungen

- Gradle immer so aufrufen (eine PowerShell-Zeile, `JAVA_HOME` muss mit gesetzt werden):
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <tasks>`
- Vollstaendige Pruefung:
  `:core-prayertimes:test :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:compileOnlineDebugKotlin :app:compileOfflineDebugKotlin`
  plus `:app:lintOnlineRelease` (0 Fehler, heute 17 Warnungen).
- **Ausgangsstand: 164 / 372 / 306 = 842 Tests, 0 Fehler.** Jede Aufgabe nennt ihren Sollstand.
- **Quelldateien NIE mit PowerShell `Get-Content`/`Set-Content` bearbeiten** — das zerstoert die
  UTF-8-Kodierung dieses Repos. Edit-Werkzeug, `perl -0777 -pi -e` oder Python-Byte-I/O.
- Commit-Nachrichten auf Deutsch, Betreffzeile ASCII-only, ohne Punkt am Ende.
- `core-prayertimes` bleibt frei von Netzcode UND frei von Oberflaechen-Sprache (deutsche Texte
  leben im `app`- bzw. `wear`-Modul).
- Ressourcen nur ueber erschoepfendes `when` auf `R.drawable.*` waehlen, nie
  `Resources.getIdentifier()` — `isShrinkResources = true`.
- Tests, die Repo-Dateien zur Laufzeit lesen, brauchen eine `inputs`-Deklaration in
  `app/build.gradle.kts`, sonst ueberspringt Gradle sie genau dann, wenn sie gebraucht werden.
- Jede Aufgabe endet mit gruenem Testlauf und einem Commit.

---

# Phase A — Die Netzschicht wird teilbar

## Aufgabe 1: Die Abrufer von App-Typen befreien

`CompositeDiyanetFetcher` importiert `de.gebetszeiten.data.AppSettings` und
`de.gebetszeiten.prayer.fetchErrorSummary` — beides aus `app`. Solange das so ist, laesst sich
die Netzschicht nicht teilen. Diese Aufgabe aendert **kein Verhalten**, nur Abhaengigkeiten.

**Dateien:**
- Aendern: `app/src/online/kotlin/de/gebetszeiten/official/CompositeDiyanetFetcher.kt`
- Aendern: `app/src/main/kotlin/de/gebetszeiten/official/OfficialTimes.kt` (Signatur von `OfficialTimesFetcher`)
- Aendern: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt` (Aufrufstelle)
- Test: `app/src/test/kotlin/de/gebetszeiten/official/CompositeFetcherContractTest.kt` (neu)

**Schnittstellen:**
- Erzeugt: `OfficialTimesFetcher.fetch(lat: Double, lng: Double, city: String, preferredLocationId: Int?): FetchResult`
  statt `fetch(settings: AppSettings)`. `FetchResult` behaelt seine heutigen Felder und
  fuehrt zusaetzlich `candidates: List<SourceResult>`.
- Entfaellt: der Aufruf von `fetchErrorSummary` IM Fetcher. Den deutschen Fehlertext baut
  kuenftig `PrayerProvider.refreshOfficial`.

- [ ] **Schritt 1: Test schreiben**

`app/src/test/kotlin/de/gebetszeiten/official/CompositeFetcherContractTest.kt`:

```kotlin
package de.gebetszeiten.official

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Netzschicht soll in ein eigenes Modul ziehen. Dafuer darf sie nichts
 * aus dem app-Modul kennen. Der Test liest den Quelltext, weil die Kante
 * sonst erst beim Modulschnitt auffaellt - und dann teuer ist.
 */
class CompositeFetcherContractTest {

    private val netFiles = listOf(
        "CompositeDiyanetFetcher.kt",
        "DiyanetDirectFetcher.kt",
        "DiyanetProxyFetcher.kt",
        "EzanVaktiFetcher.kt",
        "DiyanetHttp.kt",
        "DiyanetYearPageParser.kt",
    ).map { File("src/online/kotlin/de/gebetszeiten/official/$it") }

    private val verboten = listOf("de.gebetszeiten.data.", "de.gebetszeiten.prayer.", "de.gebetszeiten.ui.")

    @Test fun `die Abrufer kennen keine app-Typen`() {
        netFiles.forEach { f ->
            assertTrue("${f.path} fehlt - Pfad im Test anpassen", f.isFile)
            val treffer = f.readLines()
                .filter { it.startsWith("import ") }
                .filter { zeile -> verboten.any { zeile.contains(it) } }
            assertTrue(
                "${f.name} importiert app-Typen, das verhindert den Modulschnitt:\n" +
                    treffer.joinToString("\n"),
                treffer.isEmpty(),
            )
        }
    }
}
```

- [ ] **Schritt 2: Test laufen lassen, er MUSS rot sein**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testOnlineDebugUnitTest --tests "*CompositeFetcherContractTest*"
```

Erwartet: FAIL, „CompositeDiyanetFetcher.kt importiert app-Typen", darunter
`import de.gebetszeiten.data.AppSettings` und `import de.gebetszeiten.prayer.fetchErrorSummary`.

- [ ] **Schritt 3: Signatur umstellen**

In `OfficialTimes.kt`:

```kotlin
interface OfficialTimesFetcher {
    suspend fun fetch(
        lat: Double,
        lng: Double,
        city: String,
        preferredLocationId: Int?,
    ): FetchResult
}
```

In `CompositeDiyanetFetcher` die vier Parameter statt `AppSettings` nehmen,
`fetchErrorSummary(...)` aus dem Rumpf entfernen und `candidates` im `FetchResult` mitgeben.
In `PrayerProvider.refreshOfficial`:

```kotlin
val result = fetcher.fetch(targetLat, targetLng, targetCity, preferredId)
val fehler = emptyResultError(fetchErrorSummary(result.candidates))
```

**Gleiches Verhalten, anderer Ort.** Wird ein bestehender Test rot, ist das ein Fehler dieser
Aufgabe, kein erwarteter Umbau.

- [ ] **Schritt 4: Voller Testlauf**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest
```

Erwartet: 164 / 373 / 307, 0 Fehler. (Der neue Test liegt in `app/src/test` und laeuft
deshalb in BEIDEN Flavors — die Dateien, die er liest, liegen unabhaengig vom Flavor im Repo.)

- [ ] **Schritt 5: Commit**

```bash
git add -A && git commit -F - <<'MSG'
refactor(net): die Abrufer kennen keine app-Typen mehr

Vorbereitung des Modulschnitts: CompositeDiyanetFetcher hing an AppSettings
und fetchErrorSummary. Der Fetcher bekommt jetzt vier einfache Parameter und
gibt die Kandidaten zurueck; den deutschen Fehlertext baut der Aufrufer.

Ein quelltextlesender Test haelt die Kante fest - sonst faellt sie erst beim
Modulschnitt auf, und dann ist sie teuer.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Aufgabe 2: Modul `:net-diyanet` anlegen und die Abrufer verschieben

**Dateien:**
- Erstellen: `net-diyanet/build.gradle.kts`, `net-diyanet/src/main/AndroidManifest.xml`
- Verschieben nach `net-diyanet/src/main/kotlin/de/gebetszeiten/net/`:
  `CompositeDiyanetFetcher.kt`, `DiyanetDirectFetcher.kt`, `DiyanetProxyFetcher.kt`,
  `EzanVaktiFetcher.kt`, `DiyanetHttp.kt`, `DiyanetYearPageParser.kt`
- Verschieben: `OfficialTimesFetcher` und `FetchResult` aus `app/src/main/.../OfficialTimes.kt`
  **nach `core-prayertimes`**, nicht nach `:net-diyanet`. Beides sind reine Typen (eine
  Signatur und ein Datenhalter, kein Netzcode). Wuerden sie in `:net-diyanet` liegen, koennte
  der **offline**-Flavor der Uhr sie in Aufgabe 4 nicht einmal nennen — er sieht das Modul
  nicht. `:net-diyanet` implementiert die Schnittstelle dann nur.
- Aendern: `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/online/AndroidManifest.xml`
- Verschieben: die zugehoerigen Tests von `app/src/test/` nach `net-diyanet/src/test/`

**Ebenfalls verschieben — der Plan hatte das zuerst falsch:**
- `app/src/online/assets/official/locations-world.tsv` (318 KB) nach
  `net-diyanet/src/main/assets/official/`. Ohne ihn kann die Uhr in Aufgabe 6 fuer einen
  beliebigen Ort keine Diyanet-ID aufloesen — der Istanbul-Fall fiele weg. Der Index wird
  ausschliesslich gebraucht, um zu entscheiden, WAS abgerufen wird, und gehoert damit zur
  Netzschicht. Assets eines Bibliotheksmoduls werden in die einbindende App gemischt; der
  Aufruf `context.assets.open("official/locations-world.tsv")` funktioniert unveraendert.
- `app/src/online/kotlin/de/gebetszeiten/official/DiyanetPlaceIndex.kt` nach
  `net-diyanet/src/main/kotlin/de/gebetszeiten/net/DiyanetPlaceIndex.kt`.
  `CompositeDiyanetFetcher` benutzt ihn in Zeile 157 — er kann nicht in `app` bleiben.
  An der alten Stelle bleibt ein delegierendes Objekt stehen, damit die rund sechs
  Aufrufstellen in `MainActivity` und `SettingsSheet` unveraendert bleiben:

  ```kotlin
  package de.gebetszeiten.official

  import android.content.Context
  import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace

  /** Online-Flavor: der echte Index liegt in :net-diyanet, zusammen mit dem
   *  Asset, das nur zum Abrufen gebraucht wird. Dieses Objekt haelt bloss den
   *  gewohnten Namen fuer die Oberflaeche. */
  object DiyanetPlaceIndex {
      suspend fun preload(context: Context) = de.gebetszeiten.net.DiyanetPlaceIndex.preload(context)
      suspend fun nearest(context: Context, lat: Double, lng: Double): DiyanetPlace? =
          de.gebetszeiten.net.DiyanetPlaceIndex.nearest(context, lat, lng)
      fun distanceKm(place: DiyanetPlace, lat: Double, lng: Double): Double =
          de.gebetszeiten.net.DiyanetPlaceIndex.distanceKm(place, lat, lng)
  }
  ```

**NICHT verschieben:** `OfficialTimesProvider.kt` (Flavor-Naht, app-spezifisch),
`app/src/offline/.../DiyanetPlaceIndex.kt` (der Stub bleibt, wie er ist),
`WearCacheSync.kt` (Telefon zur Uhr).

**Schnittstellen:**
- Erzeugt: Gradle-Projekt `:net-diyanet`, Namensraum `de.gebetszeiten.net`.
- Erzeugt: `INTERNET` im Manifest DIESES Moduls — damit erbt sie jeder, der es einbindet,
  und nur der.

- [ ] **Schritt 1: Modul anlegen**

`settings.gradle.kts`: `include(":net-diyanet")` ergaenzen.

`net-diyanet/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.gebetszeiten.net"
    compileSdk = 36
    // 26 wie `app`, NICHT 30 wie `wear`. Ein Bibliotheksmodul setzt die
    // Untergrenze fuer jeden, der es einbindet: mit 30 wuerde der
    // veroeffentlichte online-Flavor Android 8 bis 10 ausschliessen. Die Uhr
    // darf hoeher liegen, das ist ihre eigene Untergrenze.
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":core-prayertimes"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
```

`net-diyanet/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Die Netzberechtigung lebt in DIESEM Modul. Wer es einbindet, bekommt
         sie; wer es nicht einbindet, ist beweisbar netzfrei. -->
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```

Fehlt `libs.plugins.android.library` im Versionskatalog, in `gradle/libs.versions.toml` unter
`[plugins]` ergaenzen: `android-library = { id = "com.android.library", version.ref = "agp" }`

- [ ] **Schritt 2: Dateien verschieben**

Mit `git mv`, damit die Historie erhalten bleibt. In jeder verschobenen Datei die Paketzeile
von `package de.gebetszeiten.official` auf `package de.gebetszeiten.net` aendern; in `app` die
noetigen `import de.gebetszeiten.net.*` ergaenzen. `INTERNET` aus
`app/src/online/AndroidManifest.xml` entfernen — sie kommt jetzt aus dem Modul.

`app/build.gradle.kts`, in `dependencies`:

```kotlin
    "onlineImplementation"(project(":net-diyanet"))
```

- [ ] **Schritt 3: Uebersetzen**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileOnlineDebugKotlin :app:compileOfflineDebugKotlin
```

Erwartet: BUILD SUCCESSFUL. Der offline-Flavor darf `:net-diyanet` nicht sehen.

- [ ] **Schritt 4: Testlauf plus Berechtigungsprobe am Artefakt**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test :net-diyanet:test :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOfflineDebug
```

```bash
aapt2 dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -i internet || echo "kein INTERNET - richtig"
```

- [ ] **Schritt 5: Commit**

```bash
git add -A && git commit -F - <<'MSG'
refactor(net): die Abrufer ziehen in ein eigenes Modul

:net-diyanet buendelt die Netzschicht und traegt die INTERNET-Berechtigung.
Wer das Modul einbindet, bekommt sie; wer nicht, ist beweisbar netzfrei.
Damit kann die Uhr dieselben Abrufer benutzen wie das Telefon, ohne dass es
eine zweite Kopie der Quorum-Verdrahtung gibt.

Am offline-APK geprueft: weiterhin kein INTERNET.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Aufgabe 3: Der Netzfreiheits-Test prueft die Modulkanten

**Dateien:**
- Aendern: `app/src/test/kotlin/de/gebetszeiten/official/NoNetworkInSharedCodeTest.kt`
- Loeschen: `app/src/test/kotlin/de/gebetszeiten/official/CompositeFetcherContractTest.kt`
  (Aufgabe 1 hat ihren Zweck erfuellt; die Kante ist jetzt eine Modulgrenze, die der
  Compiler durchsetzt)
- Aendern: `app/build.gradle.kts` (`inputs` fuer die beiden build-Dateien)

- [ ] **Schritt 1: Test ergaenzen**

```kotlin
    @Test fun `net-diyanet wird nur vom online-Flavor eingebunden`() {
        val zeilen = File("../app/build.gradle.kts").readLines() +
            File("../wear/build.gradle.kts").readLines()
        val einbindungen = zeilen.map { it.trim() }
            .filter { it.contains("\":net-diyanet\"") && !it.startsWith("//") }
        assertTrue("net-diyanet wird nirgends eingebunden", einbindungen.isNotEmpty())
        einbindungen.forEach {
            assertTrue(
                "net-diyanet traegt INTERNET und darf nur im online-Flavor stehen: $it",
                it.startsWith("\"onlineImplementation\""),
            )
        }
    }
```

- [ ] **Schritt 2: Mutation, die sterben muss** — in `app/build.gradle.kts` versuchsweise
  `implementation(project(":net-diyanet"))` statt `"onlineImplementation"(...)` setzen, Test
  laufen lassen (muss rot werden), Zeile zuruecknehmen.

- [ ] **Schritt 3: `inputs`-Deklaration ergaenzen** (sonst ueberspringt Gradle den Test genau
  dann, wenn jemand die build-Datei aendert)

```kotlin
    inputs.files(
        rootProject.file("app/build.gradle.kts"),
        rootProject.file("wear/build.gradle.kts"),
    )
        .withPropertyName("modulkanten")
        .withPathSensitivity(PathSensitivity.RELATIVE)
```

- [ ] **Schritt 4: Testlauf** — 164 / 373 / 307.
- [ ] **Schritt 5: Commit** `test(net): die Modulkante ist ein Build-Fehler, keine Konvention`

---

# Phase B — Die Uhr versorgt sich selbst

## Aufgabe 4: `wear` bekommt Flavors und eine Abruf-Naht

**Dateien:**
- Aendern: `wear/build.gradle.kts`
- Erstellen: `wear/src/online/kotlin/de/gebetszeiten/wear/WearFetchProvider.kt`
- Erstellen: `wear/src/offline/kotlin/de/gebetszeiten/wear/WearFetchProvider.kt`

**Schnittstellen:**
- Erzeugt: `object WearFetchProvider { const val isOnline: Boolean; fun fetcher(context: Context): OfficialTimesFetcher? }`
  — exakt das Muster von `OfficialTimesProvider` im app-Modul.

- [ ] **Schritt 1: Flavors ergaenzen** (in `wear/build.gradle.kts`, Block `android { }`)

```kotlin
    flavorDimensions += "connectivity"
    productFlavors {
        create("offline") {
            dimension = "connectivity"
            isDefault = true
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
        }
        create("online") { dimension = "connectivity" }
    }
```

und in `dependencies`:

```kotlin
    "onlineImplementation"(project(":net-diyanet"))
```

- [ ] **Schritt 2: Beide Nahtdateien schreiben**

`wear/src/online/kotlin/de/gebetszeiten/wear/WearFetchProvider.kt`:

```kotlin
package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.net.CompositeDiyanetFetcher

/** Online-Flavor der Uhr: sie ruft selbst ab, ueber dieselben drei Quellen
 *  wie das Telefon. Auf Wear OS leitet das System die Anfrage ueber Bluetooth
 *  durchs gekoppelte Telefon, wenn die Uhr kein eigenes Netz hat. */
object WearFetchProvider {
    const val isOnline = true
    fun fetcher(context: Context): OfficialTimesFetcher? = CompositeDiyanetFetcher.create(context)
}
```

`wear/src/offline/kotlin/de/gebetszeiten/wear/WearFetchProvider.kt`:

```kotlin
package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher

/** Offline-Flavor der Uhr: kein Netzcode, beweisbar. */
object WearFetchProvider {
    const val isOnline = false
    @Suppress("UNUSED_PARAMETER")
    fun fetcher(context: Context): OfficialTimesFetcher? = null
}
```

Beide Dateien nennen `OfficialTimesFetcher` aus `core-prayertimes` (dorthin verschoben in
Aufgabe 2) — der offline-Flavor sieht `:net-diyanet` nicht und koennte einen dort liegenden
Typ nicht einmal benennen.

- [ ] **Schritt 3: Uebersetzen**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:compileOnlineDebugKotlin :wear:compileOfflineDebugKotlin
```

- [ ] **Schritt 4: Netzfreiheit am Artefakt pruefen**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:assembleOfflineDebug :wear:assembleOnlineDebug
```

```bash
aapt2 dump permissions wear/build/outputs/apk/offline/debug/wear-offline-debug.apk | grep -i internet || echo "offline: kein INTERNET - richtig"
aapt2 dump permissions wear/build/outputs/apk/online/debug/wear-online-debug.apk | grep -i internet
```

- [ ] **Schritt 5: Commit** `build(wear): zwei Flavors, damit die Netzfreiheit beweisbar bleibt`

---

## Aufgabe 5: Die Uhr speichert mehrere Orte

`WearOfficialCache` haelt genau einen Ort und wirft ihn beim Ortswechsel weg — das ist der
Grund, warum der Ortswaehler der Uhr sie nach dem Umbau leeren wuerde. `CacheStore` (in
`core-prayertimes`, voll getestet) kann mehrere.

**Dateien:**
- Aendern: `wear/src/main/kotlin/de/gebetszeiten/wear/WearOfficialCache.kt`
- Test: `wear/src/test/kotlin/de/gebetszeiten/wear/WearCacheMigrationTest.kt` (neu)

**Schnittstellen:**
- Bleibt: `WearOfficialCache.get(context, date, lat, lng): SixTimes?` — gleiche Signatur,
  anderer Rumpf. Alle Aufrufer bleiben unveraendert.
- Erzeugt: `suspend fun WearOfficialCache.put(context, schedule: Map<LocalDate, SixTimes>, lat: Double, lng: Double, locationId: Int?)`
- Erzeugt: `fun migrateLegacySchedule(altText: String?, lat: Double?, lng: Double?): String?`

- [ ] **Schritt 1: Migrationstest schreiben**

```kotlin
package de.gebetszeiten.wear

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCacheMigrationTest {

    private val einTag = "2026-09-12\t04:53\t06:39\t13:18\t16:50\t19:47\t21:18\n"

    @Test fun `alter Ein-Ort-Stand bekommt eine Kopfzeile`() {
        val neu = migrateLegacySchedule(einTag, 49.4521, 11.0767)
        assertNotNull(neu)
        assertTrue("Kopfzeile fehlt: $neu", neu!!.startsWith("#49.4521|11.0767|"))
        assertTrue("Die Tageszeile ist verloren gegangen", neu.contains(einTag.trim()))
    }

    @Test fun `ohne Stempel gibt es nichts zu uebernehmen`() {
        assertNull(migrateLegacySchedule(einTag, null, null))
    }

    @Test fun `leerer Altstand ergibt null`() {
        assertNull(migrateLegacySchedule(null, 49.4521, 11.0767))
    }
}
```

- [ ] **Schritt 2: Rot sehen**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :wear:testOnlineDebugUnitTest --tests "*WearCacheMigrationTest*"
```

Erwartet: „Unresolved reference 'migrateLegacySchedule'".

- [ ] **Schritt 3: Implementieren** — `migrateLegacySchedule` schreiben, `get`/`put` auf
  `CacheStore.select` bzw. `CacheStore.put` umstellen, Migration beim ersten Lesen mit einem
  Merker `cache_migrated` (Muster: `USE_ONLINE_MIGRATED` in `SettingsRepository`).
- [ ] **Schritt 4: Testlauf** beider Wear-Flavors plus voller Lauf.
- [ ] **Schritt 5: Commit** `feat(wear): die Uhr wirft den vorigen Ort nicht mehr weg`

---

## Aufgabe 6: Die Uhr ruft selbst ab

**Dateien:**
- Verschieben: `app/src/main/kotlin/de/gebetszeiten/official/CacheFreshness.kt` nach
  `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/`
  (samt Tests)
- Erstellen: `wear/src/main/kotlin/de/gebetszeiten/wear/WearRefresh.kt`
- Aendern: `wear/src/main/kotlin/de/gebetszeiten/wear/MainActivity.kt`,
  `PrayerTileService.kt`, `PrayerComplicationService.kt`
- Test: `wear/src/testOnline/kotlin/de/gebetszeiten/wear/WearFetchProviderTest.kt`,
  `wear/src/testOffline/kotlin/de/gebetszeiten/wear/WearFetchProviderTest.kt`

**Schnittstellen:**
- Erzeugt: `suspend fun refreshWearOfficial(context: Context, force: Boolean = false)`
- Wiederverwendet: `needsRefresh(...)` und `chooseTarget(...)` — nach dem Umzug aus
  `core-prayertimes`.

- [ ] **Schritt 1: `CacheFreshness` nach `core-prayertimes` verschieben**

`git mv`, Paketzeile auf `de.gebetszeiten.core.prayertimes.officialtimes`, Importe in `app`
nachziehen, Tests mitverschieben. Danach voller Testlauf: die Gesamtzahl bleibt gleich, core
steigt um so viel, wie app faellt.

- [ ] **Schritt 2: Flavor-Tests schreiben**

`wear/src/testOnline/kotlin/de/gebetszeiten/wear/WearFetchProviderTest.kt`:

```kotlin
package de.gebetszeiten.wear

import org.junit.Assert.assertTrue
import org.junit.Test

class WearFetchProviderTest {
    @Test fun `der online-Flavor der Uhr kann abrufen`() {
        assertTrue(WearFetchProvider.isOnline)
    }
}
```

`wear/src/testOffline/kotlin/de/gebetszeiten/wear/WearFetchProviderTest.kt`:

```kotlin
package de.gebetszeiten.wear

import org.junit.Assert.assertFalse
import org.junit.Test

class WearFetchProviderTest {
    @Test fun `der offline-Flavor der Uhr ruft nie ab`() {
        assertFalse(WearFetchProvider.isOnline)
    }
}
```

- [ ] **Schritt 3: `refreshWearOfficial` implementieren** — Muster:
  `PrayerProvider.refreshOfficial`, aber nur der EINE gewaehlte Uhr-Ort, kein Favoritenreigen.
  Dieselbe Wiederholungs-Bremse (`needsRefresh`), damit Kachel und Komplikation nicht bei
  jedem Zeichnen abrufen. Ausgeloest beim Start von `MainActivity` sowie beim Auffrischen von
  Kachel und Komplikation.
- [ ] **Schritt 4: Voller Testlauf** beider Flavors.
- [ ] **Schritt 5: Commit** `feat(wear): die Uhr holt ihre amtlichen Zeiten selbst`

---

## Aufgabe 7: Der Sync bleibt, die Uhr haengt nicht mehr daran

**Dateien:**
- Aendern: `wear/src/main/kotlin/de/gebetszeiten/wear/SyncDecision.kt`,
  `WearSyncApplier.kt`
- Test: `wear/src/test/kotlin/de/gebetszeiten/wear/SyncDecisionTest.kt` (bestehend erweitern)

**Schnittstellen:**
- Erzeugt: `fun syncWins(syncUpdatedEpochMs: Long, ownUpdatedEpochMs: Long?): Boolean`

- [ ] **Schritt 1: Test schreiben**

```kotlin
    @Test fun `ein frischerer eigener Stand schlaegt den Sync`() {
        assertFalse(syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = 2_000L))
    }

    @Test fun `ohne eigenen Stand gewinnt der Sync`() {
        assertTrue(syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = null))
    }

    @Test fun `bei gleichem Stand gewinnt der Sync - er ist billiger als ein Abruf`() {
        assertTrue(syncWins(syncUpdatedEpochMs = 1_000L, ownUpdatedEpochMs = 1_000L))
    }
```

- [ ] **Schritt 2: Rot sehen.**
- [ ] **Schritt 3: Implementieren** und in `WearSyncApplier` benutzen.
- [ ] **Schritt 4: Testlauf.**
- [ ] **Schritt 5: Commit** `fix(wear): der Sync ueberschreibt keinen frischeren eigenen Stand`

---

# Phase C — Amtlich oder nichts

## Aufgabe 8: `daySourceOrder` — die Regel wird testbar

**Dateien:**
- Erstellen: `core-prayertimes/src/main/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DaySource.kt`
- Test: `core-prayertimes/src/test/kotlin/de/gebetszeiten/core/prayertimes/officialtimes/DaySourceOrderTest.kt`

**Schnittstellen:**
- Erzeugt: `enum class DaySource { ONLINE_CACHE, BUNDLED_TABLE, CALCULATION }`
- Erzeugt: `fun daySourceOrder(useOnline: Boolean, calculationFillsGaps: Boolean): List<DaySource>`

- [ ] **Schritt 1: Test schreiben**

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Quellenreihenfolge war bis hierhin von KEINEM Test gedeckt: sie stand
 * im Rumpf von PrayerProvider.daily, und der braucht einen Context.
 */
class DaySourceOrderTest {

    @Test fun `online an, Notausgang aus - Cache, dann Bundle, keine Berechnung`() {
        assertEquals(
            listOf(DaySource.ONLINE_CACHE, DaySource.BUNDLED_TABLE),
            daySourceOrder(useOnline = true, calculationFillsGaps = false),
        )
    }

    @Test fun `online an, Notausgang an - die Berechnung kommt ZULETZT`() {
        assertEquals(
            listOf(DaySource.ONLINE_CACHE, DaySource.BUNDLED_TABLE, DaySource.CALCULATION),
            daySourceOrder(useOnline = true, calculationFillsGaps = true),
        )
    }

    @Test fun `online aus - der Cache wird nicht einmal gefragt`() {
        assertEquals(
            listOf(DaySource.BUNDLED_TABLE),
            daySourceOrder(useOnline = false, calculationFillsGaps = false),
        )
    }

    @Test fun `online aus, Notausgang an - Bundle vor Berechnung`() {
        assertEquals(
            listOf(DaySource.BUNDLED_TABLE, DaySource.CALCULATION),
            daySourceOrder(useOnline = false, calculationFillsGaps = true),
        )
    }

    @Test fun `amtliche Quellen stehen IMMER vor der Berechnung`() {
        listOf(true, false).forEach { online ->
            val order = daySourceOrder(useOnline = online, calculationFillsGaps = true)
            assertEquals("Die Berechnung muss der letzte Eintrag sein", DaySource.CALCULATION, order.last())
        }
    }
}
```

- [ ] **Schritt 2: Rot sehen**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core-prayertimes:test --tests "*DaySourceOrderTest*"
```

Erwartet: „Unresolved reference 'daySourceOrder'".

- [ ] **Schritt 3: Implementieren**

```kotlin
package de.gebetszeiten.core.prayertimes.officialtimes

/** Woher die Zeiten eines Tages stammen duerfen. */
enum class DaySource { ONLINE_CACHE, BUNDLED_TABLE, CALCULATION }

/**
 * Welche Quellen fuer einen Tag befragt werden duerfen, in welcher
 * Reihenfolge. Amtliches steht immer vorn; die Berechnung steht ueberhaupt
 * nur in der Liste, wenn der Nutzer sie als Notausgang eingeschaltet hat.
 *
 * Bis 14.09.2026 stand diese Regel im Rumpf von `PrayerProvider.daily` und
 * war damit ungetestet - und sie war falsch: die Berechnung sprang ein, ohne
 * gefragt worden zu sein.
 */
fun daySourceOrder(useOnline: Boolean, calculationFillsGaps: Boolean): List<DaySource> =
    buildList {
        if (useOnline) add(DaySource.ONLINE_CACHE)
        add(DaySource.BUNDLED_TABLE)
        if (calculationFillsGaps) add(DaySource.CALCULATION)
    }
```

- [ ] **Schritt 4: Gruen sehen** — core steigt um 5 Tests.
- [ ] **Schritt 5: Commit** `feat(core): die Quellenreihenfolge wird eine pruefbare Funktion`

---

## Aufgabe 9: `daily()` darf „nichts" sagen

**Dateien:**
- Aendern: `app/src/main/kotlin/de/gebetszeiten/prayer/PrayerProvider.kt`
- Aendern (Compilerfehler abarbeiten): `MainActivity.kt`, `MonatScreen.kt`,
  `PrayerViewModel.kt`, `KarahaDisplay.kt`, `NextPrayerWidget.kt`,
  `PrayerAlarmScheduler.kt`, `PrayerAlarmReceiver.kt`, `BootReceiver.kt`

**Schnittstellen:**
- Aendert: `daily(...): DailyPrayerTimes?`, `next(...): NextPrayer?`,
  `nextPrayer(...): NextPrayer?`, `currentlyActive(...): NextPrayer?`
- `null` heisst **immer** dasselbe: keine Zeiten unter den aktuellen Einstellungen.

- [ ] **Schritt 1: `daily()` umschreiben**

```kotlin
    suspend fun daily(
        context: Context,
        settings: AppSettings,
        date: LocalDate,
        zone: ZoneId,
    ): DailyPrayerTimes? {
        for (source in daySourceOrder(settings.useOnline, settings.calculationFillsGaps)) {
            when (source) {
                DaySource.ONLINE_CACHE ->
                    OfficialTimesCache(context).get(date, settings.latitude, settings.longitude)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.BUNDLED_TABLE ->
                    BundledOfficialSource.get(context, settings.latitude, settings.longitude, date)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.CALCULATION ->
                    return PrayerSchedule.forDate(settings, date, zone)
            }
        }
        return null
    }
```

`settings.calculationFillsGaps` entsteht erst in Aufgabe 15 — bis dahin `settings.useCalculated`
einsetzen und dort umbenennen.

- [ ] **Schritt 2: Uebersetzen, die Fehlerliste IST die Arbeitsliste**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:compileOnlineDebugKotlin
```

Jede gemeldete Stelle bekommt in den Aufgaben 11 bis 14 ihre eigentliche Behandlung. Hier nur
so viel, dass es uebersetzt: `?.let { }` bzw. fruehes `return`. **KEIN `!!`, und an keiner
Stelle ein Rueckfall auf berechnete Zeiten** — genau den schaffen wir ab.

- [ ] **Schritt 3: `next`, `nextPrayer`, `currentlyActive` nullable machen**
- [ ] **Schritt 4: Voller Testlauf** — bestehende Tests bleiben gruen.
- [ ] **Schritt 5: Commit** `feat(prayer): daily() darf kein Ergebnis haben`

---

## Aufgabe 10: Die Worte fuer den Leerfall

**Dateien:**
- Erstellen: `app/src/main/kotlin/de/gebetszeiten/prayer/NoTimesNotice.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/NoTimesNoticeTest.kt`

**Schnittstellen:**
- Erzeugt: `data class NoTimesNotice(val headline: String, val detail: String, val showFetch: Boolean)`
- Erzeugt: `fun noTimesNotice(city: String, onlineEnabled: Boolean): NoTimesNotice`

- [ ] **Schritt 1: Test schreiben**

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoTimesNoticeTest {

    @Test fun `mit Online-Abruf wird das Abrufen angeboten`() {
        val n = noTimesNotice(city = "Nürnberg", onlineEnabled = true)
        assertEquals("Keine amtlichen Zeiten für Nürnberg", n.headline)
        assertTrue(n.showFetch)
    }

    @Test fun `ohne Online-Abruf wird kein Abrufen angeboten`() {
        val n = noTimesNotice(city = "Nürnberg", onlineEnabled = false)
        assertFalse("Ein Knopf, der nichts tun kann, ist eine Luege", n.showFetch)
    }

    @Test fun `der Satz sagt, was zu tun ist - nicht nur, was fehlt`() {
        listOf(true, false).forEach { online ->
            val d = noTimesNotice("Nürnberg", online).detail
            assertTrue("Kein Ausweg genannt: $d", d.contains("Berechnung"))
        }
    }

    @Test fun `mehrteilige Ortsnamen bleiben vollstaendig`() {
        assertTrue(noTimesNotice("Bad Mergentheim", true).headline.contains("Bad Mergentheim"))
    }
}
```

- [ ] **Schritt 2: Rot sehen.**
- [ ] **Schritt 3: Implementieren.** Wortlaut:
  - `headline`: „Keine amtlichen Zeiten für <Ort>"
  - `detail` mit Netz: „Die App zeigt nur amtliche Diyanet-Zeiten. Jetzt abrufen — oder die
    Berechnung als Notausgang einschalten."
  - `detail` ohne Netz: „Die App zeigt nur amtliche Diyanet-Zeiten. Schalte den Online-Abruf
    ein oder erlaube die Berechnung als Notausgang."
- [ ] **Schritt 4: Gruen sehen.**
- [ ] **Schritt 5: Commit** `feat(ui): die Worte fuer den Fall ohne amtliche Zeiten`

---

## Aufgabe 11: Heute- und Monatsansicht

**Dateien:** `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt`,
`MonatScreen.kt`, `PrayerViewModel.kt`

- [ ] **Schritt 1:** Heute-Ansicht bei `null`: statt der Zeitachse eine Karte mit
  `noTimesNotice(...)`. Zwei Knoepfe — „Jetzt abrufen" (nur bei `showFetch`, ruft dieselbe
  Funktion wie „Jetzt aktualisieren" im Blatt) und „Berechnung einschalten" (setzt
  `calculationFillsGaps = true`).
- [ ] **Schritt 2:** Monatsansicht: Tage ohne Zeiten mit „—" in allen Spalten; ueber der
  Tabelle EINE Zeile `noTimesNotice(...).headline`, sobald mindestens ein Tag leer ist — nicht
  je Zeile wiederholen.
- [ ] **Schritt 3:** `PrayerViewModel` traegt `DailyPrayerTimes?` durch, ohne `!!`.
- [ ] **Schritt 4:** Uebersetzen und voller Testlauf.
- [ ] **Schritt 5: Commit** `feat(ui): ohne amtliche Zeiten steht da ein Satz, keine Zahl`

---

## Aufgabe 12: Das Widget

**Dateien:** `app/src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidget.kt` (Zeilen 68 und 86)

- [ ] **Schritt 1:** Bei `null` statt „naechstes Gebet" den Kurztext
  `noTimesNotice(city, onlineEnabled).headline`, antippbar zur App.
- [ ] **Schritt 2:** Uebersetzen.
- [ ] **Schritt 3:** Voller Testlauf.
- [ ] **Schritt 4:** Am Emulator pruefen (Flow siehe Erinnerung `emulator-ui-verification`).
- [ ] **Schritt 5: Commit** `feat(widget): das Widget behauptet keine Zeiten, die es nicht hat`

---

## Aufgabe 13: Wecker und Dauerbenachrichtigung schweigen

**Dateien:** `app/src/main/kotlin/de/gebetszeiten/alarm/PrayerAlarmScheduler.kt`,
`PrayerAlarmReceiver.kt`, `BootReceiver.kt`,
`app/src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt`

- [ ] **Schritt 1:** `PrayerAlarmScheduler`: bei `null` ALLE Gebets- und Stufen-Wecker
  **abbestellen**, nicht bloss keine neuen setzen — sonst bleiben alte stehen und feuern auf
  veraltete Zeiten.
- [ ] **Schritt 2:** `PrayerNotifier`: bei `null` die Dauerbenachrichtigung ENTFERNEN, nicht
  mit leeren Werten anzeigen.
- [ ] **Schritt 3:** Uebersetzen, voller Testlauf.
- [ ] **Schritt 4: Mutationsprobe** — das Abbestellen auskommentieren. Mindestens ein Test
  muss sterben; stirbt keiner, fehlt der Test, und er ist zuerst zu schreiben.
- [ ] **Schritt 5: Commit** `fix(alarm): ohne Zeiten werden Wecker abbestellt, nicht vererbt`

---

## Aufgabe 14: Die eine Meldung

**Dateien:**
- Erstellen: `app/src/main/kotlin/de/gebetszeiten/notify/PauseNotice.kt`
- Aendern: `app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt`,
  `app/src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/notify/PauseNoticeTest.kt`

**Schnittstellen:**
- Erzeugt: `enum class PauseNotice { SHOW, CLEAR, NOTHING }`
- Erzeugt: `fun pauseNotice(hasTimes: Boolean, alreadyShown: Boolean): PauseNotice`
- Erzeugt: `AppSettings.pauseNoticeShown: Boolean = false`, Schluessel `pause_notice_shown`

- [ ] **Schritt 1: Test schreiben**

```kotlin
package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseNoticeTest {

    @Test fun `keine Zeiten und noch nicht gemeldet - melden`() {
        assertEquals(PauseNotice.SHOW, pauseNotice(hasTimes = false, alreadyShown = false))
    }

    @Test fun `keine Zeiten und schon gemeldet - schweigen`() {
        assertEquals(PauseNotice.NOTHING, pauseNotice(hasTimes = false, alreadyShown = true))
    }

    @Test fun `wieder Zeiten und war gemeldet - Meldung zuruecknehmen`() {
        assertEquals(PauseNotice.CLEAR, pauseNotice(hasTimes = true, alreadyShown = true))
    }

    @Test fun `wieder Zeiten und war nie gemeldet - nichts tun`() {
        assertEquals(PauseNotice.NOTHING, pauseNotice(hasTimes = true, alreadyShown = false))
    }
}
```

- [ ] **Schritt 2: Rot sehen.**
- [ ] **Schritt 3: Implementieren** — Funktion, Merker im DataStore, Meldung „Keine amtlichen
  Zeiten — Erinnerungen pausiert" auf dem bestehenden stillen Kanal, antippbar zur App.
  `CLEAR` nimmt die Meldung zurueck UND loescht den Merker.
- [ ] **Schritt 4: Gruen sehen** plus Mutationsprobe: den Merker nie setzen — der Test
  `keine Zeiten und schon gemeldet` muss sterben.
- [ ] **Schritt 5: Commit** `feat(notify): der Ausfall der Erinnerungen wird EINMAL gemeldet`

---

## Aufgabe 15: Die Einstellung bekommt ihre neue Bedeutung

**Dateien:** `app/src/main/kotlin/de/gebetszeiten/data/SettingsRepository.kt`,
`app/src/main/kotlin/de/gebetszeiten/ui/SettingsSheet.kt`,
`app/src/main/kotlin/de/gebetszeiten/prayer/TimesSourceBadge.kt`
Test: `app/src/test/kotlin/de/gebetszeiten/data/CalculationFallbackMigrationTest.kt`

**Schnittstellen:**
- Erzeugt: `AppSettings.calculationFillsGaps: Boolean = false`, Schluessel
  `calculation_fills_gaps`, Merker `calculation_fallback_migrated`
- Entfaellt: `AppSettings.useCalculated`
- Erzeugt: `fun calculationFillsGapsFromPrefs(migrated: Boolean, stored: Boolean?, legacyUseCalculated: Boolean?): Boolean`
- Erzeugt: `TimesSourceBadge.None` fuer Orte ohne amtliche Quelle bei ausgeschaltetem Notausgang

- [ ] **Schritt 1: Migrationstest schreiben**

```kotlin
package de.gebetszeiten.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der alte Schalter hiess „immer rechnen", der neue heisst „Luecken fuellen".
 * Wer ihn an hatte, bekommt kuenftig amtliche Zeiten, wo welche da sind - eine
 * Verhaltensaenderung, und die gehoert in eine benannte Migration statt in die
 * stille Umdeutung desselben Schluessels.
 */
class CalculationFallbackMigrationTest {

    @Test fun `wer immer rechnen wollte, behaelt den Notausgang`() {
        assertTrue(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = true))
    }

    @Test fun `wer amtliche Zeiten wollte, bekommt keinen Notausgang`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = false))
    }

    @Test fun `frische Installation ist ab Werk aus`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = false, stored = null, legacyUseCalculated = null))
    }

    @Test fun `nach der Migration gilt der neue Schluessel, nicht der alte`() {
        assertFalse(calculationFillsGapsFromPrefs(migrated = true, stored = false, legacyUseCalculated = true))
        assertTrue(calculationFillsGapsFromPrefs(migrated = true, stored = true, legacyUseCalculated = false))
    }
}
```

- [ ] **Schritt 2: Rot sehen.**
- [ ] **Schritt 3: Implementieren**, Muster `countdownModeFromPrefs`. Den alten Schluessel nach
  der Migration entfernen.
- [ ] **Schritt 4: Wortlaut im Blatt ersetzen.** Der heutige verspricht „Amtliche Zeiten sind
  Standard, wo verfuegbar" und beschreibt damit genau den Rueckfall, den wir abschaffen. Neu:

  > **Berechnung als Notausgang**
  > Wenn fuer einen Tag keine amtlichen Diyanet-Zeiten vorliegen, die lokale astronomische
  > Berechnung verwenden. Amtliche Zeiten haben immer Vorrang. Ist dies aus, zeigt die App
  > fuer solche Tage gar keine Zeiten.

- [ ] **Schritt 5: Commit** `feat(settings): der Schalter fuellt Luecken, statt alles zu ersetzen`

---

## Aufgabe 16: Dieselbe Regel auf der Uhr

**Dateien:** `wear/src/main/kotlin/de/gebetszeiten/wear/WearPrayer.kt`, `WearSettings.kt`,
`MainActivity.kt` (wear), `PrayerTileService.kt`, `PrayerComplicationService.kt`

- [ ] **Schritt 1:** `WearPrayer.daily` auf `daySourceOrder` umstellen und nullable machen.
- [ ] **Schritt 2:** `WearSettings.useCalculated` zu `calculationFillsGaps`, mit derselben
  Migration wie am Telefon.
- [ ] **Schritt 3:** Leerfall auf der Uhr: kurzer Satz statt Zeiten in App, Kachel und
  Komplikation. Auf der Uhr ist Platz knapp — „Keine amtlichen Zeiten" genuegt, ohne Ortsnamen.
- [ ] **Schritt 4:** Uebersetzen und voller Testlauf beider Flavors.
- [ ] **Schritt 5: Commit** `feat(wear): auch die Uhr zeigt keine Zeit, fuer die sie nicht buergt`

---

## Aufgabe 17: Der Waechter und die Auslieferung

**Dateien:**
- Erstellen: `app/src/test/kotlin/de/gebetszeiten/prayer/CalculationIsNotAFallbackTest.kt`
- Aendern: `playstore/CHECKLISTE.md`, `wear/build.gradle.kts` (versionCode)

- [ ] **Schritt 1: Waechter-Test schreiben**

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Rueckfall auf die Berechnung ist genau einmal erlaubt: in
 * PrayerProvider.daily, hinter daySourceOrder. Taucht PrayerSchedule.forDate
 * anderswo auf, ist ein zweiter Rueckfall entstanden - und der zweite ist
 * garantiert der ungetestete.
 */
class CalculationIsNotAFallbackTest {

    @Test fun `die Berechnung wird nur an einer Stelle aufgerufen`() {
        val erlaubt = setOf("PrayerProvider.kt", "PrayerSchedule.kt")
        val treffer = File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.name !in erlaubt }
            .filter { it.readText().contains("PrayerSchedule.forDate") }
            .map { it.name }
            .toList()
        assertTrue("Berechnung ausserhalb von PrayerProvider aufgerufen: $treffer", treffer.isEmpty())
    }
}
```

- [ ] **Schritt 2: Mutation, die sterben muss** — versuchsweise einen Aufruf in
  `MainActivity.kt` einfuegen, Test muss rot werden, danach zuruecknehmen. Dazu die
  `inputs.dir`-Deklaration fuer `src/main/kotlin` pruefen (aus Aufgabe 14 der frueheren
  Runde vorhanden).
- [ ] **Schritt 3:** `wear/build.gradle.kts` `versionCode` von 1015 auf 1016 erhoehen,
  `versionName` auf `0.1.16`.
- [ ] **Schritt 4:** `playstore/CHECKLISTE.md` Abschnitt 5 korrigieren — die Aussage, das
  wear-Modul sei unveraendert und muesse nicht erneut hochgeladen werden, stimmt nicht mehr.
  Neuen Pfad nennen: `wear/build/outputs/bundle/onlineRelease/wear-online-release.aab`.
- [ ] **Schritt 5:** Volle Pruefung inklusive `:app:lintOnlineRelease`, dann Commit
  `test(prayer): ein zweiter stiller Rueckfall waere ein Build-Fehler`

---

## Abschliessende Pruefung am Geraet

Emulator-Flow siehe Erinnerung `emulator-ui-verification`. **Achtung:** Der Emulator laeuft auf
UTC, und ein ANR-Dialog kann aus dem Snapshot stammen — vor jeder Diagnose
`adb logcat -b events | grep am_anr` auf ein HEUTIGES Datum pruefen.

1. Online-Flavor, frische Installation, Netz an → amtliche Zeiten, nirgends „Berechnet".
2. Ort ohne amtliche Abdeckung waehlen (Koordinaten manuell, mitten in der Nordsee) →
   Hinweis statt Zeiten, zwei Knoepfe, KEINE Zahlen.
3. „Berechnung einschalten" antippen → Zeiten erscheinen, Badge sagt „Berechnet".
4. Wieder aus → Hinweis zurueck, Dauerbenachrichtigung verschwindet, EINE Meldung
   „Erinnerungen pausiert". Ein zweiter Ausloeser darf sie NICHT wiederholen.
5. Uhr: Ort auf der Uhr wechseln, ohne das Telefon anzufassen → die Uhr holt die Zeiten selbst.
6. Flugmodus auf Telefon UND Uhr, zwischen Orten wechseln → gespeicherte amtliche Zeiten,
   sofort, ohne Ladezustand.
