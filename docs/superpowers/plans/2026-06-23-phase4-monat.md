# Phase 4 — Monat-Übersicht — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ein dritter Bottom-Nav-Tab „Monat" mit einer scrollbaren Kalendermonat-Tabelle (Datum + Fajr/Dhuhr/Asr/Maghrib/Isha), Monatswechsel per Chevron, heutige Zeile hervorgehoben.

**Architecture:** Reiner `monthTitle`-Helfer (getestet). `MainScreen` bekommt einen dritten Tab; `MonatScreen` hält den Monats-State, zeigt Kopf + Tabelle, und lädt die Zeilen per `produceState` über `PrayerProvider.daily` (eine Quelle wie Heute).

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`NavigationBar`, `LazyColumn`), java.time (`YearMonth`), JUnit4, Vektor-Drawable.

## Global Constraints
- Keine neue Abhängigkeit/Permission; Offline-Flavor netzfrei. Kotlin/JVM 17.
- 5 Gebets-Spalten (Fajr, Dhuhr, Asr, Maghrib, Isha) — KEIN Sonnenaufgang.
- Tab-Reihenfolge: HEUTE, MONAT, QIBLA.
- `PrayerProvider.daily` ist die einzige Datenquelle (amtlich/berechnet automatisch).
- Tests/Build nur über PowerShell (`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`), `gradle.properties` NICHT ändern. Compose per Build + manueller Sicht verifizieren (im Report als „nicht durch mich erfolgt" vermerken).

---

### Task 1: `ic_month` + `monthTitle` + Monat-Tab + Monatskopf

**Files:**
- Create: `app/src/main/res/drawable/ic_month.xml`
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/MonthTitle.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/MonatScreen.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (`Tab`-Enum, `MainScreen`)
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/MonthTitleTest.kt`

**Interfaces:**
- Produces: `de.gebetszeiten.prayer.monthTitle(ym: YearMonth): String` („Juni 2026"); `MonatScreen(inner: PaddingValues, settings: AppSettings)` (in Task 1 nur Kopf + Platzhalter).

- [ ] **Step 1: Failing test für `monthTitle`**

`app/src/test/kotlin/de/gebetszeiten/prayer/MonthTitleTest.kt`:

```kotlin
package de.gebetszeiten.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthTitleTest {
    @Test fun germanMonthAndYear() {
        assertEquals("Juni 2026", monthTitle(YearMonth.of(2026, 6)))
        assertEquals("Januar 2026", monthTitle(YearMonth.of(2026, 1)))
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.MonthTitleTest"`
Expected: FAIL (`monthTitle` nicht gefunden).

- [ ] **Step 3: `MonthTitle.kt` implementieren**

```kotlin
package de.gebetszeiten.prayer

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Deutscher Monatsname + Jahr, z. B. "Juni 2026". */
fun monthTitle(ym: YearMonth): String =
    "${ym.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${ym.year}"
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `.\gradlew.bat :app:testOfflineDebugUnitTest --tests "de.gebetszeiten.prayer.MonthTitleTest"`
Expected: PASS.

- [ ] **Step 5: `ic_month.xml` anlegen**

`app/src/main/res/drawable/ic_month.xml` (Tabellen-/Listen-Symbol):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M3,5v14h18V5H3zM9,7v3H5V7h4zM5,12h4v3H5v-3zM5,17h4v-1h-4zM19,17h-8v-3h8v3zM19,12h-8V9h8v3zM19,7v0h-8V7h8z" />
</vector>
```

(Falls dieses Pfad-Set beim Build Probleme macht, ein einfacheres gültiges 24dp-Listen-/Kalender-Icon verwenden — Hauptsache es linkt und unterscheidet sich von `ic_today`.)

- [ ] **Step 6: `MonatScreen.kt` (Kopf + Platzhalter) anlegen**

```kotlin
package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.monthTitle
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun MonatScreen(inner: PaddingValues, settings: AppSettings) {
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now(zone)) }

    Column(modifier = Modifier.padding(inner).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), "Vorheriger Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), "Nächster Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Tabelle folgt in Task 2.
        Text(
            "…",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 7: `MainScreen` um den Monat-Tab erweitern**

In `MainActivity.kt`:
- `private enum class Tab { HEUTE, QIBLA }` → `private enum class Tab { HEUTE, MONAT, QIBLA }`.
- TopAppBar-Titel — ersetze `Text(if (tab == Tab.HEUTE) settings.city else "Qibla")` durch:
  ```kotlin
  Text(when (tab) { Tab.HEUTE -> settings.city; Tab.MONAT -> "Monat"; Tab.QIBLA -> "Qibla" })
  ```
- In der `NavigationBar` zwischen dem Heute- und dem Qibla-`NavigationBarItem` einfügen:
  ```kotlin
  NavigationBarItem(
      selected = tab == Tab.MONAT,
      onClick = { tab = Tab.MONAT },
      icon = { Icon(painterResource(R.drawable.ic_month), contentDescription = null) },
      label = { Text("Monat") },
  )
  ```
- Im `when (tab)`-Body den Zweig ergänzen:
  ```kotlin
  Tab.MONAT -> MonatScreen(inner, settings)
  ```

- [ ] **Step 8: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/drawable/ic_month.xml app/src/main/kotlin/de/gebetszeiten/prayer/MonthTitle.kt app/src/main/kotlin/de/gebetszeiten/ui/MonatScreen.kt app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt app/src/test/kotlin/de/gebetszeiten/prayer/MonthTitleTest.kt
git commit -m "feat: Monat-Tab + Monatskopf (monthTitle, Chevron-Navigation)"
```

---

### Task 2: Monats-Tabelle (Daten + Zeilen + Heute-Hervorhebung)

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MonatScreen.kt`

**Interfaces:**
- Consumes: `PrayerProvider.daily` (eine Quelle), `monthTitle` (Task 1).

- [ ] **Step 1: `MonatScreen` um die Tabelle erweitern**

Ersetze den `MonatScreen.kt`-Inhalt durch (Kopf bleibt, Platzhalter → Tabelle):

```kotlin
package de.gebetszeiten.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.monthTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val DATE_WEIGHT = 1.5f

private data class MonatRow(val date: LocalDate, val times: List<String>)

@Composable
internal fun MonatScreen(inner: PaddingValues, settings: AppSettings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now(zone)) }
    val today = LocalDate.now(zone)

    val rows by produceState<List<MonatRow>?>(null, month, settings) {
        value = null
        value = (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val t = PrayerProvider.daily(context, settings, date, zone)
            MonatRow(date, listOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha).map { it.format(HM_MONTH) })
        }
    }

    Column(modifier = Modifier.padding(inner).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), "Vorheriger Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), "Nächster Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Tabellenkopf
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("", Modifier.weight(DATE_WEIGHT))
            listOf("Fajr", "Dhuhr", "Asr", "Magh.", "Isha").forEach {
                Text(
                    it, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val list = rows
        if (list == null) {
            Text("lädt…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list) { row ->
                    val isToday = row.date == today
                    val bg = if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
                    Row(
                        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dateLabel(row.date),
                            Modifier.weight(DATE_WEIGHT),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        row.times.forEach { time ->
                            Text(
                                time, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Mo 1." — Wochentag-Kürzel + Tag. */
private fun dateLabel(date: LocalDate): String {
    val wd = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN)
    return "$wd ${date.dayOfMonth}."
}
```

- [ ] **Step 2: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 3: Manuelle Notiz**

Im Report (NICHT durch Implementer): Monat blättern (Chevrons), heutige Zeile hervorgehoben, Zeiten identisch zu Heute für denselben Tag, „lädt…" kurz beim Monatswechsel.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MonatScreen.kt
git commit -m "feat: Monats-Tabelle (5 Gebete, Heute hervorgehoben, scrollbar)"
```

---

## Verifikation gesamt
- [ ] `.\gradlew.bat :app:testOfflineDebugUnitTest` — alle Unit-Tests grün (inkl. `monthTitle`)
- [ ] `.\gradlew.bat :app:assembleOfflineDebug` — Offline-Build erfolgreich
- [ ] Manuell: 3 Tabs (Heute/Monat/Qibla); Monat blättern, heutige Zeile hervorgehoben, Zeiten plausibel
- [ ] Offline-Flavor weiterhin ohne `INTERNET`-Permission
