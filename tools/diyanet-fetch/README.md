# Diyanet-Jahresdaten-Pipeline

Erzeugt die gebündelten amtlichen Zeiten (`shared-assets/official/`).
Einmal pro Jahr ausführen, sobald Diyanet das neue Jahr publiziert
(erfahrungsgemäß Ende Dezember — Jahresansicht der Website prüfen).

    python tools/diyanet-fetch/fetch_diyanet.py            # Vollauf (~30–45 min)
    python tools/diyanet-fetch/fetch_diyanet.py --limit 3  # Smoke-Test

- Roh-HTML wird in `cache/` abgelegt → Abbruch/Neustart überspringt Geholtes.
  Für einen frischen Jahresabruf `cache/` löschen!
- Report prüfen: unmatched-Liste (kleine Orte ohne cities.tsv-Eintrag sind ok),
  Größe < 4 MB, danach `git add shared-assets/official` + Integritätstest
  (`.\gradlew.bat :app:testOfflineDebugUnitTest`).
- Jahres-Release-Ablauf: Script laufen lassen → Assets-Diff committen →
  App-Update veröffentlichen (siehe playstore/CHECKLISTE.md).
