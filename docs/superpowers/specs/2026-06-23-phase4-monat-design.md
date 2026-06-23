# Phase 4 — Monat-Übersicht

**Datum:** 2026-06-23
**Status:** Entwurf zur Review
**Teil von:** Roadmap UI/UX; Phasen 1–3 erledigt.

## Ziel
Ein dritter Bottom-Nav-Tab **Monat** mit einer Tabellen-Übersicht der
Gebetszeiten für den gewählten Kalendermonat. Heutige Zeile hervorgehoben,
Monatswechsel per Chevron.

## Entscheidungen
- 5 Gebets-Spalten: **Fajr, Dhuhr, Asr, Maghrib, Isha** (ohne Sonnenaufgang) + Datum.
- **Kalendermonat** mit Vor/Zurück-Chevrons; heutige Zeile hervorgehoben.
- Datenquelle: `PrayerProvider.daily(...)` pro Tag (nutzt automatisch amtliche
  Diyanet-Tabelle bzw. Berechnung — wie Heute).
- Tab-Reihenfolge: **Heute · Monat · Qibla**.

## Nicht-Ziele
- Kein Export/Teilen, kein Jahres-/Wochenmodus, keine Sonnenaufgang-Spalte.

## Komponenten

### Navigation (`MainActivity.kt`)
- `enum class Tab` um `MONAT` erweitern (Reihenfolge HEUTE, MONAT, QIBLA).
- Dritter `NavigationBarItem` (Icon `ic_month`) zwischen Heute und Qibla.
- TopAppBar-Titel: `when (tab) { HEUTE -> city; MONAT -> "Monat"; QIBLA -> "Qibla" }`.

### `MonatScreen(inner, settings)` (neu, `ui/MonatScreen.kt`)
- Monats-State: `var month by rememberSaveable { mutableStateOf(YearMonth.now(zone)) }`.
- **Kopf:** Chevron-links · „Juni 2026" (deutscher Monatsname + Jahr, mittig) ·
  Chevron-rechts (`ic_chevron_left/right`).
- **Tabellenkopf:** leere Datums-Spalte + `Fajr Dhuhr Asr Maghrib Isha`
  (gedämpft, `labelSmall`).
- **Zeilen:** je Tag des Monats eine Zeile: Datum („Mo 1.") + 5 Zeiten (HH:mm).
  Heutige Zeile mit `primaryContainer`-Hintergrund hervorgehoben. Scrollbar
  (`LazyColumn`).
- **Daten:** `produceState(month, settings)` berechnet die Zeilen-Liste, indem es
  für jeden Tag `PrayerProvider.daily(context, settings, date, zone)` aufruft
  (auf `Dispatchers`-Coroutine des `produceState`); bis fertig eine schlichte
  „lädt…"-Anzeige.
- **Reiner Helfer** `monthTitle(ym: YearMonth): String` → „Juni 2026" (deutsch),
  unit-testbar.

### Icon
- `ic_month.xml` (Tabellen-/Listen-Symbol; deutlich von `ic_today` unterscheidbar).

## Layout (Skizze)
```
        Monat
   ‹   Juni 2026   ›
   ┌──────────────────────────────────┐
   │      Fajr Dhuhr Asr Magh. Isha    │
   │ Mo 1. 03:38 13:20 17:35 21:24 22:43│
   │ Di 2. 03:37 13:20 17:36 21:25 22:45│  ← heute hervorgehoben
   │ …                                 │
   └──────────────────────────────────┘
```
Kompakte Schrift (`labelMedium`/`bodySmall`), Datums-Spalte etwas breiter,
5 Zeit-Spalten gleich gewichtet.

## Tests
- **`monthTitle`** (rein): `YearMonth.of(2026,6)` → „Juni 2026"; `of(2026,1)` → „Januar 2026".
- Tabelle/Navigation: Build (`:app:compileOfflineDebugKotlin`,
  `:app:assembleOfflineDebug`) + manuell (Monat blättern, heutige Zeile
  hervorgehoben, Zeiten plausibel/identisch zu Heute).

## Auslieferung
Branch/Merge `feat/monat`. Tasks: Icon+Nav-Tab+Monats-Kopf → Tabellen-Daten/Zeilen.

## Folgephase
5. Settings-Sektionierung.
