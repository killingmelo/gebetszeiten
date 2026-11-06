# Verstehen neu: Design

Stand 06.11.2026. Entschieden im Gespräch am 05. und 06.11.2026. Entwurf zum Ansehen: `docs/entwurf/verstehen-konzept.html` (Tonprobe C, Gestaltung 3 oben und 1 darunter).

## Warum

Die heutige Seite „Verstehen“ (seit 0.5.0, Reiter je Abschnitt, Dateien in `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/`) hat laut Nutzer drei Schwächen: Der Aufbau führt nicht, der Ton passt nicht, und die Gestaltung wirkt schwach. Die Zitate stehen mitten im Lesefluss und machen die Texte schwer.

Ziel: Bei jedem Besuch lernt man etwas dazu. Die Seite arbeitet wie ein Nachschlagewerk zum Gebet, nicht wie ein Kurs.

## Wer und wann

- Die Leser sind ganz unterschiedlich, vom Einsteiger bis zum Genauen.
- Sie öffnen die Seite
  - mit einer gezielten Frage (z. B. „Wann endet ʿAṣr?“),
  - zum Lernen in Ruhe,
  - aus Heute heraus, wenn sie eine Zeit antippen.

## Ziele

- Pro Gebet steht oben alles, was man wissen muss. Wer mehr will, klappt auf. Es gibt genau **zwei Schichten**.
- Fiqh-Belege bleiben vollständig, stören aber den Lesefluss nicht: Sie stehen als **Endnoten**.
- Man findet jede Antwort schnell: über **Reiter**, eine **Suche** und **antippbare Begriffe**.

## Nicht-Ziele

- Kein Quiz, kein Lernfortschritt, kein „Wissen des Tages“.
- Kein Einsteiger- oder Genau-Modus. Die Schichten ersetzen das.
- Keine anderen Rechtsschulen, nur die hanafitische (Projektregel).

## Was es nicht werden darf

- Fiqh-Aussagen ohne hanafitisches Direktzitat mit Fundstelle (Projektregel, `memory/schreibstil-app-texte.md`).
- Uhrzeiten in Bildern oder in der Sonnenuhr (DESIGN.md).
- Gedanken- oder Halbgeviertstriche in sichtbaren Texten, KI-Floskeln, Begriffe nicht in DMG-Umschrift (CLAUDE.md).

## Aufbau

### Reiter

Oben stehen sechs Reiter in dieser Reihenfolge: Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, **Freiwillig und verpönt**. Man wischt zwischen ihnen wie heute.

Der sechste ist ein eigenes Kapitel. Er enthält Ḍuḥā, Auwābīn und Tahaǧǧud sowie die Karāha-Zeiten: Šurūq, Zawāl und das Vergilben vor Maġrib.

**Aufbau des sechsten Kapitels** (Entscheidung 06.11.2026): Jeder der sechs Teile ist ein eigener Abschnitt nach derselben Vorlage wie ein Gebet:
- Kopf,
- Zeitzeile,
- Merksatz,
- die W-Fragen,
- Rakʿa-Folge, wo es Gebete sind,
- „Mehr zu …“.

Die Endnoten des Kapitels stehen gesammelt am Ende. Oben im Kapitel zeigt ein Tagesband alle sechs Abschnitte als Übersicht, ohne Uhrzeiten. Ein Antippen springt zum Abschnitt.

Die Zeiten dieser Abschnitte rechnet die App, Diyanet liefert sie nicht. Die Zeitzeile sagt das deshalb, z. B. „Heute etwa 07:48 bis 11:44, von der App gerechnet“. Den genauen Wortlaut legt der Bauende mit den Texten zur Freigabe vor.

Sprungziele aus Heute und aus der Suche sind diese Abschnitte.

Folgen für den bisherigen Aufbau:
- Šurūq ist kein eigener Reiter mehr, es gehört in dieses Kapitel.
- Faǧr zeigt sein Ende bei Šurūq weiterhin selbst.
- Die Ǧamāʿa bleibt bei Faǧr.

### Suche

Über den Reitern steht ein Suchfeld. Es durchsucht:
- die Reiter,
- die Zwischenköpfe,
- die Begriffe aus dem Glossar,
- die deutschen Texte, Zitate eingeschlossen.

Ein Treffer springt zum Reiter und zum passenden Abschnitt.

### Einstieg aus Heute

Tippt man auf Heute eine Zeit an, öffnet sich Verstehen direkt beim passenden Reiter. Die Nebenzeiten von Heute (Ḍuḥā, Makrūh usw.) öffnen den Reiter „Freiwillig und verpönt“ an ihrer Stelle.

### Ein Reiter, von oben nach unten

1. **Himmelsband** (aus Entwurf 3). Es ersetzt die Fotos je Reiter: Die Fotos (`PhotoKey`) entfallen in Verstehen samt ihrer Bildbeschreibungen dort. Auf Heute und in den Meldungen bleiben sie. Bild-Assets werden nur gelöscht, wenn sie sonst nirgends gebraucht werden (Entscheidung 06.11.2026).

   Das Band: Ein Bild zeigt den Sonnenstand, der das Gebet bestimmt, mit Horizont, Osten und Westen. Uhrzeiten zeigt es keine.
   - Faǧr: Licht unter dem Osthorizont.
   - Ẓuhr: Sonne knapp nach dem Höchststand, ein Stab mit seinem Schatten.
   - ʿAṣr: Stab mit einfachem und doppeltem Schatten, beide markiert.
   - Maġrib: Sonne unter dem Westhorizont.
   - ʿIšāʾ: Dämmerung erloschen, Sterne.
   - Das Kapitel „Freiwillig und verpönt“ zeigt ein Band über den ganzen Tag, darauf die Abschnitte.
2. **Kopf:** Bedeutung als kleiner Kopf in Waldgrün (z. B. „Mittagsgebet“), darunter der Name in der Serife.
3. **Heute:** „Heute in Nürnberg 12:04 bis 14:27“ mit den amtlichen Diyanet-Zeiten des Tages und dem eingestellten Ort. Eine Verschiebung des Nutzers wird wie überall gekennzeichnet.
4. **Obere Schicht** (aus Entwurf 1, Buch: große Serife, Text im Fluss, viel Weiß), geführt im **Ton C, Lehrbuch**:
   - **Merksatz** in einem leicht grün getönten Kasten. Er fasst das Gebet in einem Satz (z. B. „Ẓuhr beginnt, wenn der Schatten wieder zu wachsen anfängt.“).
   - **Wann bete ich?** Beginn und Ende.
   - **Wie viele Rakʿa?** In einem Satz, darunter die Rakʿa-Folge (siehe unten).
   - **Warum lohnt es sich?** Die Faḍāʾil, also Vorzüge aus Koran und Hadith, mit Quelle in Klammern.
   - **Worauf achte ich?** Was verpönt ist oder leicht verpasst wird, in einem Hinweiskasten in Gold.
5. **„Mehr zu …“** zum Aufklappen, die zweite Schicht. Hinein gehört:
   - die genaue Abgrenzung der Zeiten mit den Ansichten innerhalb der Hanafiten (z. B. ʿAṣr beim einfachen oder doppelten Schatten),
   - welcher Ansicht Diyanet folgt,
   - weitere Hadithe,
   - wie die App eine Zeit rechnet, wenn Diyanet sie nicht liefert (z. B. Makrūh 20 Minuten vor Ẓuhr),
   - Muʾakkada und Mandūb im Unterschied (heute im Reiter Faǧr).
6. **Belege** als Endnoten: nummeriert, Autor, Werk und Seite. Das arabische Original klappt man je Beleg auf. Im Text stehen hochgestellte Ziffern in Gold, die zur Endnote springen.

### Begriffe

Begriffe sind im Text gepunktet unterstrichen. Antippen öffnet ein kleines Blatt mit der Erklärung. Die Erklärungen kommen aus dem vorhandenen, freigegebenen `GLOSSAR` (Herkunft, Bedeutung, Koran und Hadith). Die heutige Begriffsliste am Ende des Reiters entfällt.

### Rakʿa-Folge

Die Rakʿa stehen als Folge von Blöcken in der Reihenfolge, in der man betet. Jeder Block ist so breit, wie er Rakʿa hat.
- Farḍ: gefüllt in Waldgrün.
- Sunna muʾakkada: umrandet in Waldgrün.
- Empfohlen (mandūb): gestrichelt in Gold.

- **Witr (wāǧib)** bei ʿIšāʾ: gefüllt in Gold. Er steht als eigener Block nach den zwei Sunna muʾakkada. In der Legende heißt er „Witr, wāǧib“, und Wāǧib ist antippbar. Das ist meine Entscheidung; Gold auf Grund muss den Kontrasttest bestehen.
- **Schwankende Zahl**, z. B. vor ʿAṣr „4 oder 2“ (al-Qudūrī lässt die Wahl): ein gestrichelter Block in der Breite der größeren Zahl, beschriftet „4 oder 2“. Auch das ist meine Entscheidung.

Darunter steht eine kurze Legende, deren Begriffe antippbar sind. Am Freitag zeigt der Reiter Ẓuhr zusätzlich eine zweite Folge für die Ǧumʿa (wie heute in `VerstehenTabs.kt`). Die Zahlen bleiben dieselben wie heute (Marāqī l-falāḥ S. 139, 145 bis 147).

## Ton C, Lehrbuch

- **Führung:** Merksatz, dann W-Fragen als Zwischenköpfe, darunter kurze, ganze Sätze.
- **Fiqh-Aussagen:** Sie geben das Zitat sinngemäß wieder und tragen eine Endnote mit dem Direktzitat. Ohne Zitat steht keine Fiqh-Aussage da. Ausnahme bleiben die Farḍ-Zahlen, die laut Entscheidung vom 05.11.2026 ohne Zitat stehen.
- **Faḍāʾil:** Sie schöpfen breit aus der Hadith-Literatur. Schwache Hadithe muss die Seite nicht kennzeichnen (Wunsch des Nutzers). Die Quelle steht immer dabei, z. B. „(Tirmiḏī 478)“.
- **Probe für Ẓuhr:** steht im Entwurf. Ihre Belege sind geprüft:
  - al-Qudūrī, Muḫtaṣar, S. 23 (Beginn und Ende)
  - Marāqī l-falāḥ, S. 76 (Zawāl) und S. 145 f. (Rakʿa)
  - Tirmiḏī 478 (Tore des Himmels)
  - Tirmiḏī 428 (vier davor und vier danach)

## Gestaltung

Es gelten DESIGN.md und das vorhandene Thema (`ui/theme/`):
- Grund am Tag Weiß, in der Nacht Schwarz.
- Akzent Waldgrün, Gold für Belege und Hinweise.
- Noto Serif Display Regular für Name und Merksatz, Inter für den Text.
- Noto Naskh Arabic für das Arabische, 19 sp in Stein.

Die Reiter sind Pillen wie heute: Der gewählte ist gefüllt in Waldgrün, die anderen umrandet.

Alles muss mit Schriftgröße „Sehr groß“, im Hochkontrast und in der Nacht lesbar sein. Bestehende Prüfungen (`PaletteContrastTest`, `FontCoverageTest`) gelten auch für die neuen Farben und Zeichen.

## Inhalte, die neu zu schreiben sind

Für jeden der fünf Reiter und das Kapitel „Freiwillig und verpönt“:
- Merksatz,
- die vier W-Antworten,
- „Mehr zu …“,
- Endnoten.

Die vorhandenen Zitate in `VerstehenContent.kt` und das `GLOSSAR` bleiben die Grundlage. Neue Zitate kommen nur aus hanafitischen Werken mit Fundstelle, geprüft in Shamela. Neue Hadithe werden im Wortlaut über hadith-unlocked geprüft.

Alle neuen Texte legt der Bauende dem Nutzer vor dem Einbau zur Freigabe vor, Reiter für Reiter oder gesammelt.

## Auslieferung

Die neue Seite erscheint **auf einmal** (Entscheidung 06.11.2026). Bis alle sechs Kapitel freigegeben und gebaut sind, bleibt die alte Seite in den Releases. Gebaut wird auf einem eigenen Zweig oder hinter einem Schalter, der in Release-Builds aus ist. Was davon, entscheidet der Bauende.

## Was beim Bauenden bleibt

- Ob das Himmelsband die vorhandene `SundialView` weiterverwendet oder neu gezeichnet wird.
- Wie die Suche intern indexiert (Inhalte sind lokal und klein).
- Die Animation beim Aufklappen und Springen. Dabei muss die Einstellung für weniger Bewegung beachtet werden.
- Wie die Tests aufgeteilt werden. Die vorhandenen Wächter-Tests (z. B. dass `VerstehenScreen` keine Uhrzeit formatiert) bleiben sinngemäß erhalten.
