# Diyanet-Jahresdaten-Pipeline

Erzeugt die gebündelten amtlichen Zeiten (`shared-assets/official/`).
Einmal pro Jahr ausführen, sobald Diyanet das neue Jahr publiziert
(erfahrungsgemäß Ende Dezember — Jahresansicht der Website prüfen).

    python tools/diyanet-fetch/fetch_diyanet.py --year 2027            # Vollauf (~30–60 min)
    python tools/diyanet-fetch/fetch_diyanet.py --year 2027 --limit 3  # Smoke-Test

`--year` ist Pflicht. Die Jahresseite von Diyanet ist ein rollierendes
~16-Monats-Fenster: sie liefert je nach Datum Reste des laufenden Jahres und
das ganze Folgejahr. Geschrieben werden **nur Zeilen des angegebenen Jahres**,
und jeder Standort muss es **lückenlos** abdecken (365 Tage, im Schaltjahr
366) — sonst bricht das Skript hart ab und nennt Standort und fehlende Tage.
Ein halbes Jahr im Bundle wäre schlimmer als ein altes, weil es niemandem
auffällt.

- Roh-HTML wird in `cache/` abgelegt → Abbruch/Neustart überspringt Geholtes.
  Für einen frischen Jahresabruf `cache/` löschen! Ein Cache aus dem Vorjahr
  enthält das alte Fenster und führt direkt in den Abbruch oben.
- `--out-dir` schreibt woandershin (Default: `shared-assets/official`) —
  für Rauchtests, damit die echten Assets unberührt bleiben.
- Geschrieben werden `tables/t###-<jahr>.tsv`, `locations-de.tsv` und
  `coverage.tsv` (eine Zeile: erster und letzter abgedeckter Tag, Tab-getrennt).
  `coverage.tsv` wird vom Skript gepflegt, nie von Hand.
- Tabellen des Vorjahrs löscht das Skript nicht, es warnt nur — vor dem Commit
  `git rm shared-assets/official/tables/t*-<altjahr>.tsv`.
- Report prüfen: unmatched-Liste (kleine Orte ohne cities.tsv-Eintrag sind ok),
  Größe < 4 MB, danach `git add shared-assets/official` + Integritätstest
  (`.\gradlew.bat :app:testOfflineDebugUnitTest`).
- Jahres-Release-Ablauf: Script laufen lassen → Assets-Diff committen →
  App-Update veröffentlichen (siehe playstore/CHECKLISTE.md).

Der Integritätstest `OfficialAssetsIntegrityTest.bundledYearCoversTheNextTwoMonths`
liest `coverage.tsv` und wird **rot, sobald die Abdeckung in weniger als zwei
Monaten endet**. Das ist die Erinnerung, diesen Lauf zu machen.
