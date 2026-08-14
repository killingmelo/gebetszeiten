# Diyanet-Standortindex-Pipeline

Erzeugt `app/src/online/assets/official/locations-world.tsv` — den weltweiten
Index (Diyanet-ID → Koordinaten), über den die App amtliche Zeiten auch für
Orte auflöst, die Diyanet nicht als eigenen Standort führt (z. B. Serdivan →
Adapazarı/SAKARYA).

    python tools/diyanet-index/build_index.py            # Vollauf (~4 min)
    python tools/diyanet-index/build_index.py --limit 3  # Smoke-Test

- Roh-JSON liegt in `cache/` → Abbruch/Neustart überspringt Geholtes.
  Für einen frischen Jahresabruf `cache/` löschen!
- Vor dem Commit prüfen: `province-center` in der Statistik muss klein
  bleiben (< 100). Eine große Zahl heißt, dass Stufe 3 zu breit greift —
  dann erben Kleinorte fremde Koordinaten. Lauf verwerfen.
- `city-aliases.tsv` federt Faelle ab, in denen `cities.tsv` einen
  englischen Exonym fuehrt (z. B. "Nuremberg" statt "Nürnberg") — ohne diese
  Bruecke faellt der deutsche Diyanet-Name durch alle drei Geokodierungsstufen.
- Danach `.\gradlew.bat :app:testOnlineDebugUnitTest --tests "*WorldIndexIntegrityTest*"`.
- Jährlich zusammen mit `tools/diyanet-fetch/fetch_diyanet.py` ausführen.
