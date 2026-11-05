# Plan: Rückmeldung des Testers (05.11.2026)

Grundlage: zwei Sprachnachrichten (abgetippt), vier Mockup-Bilder (Heute in fünf Tageszeiten, Verstehen mit Reitern, Store-Reihe „Der Tag beginnt mit dem ersten Licht“) und der Early-Access-Eintrag im Play Store. Noch nichts umgesetzt.

## 1. Was er meint

**Gefällt ihm:** die App insgesamt („Maschallah, sehr gelungen“), besonders die Restzeit in der Statusleiste.

| # | Wunsch | Was genau gemeint ist |
|---|---|---|
| A | **Lebendiger Hintergrund nach Tageszeit** | Wie die Samsung-Wetter-App: der ganze Bildschirm zeigt den Himmel zur jetzigen Uhrzeit (Sonnenaufgang, Mittag, Abend, Nacht mit Milchstraße). Nicht vier Bilder, die stundenlang stehen, sondern fließende Übergänge, z. B. aus einem Video, von dem alle paar Minuten ein Bild weiter gezeigt wird. Bilder könne man per KI erzeugen. In seinem Mockup: Verlauf über den ganzen Bildschirm, unten eine dunkle Landschaft mit Baum. |
| B | **Klarere Hierarchie auf „Heute“** | Gebetszeiten und Nebenzeiten wirken gleichrangig, man liest es „wie ein Buch“. Sein Mockup: Abschnitt groß mit „JETZT“ darüber, „noch 1 Std 29 Min · dann Šurūq 07:09“, ein Fortschrittsbalken, darunter eine **Glaskarte nur mit den fünf Gebeten** (größer), das laufende mit „JETZT“-Marke, Šurūq nur als kleine Zeile unter Faǧr („bis Sonnenaufgang 07:09“), alles Weitere unter **„Weitere Zeiten“ (aufklappbar)**. |
| C | **Tage per Wischen wechseln** | Auf „Heute“ nach links/rechts wischen für morgen/gestern, nicht nur über die Pfeile. Die Reiter (Monat, Qibla, Verstehen) bleiben per Tippen. |
| D | **Helles Thema: weiß statt beige, mit Akzentfarbe** | Das Creme (aus der Design-Vorlage) durch helleres Weiß ersetzen; eine Akzentfarbe einführen, kleine Untertitel und Beschreibungen in dieser Farbe. Sonst sei alles „schwarz auf beige“. |
| E | **Seine Bilder verwenden** | „Ganz großer Fehler“: bei den Sternbildern (vermutlich das Nachtbild/ʿIšāʾ) wurden nicht seine Bilder genommen. Welche Dateien gemeint sind, ist offen. |
| F | **Weitere Zeitquellen** | Neben Diyanet auch z. B. Fazilet-Takvimi. |
| G | **Zeiten von Hand verschieben** | Je Gebet ein paar Minuten vor oder zurück, wie er es aus einer anderen App kennt. |
| H | **„Verstehen“ mit Reitern** | Statt einer langen Blogseite oben Reiter je Gebet (Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ). Je Reiter: Bildkarte, zwei Kacheln „BEGINNT / ENDET“ (z. B. „Morgendämmerung 05:20“ / „Sonnenaufgang“), ein kurzer Satz, danach Koranvers bzw. Zitate auf Arabisch mit Übersetzung. Hintergrund nicht stockdunkel, sondern Himmel wie in A. |
| I | **Store-Bilder als durchgehende Reihe, Kernfunktionen statt Gebetsnamen** | Fünf Bilder, die zusammen einen Himmel mit Sonnenbahn zeigen (wie bei uns), aber in Tagesreihenfolge und mit Aussagen zur Funktion: „Der Tag beginnt mit dem ersten Licht.“, „Der Himmel zeigt die Zeit.“, „Jede Zeit erklärt.“, „Der ganze Monat.“, „Ruhig, auch in der Nacht.“ Kräftigere Schrift (unsere sei „sehr, sehr dünn“). |
| J | **Schrift** | Allgemein zu dünn; im Mockup eine kräftigere Serife (Noto Serif statt Noto Serif Display Light). |

Zwei Dinge fallen in den Bildern auf, die er nicht ausspricht:
- **Der Store zeigt schon seine Mockups.** Der Early-Access-Eintrag hat seine Bilder als Screenshots. Darin steht unten „Mock-up · Zeiten hier nur angenähert berechnet“, die Zeiten sind also nicht amtlich, und das Datum lautet „24. Dschumada I 1448 AH“ (nicht DMG). Die App sieht so nicht aus.
- **Das App-Symbol** im Store-Ausschnitt ist ein violetter Verlauf, nicht das Sujūd-Symbol vom 30.10.

## 2. Abgleich mit den Regeln des Projekts

| Wunsch | Passt? | Warum |
|---|---|---|
| A Hintergrund | **ja, mit Grenze** | Passt zur Richtung („Bild ins Design aufnehmen“, 04.11.). Grenze aus DESIGN.md: **Bilder zeigen keine Uhrzeiten.** Video kostet Speicher (APK) und Akku; ein gerechneter Verlauf statt Video wäre leicht und fließend. |
| B Hierarchie | **ja** | Deckt sich mit deinem Wunsch vom 04.11. („wichtig, aber weniger als die Gebetszeiten“). Der jetzige Stand (kleine Zeilen unter dem Gebet) ist ein Schritt dahin; sein Vorschlag „Weitere Zeiten“ aufklappbar ist die stärkere Trennung. |
| C Wischen | **ja** | Kein Konflikt. |
| D Weiß + Akzent | **Konflikt mit DESIGN.md** | DESIGN.md legt Creme (#f7f1e8) als Grund und Waldgrün als Akzent fest; der Akzent ist bereits da (Waldgrün), wird aber sparsam benutzt. Entscheidung nötig: Design-Vorgabe ändern oder Akzent nur stärker einsetzen. Der Kontrasttest (`PaletteContrastTest`) gilt weiter. |
| E Seine Bilder | **ja** | Wir brauchen die Dateien und die Lizenz (eigene KI-Bilder sind meist frei nutzbar, je nach Dienst). |
| F Fazilet | **Konflikt mit Grundsatz** | Grundsatz: „amtliche Diyanet-Zeiten oder gar keine“. Fazilet ist ein anderer, nicht amtlicher Kalender mit eigener Berechnung. Ginge nur als bewusst gewählte zweite Quelle mit klarer Kennzeichnung. |
| G Minuten verschieben | **Konflikt mit Grundsatz** | Verschobene Zeiten sind keine amtlichen mehr. Ginge nur sichtbar gekennzeichnet („+2 Min, von dir eingestellt“) und standardmäßig aus. |
| H Verstehen-Reiter | **ja** | „BEGINNT/ENDET“ mit Uhrzeit ist erlaubt (das ist die Textseite, nicht das Bild). Bestehende Zitate und Quellen bleiben; nur der Aufbau ändert sich. |
| I Store-Reihe | **ja, mit Pflicht** | Echte amtliche Zeiten einer echten Stadt, DMG, keine Gedankenstriche, kein „Mock-up“-Hinweis. Die Uhrzeiten an der Sonnenbahn stehen im Store-Bild, nicht in der App, das ist mit DESIGN.md vereinbar, sollte aber stimmen. |
| J Schrift | **Entscheidung** | DESIGN.md nennt Noto Serif Display Light/Regular. Kräftiger ginge über Regular statt Light oder eine Medium-Stufe; DMG-Abdeckung muss `FontCoverageTest` bestehen. |

## 3. Vorschlag: Reihenfolge

**Phase 1, klein, sofort (kein Konflikt)**
1. Store-Eintrag: seine Mockup-Screenshots durch echte Aufnahmen in seiner Bildsprache ersetzen (I), sobald der neue Look steht; bis dahin unsere echten Screenshots. Grund: Die Mockups zeigen berechnete Zeiten und eine App, die es so nicht gibt.
2. Wischen für gestern/morgen auf „Heute“ (C).

**Phase 2, „Heute“ neu (B, A, J)**
3. Entwurf als Bild zur Freigabe: Glaskarte mit den fünf Gebeten groß, „JETZT“-Marke, Fortschrittsbalken, Šurūq als Unterzeile, „Weitere Zeiten“ aufklappbar mit Makrūh und Nafl.
4. Himmel als Hintergrund über den ganzen Bildschirm, **gerechnet** aus Sonnenstand (zwischen Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ fließend, alle paar Minuten neu), unten eine Landschaftssilhouette; Textfarben aus dem Himmel mit Kontrastprüfung. Seine Bilder (E) als Schicht darüber oder als Nachtbild, wenn er sie schickt.
5. Schrift eine Stufe kräftiger, Kontrast und DMG-Abdeckung prüfen.

**Phase 3, „Verstehen“ mit Reitern (H)**
6. Reiter je Abschnitt, Bildkarte, BEGINNT/ENDET mit heutiger Uhrzeit, Kurzsatz, darunter die bestehenden Zitate. Sprung aus „Heute“ („?“) öffnet den passenden Reiter.

**Phase 4, Farben (D)**: nach deiner Entscheidung.

**Phase 5, nur nach Entscheidung (F, G)**: zweite Quelle und Minuten-Verschiebung, beide gekennzeichnet und ab Werk aus, oder gar nicht.

**Danach**: neue Store-Reihe in seiner Art (I) mit echten Zeiten.

## 4. Entscheidungen (05.11.2026)

1. **Farben (D): Weiß + Akzent.** DESIGN.md wird angepasst: heller Grund Weiß statt Creme, Akzent sichtbarer (Untertitel, „JETZT“, Fortschritt). Kontrast für alle Rollen neu prüfen (`PaletteContrastTest`).
2. **Zeitquellen (F, G): beides, gekennzeichnet.** Fazilet als wählbare zweite Quelle und Minuten-Verschiebung je Gebet, beides ab Werk aus und überall sichtbar markiert. Diyanet bleibt Vorgabe. Das lockert den Grundsatz „amtlich oder gar nichts“ bewusst: der Nutzer wählt es selbst.
3. **Hintergrund (A): noch offen.** Optionen siehe Abschnitt 5, gemeinsam entscheiden.
4. **Seine Bilder (E): offen.** Dateien und Rechte von ihm erfragen.
5. **Store: vorerst lassen.** Seine Mockups bleiben im Early Access, bis der neue Look steht. Vor einem Produktions-Release müssen sie durch echte Aufnahmen ersetzt sein (sie zeigen berechnete Zeiten und „Mock-up“).

## 5. Tageshimmel: die Optionen im Vergleich

> **Stand 05.11.2026: zurückgestellt.** Der jetzige „Heute“-Kopf bleibt. Probebau von Option 1 und 5: `docs/entwurf/tageshimmel-probebau.html` (nachts fast schwarz; keine Sterne hinter der Schrift; um Šurūq etwa eine Stunde am Tag ein Schleier bis 50 % für 4,5:1). Für später vorgemerkt: echtes oder generiertes Foto als Vordergrund-Ebene über dem gerechneten Himmel, oder eine Bildreihe derselben Szene je Tagesabschnitt; Rechte vorher klären.

| | 1. Gerechneter Verlauf | 2. Wenige Bilder, überblendet | 3. Viele Bilder (z. B. alle 15 Min) | 4. Video, Bild für Bild | 5. Mischung: Verlauf + Bild-Ebenen |
|---|---|---|---|---|---|
| **Wie es aussieht** | flächig, grafisch, wie sein Mockup (Verlauf + Silhouette) | fotografisch, je Abschnitt ein Motiv | fotografisch, fast fließend | fotografisch, ganz fließend | Verlauf trägt die Farbe, darüber Berg/Baum/Sterne/Sonne als Ebenen |
| **Fließend über den Tag** | ja, stufenlos | nein, Sprünge oder Überblendung | fast | ja | ja |
| **Speicher (APK)** | fast nichts | 2 bis 4 MB | 20 bis 60 MB | 15 bis 80 MB, je nach Länge | 1 bis 3 MB |
| **Akku** | kaum (ein Bild je paar Minuten) | kaum | kaum | Dekodieren kostet, bei „Bild alle 5 Min“ vertretbar | kaum |
| **Passt zu jedem Ort und jeder Jahreszeit** | ja, folgt dem echten Sonnenstand | nur grob | nur grob | nur grob, ein Video für alle | ja |
| **Dunkel nachts (AMOLED)** | ja, steuerbar | je nach Bild | je nach Bild | je nach Video | ja |
| **Lesbarkeit der Schrift** | sicher prüfbar | je Bild prüfen | je Bild prüfen | je Bild schwer zu garantieren | sicher prüfbar |
| **Rechte** | keine Fragen | seine/KI-Bilder klären | dito | dito | Ebenen selbst gezeichnet oder seine |
| **Aufwand** | mittel | klein | groß (Bilder erzeugen) | groß (Video erzeugen, Wiedergabe) | mittel bis groß |
| **Widget** | gleich nutzbar | gleich nutzbar | zu groß fürs Widget | nicht im Widget | nutzbar |

Meine Einschätzung: 1 oder 5 passen am besten zu „schlank, akkusparend“ und folgen dem echten Himmel am Ort; 2 bis 4 sehen fotografischer aus, sind aber schwerer, gelten nicht für jeden Ort und jede Jahreszeit und machen die Lesbarkeit schwer prüfbar. Vor der Entscheidung lohnt ein kleiner Probebau von 1 und 5 nebeneinander.
