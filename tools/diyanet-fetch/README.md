# Diyanet-Datenpipeline

Erzeugt die gebündelten amtlichen Zeiten (`shared-assets/official/`).
Ausführen, wann immer die Reserve knapp wird — der Integritätstest sagt
Bescheid (siehe unten).

    python tools/diyanet-fetch/fetch_diyanet.py            # Vollauf (~20–60 min)
    python tools/diyanet-fetch/fetch_diyanet.py --limit 5  # Smoke-Test

## Was die Jahresseite wirklich liefert

Am 19.09.2026 nachgemessen. Sie trägt **zwei** Tabellen, und dazwischen liegt
ein Loch:

| Tabelle | Inhalt | am 19.09.2026 |
|---|---|---|
| `tab-1` | 31 Tage ab heute | 2026-09-19 – 2026-10-19 |
| `tab-2` | das **nächste** Kalenderjahr | 2027-01-01 – 2027-12-31 |
| — | wird nie ausgeliefert | 2026-10-20 – 2026-12-31 |

Das Skript liest `tab-2`. Die 403 Zeilen, die die Seite insgesamt zeigt, galten
früher als zusammenhängendes „rollierendes ~16-Monats-Fenster" — sie sind es
nicht, und diese Fehlannahme steckte bis September 2026 im Kopftext des
Skripts, in diesem README und in der Planung.

**Daraus folgt die wichtigste Regel:** ein Lauf **ergänzt** den vorhandenen
Jahrgang, er **ersetzt** ihn nicht. Den Rest des laufenden Jahres gibt die
Seite nie wieder her. In Commit `647fd0d` („Jahrgang 2027 löst 2026 ab") ist
genau das passiert: danach begann die Reserve erst am 01.01.2027, und bis
dahin hatte die offline-Variante für keinen einzigen Ort eine amtliche Zeit.
Gemerkt hat es niemand, weil damals noch die eigene Berechnung einsprang.

## Der vollständige Ablauf

    # 1. frisch holen (alter Cache enthält das alte Fenster!)
    rm -rf tools/diyanet-fetch/cache
    python tools/diyanet-fetch/fetch_diyanet.py --out-dir /tmp/neu

    # 2. mit dem ausgelieferten Bundle zusammenführen, nicht ersetzen
    python tools/diyanet-fetch/merge_bundles.py \
        --base shared-assets/official --overlay /tmp/neu --out shared-assets/official

    # 3. Tabellen des vorigen Laufs entfernen (das Skript nennt den Befehl)
    git rm shared-assets/official/tables/t*-<alte-kennung>.tsv
    git add shared-assets/official

Zusammengeführt wird über die **Diyanet-Standort-Kennung**, die über Läufe
hinweg stabil ist — nicht über `t###`. Bei Überschneidung gewinnt `--overlay`,
der neuere Abruf. Ein Ort, den nur der alte Jahrgang kennt, fällt weg; einen
Ort, den Diyanet nicht mehr führt, bündeln wir nicht.

## Was das Skript prüft

Nicht mehr „enthält Jahr X lückenlos", sondern **„enthält jeden Tag vom
Stichtag bis zum letzten gemeinsamen Tag lückenlos"**. Ein Standort mit
Löchern darin lässt den Lauf hart scheitern und wird benannt.

- **Stichtag und Ende kommen aus den Daten**, nicht aus einer Jahreszahl: der
  späteste Anfang und das früheste Ende über alle Standorte. `--from` setzt
  den Stichtag von Hand. Ein geratenes Jahr war der ursprüngliche Fehler.
- Wer früher endet oder später anfängt, beschneidet das Fenster für alle — das
  Skript nennt die Standorte, bei denen das passiert.
- Roh-HTML wird in `cache/` abgelegt → Abbruch/Neustart überspringt Geholtes.
  **Für einen frischen Abruf `cache/` löschen.**
- `--out-dir` schreibt woandershin (Default: `shared-assets/official`) — für
  Rauchtests und für Schritt 1 oben, damit die echten Assets unberührt
  bleiben, bis zusammengeführt wurde.

## Warum die Dateien eine Lauf-Kennung tragen

`<lauf-id>` ist das Abrufdatum (`t000-20260919.tsv`), gesetzt per `--run-id`,
Default heute. Ein Jahres-Suffix ginge nicht mehr: eine zusammengeführte
Tabelle umspannt zwei Kalenderjahre.

Dieselbe Kennung steht als `tableRef` in `locations-de.tsv`, Index und
Tabellen stammen also immer aus demselben Vorgang. Das ist keine Kosmetik: die
Kennungen `t000`, `t001`, … werden bei **jedem** Lauf neu nach
Inhaltsreihenfolge vergeben, `t005` bedeutet danach einen anderen Ort. Läge
ein zweiter Jahrgang daneben, wären das falsche Zeiten, die richtig aussehen.

Tabellen früherer Läufe löschen die Skripte nicht, sie warnen nur. Der
Unit-Test `OfficialAssetsIntegrityTest.onlyOneVintageIsBundled` macht daraus
einen Build-Fehler.

## Nicht jeder Ort deckt dasselbe Fenster ab

Seit dem Zusammenführen ist die Abdeckung ungleichmäßig, und das ist richtig
so: die 621 Orte des Jahrgangs 2026 reichen vom 01.01.2026 bis 31.12.2027, die
326 Orte, die Diyanet erst 2027 führt, beginnen am 01.01.2027.

`coverage.tsv` nennt deshalb den **Schnitt** — den Zeitraum, den *jeder*
gebündelte Ort abdeckt (spätester Anfang, frühestes Ende). Einzelne Orte
dürfen mehr haben; die App schlägt ohnehin je Tag nach und zeigt für einen
nicht abgedeckten Tag einen Hinweis. Die Datei wird von den Skripten gepflegt,
nie von Hand.

## Nach dem Lauf

- Report prüfen: unmatched-Liste (kleine Orte ohne `cities.tsv`-Eintrag sind
  ok) und **~4.6 KB je Standort und 365 Tage** — nicht die Gesamtgröße, die
  wächst mit der Zahl der Orte und der Fensterlänge.
- Integritätstest: `.\gradlew.bat :app:testOfflineDebugUnitTest`
- Release-Ablauf: Assets-Diff committen → App- **und** Wear-Update
  veröffentlichen (siehe `playstore/CHECKLISTE.md`, Abschnitt 8).

Zwei Stolperdrähte in `OfficialAssetsIntegrityTest` wachen darüber:
`coverageHasAlreadyBegun` wird rot, sobald die Ankerorte Nürnberg und Berlin
**heute** keine Reserve mehr haben oder mehr als 326 Orte ohne dastehen —
beides heißt, dass ein Jahrgang ersetzt statt ergänzt wurde.
`bundledYearCoversTheNextTwoMonths` wird rot, sobald die Abdeckung in weniger
als zwei Monaten **endet**. Das ist die Erinnerung, diesen Lauf zu machen.
