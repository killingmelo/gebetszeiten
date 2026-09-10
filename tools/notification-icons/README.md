# Countdown-Symbole für die Statusleiste

Erzeugt die 23 Vektoren `app/src/main/res/drawable/ic_countdown_*.xml` — die
Restzeit bis zum nächsten Gebet als Zahl in der Statusleiste statt des
statischen Monds.

```
python build_icons.py             # schreibt die Vektoren + icons.sha256
python build_icons.py --check     # nur prüfen, nichts schreiben
python build_icons.py --preview   # zusätzlich preview.png (braucht Pillow)
```

**Nur ausführen, wenn sich die Ziffernform ändert.** Die Dateien gehören ins
Repo; der normale Build ruft dieses Skript nicht auf.

- 23 Symbole: `1h`…`9h`, `50 40 30 20 10`, `9`…`1`. Der Mond bleibt
  `ic_notification.xml` und wird hier nicht angefasst — `CountdownGlyph.Now`
  und `.None` zeigen weiter ihn.
- Elf Zeichen (`0`–`9` und `h`) sind einmal als Sieben-Segment-Geometrie
  definiert, alles andere setzt das Skript zusammen. Keine Schriftart: keine
  Font-Abhängigkeit, keine Lizenzfrage, und bei 24 dp sind Rechteck-Segmente
  eindeutiger als extrahierte Schriftumrisse. Einzige Abweichung von der
  reinen Sieben-Segment-Form: die alleinstehende `1` (`SINGLE_SEGMENTS`, siehe
  unten).
- Wiederholbar: zweimal laufen lassen ergibt byte-gleiche Dateien.
- Eingebaute Prüfungen (hartes Skript-Fail): genau 23 Symbole, jedes Zeichen
  definiert, alle Koordinaten im 24×24-Viewport, kein Symbol pfadgleich mit
  einem anderen.
- `icons.sha256` ist das Manifest. `CountdownIconAssetsTest` vergleicht es mit
  den Dateien im Repo und schlägt an, wenn jemand einen Pfad von Hand
  nachgebessert hat — der nächste Generatorlauf würde die Korrektur sonst
  stillschweigend löschen. Nach jeder Änderung an der Ziffernform also das
  Skript laufen lassen und beides zusammen committen.
- `CountdownGlyphShapeTest` löst die `pathData` zurück in Segmentbelegungen
  auf und hält sie gegen eine Sollbelegung, die **im Test** steht. Wer hier die
  Belegung einer Ziffer ändert, muss sie dort ebenfalls ändern — genau das ist
  der Zweck: ein Zahlendreher in `SEGMENTS` (etwa `6` und `9` vertauscht)
  bleibt sonst unsichtbar, weil alle Dateien verschieden bleiben und das
  Manifest mitgeschrieben wird.

## Die Maße, an denen man dreht

Alle Maße stehen als benannte Konstanten im Kopf von `build_icons.py`, in dp
im 24×24-Viewport:

| Konstante | Wert | Wirkung |
|---|---|---|
| `GLYPH_HEIGHT` | 19.0 | Zeichenhöhe; oben/unten bleiben je 2.5 dp |
| `MARGIN` | 2.0 | seitlicher Rand beim breitesten Symbol |
| `PAIR_WIDTH` | 8.6 | Breite eines Zeichens im zweistelligen Symbol |
| `PAIR_GAP` | 2.8 | Lücke zwischen den beiden Zeichen |
| `PAIR_STROKE` | 2.6 | Segmentdicke zweistellig |
| `SINGLE_WIDTH` | 13.0 | Breite des einzelnen Zeichens |
| `SINGLE_STROKE` | 3.4 | Segmentdicke einstellig |
| `ONE_WIDTH` | 9.0 | Breite der alleinstehenden `1` |

Ein zweistelliges Symbol ist damit `2 × 8.6 + 2.8 = 20` dp breit, also 83 % des
Viewports; ein einstelliges 13 dp, also 54 %.

**Ausnahme `1`.** Die `1` ist nur so breit wie ihr Balken (`NARROW`), sonst
klaffte in `1h` und `10` eine Lücke wie ein Leerzeichen. Diese beiden sind
darum `2.6 + 2.8 + 8.6 = 14` dp breit (58 %), nicht 20. Die *alleinstehende*
`1` ist ein Sonderfall für sich: als blosser Balken wäre sie 3.4 dp breit
(14 %) und in der Statusleiste eher ein Trennstrich als eine Ziffer, deshalb
bekommt sie Fuss und Fahne und damit `ONE_WIDTH` = 9 dp (38 %). In `1h` und
`10` bleibt es beim Balken — dort liest der Nachbar mit, und Fuss plus Fahne
machten die ohnehin engen Doppelsymbole nur voller.

**Wenn die Ziffern auf einem echten Gerät zu dünn oder matschig wirken:** zuerst
`PAIR_STROKE` und `SINGLE_STROKE` erhöhen (z. B. auf 3.0 / 3.8) — die
Statusleiste skaliert 24 dp auf wenige Millimeter herunter, und dort verlieren
sich dünne Segmente zuerst. Erst danach an `GLYPH_HEIGHT` drehen; über 20 dp
stößt es an den Rand. `PAIR_WIDTH` ergibt sich aus `MARGIN` und `PAIR_GAP`, ist
also keine eigene Schraube.

`preview.png` zeigt oben alle Symbole in Originalgröße nebeneinander (so groß
sind sie im Gerät) und darunter vergrößert. Das ersetzt keinen Blick auf ein
echtes Gerät, aber es zeigt, was eine Änderung an den Maßen bewirkt.

**Worauf man auf einem echten Gerät zuerst schaut:** auf die alleinstehende
`1` — sie steht in der letzten Minute vor dem Gebet und wird darum am
häufigsten gesehen — und auf `1h`, das im Render eher wie `lh` liest als wie
„eine Stunde". Ist die `1` dort nicht als Ziffer zu erkennen, ist `PAIR_GAP`
die erste Schraube.
