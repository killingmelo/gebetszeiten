# Play-Store-Listing (de-DE)

## Woher die Texte kommen

**Nicht mehr von hier.** Kurz- und Vollbeschreibung stehen in den
fastlane-Metadaten und werden dort gepflegt:

| Feld in der Console | Datei |
|---|---|
| Kurzbeschreibung (max. 80 Zeichen) | `fastlane/metadata/android/de-DE/short_description.txt` |
| Vollständige Beschreibung (max. 4000) | `fastlane/metadata/android/de-DE/full_description.txt` |
| Was ist neu (max. 500 je Sprache) | `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt` |
| dasselbe auf Englisch | `fastlane/metadata/android/en-US/…` |

Bis zum 20.09.2026 standen die Texte hier **und** dort, und beide Fassungen
beschrieben eine App, die es nicht mehr gibt: rein offline rechnend, ohne
INTERNET-Berechtigung. Veröffentlicht wird seit Juli 2026 der **online**-Flavor,
und seit dem Umbau „amtliche Zeiten oder nichts" rechnet er nicht mehr
stillschweigend. Eine Beschreibung, die das Gegenteil behauptet, ist im Store
keine Ungenauigkeit, sondern eine falsche Angabe. Zwei Kopien sind zwei
Gelegenheiten, genau das zu übersehen — deshalb nur noch eine.

## Was nur in der Console steht

### App-Name (max. 30 Zeichen)
```
Gebetszeiten Islam
```
*(18 Zeichen; steht auch in `fastlane/metadata/android/de-DE/title.txt`, geprüft von
`StoreListingTextTest`. „Diyanet“ gehört nicht in den Namen: das sähe nach einem offiziellen
Diyanet-Angebot aus. Unter dem Symbol auf dem Gerät heißt die App weiter „Gebetszeiten“.)*

### Kategorie & Tags
- Kategorie: **Lifestyle**
- Tags: Gebetszeiten, Namaz, Salah, Islam, Diyanet

### Kontakt
- E-Mail (Pflichtfeld): h.richtersohn@gmail.com
- Datenschutzerklärung (Pflicht-URL): https://github.com/killingmelo/gebetszeiten/blob/main/PRIVACY.md

### Grafiken
| Artefakt | Pfad |
|---|---|
| App-Icon 512×512 | `playstore/icon_512.png` |
| Feature-Graphic 1024×500 | `playstore/feature_1024x500.png` |
| Telefon-Screenshots | `fastlane/metadata/android/de-DE/images/phoneScreenshots/` |
| Wear-Screenshots | `fastlane/metadata/android/de-DE/images/wearScreenshots/` |

Icon und Feature-Graphic erzeugt `playstore/make_assets.py`. Das Motiv (Sujūd vor
andalusischem Miḥrāb) kommt aus derselben Geometrie wie das Startbildschirm-Symbol
(`tools/app-icon/geometry.json`), der Himmel des Feature-Bilds aus demselben Panorama wie
die Screenshots (`playstore/panorama.py`). Nicht von Hand nachbessern.
