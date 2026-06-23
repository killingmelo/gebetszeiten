# Phase 3 — Bottom-Navigation + Qibla-Kompass

**Datum:** 2026-06-23
**Status:** Entwurf zur Review
**Teil von:** Roadmap „UI/UX/Design/Funktionalität verbessern"; Phasen 1+2 erledigt.

## Ziel

1. **Bottom-Navigation** mit zwei Tabs: **Heute** (der bestehende Gebetszeiten-
   Screen, unverändert im Inhalt) und **Qibla** (neuer Kompass-Screen). Der
   Monat-Tab folgt in Phase 4.
2. **Qibla-Kompass:** zeigt die Gebetsrichtung (zur Kaaba) als Live-Kompass, der
   sich mit dem Gerät dreht; auf Geräten ohne Magnetometer Fallback auf eine
   statische Gradzahl. Plus Gradzahl, Himmelsrichtung und Luftlinie zur Kaaba.

## Entscheidungen (festgezurrt)
- 2 Tabs (Heute + Qibla); Monat in Phase 4.
- Live-Kompass + Fallback bei fehlendem Sensor.
- Anzeige: Pfeil + „119° · Südost" + „Kaaba · 3.012 km".
- **Keine neue Navigations-Abhängigkeit:** kein `androidx.navigation`; einfache
  `NavigationBar` + Tab-State (`rememberSaveable`). Passt zur Lightweight-Linie.
- **Kein GPS:** Qibla wird aus den manuell gesetzten `settings.latitude/longitude`
  berechnet (wie die Gebetszeiten). Der Kompass nutzt nur Lage-/Magnetsensoren
  (keine Laufzeit-Berechtigung).

## Nicht-Ziele (YAGNI)
- Keine Monat-Übersicht (Phase 4), keine Standort-Automatik/GPS.
- Keine Kalibrier-Animation/AR; schlichter Kompass.

---

## 1. Navigations-Gerüst (Refactor)

**Heute** ist aktuell `PrayerScreen`, das ein eigenes `Scaffold` mit `TopAppBar`
(Stadt + Einstellungen-Icon), die `ModalBottomSheet`-Einstellungen und den
Karaha-`AlertDialog` besitzt. Für die Navigation wird ein gemeinsames Gerüst
eingezogen:

```
MainScreen(viewModel):
  Scaffold(
    topBar = TopAppBar(
       title = when(tab) { HEUTE -> settings.city; QIBLA -> "Qibla" },
       actions = { IconButton(ic_tune) → showSettings = true }   // geteilt
    ),
    bottomBar = NavigationBar {
       NavigationBarItem(Heute,  ic_today,  selected = tab==HEUTE)
       NavigationBarItem(Qibla,  ic_qibla,  selected = tab==QIBLA)
    },
  ) { inner ->
    when (tab) {
      HEUTE -> HeuteContent(inner, viewModel)   // bisheriger PrayerScreen-Body
      QIBLA -> QiblaScreen(inner, settings)
    }
  }
  if (showSettings) ModalBottomSheet { LocationSettings(...) }   // geteilt, hochgezogen
```

- **`HeuteContent`** = der bisherige `PrayerScreen`-Inhalt **ohne** Scaffold/
  TopAppBar/Settings-Sheet: die scrollbare `Column` mit `DateNavigator`,
  `TimesCard`, Footer — plus der Karaha-`AlertDialog` (bleibt Heute-spezifisch)
  und der Minuten-`tick`, `selectedDate`, `dayInfo`, `officialSource`-Logik.
  Das `inner`-Padding kommt von `MainScreen`.
- **Tab-State:** `var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }` mit
  `enum class Tab { HEUTE, QIBLA }`.
- **Einstellungen** bleiben über alle Tabs erreichbar (Icon im geteilten
  TopAppBar); `showSettings` + das Sheet wandern nach `MainScreen`.
- `MainActivity.onCreate` ruft künftig `MainScreen(viewModel)` statt
  `PrayerScreen(viewModel)`.

**Risiko/Sorgfalt:** Dies ist der einzige nennenswerte Refactor. Verhalten von
Heute bleibt identisch; nur die Hülle ändert sich. Verifikation per Build +
manueller Sicht (Heute unverändert, Tab-Wechsel funktioniert).

---

## 2. Qibla-Mathematik (rein, testbar)

Neue Datei `prayer/QiblaMath.kt`, Kaaba = `21.4225 N, 39.8262 O`.

```kotlin
object QiblaMath {
    // Initiale Großkreis-Peilung von (lat,lng) zur Kaaba, normalisiert 0..360.
    fun bearing(lat: Double, lng: Double): Double
    // Luftlinie (Haversine) in km zur Kaaba.
    fun distanceKm(lat: Double, lng: Double): Double
    // 8-Wind-Himmelsrichtung eines Winkels: "Nord","Nordost",…,"Nordwest".
    fun cardinal(bearing: Double): String
    // Winkel auf 0..360 normalisieren (auch negative / >360).
    fun normalizeDegrees(deg: Float): Float
}
```

**Tests (deterministische Anker):**
- `bearing(90.0, 0.0)` ≈ 180° (vom Nordpol ist jede Richtung Süden).
- `bearing(50.0, 39.8262)` ≈ 180° (direkt nördlich der Kaaba auf demselben
  Meridian → genau Süd).
- Plausibilität Nürnberg `bearing(49.4521, 11.0767)` im Bereich 125°–140°;
  `cardinal(...)` = „Südost".
- `distanceKm(21.4225, 39.8262)` ≈ 0; Nürnberg im Bereich 3800–4200 km (≈4013).
- `cardinal(0.0)`=„Nord", `cardinal(180.0)`=„Süd", `cardinal(135.0)`=„Südost".
- `normalizeDegrees(-10f)`=350f, `normalizeDegrees(370f)`=10f.

---

## 3. Kompass-Sensor (Compose)

Neue Datei `ui/Compass.kt`:

```kotlin
// Geräte-Azimut (Grad von Norden, 0..360) oder null, wenn kein Sensor verfügbar.
@Composable fun rememberDeviceAzimuth(): Float?
```

- Bevorzugt `Sensor.TYPE_ROTATION_VECTOR`; fehlt er, kein Live-Kompass → `null`
  (→ Fallback-UI). (Beschleuniger+Magnetometer-Kombination ist optional; für die
  Lightweight-Variante genügt ROTATION_VECTOR, sonst Fallback.)
- Registrierung/Deregistrierung an den Compose-Lifecycle gebunden
  (`DisposableEffect` + `repeatOnLifecycle`/`onResume`), `SENSOR_DELAY_UI`.
- `SensorManager.getRotationMatrixFromVector` → `remapCoordinateSystem` nach
  Display-Rotation → `getOrientation` → Azimut; via `normalizeDegrees` auf 0..360.
- **Glättung:** leichter Tiefpass (z. B. exponentiell, α≈0.15) gegen Zittern;
  Winkel-Wraparound (359°→1°) korrekt behandeln.
- Sensorcode ist nicht unit-testbar; nur `normalizeDegrees` (in QiblaMath) ist
  getestet. Verifikation des Kompass per Build + manuell.

---

## 4. Qibla-Screen (Compose)

Neue Datei `ui/QiblaScreen.kt`. Schlicht, ruhiges Grün, Lightweight-Ästhetik:

```
            Qibla
        ┌───────────┐
        │     N      │
        │      ╲     │     Kompassrose (N/O/S/W-Ticks, gedämpft),
        │   W  ◉──►O │     Pfeil (Primär-Grün) zeigt zur Kaaba:
        │      S     │     Winkel = bearing − azimuth (Live) bzw.
        └───────────┘      = bearing (Fallback, Rose fix nach Norden)
          119° · Südost
        Kaaba · 3.012 km
```

- **Festgelegte Variante:** Die **gesamte** Kompass-Zeichnung (Rose + N/O/S/W-
  Ticks + Qibla-Pfeil) wird um `-azimuth` rotiert. Innerhalb dieser Rotation
  liegt N oben und der Qibla-Pfeil bei `bearing`. Effekt: dreht man das Gerät,
  hält die Rose N auf geografisch Nord und der Pfeil zeigt stabil zur Kaaba.
- **Fallback (kein Sensor):** `azimuth = 0` → Rose fix (Bildschirm-oben = Nord),
  Pfeil auf `bearing`, zusätzlicher Hinweis „Kompass nicht verfügbar — Qibla
  liegt bei 119° Südost".
- Unter der Rose: `"${bearing.roundToInt()}° · ${cardinal}"` und
  `"Kaaba · ${distanceKm-formatiert} km"` (Tausenderpunkt, gerundet).
- Zeichnung via `Canvas` (Rose, Ticks, Pfeil) + Rotation per `Modifier`/Canvas-
  Transformation; Pfeilrotation sanft animiert (respektiert
  `rememberAnimationsEnabled()` aus Phase 2).
- Accessibility: `contentDescription` z. B. „Qibla 119 Grad Südost, Kaaba 3012
  Kilometer" (sensorunabhängig, immer gesetzt).

---

## 5. Icons

Zwei neue Vektor-Drawables für die `NavigationBarItem`s (wie `ic_tune`):
- `ic_today.xml` — Kalender-/Heute-Symbol (Heute-Tab).
- `ic_qibla.xml` — Kompass-/Navigations-Symbol (Qibla-Tab).

---

## Tests

Reine JVM-Unit-Tests:
1. **`QiblaMath`** — `bearing`/`distanceKm`/`cardinal`/`normalizeDegrees` mit den
   Ankern aus §2 (Toleranz für Peilung/Distanz, exakt für `cardinal`/`normalize`).

Compose/Sensor (Navigation, Kompass, Screen): Build (`:app:compileOfflineDebugKotlin`,
`:app:assembleOfflineDebug`) + manuelle Sicht:
- Tab-Wechsel Heute↔Qibla; Heute inhaltlich unverändert; Einstellungen aus
  beiden Tabs erreichbar.
- Qibla: Pfeil dreht plausibel mit dem Gerät; Gradzahl/Richtung/Distanz korrekt
  für die gesetzte Stadt; auf einem Emulator ohne Magnetometer erscheint der
  Fallback.

## Auslieferung
Eigenständiger Branch/Merge `feat/nav-qibla`. Tasks: QiblaMath → Icons →
Navigations-Refactor → Kompass-Sensor → Qibla-Screen.

## Offene Folgephasen
4. Monat-Übersicht (fügt den dritten Tab hinzu) · 5. Settings-Sektionierung.
