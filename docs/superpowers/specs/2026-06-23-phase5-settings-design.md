# Phase 5 — Settings-Sektionierung

**Datum:** 2026-06-23
**Status:** Entwurf zur Review
**Teil von:** Roadmap UI/UX; Phasen 1–4 erledigt. Letzte Phase.

## Ziel
Den langen, flachen `LocationSettings`-Scroll in **optisch getrennte Sektions-
Cards** gliedern, damit er schneller scanbar ist. **Reiner Layout-Umbau — kein
Verhaltenswechsel:** jede Einstellung, jeder `commit{}`-Handler und jeder
Hinweistext bleibt erhalten, nur die visuelle Gruppierung ändert sich.

## Entscheidungen
- Gruppierung in vier Sektions-Cards (+ die bestehende Battery-Card):
  1. **Ort** — Stadt-Dropdown, Breite/Länge, „Ort übernehmen".
  2. **Anzeige** — „Makruh-Zeiten anzeigen" (+Hinweis); „Restzeit"-Unterblock
     (In der App / Widget / Sperrbildschirm + Hinweise + Wear-Hinweis +
     „Offizielle Diyanet-Zeiten (online)" sofern Online-Flavor); Schriftgröße;
     Hoher Kontrast.
  3. **Erinnerungen** — pro-Gebet-Schalter, Stil, Vorlauf, „Dauerhafte Anzeige".
  4. **Darstellung** — Design (Theme), Hijri-Korrektur, „Widget-Hintergrund
     transparent".
- **Reihenfolge der Einstellungen bleibt** wie heute (nur Card-Grenzen werden
  eingezogen) — minimiert Risiko, dass etwas verloren geht.
- Card-Stil: schlichte `Card` mit Sektionstitel oben (`titleMedium`), Inhalt als
  `Column` mit dem bisherigen `spacedBy(12.dp)`. Ruhiges Grün-Theme, kein
  Schnickschnack.
- Die zwei bisherigen Inline-Überschriften „Erinnerungen" und „Darstellung"
  werden zu Card-Titeln (die redundanten Inline-`Text` entfallen). Die
  „Restzeit"-Überschrift bleibt als **Unter**überschrift in der Anzeige-Card.
- Intro bleibt oben über den Cards: „Einstellungen" + „Änderungen werden sofort
  übernommen."

## Nicht-Ziele
- Keine neuen Einstellungen, keine geänderten Defaults/Handler, keine
  Umbenennung von Optionen, kein Suchfeld.

## Komponente
Neuer Helfer in `MainActivity.kt`:
```kotlin
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
```
`LocationSettings` wickelt seine vier Gruppen in `SettingsSection(...) { … }`;
die äußere `Column` behält Scroll + Padding, Abstand zwischen den Cards via
`spacedBy`.

## Tests
Kein reiner Logik-Anteil → keine Unit-Tests. Verifikation:
- Build (`:app:compileOfflineDebugKotlin`, `:app:assembleOfflineDebug`).
- Review-Vollständigkeitscheck: **jede** Steuerung (Dropdown, beide Koordinaten-
  Felder, Übernehmen-Button, alle `ToggleRow`s, `CountdownModeSelector` ×2,
  `FontSizeSelector`, alle `FilterChip`-Gruppen, `BatteryOptimizationCard`) und
  **jeder** `commit{}`-Handler/Hinweis ist unverändert vorhanden, nur neu
  gruppiert.
- Manuell: Einstellungen öffnen, alle Optionen vorhanden und funktionsfähig
  (sofort-übernehmen wie bisher), Cards sauber getrennt.

## Auslieferung
Branch/Merge `feat/settings-sections`. Eine Aufgabe (atomarer Wrap-Refactor).

## Abschluss
Letzte Phase der Roadmap — danach sind alle 5 Phasen umgesetzt.
