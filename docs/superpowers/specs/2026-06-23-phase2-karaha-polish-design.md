# Phase 2 — Karaha-Countdown + Polish/Bugfix

**Datum:** 2026-06-23
**Status:** Entwurf zur Review
**Teil von:** Roadmap „UI/UX/Design/Funktionalität verbessern" (Phasen 1–5); Phase 1 erledigt.

## Ziel

Sichtbare UX-Gewinne auf dem bestehenden „Heute"-Screen, ohne die geliebte
Pill-Timeline umzubauen:

1. **Karaha-Countdown (Variante 3):** Der laufende Pill färbt sich amber und
   zeigt einen Countdown, während eine Makruh-(Karaha-)Zeit aktiv ist; Karaha
   in Lücken ohne laufenden Pill behält ihren Chip, bekommt aber denselben
   Countdown-Text.
2. **Bugfix Theme-Farben:** `TimesCard` nutzt `isSystemInDarkTheme()` statt des
   effektiven Theme-Zustands → bei manuellem Hell/Dunkel-Override falsche
   amber/green-Akzente. Wird über ein `LocalIsDark`-CompositionLocal behoben.
3. **Polish:** Chevron-Icons in der Datumsnavigation, Touch-Targets ≥48dp für
   tappbare Karaha/Nafl-Zeilen, `prefers-reduced-motion` respektieren, Hijri-
   Monatsnamen in deutscher/internationaler Transliteration.

## Nicht-Ziele (YAGNI)

- Keine Bottom-Navigation, kein Qibla, keine Monatsansicht (Phasen 3–4).
- Kein globales Karaha-Warnbanner (Variante 2 wurde nicht gewählt).
- Keine Änderung an der Zeitberechnung oder den Datenquellen (Phase 1).

---

## 1. Karaha-Countdown (Variante 3)

### Hintergrund (heutiger Stand)
- Eine Makruh-Zeit `K = [start, end]` wird je nach Lage dargestellt:
  - **(A) innerhalb des laufenden Gebets** (`pillMakruh`): als geriffeltes
    amber Band im Verlaufs-Pill (`ProgressPill.makruhBand`). Statisch.
  - **(B) als Chip** an einem Gebet/Duha (`MakruhChipRow`): İşrak über Duha,
    Zenit über Dhuhr, İsfirar über Maghrib. Füllt sich amber, wenn `now`
    gerade darin liegt (`isMakruhNow`), zeigt aber **keinen** Countdown.

### Neues Verhalten
Ein gemeinsamer, **reiner** Helper liefert den Countdown-Text für eine
Karaha-Zeit relativ zu `now`:

```kotlin
// de.gebetszeiten.prayer.KarahaCountdown
// null  → nichts anzeigen
// "in 18 Min"   wenn now in [start-LEAD, start)   (bevorstehend, LEAD=60 Min)
// "noch 12 Min" wenn now in [start, end)          (aktiv)
fun karahaCountdown(now, start, end, leadMinutes = 60): String?
```

- **(A) laufender Pill:** wenn das `pillMakruh`-Band **aktiv** ist
  (`start ≤ now < end`):
  - Der Pill-Akzent (Rand/Füllkante) wechselt von Primär-Grün auf **amber**.
  - Die Restzeit-Zeile zeigt zusätzlich `⛔ Karaha · noch {dauer}` (amber),
    unter der bestehenden „noch X Min"-Zeile (Zeit bis nächstes Gebet bleibt
    sichtbar — beide Infos sind relevant).
  - Ist das Band nur **bevorstehend** (`now < start`, innerhalb LEAD): eine
    dezente amber Zeile `⚠ Karaha in {dauer}`.
- **(B) Chip (`MakruhChipRow`):** der bestehende Chip bekommt den Countdown
  angehängt:
  - bevorstehend: `Makruh · {label} · {start}–{end} · in {dauer}`
  - aktiv (bereits amber gefüllt): `… · noch {dauer}`
- Vergangene/ferne Karaha (kein Countdown im Fenster): unverändert.

Nur relevant, wenn `highlight` (heute) aktiv ist; für andere Tage kein
Countdown.

### Betroffene Stellen
`MainActivity.kt`: `TimesCard` (berechnet `pillMakruh` + Aktiv-Status),
`ProgressPill` (amber Akzent-Parameter), die Pill-Inhalts-Composable
(Countdown-Zeile), `MakruhChipRow` (Countdown-Suffix). Neuer Helper
`KarahaCountdown.kt` + Test.

---

## 2. Bugfix: effektiver Dark-Zustand

`GebetszeitenTheme` kennt den effektiven Dark-Zustand bereits (Parameter
`darkTheme`, berechnet in `MainActivity.onCreate` aus `settings.themeMode`).
`TimesCard` liest aber `isSystemInDarkTheme()` (Zeile ~383) für die amber/green-
Akzentberechnung → bei „Hell" auf dunklem System (oder „Dunkel" auf hellem
System) falsche Farben.

**Fix:** Analog zu `LocalHighContrast` ein `LocalIsDark` in `Theme.kt`
bereitstellen:

```kotlin
val LocalIsDark = staticCompositionLocalOf { false }
// in GebetszeitenTheme: CompositionLocalProvider(LocalIsDark provides darkTheme) { ... }
```

`TimesCard` ersetzt `isSystemInDarkTheme()` durch `LocalIsDark.current`. Keine
weitere Stelle nutzt `isSystemInDarkTheme()` für Farbentscheidungen (verifiziert
in `MainActivity`; `inDuha`-Logik etc. nutzt es nicht).

---

## 3. Polish

### 3a. Chevron-Icons in der Datumsnavigation
`DateNavigator` nutzt die Text-Glyphen `‹` / `›` in `headlineMedium`. Ersetzen
durch zwei neue Vektor-Drawables `ic_chevron_left.xml` / `ic_chevron_right.xml`
(24dp, Standard-Material-Pfade) via `painterResource` — konsistent mit
`ic_tune` und ohne neue Abhängigkeit. `contentDescription` bleibt
(„Vorheriger/Nächster Tag").

### 3b. Touch-Targets ≥48dp
Tappbare Elemente, die heute < 48dp hoch sind und einen Dialog öffnen
(`MakruhChipRow`, `NaflTipRow`), bekommen via Compose-Material3
`Modifier.minimumInteractiveComponentSize()` ein 48dp-Mindest-Touch-Target —
das visuelle Layout bleibt kompakt (Inhalt wird im 48dp-Slot zentriert), nur
die Trefferfläche wächst.
**⚠ Bitte bestätigen:** Das erhöht die vertikale Höhe dieser Zeilen leicht;
falls die dichte Optik Vorrang hat, alternativ nur den Klick-Bereich ohne
reservierte Höhe vergrößern. Default: `minimumInteractiveComponentSize()`.

### 3c. prefers-reduced-motion
`ProgressPill` animiert die Füllung mit `tween(650)`. Bei aktivierter System-
Einstellung „Animationen aus" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`)
soll der Wert ohne Animation gesetzt werden (`snap`). Umsetzung über einen
kleinen `@Composable rememberAnimationsEnabled(): Boolean`, der den globalen
Scale liest; `animationSpec` = `snap()` wenn deaktiviert, sonst `tween(650)`.

### 3d. Hijri-Monatsnamen (Transliteration)
`HijriText.HIJRI_MONTHS` von türkisch auf eine deutsch/internationale
Transliteration umstellen (betrifft Hauptscreen **und** Widget-Header über
`hijriTextShort`). Vorgeschlagener Satz (**bitte in Review bestätigen**):

```
Muharram, Safar, Rabi al-awwal, Rabi al-thani, Jumada al-awwal,
Jumada al-thani, Rajab, Schaban, Ramadan, Schawwal, Dhul-Qada, Dhul-Hijja
```

(Konsistent mit dem vom Nutzer genannten „Dhul-Hijja"; „Ramadan" statt
türk. „Ramazan". Falls eine andere Konvention gewünscht ist — z. B.
durchgängig „Sch"/„Dsch" oder englisch „Sha'ban" — hier festlegen.)

---

## Tests

Reine JVM-Unit-Tests im app-Modul (Compose-UI wird per Build + manuell
verifiziert):

1. **`karahaCountdown`** — bevorstehend (`in X`), aktiv (`noch X`), außerhalb
   (`null`), Grenzfälle (genau `start`, genau `end`), LEAD-Fenster.
2. **Hijri-Monate** — `hijriText`/`hijriTextShort` liefern den neuen
   Monatsnamen für je ein bekanntes Datum (z. B. ein Datum in Dhul-Hijja 1447 →
   „… Dhul-Hijja 1447"), und das Array hat genau 12 Einträge.
3. **`rememberAnimationsEnabled`-Kernlogik** — falls als reine Funktion
   `animationsEnabled(scale: Float): Boolean` extrahierbar: `0f → false`,
   `1f → true`.

Manuelle Verifikation (Gerät): Karaha-Countdown im Pill (amber) und im Chip;
Theme-Override Hell/Dunkel zeigt korrekte amber/green-Akzente; Chevrons sichtbar;
Hijri-Monat korrekt; bei „Animationen aus" füllt der Pill ohne Animation.

## Auslieferung

Eigenständiger Branch/Merge `feat/karaha-polish`. Jede der sechs Arbeitseinheiten
ist ein eigener Commit mit (wo möglich) Test.

## Offene Folgephasen
3. Bottom-Nav + Qibla · 4. Monat-Übersicht · 5. Settings-Sektionierung.
