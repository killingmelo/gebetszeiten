# Online-First: amtliche Diyanet-Zeiten weltweit on demand

**Datum:** 2026-07-29
**Status:** abgestimmt (Brainstorming-Session, Nutzer-Entscheidungen siehe unten)

## Ziel

Die veröffentlichte App wird der **Online-Flavor**. Sie holt amtliche
Diyanet-Zeiten für jeden Standort weltweit bei Bedarf — primär direkt von
`namazvakitleri.diyanet.gov.tr`, bei Fehler über den Community-Proxy
(`prayertimes.api.abdus.dev`) — und behält DE-Bundle + astronomische
Berechnung als Offline-Fallback. Eine Ausweitung des gebündelten
Offline-Bundles auf weitere Länder entfällt damit komplett.

**Nutzer-Entscheidungen (2026-07-29):**
1. Strategie: Online-Flavor veröffentlichen (statt Bundle-Ausweitung
   auf weitere Länder oder Zwei-Listing-Betrieb).
2. Datenquelle: Diyanet-Direktabruf primär, Proxy als Fallback.
3. Grundsatz (steht über allem, siehe Memory `echte-diyanet-zeiten-grundsatz`):
   immer die echten amtlichen Zeiten bevorzugen, Berechnung nur letzter
   Fallback.

## 1. Flavor-/AppId-Tausch (Gradle)

- `online`-Flavor: `applicationIdSuffix`/`versionNameSuffix` entfernen →
  übernimmt `de.gebetszeiten` und bedient das bestehende Play-Listing als
  normales Update (gleicher Signatur-Key).
- `offline`-Flavor: bekommt `applicationIdSuffix = ".offline"` (und
  `versionNameSuffix = "-offline"`), bleibt baubar als puristisch
  netzfreie Variante und Test-Basis. `isDefault` darf bei `offline`
  bleiben (Entwickler-Default), veröffentlicht wird
  `:app:bundleOnlineRelease`.
- Wear: unverändert netzfrei (Bundle + Berechnung), keine Änderung.
- Folge: Wer die bisherige Sideload-`de.gebetszeiten.online`-Variante
  installiert hat (nur der Entwickler), installiert einmal neu.

## 2. Neuer `DiyanetDirectFetcher` (online-Sourceset)

- GET `https://namazvakitleri.diyanet.gov.tr/tr-TR/{id}` (~390 KB,
  gemessen an 500 Cache-Seiten der Pipeline).
- Parser = 1:1-Kotlin-Port von `parse_year_table` aus
  `tools/diyanet-fetch/fetch_diyanet.py`: `id="tab-2"`-Jahres-Tabelle,
  8 Zellen pro Zeile, türkische Monatsnamen, strikte Validierung
  (`HH:MM`-Format, Zellenzahl durch 8 teilbar) — wirft bei jedem
  Format-Bruch, damit der Fallback greift.
- Liefert `Map<LocalDate, SixTimes>` für das **ganze Jahr** (statt 31
  Tage über den Proxy). Das bestehende Freshness-Gate (<7 Tage
  Abdeckung oder Standortwechsel) feuert damit praktisch nur noch ~1×
  pro Jahr plus am Jahreswechsel (Diyanet publiziert das Folgejahr
  erfahrungsgemäß Ende Dezember; bis dahin versucht das Gate es bei
  jedem Anlass erneut — harmlos, ein Abruf pro Alarm/App-Start max).

## 3. Fetcher-Kette (Composite)

- `OfficialTimesProvider.fetcher` liefert einen Composite:
  `DiyanetDirectFetcher` zuerst; bei Exception **oder leerem Ergebnis**
  der bestehende `DiyanetProxyFetcher` (31-Tage-JSON).
- Namens→ID-Auflösung unverändert: DE über `BundledOfficialSource`
  (id-genau), sonst Proxy-`/search` (Region-vor-City-Match).
- **Neu:** die aufgelöste Diyanet-ID wird zusammen mit dem
  Standort-Stempel im `OfficialTimesCache` persistiert. Folge-Refreshes
  am selben Standort (auch der Direktabruf) kommen dann ohne Proxy aus;
  die Namens-Suche braucht es nur noch einmalig pro neuem Nicht-DE-Ort.

## 4. Unverändert

- Zeiten-Format im Cache (`OfficialTimesCache`, eine Zeile pro Tag; ein
  Jahr ≈ 13 KB Text im DataStore) und Standort-Stempel-Schutz (1 km) —
  neu kommt nur das ID-Feld aus Abschnitt 3 als zusätzlicher Key hinzu.
- Prioritätskette `PrayerProvider.daily`: Online-Cache → DE-Bundle →
  Berechnung; `useCalculated` bleibt explizites Opt-out.
- `useOnline`-Default = an im Online-Flavor (Commit 61e264f), Refresh
  bei App-Start + Gebets-Alarm.
- Isha-Regionalkalibrierung der Berechnung als letzter Fallback.
- DE-Bundle bleibt an Bord (Offline-Sicherheit DE + Wear + Footer-Name);
  jährlicher Pipeline-Lauf laut `tools/diyanet-fetch/README.md` bleibt.

## 5. Fehlerpfade

HTML-Umbau bei Diyanet → Parser wirft → Proxy übernimmt. Beide Quellen
down → Cache (bis zu 1 Jahr gültig) → DE-Bundle → Berechnung. Alles wie
bisher geloggt (`DiyanetFetch`-Tag), nie ein Crash-Pfad, nie stale Daten
eines anderen Standorts (Stempel).

## 6. Compliance (manuelle Schritte, in playstore/CHECKLISTE.md ergänzen)

- PRIVACY.md: Direktabruf diyanet.gov.tr beschreiben — übermittelt wird
  nur IP + Diyanet-Standort-ID (kein GPS, keine Nutzerdaten); Proxy als
  Fallback benennen.
- Play-Console-Data-Safety-Formular anpassen (bisher „keine Daten", das
  stimmte nur für den Offline-Flavor).

## 7. Tests

- Parser-Fixture-Tests mit echten HTML-Seiten aus
  `tools/diyanet-fetch/cache` (mind. 2 Standorte) + Negativtest
  (verstümmeltes HTML → Exception).
- Composite-Test mit Fake-Fetchern: direct ok → Proxy nie gefragt;
  direct wirft/leer → Proxy-Ergebnis; beide leer → leere Map.
- ID-Persistenz-Test (Cache liefert gespeicherte ID nur bei
  Stempel-Match).
- Bestehende Suiten beider Flavors + core bleiben grün.

## Feste Folgephase: Wear-Cache-Sync (eigene Spec nach diesem PR)

Vom Nutzer verbindlich gewünscht (2026-07-29, „2 unbedingt"): Das Handy
synct seinen amtlichen Zeiten-Cache per Wear-Data-Layer an die Uhr,
damit Uhr und Handy außerhalb der DE-Bundle-Abdeckung identische
amtliche Zeiten zeigen (statt Berechnung ±2 min auf der Uhr). Die Uhr
scrapt bewusst NICHT selbst (Akku, Komplexität); sie bleibt netzfrei
und liest nur den gesyncten Cache mit derselben Prioritätskette
(Sync-Cache → Bundle → Berechnung). Details in eigener Spec, sobald der
Online-First-Umbau gemerged ist.

## Bewusst nicht dabei (YAGNI)

Bundle-Ausweitung auf weitere Länder, eigener Server, Wear scrapt
selbst, Mehrquellen-Merge, Vorab-Downloads für Reiserouten.
