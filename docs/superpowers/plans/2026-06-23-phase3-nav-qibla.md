# Phase 3 — Bottom-Navigation + Qibla-Kompass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Eine Bottom-Navigation mit den Tabs „Heute" (bestehender Screen) und „Qibla" (neuer Kompass-Screen, Live-Kompass + Fallback, mit Gradzahl/Himmelsrichtung/Entfernung zur Kaaba), ohne neue Navigations-Abhängigkeit.

**Architecture:** Reine `QiblaMath` (Peilung/Distanz/Himmelsrichtung) in `de.gebetszeiten.prayer`. `MainScreen` hält den Tab-State und ein gemeinsames `Scaffold` (TopAppBar + `NavigationBar`); der bisherige `PrayerScreen`-Body wird zu `HeuteContent`. `QiblaScreen` zeichnet einen Kompass per `Canvas`, der Geräte-Azimut kommt aus `rememberDeviceAzimuth` (ROTATION_VECTOR-Sensor) oder ist `null` (Fallback).

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`NavigationBar`), Android SensorManager (ROTATION_VECTOR), JUnit4, java.time, Vektor-Drawables.

## Global Constraints

- **Keine neue Abhängigkeit:** kein `androidx.navigation`; `NavigationBar` + `rememberSaveable`-Tab-State. Icons als Vektor-Drawables (wie `ic_tune`).
- **Kein GPS / keine neue Permission:** Qibla aus `settings.latitude/longitude`; Kompass nur über Lagesensoren (keine Laufzeit-Berechtigung). Offline-Flavor bleibt netzfrei.
- **Kaaba:** `21.4225, 39.8262`.
- **Heute-Verhalten unverändert:** der Refactor ändert nur die Hülle, nicht das Verhalten des Gebetszeiten-Inhalts.
- **2 Tabs** (Heute + Qibla); Monat folgt Phase 4.
- Kotlin / JVM 17 / minSdk 26.
- **Tests** auf Offline-Flavor; Gradle nur über PowerShell (`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`), `gradle.properties` NICHT ändern. Compose/Sensor per `:app:compileOfflineDebugKotlin` + `:app:assembleOfflineDebug` + manueller Sicht verifizieren; im Report manuelle Checks als „nicht durch mich erfolgt" vermerken.

---

### Task 1: `QiblaMath` (rein, TDD)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/QiblaMath.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/QiblaMathTest.kt`

**Interfaces:**
- Produces: `QiblaMath.bearing(lat,lng): Double`, `QiblaMath.distanceKm(lat,lng): Double`, `QiblaMath.cardinal(bearing): String`, `QiblaMath.normalizeDegrees(deg: Float): Float`.

- [ ] **Step 1: Failing test schreiben**

`app/src/test/kotlin/de/gebetszeiten/prayer/QiblaMathTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaMathTest {
    @Test fun bearingDueNorthOfKaabaIsSouth() {
        assertEquals(180.0, QiblaMath.bearing(50.0, 39.8262), 0.5)   // gleicher Meridian, nördlich → Süd
    }
    @Test fun bearingDueSouthOfKaabaIsNorth() {
        val b = QiblaMath.bearing(0.0, 39.8262)                      // gleicher Meridian, südlich → Nord
        assertTrue("expected ~0/360 but was $b", b < 0.5 || b > 359.5)
    }
    @Test fun bearingFromNurembergIsSoutheast() {
        val b = QiblaMath.bearing(49.4521, 11.0767)
        assertTrue("expected 125..140 but was $b", b in 125.0..140.0)
        assertEquals("Südost", QiblaMath.cardinal(b))
    }
    @Test fun distanceFromKaabaIsZero() {
        assertEquals(0.0, QiblaMath.distanceKm(21.4225, 39.8262), 1.0)
    }
    @Test fun distanceFromNurembergIsPlausible() {
        assertTrue(QiblaMath.distanceKm(49.4521, 11.0767) in 2700.0..3300.0)
    }
    @Test fun cardinalMapping() {
        assertEquals("Nord", QiblaMath.cardinal(0.0))
        assertEquals("Ost", QiblaMath.cardinal(90.0))
        assertEquals("Süd", QiblaMath.cardinal(180.0))
        assertEquals("Südost", QiblaMath.cardinal(135.0))
    }
    @Test fun normalizeWraps() {
        assertEquals(350f, QiblaMath.normalizeDegrees(-10f), 0.001f)
        assertEquals(10f, QiblaMath.normalizeDegrees(370f), 0.001f)
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.QiblaMathTest"`
Expected: FAIL (`QiblaMath` nicht gefunden).

- [ ] **Step 3: `QiblaMath.kt` implementieren**

```kotlin
package de.gebetszeiten.prayer

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Qibla-Richtung und -Entfernung zur Kaaba, rein aus Koordinaten (kein GPS). */
object QiblaMath {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262
    private const val EARTH_KM = 6371.0

    /** Initiale Großkreis-Peilung von (lat,lng) zur Kaaba, normalisiert 0..360. */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Luftlinie (Haversine) in km zur Kaaba. */
    fun distanceKm(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dp = Math.toRadians(KAABA_LAT - lat)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_KM * asin(min(1.0, sqrt(a)))
    }

    private val DIRS = arrayOf("Nord", "Nordost", "Ost", "Südost", "Süd", "Südwest", "West", "Nordwest")

    /** 8-Wind-Himmelsrichtung eines Winkels. */
    fun cardinal(bearing: Double): String {
        val norm = ((bearing % 360.0) + 360.0) % 360.0
        return DIRS[(norm / 45.0).roundToInt() % 8]
    }

    /** Winkel auf 0..360 normalisieren. */
    fun normalizeDegrees(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}
```

- [ ] **Step 4: Tests ausführen, Erfolg bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.QiblaMathTest"`
Expected: PASS (7 Tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/prayer/QiblaMath.kt app/src/test/kotlin/de/gebetszeiten/prayer/QiblaMathTest.kt
git commit -m "feat: QiblaMath - Peilung/Distanz/Himmelsrichtung zur Kaaba (rein, getestet)"
```

---

### Task 2: Navigations-Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_today.xml`
- Create: `app/src/main/res/drawable/ic_qibla.xml`

- [ ] **Step 1: Drawables anlegen**

`app/src/main/res/drawable/ic_today.xml` (Kalender-Symbol):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M19,3h-1V1h-2v2H8V1H6v2H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM19,19H5V9h14V19zM19,7H5V5h14V7zM7,11h5v5H7V11z" />
</vector>
```

`app/src/main/res/drawable/ic_qibla.xml` (Navigations-/Kompass-Symbol):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10 10,-4.49 10,-10S17.51,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM7.5,16.5l5.07,-2.33 2.33,-5.07 -5.07,2.33zM12,11.1c0.5,0 0.9,0.4 0.9,0.9s-0.4,0.9 -0.9,0.9 -0.9,-0.4 -0.9,-0.9 0.4,-0.9 0.9,-0.9z" />
</vector>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/drawable/ic_today.xml app/src/main/res/drawable/ic_qibla.xml
git commit -m "feat: Navigations-Icons (Heute, Qibla)"
```

---

### Task 3: Navigations-Gerüst + statischer Qibla-Screen

Refactor: `PrayerScreen` → `HeuteContent`, neues `MainScreen` mit `NavigationBar`; `QiblaScreen` zeigt zunächst nur die statische Richtung/Distanz (Live-Kompass kommt in Task 4). Verhalten von Heute bleibt identisch.

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt`

**Interfaces:**
- Produces: `MainScreen(viewModel)`, `HeuteContent(inner: PaddingValues, settings: AppSettings)`, `QiblaScreen(inner: PaddingValues, settings: AppSettings)`.

- [ ] **Step 1: `onCreate` auf `MainScreen` umstellen**

In `MainActivity.onCreate` den Aufruf `PrayerScreen(viewModel)` durch `MainScreen(viewModel)` ersetzen.

- [ ] **Step 2: `PrayerScreen` → `HeuteContent` umbauen**

`PrayerScreen` wird zu `HeuteContent`. Ersetze die Signatur

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerScreen(viewModel: PrayerViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val zone = ZoneId.systemDefault()
```

durch

```kotlin
@Composable
private fun HeuteContent(inner: PaddingValues, settings: AppSettings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
```

Entferne dann in `HeuteContent` den **gesamten** `Scaffold(...) { inner -> Column(...) { … } }`-Rahmen und das nachfolgende `if (showSettings) ModalBottomSheet { … }`-Block, sodass nur noch die innere `Column` (mit `DateNavigator`, `TimesCard`, Footer) und der `karahaInfo`-`AlertDialog` übrig bleiben. Konkret:
- Lösche `var showSettings by remember { mutableStateOf(false) }`.
- Ersetze den `Scaffold(topBar = { … }) { inner -> Column(modifier = Modifier.padding(inner)…) { … } }` durch die nackte `Column` mit demselben Inhalt, deren Modifier `Modifier.padding(inner)` das **Parameter**-`inner` nutzt:

```kotlin
    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateNavigator(
            date = selectedDate,
            isToday = isToday,
            hijriOffsetDays = settings.hijriOffsetDays,
            onPrev = { selectedDate = selectedDate.minusDays(1) },
            onNext = { selectedDate = selectedDate.plusDays(1) },
            onToday = { selectedDate = LocalDate.now(zone) },
        )
        dayInfo?.let { info ->
            TimesCard(
                info = info,
                now = ZonedDateTime.now(zone),
                highlight = isToday,
                showKaraha = settings.showKaraha,
                showRemaining = settings.showCountdown,
                onKaraha = { karahaInfo = it },
            )
        }
        Text(
            text = context.getString(de.gebetszeiten.prayer.dataCreditRes(officialSource)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
```

- Lösche den `if (showSettings) { ModalBottomSheet(...) { LocationSettings(...) } }`-Block (wandert nach `MainScreen`).
- Behalte den `karahaInfo?.let { AlertDialog(...) }`-Block am Ende von `HeuteContent`.
- Die innere Logik (`tick`, `selectedDate`, `isToday`, `dayInfo`, `officialSource`, `karahaInfo`) bleibt unverändert erhalten.

- [ ] **Step 3: `MainScreen` hinzufügen**

Direkt vor `HeuteContent` einfügen:

```kotlin
private enum class Tab { HEUTE, QIBLA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: PrayerViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.HEUTE) settings.city else "Qibla") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = "Einstellungen")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.HEUTE,
                    onClick = { tab = Tab.HEUTE },
                    icon = { Icon(painterResource(R.drawable.ic_today), contentDescription = null) },
                    label = { Text("Heute") },
                )
                NavigationBarItem(
                    selected = tab == Tab.QIBLA,
                    onClick = { tab = Tab.QIBLA },
                    icon = { Icon(painterResource(R.drawable.ic_qibla), contentDescription = null) },
                    label = { Text("Qibla") },
                )
            }
        },
    ) { inner ->
        when (tab) {
            Tab.HEUTE -> HeuteContent(inner, settings)
            Tab.QIBLA -> QiblaScreen(inner, settings)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            LocationSettings(settings = settings, onApply = { viewModel.save(it) })
        }
    }
}
```

- [ ] **Step 4: Imports ergänzen**

In `MainActivity.kt` ergänzen:
```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveable
```
Ungenutzt gewordene Imports entfernen, falls der Compiler sie meldet (z. B. wenn `PrayerScreen` keine eigenen mehr braucht — i. d. R. bleiben alle genutzt).

- [ ] **Step 5: `QiblaScreen.kt` (statisch) anlegen**

```kotlin
package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.QiblaMath
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(inner: PaddingValues, settings: AppSettings) {
    val bearing = QiblaMath.bearing(settings.latitude, settings.longitude)
    val distance = QiblaMath.distanceKm(settings.latitude, settings.longitude)
    val cardinal = QiblaMath.cardinal(bearing)
    val km = String.format(Locale.GERMAN, "%,d", distance.roundToInt())

    Column(
        modifier = Modifier.padding(inner).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${bearing.roundToInt()}° · $cardinal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Kaaba · $km km",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 6: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 7: Manuelle Notiz**

Im Report: manuell zu prüfen (NICHT durch Implementer) — Tab-Wechsel Heute↔Qibla; Heute inhaltlich unverändert; Einstellungen aus beiden Tabs erreichbar; Qibla zeigt plausible Gradzahl/Richtung/Distanz für die gesetzte Stadt.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt
git commit -m "feat: Bottom-Navigation (Heute/Qibla) + statischer Qibla-Screen"
```

---

### Task 4: Live-Kompass (Sensor + Canvas) im Qibla-Screen

Fügt den drehenden Kompass hinzu: `rememberDeviceAzimuth` liest den ROTATION_VECTOR-Sensor; `QiblaScreen` zeichnet Rose + Qibla-Pfeil per `Canvas`, rotiert um `-azimuth`. Kein Sensor → Fallback (statische Rose nach Norden + Hinweis).

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/Compass.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt`

**Interfaces:**
- Consumes: `QiblaMath` (Task 1), `rememberAnimationsEnabled` (Phase 2, `de.gebetszeiten.ui`).
- Produces: `rememberDeviceAzimuth(): Float?` (null = kein Sensor; sonst 0..360, geglättet).

- [ ] **Step 1: `Compass.kt` (Sensor) anlegen**

```kotlin
package de.gebetszeiten.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.gebetszeiten.prayer.QiblaMath

/** Geräte-Azimut (0..360 von Norden), geglättet; null wenn kein Rotationssensor. */
@Composable
fun rememberDeviceAzimuth(): Float? {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(rotationSensor) {
        if (rotationSensor == null) {
            onDispose { }
        } else {
            val matrix = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val (axisX, axisY) = when (displayRotation(context)) {
                        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                    val deg = QiblaMath.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
                    azimuth = lowPass(deg, azimuth)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    // Sensor vorhanden, aber noch keine Messung → provisorisch 0; gar kein Sensor → null.
    return if (rotationSensor == null) null else (azimuth ?: 0f)
}

/** Zirkulärer Tiefpass (Wraparound-sicher) gegen Zittern. */
private fun lowPass(new: Float, old: Float?): Float {
    if (old == null) return new
    var delta = new - old
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return QiblaMath.normalizeDegrees(old + 0.15f * delta)
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
```

- [ ] **Step 2: `QiblaScreen` um den Kompass erweitern**

Ersetze den Inhalt von `QiblaScreen.kt` durch:

```kotlin
package de.gebetszeiten.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.QiblaMath
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(inner: PaddingValues, settings: AppSettings) {
    val bearing = QiblaMath.bearing(settings.latitude, settings.longitude)
    val distance = QiblaMath.distanceKm(settings.latitude, settings.longitude)
    val cardinal = QiblaMath.cardinal(bearing)
    val km = String.format(Locale.GERMAN, "%,d", distance.roundToInt())

    val azimuth = rememberDeviceAzimuth()
    val live = azimuth != null
    val target = -(azimuth ?: 0f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (rememberAnimationsEnabled()) tween(250) else snap(),
        label = "compass",
    )

    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val north = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.padding(inner).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            rotate(animated) {
                drawCompassRose(ring, north)
                rotate(bearing.toFloat(), pivot = center) {
                    drawQiblaArrow(accent)
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        Text(
            "${bearing.roundToInt()}° · $cardinal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Kaaba · $km km",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!live) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Text(
                "Kompass nicht verfügbar — Qibla liegt bei ${bearing.roundToInt()}° $cardinal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ring + N/O/S/W-Ticks; N-Tick in [north] hervorgehoben. */
private fun DrawScope.drawCompassRose(ring: Color, north: Color) {
    val r = size.minDimension / 2f
    drawCircle(ring.copy(alpha = 0.5f), radius = r, style = Stroke(width = 3.dp.toPx()))
    for (i in 0 until 4) {
        val isNorth = i == 0
        rotate(i * 90f, pivot = center) {
            drawLine(
                color = if (isNorth) north else ring,
                start = Offset(center.x, center.y - r),
                end = Offset(center.x, center.y - r + (if (isNorth) 22.dp.toPx() else 14.dp.toPx())),
                strokeWidth = if (isNorth) 5.dp.toPx() else 3.dp.toPx(),
            )
        }
    }
}

/** Vom Zentrum nach oben zeigender, gefüllter Qibla-Pfeil. */
private fun DrawScope.drawQiblaArrow(accent: Color) {
    val r = size.minDimension / 2f
    val tip = Offset(center.x, center.y - r + 26.dp.toPx())
    val baseY = center.y + r * 0.35f
    val half = 12.dp.toPx()
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x - half, baseY)
        lineTo(center.x + half, baseY)
        close()
    }
    drawPath(path, accent)
    drawCircle(accent, radius = 5.dp.toPx(), center = center)
}
```

- [ ] **Step 3: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 4: Manuelle Notiz**

Im Report (NICHT durch Implementer): auf einem Gerät mit Magnetometer dreht sich die Rose mit dem Gerät, der grüne Pfeil zeigt stabil zur Kaaba; auf einem Emulator ohne Sensor erscheint der Fallback-Hinweis und der Pfeil zeigt die statische Richtung.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/Compass.kt app/src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt
git commit -m "feat: Live-Qibla-Kompass (ROTATION_VECTOR) mit Fallback"
```

---

## Verifikation gesamt

- [ ] `.\gradlew.bat :app:testOfflineDebugUnitTest` — alle Unit-Tests grün (QiblaMath + Phasen 1/2)
- [ ] `.\gradlew.bat :app:assembleOfflineDebug` — Offline-Build erfolgreich
- [ ] Manuell: Tab-Wechsel Heute↔Qibla, Heute unverändert, Einstellungen aus beiden Tabs; Qibla-Kompass dreht (bzw. Fallback), Gradzahl/Richtung/Distanz plausibel
- [ ] Offline-Flavor weiterhin ohne `INTERNET`-Permission; keine neue Laufzeit-Berechtigung
