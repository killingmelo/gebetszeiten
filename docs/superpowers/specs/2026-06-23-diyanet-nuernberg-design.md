# Phase 1 — Amtliche Diyanet-Zeiten für Nürnberg

**Datum:** 2026-06-23
**Status:** Entwurf zur Review
**Teil von:** Roadmap „UI/UX/Design/Funktionalität verbessern" (Phasen 1–5)

## Ziel

Für Nürnberg sollen die **amtlichen, veröffentlichten Diyanet-Zeiten** angezeigt
werden statt der lokal berechneten Werte (`DiyanetPrayerTimesCalculator`). Die
amtliche Jahrestabelle 2026 liegt bereits vor (heute als Test-Ressource
`core-prayertimes/src/test/resources/diyanet_nurnberg_2026.csv`). Sie wird als
**gebündeltes Offline-Asset** mitgeliefert und bevorzugt genutzt; die App bleibt
dadurch im Offline-Flavor weiterhin beweisbar netzfrei.

## Nicht-Ziele (YAGNI)

- Keine weiteren Städte in dieser Phase (nur Nürnberg).
- Keine amtlichen Daten für 2027+ (automatischer Rückfall auf Berechnung).
- Kein neuer Settings-Schalter — die amtliche Tabelle greift für Nürnberg
  automatisch, ohne Nutzerinteraktion.
- Keine Änderungen an der Karaha-/Nafl-Logik: diese leitet sich in
  `IslamicWindows` aus den Tageszeiten ab und nutzt damit automatisch die
  amtlichen Werte.

## Verhalten / Quellen-Priorität

`PrayerProvider.daily(...)` wählt die Quelle für einen Tag in dieser Reihenfolge:

1. **Online-Cache** (nur Online-Flavor + `settings.useOnline`): amtliche Zeiten,
   die live vom Diyanet-Proxy geladen wurden — *frischste* Quelle, hat Vorrang.
   (unverändert bestehend)
2. **Gebündelte amtliche Tabelle** (NEU): wenn eine mitgelieferte Tabelle die
   Kombination (Stadt, Datum) abdeckt → amtliche Zeit aus dem Asset. Greift in
   **beiden** Flavors, offline, unabhängig von `useOnline`.
3. **Berechnung** (Fallback): `PrayerSchedule.forDate(...)` wie bisher.

Diese Ordnung setzt die Entscheidung „beides kombiniert" um: gebündelte Tabelle
als Basis, ein online geladenes Update überschreibt sie, wenn vorhanden. Für
Daten ab 2027 existiert keine gebündelte Tabelle → automatischer Rückfall auf
die Berechnung (Schritt 3).

## Datenformat (Asset)

Neues Asset: `app/src/main/assets/official/nuernberg-2026.tsv`

Eine Zeile pro Tag, Tab-getrennt, ISO-Werte, **kein** Header:

```
2026-01-01	06:15	08:05	12:24	14:13	16:33	18:11
```

Spalten: `date  fajr  sunrise  dhuhr  asr  maghrib  isha`

**Mapping aus der Diyanet-Quelle** (türk. Spaltennamen der CSV):
`imsak→fajr`, `gunes→sunrise`, `ogle→dhuhr`, `ikindi→asr`, `aksam→maghrib`,
`yatsi→isha`. (Diyanets „İmsak" ist die Fajr-/Sabah-Zeit — Mapping korrekt und
deckungsgleich mit dem bestehenden Online-Parser.)

Provenienz: `namazvakitleri.diyanet.gov.tr`, Standort Nürnberg, Jahr 2026. Das
Asset wird aus der vorhandenen Test-CSV erzeugt und committet.

## Komponenten

### `BundledOfficialSource` (neu)

Ort: `app/src/main/kotlin/de/gebetszeiten/official/BundledOfficialSource.kt`
(Main-Source-Set → in beiden Flavors verfügbar).

Verantwortung: Liefert amtliche `SixTimes` für (Stadt, Datum), falls eine
gebündelte Tabelle das abdeckt — sonst `null`.

Öffentliche Schnittstelle:
```kotlin
object BundledOfficialSource {
    /** Amtliche Zeit für (Stadt, Datum) aus einer gebündelten Tabelle, oder null. */
    suspend fun get(context: Context, city: String, date: LocalDate): SixTimes?

    /** True, wenn für diese Stadt überhaupt eine gebündelte Tabelle existiert
     *  (für das Quellen-Label / Footer). */
    suspend fun covers(context: Context, city: String, date: LocalDate): Boolean
}
```

Intern:
- **Registry** (Konstante): normalisierter Stadtname → Liste von Asset-Pfaden.
  Start: `"nurnberg" → ["official/nuernberg-2026.tsv"]`.
- **Normalisierung** identisch zur bestehenden Logik in `Cities.normalize`
  (Akzente/Umlaute/türkische Zeichen → ASCII). Um Duplikate zu vermeiden, wird
  die Normalisierung in eine kleine, geteilte Hilfsfunktion ausgelagert
  (`de.gebetszeiten.data` oder ein `TextNormalize`-Objekt), die `Cities` und
  `BundledOfficialSource` gemeinsam nutzen.
- **Lazy-Cache**: je Asset-Datei ein `Map<LocalDate, SixTimes>`, beim ersten
  Zugriff auf `Dispatchers.IO` geladen und im Speicher gehalten (analog zu
  `Cities.cache`).
- **Parser** als reine Funktion `parseOfficialTsv(lines: Sequence<String>):
  Map<LocalDate, SixTimes>` — Android-unabhängig, damit unit-testbar. Defekte
  Zeilen werden übersprungen (robust wie `OfficialTimesCache.parseLine`).

### `PrayerProvider.daily(...)` (Änderung)

Ein zusätzlicher Schritt zwischen Online-Cache und Berechnung:

```kotlin
suspend fun daily(context, settings, date, zone): DailyPrayerTimes {
    if (settings.useOnline) {
        OfficialTimesCache(context).get(date)?.let { return it.toDaily(date, zone) }
    }
    BundledOfficialSource.get(context, settings.city, date)
        ?.let { return it.toDaily(date, zone) }            // NEU
    return PrayerSchedule.forDate(settings, date, zone)
}
```

Alle anderen `PrayerProvider`-Methoden rufen `daily(...)` auf und profitieren
automatisch.

### Quellen-Label im Footer (Änderung, sichtbarer Nachweis)

Heute zeigt der Footer fix `R.string.data_credit`
(„… Berechnung: Diyanet-Methode (offline)"). Künftig dynamisch je nach Quelle
des angezeigten Tages:

- amtlich (gebündelt oder online): **„Amtliche Diyanet-Zeiten · Nürnberg"**
- berechnet: **„Berechnung: Diyanet-Methode (offline)"** (bisheriger Text)

Umsetzung: neue Strings `data_credit_official` / `data_credit_calculated`. Die
`PrayerScreen`-Composable ermittelt das Label über
`BundledOfficialSource.covers(...)` (bzw. ob der Online-Cache den Tag liefert)
für `selectedDate` und zeigt den passenden Text. So sieht der Nutzer, dass für
Nürnberg/2026 die amtlichen Zeiten aktiv sind und ab 2027 wieder gerechnet wird.

## Datenfluss

```
PrayerScreen (selectedDate)
   → PrayerProvider.daily(context, settings, date, zone)
        1. useOnline? OfficialTimesCache.get(date)        → SixTimes?  (online override)
        2. BundledOfficialSource.get(city, date)          → SixTimes?  (amtlich, offline)
        3. PrayerSchedule.forDate(settings, date, zone)   → berechnet  (Fallback)
   → DailyPrayerTimes → IslamicWindows (Karaha/Nafl) → Timeline
```

## Annahmen / Edge Cases

- **Stadt-Match per normalisiertem Namen.** „Nürnberg" (auch „NÜRNBERG",
  „Nurnberg") → `nurnberg` → Treffer. Ändert der Nutzer den Stadtnamen, gibt es
  keinen Treffer → Berechnung. Akzeptiert.
- **Manuell geänderte Koordinaten bei Name „Nürnberg".** Die amtliche
  Nürnberg-Tabelle greift weiterhin (sie *ist* Nürnbergs amtliche Tabelle).
  Akzeptiert und gewollt.
- **Zeitzone.** Amtliche Zeiten sind lokale Wand-Uhrzeit (Europe/Berlin);
  `SixTimes.toDaily` hängt die Geräte-Zeitzone an. Für Nutzer in Deutschland
  identisch. Dokumentierte Annahme, kein Sonderfall-Handling.
- **DST.** Diyanet veröffentlicht bereits in lokaler Zeit inkl.
  Sommerzeit-Umstellung — die Tabellenwerte sind direkt korrekt.

## Tests

Im **app-Modul** als reine JVM-Unit-Tests (`app/src/test`, kein Gerät nötig).
`SixTimes` und `parseOfficialTsv` sind Android-unabhängig; die Parser-Tests
lesen ihre Eingabe aus einer Test-Ressource (nicht aus `assets`, das bräuchte
einen `Context`). Der Asset-Integritätstest liest die committete TSV-Datei über
ihren Repo-Pfad:

1. **Parser:** `parseOfficialTsv` liefert für mehrere Stichtage exakt die
   erwarteten `SixTimes` (gegen die bekannten Werte aus der 2026-Tabelle).
2. **Abdeckung/Fallback:** Ein Datum in 2026 ist abgedeckt; ein Datum in 2027
   ist es nicht (`get` → null), sodass `PrayerProvider` rechnet.
3. **Mapping:** Verifiziert, dass `imsak`→`fajr` und `gunes`→`sunrise` korrekt
   zugeordnet sind (eine Zeile mit bekannten Werten).
4. **Asset-Integrität (optional):** Zeilenzahl der TSV = 365 (2026 kein
   Schaltjahr), alle Zeiten parsebar.

## Auslieferung

Eigenständiger Commit/PR. Sichtbares Ergebnis: Für Nürnberg zeigt die App in
2026 die amtlichen Diyanet-Zeiten (Footer „Amtliche Diyanet-Zeiten · Nürnberg");
für andere Städte und ab 2027 unverändert die Berechnung.

## Offene Folgephasen (nicht Teil dieser Spec)

2. Karaha-Countdown (Pill-Amber) + Polish/Bugfix (`isSystemInDarkTheme`,
   Chevron-Icons, Touch-Targets, reduced-motion, Hijri-Schreibweise)
3. Bottom-Navigation + Qibla-Kompass
4. Monat-Übersicht
5. Settings-Sektionierung
