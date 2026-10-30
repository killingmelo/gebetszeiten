# App-Symbol

Eine Quelle für das Symbol auf dem Gerät und im Play Store: `geometry.json`.

Das Motiv: eine Figur in Sujūd, von schräg hinten gesehen, kniet nach Nordost auf einen
andalusischen Hufeisenbogen zu. Waldgrün `#224228` auf Creme `#F7F1E8`. Gewählt am 30.10.2026
(Spec `docs/superpowers/specs/2026-10-30-playstore-auftritt-design.md`).

| Datei | Wozu |
|---|---|
| `geometry.json` | Figur, Füße und Teppich als Polygone im 108er-Viewport; Lage und Maße des Bogens |
| `build_app_icon.py` | baut den Bogen, prüft alles, schreibt `ic_launcher_foreground.xml`, `ic_launcher_background.xml` und `icon.sha256`; `--preview` zeigt 512/192/48/32 px |
| `trace_vorlage.py` | hat die Figur einmalig aus dem Entwurf „Mitte 2“ nachgezogen; nur für eine neue Vorlage nötig |
| `icon.sha256` | Manifest, gegen das `AppIconAssetsTest` die Drawables prüft |

```
python tools/app-icon/build_app_icon.py --preview
```

Das 512er-Store-Symbol und das Feature-Bild entstehen in `playstore/make_assets.py` aus derselben
Geometrie (`render_png`). Die Sichel in der Statusleiste kommt weiter aus
`tools/notification-icons/`.

Warum Polygone: dieselben Punkte ergeben ohne Umrechnung die Vektorpfade fürs Gerät und, mit Pillow
gefüllt, das Store-Symbol. Nichts muss abgeglichen werden.

Warum der Bogen nicht nachgezogen ist: in der Vorlage ist er oben spitz. Gewollt ist der runde
Hufeisenbogen wie im Miḥrāb von Córdoba, als ein Pinselzug mit wechselnder Breite (`BRUSH_PROFILE`).
