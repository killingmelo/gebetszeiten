# Phase 5 — Settings-Sektionierung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Den flachen `LocationSettings`-Scroll in vier optisch getrennte Sektions-Cards (Ort, Anzeige, Erinnerungen, Darstellung) + die bestehende Battery-Card gliedern — ohne jede Verhaltensänderung.

**Architecture:** Ein neuer `SettingsSection(title, content)`-Card-Helfer; `LocationSettings` wickelt seine bestehenden Inhaltsgruppen darin ein. Jede Steuerung, jeder `commit{}`-Handler und jeder Hinweistext bleibt 1:1 erhalten; nur Card-Grenzen + zwei zu Card-Titeln gewordene Überschriften ändern sich.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`Card`).

## Global Constraints
- **Kein Verhaltenswechsel:** keine neue/entfernte/umbenannte Einstellung, keine geänderten Handler/Defaults. Reiner Layout-Wrap.
- Reihenfolge der Einstellungen bleibt wie heute.
- Kotlin/JVM 17; offline network-free; keine neue Abhängigkeit.
- Nicht unit-testbar (Layout) → Verifikation per Build + Vollständigkeits-Review. Gradle nur über PowerShell (`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`); `gradle.properties` NICHT ändern.

---

### Task 1: `SettingsSection`-Card + `LocationSettings` sektionieren

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt`

- [ ] **Step 1: `SettingsSection`-Helfer hinzufügen**

Direkt vor `LocationSettings` einfügen:

```kotlin
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            },
        )
    }
}
```

Importe sicherstellen (i. d. R. bereits vorhanden): `androidx.compose.foundation.layout.ColumnScope` ggf. ergänzen.

- [ ] **Step 2: `LocationSettings`-Body sektionieren**

Die äußere `Column` (Scroll + Padding + `spacedBy(12.dp)`) und der Intro-Block bleiben:

```kotlin
        Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
        Text(
            "Änderungen werden sofort übernommen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
```

Danach werden die bestehenden Inhaltsgruppen in vier `SettingsSection(...)`-Blöcke verschoben — **Inhalt jeder Gruppe wörtlich übernehmen** (jede Steuerung, jeder `commit { … }`-Handler, jeder Hinweis-`Text`), nur die Wrapper ändern sich:

1. `SettingsSection("Ort") { … }` enthält:
   - den `ExposedDropdownMenuBox`-Stadt-Block (unverändert, inkl. `commit { copy(city = …, latitude = …, longitude = …) }`),
   - die `Row` mit den beiden `OutlinedTextField` Breite/Länge,
   - den `if (locationDirty) { Button(…){ Text("Ort übernehmen") } }`-Block.

2. `SettingsSection("Anzeige") { … }` enthält (in dieser Reihenfolge, alles unverändert):
   - `ToggleRow("Makruh-Zeiten anzeigen", …)` + den `if (settings.showKaraha) { Text(…) }`-Hinweis,
   - die bestehende **Unter**überschrift `Text("Restzeit", style = titleMedium, …)` + ihren Beschreibungs-`Text`,
   - `ToggleRow("In der App", …)`, `CountdownModeSelector("Widget", …)`, `CountdownModeSelector("Sperrbildschirm", …)`,
   - den `if (!settings.persistentNotification) { Text(…) }`-Hinweis,
   - den Wear-OS-`Text`-Hinweis,
   - den `if (OfficialTimesProvider.isOnline) { ToggleRow("Offizielle Diyanet-Zeiten (online)", …) }`-Block,
   - `FontSizeSelector(settings.fontScale) { … }`,
   - `ToggleRow("Hoher Kontrast", …)`.

3. `SettingsSection("Erinnerungen") { … }` enthält (die bisherige Inline-Überschrift `Text("Erinnerungen", titleMedium)` ENTFÄLLT — der Card-Titel ersetzt sie; der Beschreibungs-`Text` „Stille Benachrichtigung …" bleibt):
   - der Beschreibungs-`Text`,
   - die `listOf(Prayer.FAJR, …).forEach { ToggleRow(…) }`-Schleife,
   - `Text("Stil", bodyLarge)` + die Stil-`FilterChip`-`Row`,
   - `Text("Vorlauf", bodyLarge)` + dessen Beschreibungs-`Text` + die Vorlauf-`FilterChip`-`Row`,
   - `ToggleRow("Dauerhafte Anzeige (Sperrbildschirm)", …)` + dessen Beschreibungs-`Text`.

4. `SettingsSection("Darstellung") { … }` enthält (die bisherige Inline-Überschrift `Text("Darstellung", titleMedium)` ENTFÄLLT — Card-Titel ersetzt sie):
   - `Text("Design", bodyLarge)` + die Theme-`FilterChip`-`Row` + den „Dunkel = AMOLED…"-`Text`,
   - `Text("Hijri-Korrektur", bodyLarge)` + die Hijri-`FilterChip`-`Row` + den „Verschiebt das Hijri-Datum…"-`Text`,
   - `ToggleRow("Widget-Hintergrund transparent", …)`.

Nach den vier Sektionen bleibt `BatteryOptimizationCard()` als letztes (eigene Card, unverändert).

**Wichtig:** Es darf KEINE Steuerung und KEIN `commit{}`-Handler entfallen oder geändert werden. Nur: 4 Wrapper hinzugefügt, 2 redundante Inline-Überschriften („Erinnerungen", „Darstellung") entfernt.

- [ ] **Step 3: Kompilieren + bauen**

Run: `.\gradlew.bat :app:compileOfflineDebugKotlin` → BUILD SUCCESSFUL
Run: `.\gradlew.bat :app:assembleOfflineDebug` → BUILD SUCCESSFUL

- [ ] **Step 4: Selbst-Vollständigkeitscheck (im Report dokumentieren)**

Im Report auflisten, dass alle folgenden Steuerungen weiterhin vorhanden sind und ihren unveränderten Handler haben: Stadt-Dropdown (+Picker-commit), Breite-Feld, Länge-Feld, „Ort übernehmen"-Button, „Makruh-Zeiten anzeigen", „In der App", „Widget" (CountdownModeSelector), „Sperrbildschirm" (CountdownModeSelector), „Offizielle Diyanet-Zeiten (online)" (nur online-Flavor), Schriftgröße, „Hoher Kontrast", 5× Gebets-`ToggleRow`, Stil-Chips (3), Vorlauf-Chips (5), „Dauerhafte Anzeige", Theme-Chips (3), Hijri-Chips (5), „Widget-Hintergrund transparent", `BatteryOptimizationCard`. Plus: alle Hinweistexte erhalten; nur die 2 Inline-Überschriften zu Card-Titeln geworden.

- [ ] **Step 5: Manuelle Notiz**

Im Report (NICHT durch Implementer): Einstellungen-Sheet öffnen — vier getrennte Cards (Ort/Anzeige/Erinnerungen/Darstellung) + Akku-Card; alle Optionen vorhanden und wenden weiterhin sofort an.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt
git commit -m "feat: Einstellungen in Sektions-Cards gegliedert (Ort/Anzeige/Erinnerungen/Darstellung)"
```

---

## Verifikation gesamt
- [ ] `.\gradlew.bat :app:testOfflineDebugUnitTest` — bestehende Tests grün (Phase 5 fügt keine hinzu)
- [ ] `.\gradlew.bat :app:assembleOfflineDebug` — Offline-Build erfolgreich
- [ ] Vollständigkeit: jede Einstellung + jeder Handler erhalten (Review)
- [ ] Manuell: vier Sektions-Cards, alle Optionen funktionsfähig, Offline-Flavor weiter ohne `INTERNET`
