# Gebetszeiten

Eine **maximal akkusparende, leichtgewichtige und datenschutzfreundliche**
Gebetszeiten-App für Android und Wear OS. Standardmethode: **Diyanet** (Türkiye),
vollständig **offline** berechnet.

## Leitprinzipien

1. **Akkusparend** — kein Polling, kein Hintergrunddienst, kein periodisches
   Refresh. Das Widget wird ereignisgetrieben durch exakte Alarme zu den
   Gebetszeiten aktualisiert (~5–6 Aufwachvorgänge/Tag). Auf der Uhr rendert das
   System Tile/Complication; die App wacht dafür nicht auf.
2. **Datenschutz by Construction** — die Standardvariante (Flavor `offline`) hat
   **weder die INTERNET- noch eine Standort-Berechtigung**. Die App kann technisch
   nichts senden und nichts tracken. Ortswahl nur manuell (Stadt aus Offline-DB
   oder Koordinaten).
3. **Leichtgewichtig** — natives Kotlin/Jetpack (Compose, Glance), Release-APK
   ~3 MB.

## Funktionen

- **Offline-Berechnung** der Diyanet-Zeiten (Fajr 18° / Isha 17°, Şafiî-Asr,
  Diyanet-Ihtiyat-Offsets, kalibrierte Hochbreiten-/Takdir-Regel).
- **Mitlaufender Tagesüberblick**: aktuelles Gebet als Fortschritts-Pille (die
  Pille füllt sich über die laufende Phase), nächste/übernächste Zeit; Vergangenes
  und Späteres ein-/ausklappbar.
- **Hanafi-Makruh-Zeiten** (İşrak/Zeval/İsfirar) und freiwillige Gebete
  (Duha-Vormittag; Awwabin/Tahajjud als Tipps) — optional, dezent annotiert.
- **Hijri-Datum**, einstellbare **Schriftgröße** und **hoher Kontrast**.
- **Stille Erinnerungen** pro Gebet (an/aus je Gebet), kein Ton/keine Vibration
  als Standard.
- **Homescreen-Widget** (nächstes Gebet, optional akkufreier System-Countdown).
- **Wear OS**: App-Screen, Tile und Complication zeigen statisch das nächste
  Gebet; das System hält sie ohne App-Wakeups aktuell.
- **Optional online** (Flavor `online`): exakte offizielle Diyanet-Zeiten über
  einen Community-Proxy. Nur diese Variante hat die INTERNET-Berechtigung.

## Genauigkeit

Die Offline-Engine ist gegen die **offizielle Diyanet-Jahrestabelle 2026**
(Nürnberg, Distrikt 11024 — alle 365 Tage) kalibriert und per Regressionstest
abgesichert (`core-prayertimes/.../YearCompareTest`):

| Gebet | mittlerer Fehler | max. Fehler |
|---|---|---|
| Sonnenaufgang / Dhuhr / Asr / Maghrib | ≤ ~1 Min | ≤ 2 Min |
| Fajr | ~2,4 Min | 23 Min |
| Isha | ~2,8 Min | 19 Min |

Der größere Restfehler bei Fajr/Isha liegt ausschließlich in den
Übergangswochen Ende April / Anfang August (Diyanets „aqrab al-ayyâm"-Methode für
die Weißen Nächte, die ohne deren exakten Algorithmus nicht minutengenau
nachbildbar ist).

## Build

- JDK 17, Android SDK (compileSdk 36), Gradle Wrapper.
- App-Module: `:app` (Phone), `:wear` (Wear OS), `:core-prayertimes` (reine
  Kotlin-Engine, unit-getestet).

```bash
# Offline-Variante (Standard, ohne INTERNET):
./gradlew :app:assembleOfflineRelease
# Online-Variante (mit INTERNET, optional):
./gradlew :app:assembleOnlineRelease
# Wear:
./gradlew :wear:assembleRelease
# Engine-Tests inkl. Jahresvergleich:
./gradlew :core-prayertimes:test
```

Produkt-Flavors: `offline` (Standard, keine INTERNET-Berechtigung) und `online`
(`de.gebetszeiten.online`, INTERNET nur in `app/src/online/AndroidManifest.xml`).

## Lizenz

Dieses Projekt steht unter der **GNU General Public License v3.0** — siehe
[`LICENSE`](LICENSE).

## Attribution & Drittquellen

- **Städtedaten:** [GeoNames](https://www.geonames.org/) (cities ≥ 15k),
  lizenziert unter **CC BY 4.0** — © GeoNames. Verwendung gemäß Namensnennung.
- **Berechnungsmethode & Referenzzeiten:** Diyanet İşleri Başkanlığı
  (namazvakitleri.diyanet.gov.tr). Die mitgelieferte Jahrestabelle dient nur als
  Test-/Kalibrierungsreferenz.
- **Astronomische Basisberechnung:** [adhan](https://github.com/batoulapps/adhan)
  (batoulapps), MIT-Lizenz.
- Optionaler Online-Abgleich: inoffizieller Community-Proxy
  `prayertimes.api.abdus.dev` (nur im `online`-Flavor, opt-in).
