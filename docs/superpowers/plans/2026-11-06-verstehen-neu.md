# Verstehen neu: Bauplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Seite „Verstehen“ wird als Nachschlagewerk im Lehrbuch-Ton neu gebaut:
- sechs Reiter, Himmelsband, zwei Schichten,
- Belege als Endnoten, antippbare Begriffe, Suche, Rakʿa-Folge.

Sie erscheint auf einmal und ersetzt die alte Seite.

**Architecture:** Inhalt und Logik liegen in reinen Kotlin-Dateien ohne Android-Bezug, damit sie im JVM-Test laufen:
- Modell, Textauszeichnung, Zeitzeilen, Sprungziele, Suche.
- Die Compose-Oberfläche setzt nur zusammen.

Die neue Seite steht bis zum letzten Task hinter dem Schalter `BuildConfig.VERSTEHEN_NEU`. Er ist im Debug-Build an und im Release aus. Erst Task 9 schaltet ihn fest ein und entfernt die alte Seite.

**Tech Stack:**
- Kotlin, Jetpack Compose (Material 3), HorizontalPager, Canvas
- JUnit 4 (JVM-Tests, kein Robolectric); Quelltext lesende Wächter-Tests wie im Projekt üblich

**Spec:** `docs/superpowers/specs/2026-11-06-verstehen-neu-design.md` (Entwurf zum Ansehen: `docs/entwurf/verstehen-konzept.html`)

## Global Constraints

**Texte und Belege**
- In sichtbaren Texten kein U+2014 und kein U+2013, auch nicht in Zeitspannen („11:45 bis 12:05“, `timeRange()`); keine KI-Floskeln.
- Alle Begriffe in DMG-Umschrift (Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, Ḍuḥā, Makrūh, Zawāl); ʿ = U+02BF, ʾ = U+02BE.
- Fiqh-Aussagen nur als hanafitisches Direktzitat mit Fundstelle, als Endnote. Ausnahme: Farḍ-Zahlen ohne Zitat (Entscheidung 05.11.2026).
- Faḍāʾil breit aus der Hadith-Literatur, immer mit Quelle in Klammern, ohne Kennzeichnung schwacher Hadithe.
- Arabische Zitate wörtlich aus Shamela, nur Matn (nie Fußnote). Hadithe im Wortlaut über hadith-unlocked geprüft. Jede neue Fundstelle kommt in `docs/superpowers/specs/2026-10-28-zitate-belege.md`.

**Zeiten und Bilder**
- Bilder (Himmelsband, Tagesband) zeigen keine Uhrzeiten.
- Zeiten: nur amtliche Diyanet-Zeiten. Von der App gerechnete Spannen tragen „von der App gerechnet“.
- Eine Verschiebung des Nutzers wird gekennzeichnet wie überall („12:09 (+5)“, `offsetMark`).

**Gestaltung (DESIGN.md)**
- Farben nur aus `Tokens` (`ui/theme/Color.kt`): Grund `canvas`, Akzent `accent`, Belege und Hinweise `gold`.
- Schriften: Noto Serif Display Regular für Name und Merksatz, Inter für den Text, Noto Naskh Arabic 19 sp in `stone` für Arabisch.
- Kleine Köpfe in Großbuchstaben in Waldgrün.

**Arbeitsweise**
- Neue Texte werden dem Nutzer vor dem Einbau zur Freigabe vorgelegt. Ohne seine Freigabe kommt kein Text in den Code.
- Gradle nur so: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain <tasks>` (PowerShell).
- Quelldateien nie über PowerShell `Get-Content`/`Set-Content` bearbeiten (UTF-8).
- Commit nur nach Rückfrage beim Nutzer; nie `ezan/` committen.

## Review Focus

1. **Suche ohne Sonderzeichen:** Wer „zuhr“, „asr“ oder „isha“ tippt, erwartet Ẓuhr, ʿAṣr und ʿIšāʾ als Treffer. Test in Task 4.
2. **Keine Zeiten:** Ohne Ort, offline oder ohne Diyanet-Daten erwartet man, dass die Seite vollständig erscheint und nur die Zeitzeile fehlt, statt leer zu bleiben oder abzustürzen. Tests in Task 3 (null) und Task 7 (Bildschirm).
3. **ʿIšāʾ ohne Faǧr von morgen:** Das Ende fehlt. Erwartet wird „ab 18:25“, nicht „18:25 bis null“. Test in Task 3.
4. **Jeder Sprung aus Heute landet:** Jede `Section` und jeder `Moment` muss auf einen vorhandenen Reiter und Abschnitt zeigen. Test über alle Enum-Werte in Task 3.
5. **Schriftgröße „Sehr groß“:** Kleine Rakʿa-Blöcke wie „2“ neben „4 oder 2“ dürfen ihren Text nicht abschneiden. Dafür hat jeder Block ein Mindestgewicht. Test in Task 2, Sichtprüfung in Task 9.

---

## Dateien

Alle neuen Dateien liegen in `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/`, Präfix `Lehr`:

| Datei | Verantwortung |
|---|---|
| `LehrModell.kt` | Datentypen: `Teil`, `Kapitel`, `Abschnitt`, `RakaBlock`, `RakaArt`, `Band`, `Beleg` |
| `LehrText.kt` | Auszeichnung `[[Begriff]]` und `{beleg-id}` zerlegen, Endnoten nummerieren |
| `LehrZeiten.kt` | Zeitzeile je Abschnitt, Sprungziele aus Heute, Startreiter |
| `LehrSuche.kt` | Suche mit Normalisierung von DMG-Zeichen |
| `LehrInhalt.kt` | `LEHRE: List<Kapitel>`, die freigegebenen Texte (Tasks 8a bis 8f) |
| `Himmelsband.kt` | Canvas-Bild je `Band`, ohne Uhrzeit |
| `VerstehenNeuScreen.kt` | Oberfläche: Suche, Reiter, Kapitelseite, Begriffsblatt, Endnoten |

Tests liegen in `app/src/test/kotlin/de/gebetszeiten/ui/verstehen/`: `LehrTextTest`, `LehrZeitenTest`, `LehrSucheTest`, `LehrInhaltTest`, `VerstehenNeuWiringTest`.

Zu ändern sind:
- `app/build.gradle.kts`: BuildConfig-Schalter.
- `MainActivity.kt`: Weiche zur neuen Seite.
- In Task 9: die alte Seite und ihre Tests entfernen.

---

### Task 1: Schalter und Weiche

**Files:**
- Modify: `app/build.gradle.kts` (Block `buildFeatures` Zeile 88, `buildTypes`)
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt:358`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/VerstehenNeuScreen.kt` (vorerst leerer Platzhalter mit derselben Signatur)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/verstehen/VerstehenNeuWiringTest.kt`

**Interfaces:**
- Produces: `@Composable fun VerstehenNeuScreen(inner: PaddingValues, settings: AppSettings, focus: Section?, onFocusConsumed: () -> Unit)`; `BuildConfig.VERSTEHEN_NEU: Boolean`

- [ ] **Step 1: Failing Test `weicheNachSchalter`** liest `MainActivity.kt` und prüft, dass `Tab.VERSTEHEN ->` sowohl `VerstehenNeuScreen(` als auch `BuildConfig.VERSTEHEN_NEU` enthält. Ein zweiter Test `releaseHatSchalterAus` liest `app/build.gradle.kts` und erwartet `buildConfigField("boolean", "VERSTEHEN_NEU", "false")` in `defaultConfig` und `buildConfigField("boolean", "VERSTEHEN_NEU", "true")` im Build-Typ `debug`.
- [ ] **Step 2:** `:app:testOnlineDebugUnitTest --tests "*VerstehenNeuWiringTest*"`, erwartet: FAIL.
- [ ] **Step 3:** In `app/build.gradle.kts` `buildFeatures { buildConfig = true }` setzen. In `defaultConfig` den Wert `false` setzen, im Debug-Build `true`. In `MainActivity` bei `Tab.VERSTEHEN` mit `if (BuildConfig.VERSTEHEN_NEU) VerstehenNeuScreen(...) else VerstehenScreen(...)` verzweigen, mit denselben Argumenten.
- [ ] **Step 4:** Test erwartet PASS, `:app:assembleOnlineRelease` erwartet BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit (nach Rückfrage): `feat(verstehen): Schalter für die neue Seite (nur Debug)`

### Task 2: Modell, Textauszeichnung, Endnoten

**Files:**
- Create: `LehrModell.kt`, `LehrText.kt`
- Test: `LehrTextTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Teil { FAJR, ZUHR, ASR, MAGHRIB, ISHA, DUHA, AWWABIN, TAHAJJUD, SHURUQ, ZAWAL, ISFIRAR }
enum class Band { FAJR, ZUHR, ASR, MAGHRIB, ISHA, TAG }
enum class RakaArt { FARD, MUAKKADA, MANDUB, WITR }
data class RakaBlock(val art: RakaArt, val anzahl: Int, val beschriftung: String? = null) // „4 oder 2“
data class Beleg(val id: String, val quote: Quote)            // Quote aus VerstehenContent.kt
data class Frage(val kopf: String, val text: String)          // Text mit Auszeichnung
data class Abschnitt(
    val teil: Teil, val kopf: String /* „Mittagsgebet“ */, val name: String /* „Ẓuhr“ */,
    val merksatz: String, val wann: String, val wieviele: String?, val rakaat: List<RakaBlock>,
    val rakaatGumua: List<RakaBlock>? = null, val warum: String, val achtung: String,
    val mehr: List<Frage>,
)
data class Kapitel(val reiter: String, val band: Band, val abschnitte: List<Abschnitt>, val belege: List<Beleg>)
sealed interface Stueck { data class Text(val s: String); data class Begriff(val key: String, val anzeige: String); data class Verweis(val nr: Int) }
fun zerlege(text: String, nummer: (String) -> Int): List<Stueck>
fun nummern(kapitel: Kapitel): Map<String, Int>               // Beleg-ID -> 1, 2, … nach erstem Auftreten
fun rakaGewicht(block: RakaBlock): Float                       // max(anzahl, 2)
```
- **Auszeichnung:**
  - `[[Zawāl]]` steht für einen Begriff mit dem Schlüssel `Zawāl`.
  - `[[Sunna muʾakkada|bekräftigte Sunna]]` steht für Schlüssel und Anzeigetext.
  - `{zuhr-beginn}` steht für einen Verweis auf die Beleg-ID.

- [ ] **Step 1: Failing Tests:**
  - `zerlege("Ab dem [[Zawāl]].{a}", {1})` == `[Text("Ab dem "), Begriff("Zawāl","Zawāl"), Text("."), Verweis(1)]`
  - `[[A|b]]` ergibt `Begriff("A","b")`.
  - `nummern` mit Verweis-Reihenfolge b, a, b (über merksatz, wann, …, mehr eines Abschnitts, dann des nächsten) ergibt `{b=1, a=2}`.
  - Ein unbekannter Verweis wirft `IllegalArgumentException`.
  - `rakaGewicht(RakaBlock(MUAKKADA, 2)) == 2f`, `rakaGewicht(RakaBlock(WITR, 3)) == 3f`, `rakaGewicht(RakaBlock(FARD, 1)) == 2f`.
- [ ] **Step 2:** FAIL prüfen.
- [ ] **Step 3:** Implementieren. Die Reihenfolge der Felder beim Nummerieren ist: merksatz, wann, wieviele, warum, achtung, dann `mehr` in Listenfolge.
- [ ] **Step 4:** PASS prüfen.
- [ ] **Step 5:** Commit (nach Rückfrage).

### Task 3: Zeitzeilen und Sprungziele

**Files:**
- Create: `LehrZeiten.kt`
- Test: `LehrZeitenTest.kt`

**Interfaces:**
- Consumes: `VerstehenDay`, `timeRange`, `offsetMark`, `TimeOffsets`, `Section`, `Moment` (vorhanden)
- Produces:
```kotlin
fun zeitzeile(teil: Teil, day: VerstehenDay?, ort: String, offsets: TimeOffsets): String?
data class Ziel(val kapitel: Int, val teil: Teil)
fun zielVon(section: Section): Ziel
fun zielVonMoment(moment: Moment): Ziel
val GERECHNET: Set<Teil> // DUHA, AWWABIN, TAHAJJUD, SHURUQ, ZAWAL, ISFIRAR
```
- **Wortlaut der Zeitzeile:**
  - amtlich: `"Heute in $ort ${timeRange(a, b)}"`,
  - gerechnet: `"Heute etwa ${timeRange(a, b)}, von der App gerechnet"`,
  - ohne Ende: `"Heute in $ort ab $a"`,
  - `day == null` ergibt `null`.
- **Verschiebung:** Die Uhrzeit erhält `" (+5)"` aus `offsetMark` für das betroffene Gebet, wie in `PrayerNotifier`.
- **Sprungziele:** Kapitel 0 bis 4 sind die Gebete, 5 ist „Freiwillig und verpönt“.
  - `JAMAA` führt zu (0, FAJR).
  - `SHURUQ_MAKRUH` führt zu (5, SHURUQ), `DUHA` zu (5, DUHA), `ISFIRAR_MAKRUH` zu (5, ISFIRAR), `AWWABIN` und `TAHAJJUD` entsprechend.
  - `Moment.ZAWAL` führt zu (5, ZAWAL), `Moment.SHURUQ` zu (5, SHURUQ), `Moment.DUHA` zu (5, DUHA).
- **Spannen:** Sie kommen aus denselben Feldern wie `tabSpan` und `sideTime` in `VerstehenTabs.kt`. Zawāl ist `karaha.zevalStart` bis `t.dhuhr` (Feldnamen in `KarahaTimes` prüfen). Die Uhrzeit wird nur hier formatiert.

- [ ] **Step 1: Failing Tests mit einem festen `VerstehenDay`** (Muster aus `VerstehenTabsTest`):
  - `zeitzeile(ZUHR, day, "Nürnberg", NONE) == "Heute in Nürnberg 12:04 bis 14:27"` (Tageszeiten 12:04 und 14:27 im Testtag)
  - Ein Ḍuḥā-Text endet auf `", von der App gerechnet"`.
  - ʿIšāʾ mit `nextFajr = null` ergibt `"Heute in Nürnberg ab 18:25"`.
  - `zeitzeile(ZUHR, null, …) == null`.
  - Mit `TimeOffsets.NONE.with(Prayer.DHUHR, 5)` enthält die Zeile `"12:09 (+5)"`.
  - Kein Ergebnis enthält U+2013 oder U+2014.
  - `zielVon` für **alle** `Section.entries` und `zielVonMoment` für **alle** `Moment.entries` zeigen auf `kapitel in 0..5`, und der Teil gehört zu diesem Kapitel. Die Zuordnung prüft eine Tabelle im Test, unabhängig von `LEHRE`.
- [ ] **Step 2:** FAIL prüfen.
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** PASS prüfen. Dazu kommt der Wächter `VerstehenNeuWiringTest.nurLehrZeitenFormatiert`: In `VerstehenNeuScreen.kt` und `Himmelsband.kt` kommen weder `HH:mm` noch `DateTimeFormatter` vor.
- [ ] **Step 5:** Commit (nach Rückfrage).

### Task 4: Suche

**Files:**
- Create: `LehrSuche.kt`
- Test: `LehrSucheTest.kt`

**Interfaces:**
- Consumes: `Kapitel`, `zerlege` (nur Anzeigetext), `GLOSSAR`
- Produces:
```kotlin
fun normalisiere(s: String): String
data class Treffer(val ziel: Ziel, val ort: String /* „Ẓuhr · Wann bete ich?“ */, val ausschnitt: String)
fun suche(frage: String, lehre: List<Kapitel>, glossar: Map<String, GlossaryTerm>): List<Treffer>
```
- **`normalisiere`:**
  - NFD zerlegen, kombinierende Zeichen entfernen.
  - ʿ, ʾ, ', ’ entfernen.
  - Kleinschreibung.
  - Diese Zeichen in ASCII umsetzen: ǧ zu „g“, š zu „s“, ḫ zu „h“, ġ zu „g“, ḏ zu „d“, ṯ zu „t“. Bei Bedarf zusätzlich „dsch“ zu „g“ und „sch“ zu „s“, damit auch „Ischa“ trifft.
- **Durchsucht werden:**
  - Reiter und Name,
  - Merksatz, die W-Antworten, `mehr` (Kopf und Text),
  - das Deutsch der Belege.
- **Glossarbegriffe:** Ihre Treffer führen zum ersten Abschnitt, der den Begriff verwendet.
- **Reihenfolge:** Treffer in Name oder Kopf zuerst, dann Text. Höchstens 20 Treffer; eine leere Frage ergibt eine leere Liste.

- [ ] **Step 1: Failing Tests** (gegen eine kleine Test-`Kapitel`-Liste, nicht gegen `LEHRE`):
  - `normalisiere("ʿIšāʾ") == "isa"`
  - `suche("zuhr")` hat einen Treffer mit Ziel (1, ZUHR) an erster Stelle.
  - `suche("asr")` findet ʿAṣr.
  - `suche("Zawal")` findet den Abschnitt mit `[[Zawāl]]`.
  - `suche("")` ist leer.
  - `suche("xyz")` ist leer.
- [ ] **Step 2 bis 4:** FAIL, implementieren, PASS.
- [ ] **Step 5:** Commit (nach Rückfrage).

### Task 5: Inhaltsregeln als Tests (ohne Inhalt)

**Files:**
- Create: `LehrInhalt.kt` mit `val LEHRE: List<Kapitel>`. Vorerst steht dort nur Ẓuhr, aus der freigegebenen Probe in `docs/entwurf/verstehen-konzept.html`, Ton C, Belege wie dort.
- Test: `LehrInhaltTest.kt`

**Interfaces:**
- Consumes: Task 2 (Modell, `nummern`), Task 3 (`GERECHNET`)
- Produces: `LEHRE`. Die Reihenfolge der Reiter ist Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, „Freiwillig und verpönt“; das Kapitel 5 hat die Teile in Tagesfolge (SHURUQ, DUHA, ZAWAL, ISFIRAR, AWWABIN, TAHAJJUD).

- [ ] **Step 1: Tests**, die für jedes vorhandene Kapitel gelten. So schützen sie jedes später hinzugefügte Kapitel mit:
  - **Zeichen:** kein U+2013 oder U+2014 in irgendeinem Text.
  - **Auszeichnung:** Jeder `[[Begriff]]`-Schlüssel steht in `GLOSSAR`. Jeder `{id}` gehört zu einem `Beleg` desselben Kapitels. Kein Beleg bleibt unbenutzt.
  - **Felder:** Merksatz, wann, warum und achtung sind nicht leer. Jedes `Beleg.quote` hat Arabisch, Deutsch und Quelle.
  - **Faḍāʾil:** Jeder `warum`-Text enthält mindestens eine Quellenangabe in Klammern (Regex `\([^)]*\d+\)`).
  - **Farḍ-Zahlen:** Faǧr 2, Ẓuhr 4, ʿAṣr 4, Maġrib 3, ʿIšāʾ 4, als Summe der `FARD`-Blöcke, geprüft für vorhandene Kapitel.
  - **Kapitel 5:** Ohne Farḍ-Block; jeder Abschnitt aus `GERECHNET` existiert, sobald das Kapitel existiert.
- [ ] **Step 2:** Die Ẓuhr-Texte aus der Probe in `LEHRE` eintragen, ohne neue Formulierungen. Rakʿa-Folge: MUAKKADA 4, FARD 4, MUAKKADA 2, MANDUB 2. Ǧumʿa-Folge: MUAKKADA 4, FARD 2, MUAKKADA 4.
- [ ] **Step 3:** Tests erwarten PASS.
- [ ] **Step 4:** Commit (nach Rückfrage).

### Task 6: Himmelsband

**Files:**
- Create: `Himmelsband.kt`
- Test: Ergänzung in `VerstehenNeuWiringTest.kt`

**Interfaces:**
- Consumes: `Band`, `Tokens`
- Produces:
  - `@Composable fun Himmelsband(band: Band, modifier: Modifier = Modifier)`
  - `@Composable fun Tagesband(teile: List<Teil>, onTeil: (Teil) -> Unit, modifier: Modifier = Modifier)`

**Was die Bilder zeigen:**
- Beide zeichnen mit `Canvas` und nur mit Token-Farben. Zur Wahl stehen der Sonnenbogen in `ochre`, der Horizont in `hairline`, die Sonne in `gold`, Stab und Schatten in `ink` und Text in `stone`.
- Die Bilder je `Band`:
  - **FAJR:** Licht unter dem Osthorizont, ohne Sonne.
  - **ZUHR:** Sonne knapp nach dem Höchststand, ein kurzer Stabschatten nach Osten.
  - **ASR:** Stab mit einfachem und doppeltem Schatten, beide markiert. Es gilt dieselbe Aussage wie bei den zwei Linien der `SundialView`.
  - **MAGHRIB:** Sonne knapp unter dem Westhorizont.
  - **ISHA:** Dunkler Himmel mit Sternen.
- **Beschriftung:** nur „Osten“, „Westen“ und „Höchststand“.
- **Tagesband:** ein Streifen mit den sechs Teilen als antippbare Abschnitte in Tagesfolge, gleich breit, ohne Zeiten. Jeder Abschnitt ist mindestens 48 dp hoch und trägt eine Inhaltsbeschreibung mit dem Namen des Teils.
- **Barrierefreiheit:** Jedes Band hat eine Inhaltsbeschreibung in Worten, etwa „Sonnenbahn: die Sonne hat den höchsten Punkt gerade überschritten“.

- [ ] **Step 1:** Wächter-Test `himmelsbandOhneUhrzeit`: `Himmelsband.kt` enthält kein `format(`, kein `HH`, kein `ZonedDateTime`. Wächter-Test `himmelsbandBeschrieben`: Jeder `Band`-Zweig setzt eine `contentDescription`.
- [ ] **Step 2 bis 4:** FAIL, implementieren, PASS. Sichtprüfung im Emulator bei Tag, Nacht und Hochkontrast. Das Debug-Build ist dazu nötig, weil der Schalter dort an ist.
- [ ] **Step 5:** Commit (nach Rückfrage).

### Task 7: Bildschirm

**Files:**
- Modify: `VerstehenNeuScreen.kt` (Platzhalter aus Task 1 ersetzen)
- Test: `VerstehenNeuWiringTest.kt`

**Interfaces:**
- Consumes: alles aus Tasks 2 bis 6, `GLOSSAR`, `LocalTokens`, `PrayerProvider.daily`, `IslamicWindows` (wie heute in `VerstehenScreen.kt:122-129`)
- Produces: die fertige Seite.

**Von oben nach unten:**

1. **Suchfeld:** ein `OutlinedTextField` mit runden Ecken. Mit Text ersetzt eine Trefferliste die Reiter und den Pager. Ein Treffer springt zu `Ziel`, leert das Feld und scrollt zum Abschnitt.
2. **Reiter:** Pillen aus `TabRowPills` (aus der alten Datei übernehmen, umbenennen), darunter der `HorizontalPager` über `LEHRE`.
3. **Kapitelseite:** eine `LazyColumn`. Bei Kapitel 5 steht zuerst das `Tagesband`, das zum Abschnitt springt. Danach folgt je Abschnitt:
   1. `Himmelsband` (nur Kapitel 0 bis 4; `kapitel.band`)
   2. Kopf (`labelSmall`, Großbuchstaben, `accent`), Name (`displayLarge`-Serife)
   3. `zeitzeile(...)` in `stone`, wenn nicht null
   4. Merksatz im Kasten (`accent` mit etwa 8 % auf `canvas`, Serife `headlineSmall`, kleiner Kopf „MERKSATZ“)
   5. Die W-Fragen als `titleSmall`-Köpfe:
      - „Wann bete ich?“
      - „Wie viele Rakʿa?“ (mit `RakaFolge`)
      - „Warum lohnt es sich?“
      - „Worauf achte ich?“ im Hinweiskasten mit `gold`
      - In Kapitel 5 heißen sie bei Makrūh-Teilen „Wann gilt das?“ statt „Wann bete ich?“; die übrigen Köpfe gleich.
   6. „Mehr zu {name}“: Zeile mit Pfeil, klappt `mehr` auf. Die Animation folgt `LocalAccessibilityManager`; bei reduzierter Bewegung gibt es keine Animation.
   7. Belege des Kapitels einmal am Kapitelende, nummeriert. Je Beleg Quelle in `stone` und „Arabisch“ zum Aufklappen (Naskh 19 sp, rechts nach links).
4. **Text:** `zerlege` erzeugt einen `AnnotatedString`.
   - Begriffe sind gepunktet unterstrichen in `accent`, Antippen öffnet ein `ModalBottomSheet` mit `GLOSSAR[key]`.
   - Verweise stehen hochgestellt in `gold`, Antippen scrollt zum Beleg.
   - Klickbare Teile über `LinkAnnotation.Clickable`.
   - TalkBack liest einen Verweis als „Beleg 3“.
5. **`RakaFolge(blocks: List<RakaBlock>)`:** eine `Row` mit `Modifier.weight(rakaGewicht(b))`. Die Arten:
   - FARD gefüllt `accent` mit Text in `canvas`,
   - MUAKKADA mit 1,5 dp Rand in `accent`,
   - MANDUB gestrichelt in `gold` (über `drawBehind` mit `PathEffect.dashPathEffect`),
   - WITR gefüllt `gold` mit Text in `canvas`.
   
   Darunter steht die Legende, ihre Begriffe sind antippbar. Ist `rakaatGumua` gesetzt und heute Freitag, folgt eine zweite Folge mit dem Kopf „Am Freitag: Ǧumʿa“.
6. **Fokus und Start:** `focus` (aus Heute) führt über `zielVon` zum Reiter und Abschnitt. Ohne `focus` öffnet die Seite einmal beim laufenden Abschnitt über `zielVonMoment(heroState(...).moment)`; das Muster steht in `VerstehenScreen.kt:131-153`.
7. **Minutentakt:** wie in `VerstehenScreen.kt:110-120`, nur im Vordergrund.

- [ ] **Step 1: Wächter-Tests (Quelltext lesend)** für `VerstehenNeuScreen.kt`:
  - `nutztZerlegeUndGlossar` enthält `zerlege(` und `GLOSSAR[`.
  - `arabischRechtsNachLinks` enthält `LayoutDirection.Rtl` oder `TextDirection.Rtl`.
  - `fokusUeberZielVon` enthält `zielVon(` und `zielVonMoment(`.
  - `minutenSchleifeNurImVordergrund` enthält `repeatOnLifecycle(Lifecycle.State.STARTED)`.
  - `keineFestenFarben` enthält kein `Color(0x`.
  - `ohneZeitenKeinAbsturz`: Das Ergebnis von `zeitzeile(` wird nur über `?.let` oder `if (… != null)` gezeigt, kein `!!`.
- [ ] **Step 2 bis 4:** FAIL, implementieren, PASS.
- [ ] **Step 5:** Im Emulator (Debug-Build) prüfen:
  - Ẓuhr wie im Entwurf.
  - Suche „zuhr“ springt.
  - Begriffsblatt öffnet.
  - Beleg 1 springt und klappt auf.
  - Ohne Ort (Ersteinrichtung abbrechen oder Flugmodus mit leerem Speicher) erscheint die Seite ohne Zeitzeile.
  - Screenshot an den Nutzer.
- [ ] **Step 6:** Commit (nach Rückfrage).

### Task 8a bis 8f: Inhalte, Kapitel für Kapitel

Reihenfolge: 8a Faǧr, 8b Ẓuhr (Probe ergänzen um „Mehr zu Ẓuhr“ und Ǧumʿa), 8c ʿAṣr, 8d Maġrib, 8e ʿIšāʾ, 8f „Freiwillig und verpönt“ (sechs Abschnitte).

Je Kapitel gelten dieselben Schritte:

**Files:**
- Modify: `LehrInhalt.kt`, `docs/superpowers/specs/2026-10-28-zitate-belege.md`
- Test: `LehrInhaltTest.kt` gilt automatisch, siehe Task 5.

- [ ] **Step 1: Quellen sammeln.**
  - Vorhandene Zitate aus `VerstehenContent.kt` für diesen Reiter übernehmen. Ihre Belege stehen schon in der Belegdatei.
  - Fehlende Fiqh-Aussagen in Shamela suchen: Marāqī 412, Radd al-muḥtār 21613, Ṭaḥṭāwī 11993, al-Qudūrī. Nur Matn, mit `shamela_get_citation`.
  - Faḍāʾil über hadith-unlocked im Wortlaut holen.
- [ ] **Step 2: Texte im Ton C schreiben,** mit den Feldern `merksatz`, `wann`, `wieviele`, `warum`, `achtung`, `mehr`:
  - In `mehr` stehen mindestens die genaue Abgrenzung der Zeit mit den hanafitischen Ansichten und die Diyanet-Ansicht, wo es eine gibt.
  - Wo nötig steht dort auch die App-Rechnung (wie die heutigen `note`-Felder).
  - Im Kapitel Faǧr stehen in `mehr` außerdem Muʾakkada und Mandūb im Unterschied (Zitate vom 05.11.2026: Marāqī S. 31, Ibn ʿĀbidīn Bd. 1, S. 104 und S. 123).
- [ ] **Step 3: Dem Nutzer vorlegen** (Freigabe-Gate). Lesbar als HTML-Ansicht im Stil von `docs/entwurf/verstehen-konzept.html`, je Kapitel eine Seite mit den Belegen. Warten, bis er freigibt oder ändert.
- [ ] **Step 4:** Freigegebene Texte und Rakʿa-Folge eintragen.
  - ʿAṣr: MANDUB 4 mit Beschriftung „4 oder 2“, dann FARD 4.
  - Maġrib: FARD 3, MUAKKADA 2, MANDUB 6. Das Verhältnis zur Sunna in `mehr` erklärt das vorhandene Auwābīn-Zitat.
  - ʿIšāʾ: MANDUB 4, FARD 4, MUAKKADA 2, MANDUB 4, WITR 3.
  - Neue Fundstellen in die Belegdatei.
- [ ] **Step 5:** `LehrInhaltTest` erwartet PASS. Das Kapitel im Emulator ansehen.
- [ ] **Step 6:** Commit (nach Rückfrage): `content(verstehen): Kapitel <Name>`

### Task 9: Umschalten und Aufräumen

**Files:**
- Modify: `app/build.gradle.kts` (Schalter entfernen), `MainActivity.kt` (nur noch `VerstehenNeuScreen`)
- Delete:
  - `VerstehenScreen.kt`, `VerstehenTabs.kt`
  - die Teile von `VerstehenContent.kt`, die `LEHRE` nicht mehr nutzt: `VERSTEHEN`, `VerstehenEntry`, `Place`, `QuoteGroup`. `Quote`, `GLOSSAR`, `GlossaryTerm` und `TRANSLATION_NOTE` bleiben.
  - `SundialView.kt` und `SundialLayoutTest.kt`, wenn `Himmelsband` sie nicht nutzt.
  - `VerstehenTabsTest.kt`, `VerstehenWiringTest.kt` und die Teile von `VerstehenContentTest.kt`, die alte Typen prüfen.
- Modify: Wächter-Tests, die alte Namen erwarten (`grep -rn "VerstehenScreen\|VERSTEHEN_TABS\|PhotoKey" app/src/test`).

- [ ] **Step 1:** Prüfen, dass `LehrInhaltTest` sechs Kapitel sieht. Ein neuer Test `alleKapitelDa` erwartet `LEHRE.size == 6`, die Reiternamen in der festen Reihenfolge und in Kapitel 5 alle sechs Teile.
- [ ] **Step 2:** Den Schalter und die Weiche entfernen, die alten Dateien löschen. `PhotoKey` und die Fotos bleiben, solange `HeroState`, Heute und die Meldungen sie nutzen (`grep -rn "PhotoKey" app/src/main`).
- [ ] **Step 3:** Prüfen mit `test :app:lintOnlineRelease :wear:lintOnlineRelease`. Erwartet: alle Tests grün, 0 Lint-Fehler.
- [ ] **Step 4:** Release-Build im Emulator (`assembleOnlineRelease`, R8):
  - alle sechs Reiter, Suche, Sprung aus Heute (Nebenzeile Ḍuḥā antippen),
  - Schriftgröße „Sehr groß“ (Rakʿa-Blöcke nicht abgeschnitten),
  - Nacht und Hochkontrast,
  - Screenshots an den Nutzer.
- [ ] **Step 5:** Commit (nach Rückfrage): `feat(verstehen): neue Seite ersetzt die alte`
