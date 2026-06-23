# Phase 2 — Karaha-Countdown + Polish/Bugfix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Karaha-Zeiten mit Countdown (amber) indizieren, den Theme-Farben-Bugfix beheben und vier Polish-Punkte umsetzen — alles auf dem bestehenden „Heute"-Screen, ohne die Pill-Timeline umzubauen.

**Architecture:** Reine, testbare Helfer (`durationLabel`, `KarahaCountdown`, `animationsEnabled`, Hijri-Namen) in `de.gebetszeiten.prayer`; die Compose-Anbindung in `MainActivity.kt`/`Theme.kt` nutzt sie. Neuer `LocalIsDark`-CompositionLocal liefert den effektiven Dark-Zustand.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit4 (`libs.junit`), java.time, Vektor-Drawables.

## Global Constraints

- **Offline-Flavor bleibt netzfrei.** Keine neuen Netzwerk-/Permission-Änderungen.
- **Kotlin / JVM 17 / minSdk 26.** Compose-Material3 (BOM 2026.05.01).
- **Keine neue Abhängigkeit** für Icons — Vektor-Drawables + `painterResource` (wie `ic_tune`).
- **Hijri-Namen (international):** `Muharram, Safar, Rabi al-awwal, Rabi al-thani, Jumada al-awwal, Jumada al-thani, Rajab, Schaban, Ramadan, Schawwal, Dhul-Qada, Dhul-Hijja`.
- **Touch-Targets:** `Modifier.minimumInteractiveComponentSize()` (48dp) für tappbare Karaha/Nafl-Zeilen.
- **Karaha-Countdown** nur am heutigen Tag (`highlight`).
- **Pill-Timeline-Optik bleibt** im Kern unverändert; Karaha-Aktivzustand fügt amber Akzent + Countdown-Zeile hinzu.
- **Tests** laufen auf dem Offline-Flavor: `./gradlew :app:testOfflineDebugUnitTest`. Gradle nur über PowerShell (`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`), Heap 2048m — `gradle.properties` NICHT ändern.
- Compose-UI ist nicht unit-testbar ohne Gerät: solche Tasks per `:app:compileOfflineDebugKotlin` + `:app:assembleOfflineDebug` verifizieren und die manuelle Prüfung im Report als „nicht durch mich erfolgt" vermerken.

---

### Task 1: Hijri-Monatsnamen → internationale Transliteration

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/HijriText.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/HijriTextTest.kt`

**Interfaces:**
- Bestehende `hijriText(date, offsetDays)` / `hijriTextShort(date, offsetDays)` bleiben unverändert in der Signatur; nur das `HIJRI_MONTHS`-Array ändert sich.

- [ ] **Step 1: Failing test schreiben**

Anker: Am 9. Juni 2026 zeigt die App (java.time `HijrahDate`, Offset 0) den 23. Tag des 12. Hijri-Monats 1447 (bisher „Zilhicce", neu „Dhul-Hijja").

`app/src/test/kotlin/de/gebetszeiten/prayer/HijriTextTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriTextTest {
    @Test fun usesInternationalTransliterationForDhulHijja() {
        assertEquals("23. Dhul-Hijja 1447", hijriText(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun shortFormDropsYear() {
        assertEquals("23. Dhul-Hijja", hijriTextShort(LocalDate.of(2026, 6, 9), 0))
    }

    @Test fun offsetShiftsDay() {
        assertTrue(hijriText(LocalDate.of(2026, 6, 9), -1).startsWith("22. Dhul-Hijja"))
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run (PowerShell): `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.HijriTextTest"`
Expected: FAIL (`expected:<…Dhul-Hijja…> but was:<…Zilhicce…>`).

- [ ] **Step 3: Array ersetzen**

In `HijriText.kt` das `HIJRI_MONTHS`-Array ersetzen durch:

```kotlin
// International (deutsch gebräuchliche) Transliteration der Hijri-Monatsnamen.
private val HIJRI_MONTHS = arrayOf(
    "Muharram", "Safar", "Rabi al-awwal", "Rabi al-thani", "Jumada al-awwal", "Jumada al-thani",
    "Rajab", "Schaban", "Ramadan", "Schawwal", "Dhul-Qada", "Dhul-Hijja",
)
```

Den Kommentar in den KDoc-Beispielen („24. Zilhicce 1447") auf „24. Dhul-Hijja 1447" anpassen.

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.HijriTextTest"`
Expected: PASS (3 Tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/prayer/HijriText.kt app/src/test/kotlin/de/gebetszeiten/prayer/HijriTextTest.kt
git commit -m "feat: Hijri-Monatsnamen auf internationale Transliteration (Dhul-Hijja)"
```

---

### Task 2: `LocalIsDark` + Theme-Farben-Bugfix

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (in `TimesCard`)

**Interfaces:**
- Produces: `de.gebetszeiten.ui.theme.LocalIsDark: ProvidableCompositionLocal<Boolean>` — true, wenn das effektive Theme dunkel ist (folgt dem `darkTheme`-Parameter von `GebetszeitenTheme`, nicht dem System).

- [ ] **Step 1: `LocalIsDark` in Theme.kt ergänzen**

In `Theme.kt` neben `LocalHighContrast` einfügen:

```kotlin
/** True when the effective (possibly user-forced) theme is dark. Read by accent
 *  colours so they don't depend on the raw system setting. */
val LocalIsDark = staticCompositionLocalOf { false }
```

Und in `GebetszeitenTheme` den Provider erweitern — ersetze

```kotlin
    CompositionLocalProvider(LocalHighContrast provides highContrast) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
```

durch

```kotlin
    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
```

- [ ] **Step 2: `TimesCard` auf `LocalIsDark` umstellen**

In `MainActivity.kt`, in `TimesCard`, ersetze

```kotlin
    val dark = isSystemInDarkTheme()
```

durch

```kotlin
    val dark = de.gebetszeiten.ui.theme.LocalIsDark.current
```

(`isSystemInDarkTheme` bleibt anderweitig importiert/genutzt — u. a. in `onCreate` und der `inDuha`/Sunrise-Logik —, daher den Import NICHT entfernen.)

- [ ] **Step 3: Kompilieren**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manuelle Notiz**

Compose-Verhalten nicht unit-testbar. Im Report vermerken: manuell zu prüfen — Theme „Hell" auf dunklem System (und umgekehrt) zeigt korrekte amber/green-Akzente in der Karte. NICHT durch den Implementer ausgeführt.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/theme/Theme.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "fix: TimesCard nutzt effektiven Dark-Zustand (LocalIsDark) statt System"
```

---

### Task 3: Chevron-Icons in der Datumsnavigation

**Files:**
- Create: `app/src/main/res/drawable/ic_chevron_left.xml`
- Create: `app/src/main/res/drawable/ic_chevron_right.xml`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (in `DateNavigator`)

- [ ] **Step 1: Drawables anlegen**

`app/src/main/res/drawable/ic_chevron_left.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="#FF000000"
        android:pathData="M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z" />
</vector>
```

`app/src/main/res/drawable/ic_chevron_right.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="#FF000000"
        android:pathData="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z" />
</vector>
```

- [ ] **Step 2: `DateNavigator` auf Icons umstellen**

In `MainActivity.kt`, in `DateNavigator`, den linken `IconButton` — ersetze

```kotlin
        IconButton(
            onClick = onPrev,
            modifier = Modifier.semantics { contentDescription = "Vorheriger Tag" },
        ) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
```

durch

```kotlin
        IconButton(onClick = onPrev) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = "Vorheriger Tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

und analog den rechten `IconButton` — ersetze

```kotlin
        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = "Nächster Tag" },
        ) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
```

durch

```kotlin
        IconButton(onClick = onNext) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = "Nächster Tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

(`Icon`, `painterResource`, `R` sind in `MainActivity.kt` bereits importiert.)

- [ ] **Step 3: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/ic_chevron_left.xml app/src/main/res/drawable/ic_chevron_right.xml app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "feat: Chevron-Icons in der Datumsnavigation statt Text-Glyphen"
```

---

### Task 4: `durationLabel` extrahieren + `KarahaCountdown` (rein, TDD)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/DurationText.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/KarahaCountdown.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (private `durationLabel` entfernen, Import ergänzen)
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/DurationTextTest.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/KarahaCountdownTest.kt`

**Interfaces:**
- Produces: `de.gebetszeiten.prayer.durationLabel(min: Long): String` — „3 Std 26 Min" / „55 Min".
- Produces: `de.gebetszeiten.prayer.KarahaCountdown.state(now, start, end): KarahaCountdown.State?` mit `data class State(val active: Boolean, val text: String)`. `null` außerhalb des Fensters; `active=true` + `text="noch …"` wenn `start ≤ now < end`; `active=false` + `text="in …"` wenn `0 ≤ (start-now) < 60 Min`.

- [ ] **Step 1: Failing tests schreiben**

`app/src/test/kotlin/de/gebetszeiten/prayer/DurationTextTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test fun hoursAndMinutes() { assertEquals("3 Std 26 Min", durationLabel(206)) }
    @Test fun fullHours() { assertEquals("2 Std", durationLabel(120)) }
    @Test fun minutesOnly() { assertEquals("55 Min", durationLabel(55)) }
    @Test fun zero() { assertEquals("0 Min", durationLabel(0)) }
}
```

`app/src/test/kotlin/de/gebetszeiten/prayer/KarahaCountdownTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class KarahaCountdownTest {
    private val z = ZoneId.of("Europe/Berlin")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 6, 9, h, m, 0, 0, z)
    private val start = t(20, 47)
    private val end = t(21, 27)

    @Test fun activeShowsRemaining() {
        val s = KarahaCountdown.state(t(21, 15), start, end)!!
        assertEquals(true, s.active)
        assertEquals("noch 12 Min", s.text)
    }

    @Test fun imminentWithinLeadShowsCountdown() {
        val s = KarahaCountdown.state(t(20, 29), start, end)!!
        assertEquals(false, s.active)
        assertEquals("in 18 Min", s.text)
    }

    @Test fun beforeLeadIsNull() {
        assertNull(KarahaCountdown.state(t(19, 0), start, end))
    }

    @Test fun afterEndIsNull() {
        assertNull(KarahaCountdown.state(t(21, 27), start, end))
    }
}
```

- [ ] **Step 2: Tests ausführen, Fehlschlag bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.DurationTextTest" --tests "de.gebetszeiten.prayer.KarahaCountdownTest"`
Expected: FAIL (Kompilierfehler: `durationLabel` / `KarahaCountdown` nicht gefunden).

- [ ] **Step 3: `DurationText.kt` implementieren**

```kotlin
package de.gebetszeiten.prayer

/** "3 Std 26 Min" / "55 Min" — kompakte Dauer in Stunden/Minuten. */
fun durationLabel(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h Std $m Min"
        h > 0 -> "$h Std"
        else -> "$m Min"
    }
}
```

- [ ] **Step 4: `KarahaCountdown.kt` implementieren**

```kotlin
package de.gebetszeiten.prayer

import java.time.Duration
import java.time.ZonedDateTime

/** Countdown-Text für eine Makruh-(Karaha-)Zeit relativ zu [now]. */
object KarahaCountdown {

    private const val LEAD_MINUTES = 60L

    data class State(val active: Boolean, val text: String)

    /** null außerhalb des relevanten Fensters; sonst aktiv ("noch …") oder
     *  bevorstehend innerhalb [LEAD_MINUTES] ("in …"). */
    fun state(now: ZonedDateTime, start: ZonedDateTime, end: ZonedDateTime): State? = when {
        !now.isBefore(start) && now.isBefore(end) ->
            State(active = true, text = "noch " + durationLabel(Duration.between(now, end).toMinutes().coerceAtLeast(0)))
        now.isBefore(start) -> {
            val toStart = Duration.between(now, start).toMinutes()
            if (toStart in 0 until LEAD_MINUTES) State(active = false, text = "in " + durationLabel(toStart)) else null
        }
        else -> null
    }
}
```

- [ ] **Step 5: MainActivity-`durationLabel` entfernen, Import ergänzen**

In `MainActivity.kt` die private Funktion `durationLabel` (samt ihrem KDoc-Kommentar „/** "3 Std 26 Min" … */") löschen und den Import

```kotlin
import de.gebetszeiten.prayer.durationLabel
```

ergänzen (zu den übrigen `de.gebetszeiten.prayer.*`-Imports). Alle bestehenden `durationLabel(...)`-Aufrufe in `MainActivity.kt` lösen dann gegen die Top-Level-Funktion auf.

- [ ] **Step 6: Tests + Kompilierung**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.DurationTextTest" --tests "de.gebetszeiten.prayer.KarahaCountdownTest"` → PASS
Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/prayer/DurationText.kt app/src/main/kotlin/de/gebetszeiten/prayer/KarahaCountdown.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/test/kotlin/de/gebetszeiten/prayer/DurationTextTest.kt app/src/test/kotlin/de/gebetszeiten/prayer/KarahaCountdownTest.kt
git commit -m "feat: durationLabel extrahiert + KarahaCountdown-Helfer (rein, getestet)"
```

---

### Task 5: prefers-reduced-motion für `ProgressPill`

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/Motion.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (in `ProgressPill`)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/MotionTest.kt`

**Interfaces:**
- Produces: `de.gebetszeiten.ui.animationsEnabled(scale: Float): Boolean` (rein) und `@Composable de.gebetszeiten.ui.rememberAnimationsEnabled(): Boolean`.

- [ ] **Step 1: Failing test schreiben**

`app/src/test/kotlin/de/gebetszeiten/ui/MotionTest.kt`:

```kotlin
package de.gebetszeiten.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun zeroScaleDisablesAnimations() { assertEquals(false, animationsEnabled(0f)) }
    @Test fun normalScaleEnablesAnimations() { assertEquals(true, animationsEnabled(1f)) }
    @Test fun halfScaleStillEnabled() { assertEquals(true, animationsEnabled(0.5f)) }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.ui.MotionTest"`
Expected: FAIL (`animationsEnabled` nicht gefunden).

- [ ] **Step 3: `Motion.kt` implementieren**

```kotlin
package de.gebetszeiten.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** False, wenn der System-Animationsskalierungsfaktor 0 ist ("Animationen aus"). */
fun animationsEnabled(scale: Float): Boolean = scale != 0f

/** Liest die globale Animationsskalierung und meldet, ob animiert werden soll. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return animationsEnabled(scale)
}
```

- [ ] **Step 4: `ProgressPill` anpassen**

In `MainActivity.kt`, in `ProgressPill`, ersetze

```kotlin
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "pillFill",
    )
```

durch

```kotlin
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (rememberAnimationsEnabled()) tween(durationMillis = 650) else snap(),
        label = "pillFill",
    )
```

und ergänze den Import `import androidx.compose.animation.core.snap` (sowie `import de.gebetszeiten.ui.rememberAnimationsEnabled`, falls nicht durch das gleiche Package automatisch sichtbar — `ProgressPill` liegt in `de.gebetszeiten.ui`, daher genügt der Top-Level-Aufruf ohne Import).

- [ ] **Step 5: Tests + Kompilierung**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.ui.MotionTest"` → PASS
Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/Motion.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/test/kotlin/de/gebetszeiten/ui/MotionTest.kt
git commit -m "feat: ProgressPill respektiert prefers-reduced-motion (Animationen aus)"
```

---

### Task 6: Karaha-Aktivzustand — amber Akzent + Countdown (Compose-Wiring)

Bindet `KarahaCountdown` (Task 4) in Pill und Chip ein. Kein Unit-Test (Logik ist in Task 4 getestet); Verifikation per Build + manuell.

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (`Timeline`, `MakruhChipRow`, `ProgressPill`, Pill-Inhalt im selektierten `PrayerBlock`)

**Interfaces:**
- Consumes: `KarahaCountdown.state(now, start, end)` (Task 4).

- [ ] **Step 1: `MakruhChipRow` um Countdown erweitern**

Signatur erweitern (neuer Parameter `countdown: KarahaCountdown.State?`). Ersetze den Funktionskopf

```kotlin
private fun MakruhChipRow(
    block: MakruhBlock,
    amber: Color,
    selected: Boolean,
    faded: Boolean,
    onKaraha: (Pair<String, String>) -> Unit,
) {
```

durch

```kotlin
private fun MakruhChipRow(
    block: MakruhBlock,
    amber: Color,
    selected: Boolean,
    faded: Boolean,
    countdown: KarahaCountdown.State?,
    onKaraha: (Pair<String, String>) -> Unit,
) {
```

und im Text-Composable die Chip-Beschriftung — ersetze

```kotlin
            Text(
                "Makruh · ${block.label} · ${block.start.format(HM)}–${block.end.format(HM)}",
```

durch

```kotlin
            Text(
                "Makruh · ${block.label} · ${block.start.format(HM)}–${block.end.format(HM)}" +
                    (countdown?.let { " · ${it.text}" } ?: ""),
```

Importiere `de.gebetszeiten.prayer.KarahaCountdown` in `MainActivity.kt`.

- [ ] **Step 2: Aufrufer von `MakruhChipRow` mit Countdown versorgen**

In `Timeline` gibt es zwei `MakruhChipRow(...)`-Aufrufe (einen im `PrayerBlock`-Zweig, einen im Duha/`NaflBlock`-Zweig). Beide so anpassen, dass sie `KarahaCountdown.state(now, it.start, it.end).takeIf { highlight }` übergeben. Ersetze den Aufruf

```kotlin
                            mkVisible(block.before)?.takeIf { it !== pillMakruh }?.let {
                                MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
                            }
```

durch

```kotlin
                            mkVisible(block.before)?.takeIf { it !== pillMakruh }?.let {
                                MakruhChipRow(
                                    it, amber, isMakruhNow(it), highlight && it.end.isBefore(now),
                                    countdown = if (highlight) KarahaCountdown.state(now, it.start, it.end) else null,
                                    onKaraha = onKaraha,
                                )
                            }
```

und den Duha-Zweig — ersetze

```kotlin
                                mkVisible(block.before)?.let {
                                    MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
                                }
```

durch

```kotlin
                                mkVisible(block.before)?.let {
                                    MakruhChipRow(
                                        it, amber, isMakruhNow(it), highlight && it.end.isBefore(now),
                                        countdown = if (highlight) KarahaCountdown.state(now, it.start, it.end) else null,
                                        onKaraha = onKaraha,
                                    )
                                }
```

- [ ] **Step 3: `ProgressPill` um amber Akzent erweitern**

Neuer Parameter `accentAmber: Boolean = false`. Ersetze den Funktionskopf

```kotlin
private fun ProgressPill(
    fraction: Float,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer,
    makruhBand: Pair<Float, Float>? = null,
    makruhColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
```

durch

```kotlin
private fun ProgressPill(
    fraction: Float,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer,
    makruhBand: Pair<Float, Float>? = null,
    makruhColor: Color = MaterialTheme.colorScheme.primary,
    accentAmber: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
```

und die Kanten-Farbe der Füllung — ersetze

```kotlin
    val edge = MaterialTheme.colorScheme.primary
```

durch

```kotlin
    val edge = if (accentAmber) makruhColor else MaterialTheme.colorScheme.primary
```

- [ ] **Step 4: Pill-Inhalt im selektierten Gebet um Karaha-Zeile erweitern**

Im selektierten `PrayerBlock`-Zweig wird `pillMakruh` als `band` gezeichnet. Berechne den Karaha-Zustand und gib ihn an Pill + Inhalt. Ersetze den `ProgressPill(`-Aufruf-Kopf

```kotlin
                                ProgressPill(
                                    fraction = frac,
                                    makruhBand = band,
                                    makruhColor = amber,
                                    onClick = pillMakruh?.let { mk -> { onKaraha(mk.explain) } },
                                ) {
```

durch

```kotlin
                                val karaha = pillMakruh?.let { KarahaCountdown.state(now, it.start, it.end) }
                                ProgressPill(
                                    fraction = frac,
                                    makruhBand = band,
                                    makruhColor = amber,
                                    accentAmber = karaha?.active == true,
                                    onClick = pillMakruh?.let { mk -> { onKaraha(mk.explain) } },
                                ) {
```

Und innerhalb des Pill-Inhalts, direkt nach der bestehenden „noch …"-Restzeit-Zeile (dem `if (showRemaining) { Text("noch ${durationLabel(remMin)}", …) }`-Block), die Karaha-Zeile ergänzen:

```kotlin
                                        karaha?.let { k ->
                                            Text(
                                                text = (if (k.active) "⛔ Karaha · " else "⚠ Karaha · ") + k.text,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = amber,
                                                modifier = Modifier.padding(top = 1.dp),
                                            )
                                        }
```

- [ ] **Step 5: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 6: Manuelle Notiz**

Im Report vermerken (nicht durch Implementer ausgeführt): manuell prüfen, dass kurz vor/während einer Karaha (z. B. İsfirar vor Maghrib) der laufende Pill eine amber Karaha-Zeile zeigt und der Chip einer Lücken-Karaha (İşrak/Zenit) „· in X Min"/„· noch X Min" anhängt.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "feat: Karaha-Aktivzustand mit amber Pill-Akzent und Countdown (Variante 3)"
```

---

### Task 7: Touch-Targets ≥48dp

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (`MakruhChipRow`, `NaflTipRow`)

- [ ] **Step 1: `minimumInteractiveComponentSize` auf den Makruh-Chip**

In `MakruhChipRow`: das tappbare innere `Row` (das `mod` mit `.clickable(...)`) erhält ein Mindest-Touch-Target. Ergänze in der `mod`-Kette nach dem `.clickable(...)` den Modifier `.minimumInteractiveComponentSize()`. Konkret ersetze

```kotlin
    var mod = Modifier
        .clickable(onClickLabel = "Erklärung anzeigen") { onKaraha(block.explain) }
```

durch

```kotlin
    var mod = Modifier
        .minimumInteractiveComponentSize()
        .clickable(onClickLabel = "Erklärung anzeigen") { onKaraha(block.explain) }
```

- [ ] **Step 2: `minimumInteractiveComponentSize` auf die Nafl-Tipp-Zeile**

In `NaflTipRow`: ersetze

```kotlin
    var mod = Modifier.padding(start = 12.dp)
    block.explain?.let { ex -> mod = mod.clickable(onClickLabel = "Erklärung anzeigen") { onInfo(ex) } }
```

durch

```kotlin
    var mod = Modifier.padding(start = 12.dp)
    block.explain?.let { ex ->
        mod = mod.minimumInteractiveComponentSize().clickable(onClickLabel = "Erklärung anzeigen") { onInfo(ex) }
    }
```

Importiere `androidx.compose.material3.minimumInteractiveComponentSize` in `MainActivity.kt`.

- [ ] **Step 3: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 4: Manuelle Notiz**

Im Report vermerken (nicht durch Implementer ausgeführt): manuell prüfen, dass Makruh-Chip und Nafl-Tipp bequem tappbar sind und die Timeline-Dichte akzeptabel bleibt.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "feat: 48dp Touch-Targets fuer tappbare Karaha/Nafl-Zeilen"
```

---

## Verifikation gesamt

- [ ] `.\gradlew.bat :app:testOfflineDebugUnitTest` — alle Unit-Tests grün (Hijri, durationLabel, KarahaCountdown, Motion + Phase-1-Tests)
- [ ] `.\gradlew.bat :app:assembleOfflineDebug` — Offline-Build erfolgreich
- [ ] Manuell: Karaha-Countdown (Pill amber + Chip-Suffix), Theme-Override-Farben korrekt, Chevrons sichtbar, Hijri „Dhul-Hijja", „Animationen aus" → Pill ohne Animation, Touch-Targets bequem
- [ ] Offline-Flavor weiterhin ohne `INTERNET`-Permission
