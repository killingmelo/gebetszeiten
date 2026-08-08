# Städte-Pipeline

Erzeugt `app/src/main/assets/cities.tsv` (Ortssuche) aus einem GeoNames-Extrakt.

```
python build_cities.py                # Default: cities500 (Orte ab ~500 Einwohner)
python build_cities.py --source cities1000   # kleineres Asset, falls nötig
```

- Downloads werden in `cache/` abgelegt (Resume-fähig; Ordner löschen für frische Daten).
- `extra-places.tsv`: Muss-Orte per geonameid, die im Extrakt fehlen können
  (z. B. Esenköy/Yalova — GeoNames hat dort keine gepflegte Einwohnerzahl).
  Sie werden aus dem jeweiligen Länder-Dump ergänzt.
- Eingebaute Prüfungen (hartes Skript-Fail): Sentinel-Orte (Esenköy, Çınarcık,
  Nuremberg, İstanbul) vorhanden, alle Ziele aus `city-aliases.tsv` auflösbar.
- Ausgabeformat (6 Spalten, kein Header, Population absteigend = Suchrang):
  `name \t asciiname \t ISO2 \t lat \t lng \t admin1Name`

Etwa einmal pro Jahr ausführen. Datenquelle: [GeoNames](https://www.geonames.org)
(CC BY 4.0) — Attribution steht in der App (`data_credit`) und im Haupt-README.
