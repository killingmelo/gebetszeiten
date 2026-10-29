# Design-Neuausrichtung Android-App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Android-App (Modul `app`) bekommt ein neues Erscheinungsbild nach DESIGN.md „Oura“: Reiter „Heute“ mit Foto und großer Restzeit, neuer Reiter „Zeiten verstehen“ mit hanafitischen Zitaten und berechneter Sonnenuhr, neu gestaltete übrige Bildschirme, Widget und Benachrichtigungen, korrigierter Qibla-Kompass.

**Architecture:** Alle Entscheidungen, die Logik tragen (Titel-Zustand, Tagesliste, Sonnenuhr-Geometrie, Kompass-Korrektur, Kontrast, Schriftabdeckung, Schreibregeln), liegen in reinen Kotlin-Funktionen mit JVM-Unit-Tests. Compose-Code ist dünn und wird, wie im Projekt üblich (kein Robolectric), über quelltextlesende Wiring-Tests und Emulator-Screenshots geprüft. Zeiten kommen ausschließlich aus `PrayerProvider.daily`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (BOM 2026.05.01), Glance (Widget), NotificationCompat, JUnit 4, gebündelte OFL-Schriften (`res/font`), WebP-Fotos (`res/drawable-nodpi`).

**Spec:** `docs/superpowers/specs/2026-10-28-design-neuausrichtung-design.md`

## Global Constraints

- Nur amtliche Diyanet-Zeiten; Bilder und Sonnenuhr liefern keine Uhrzeiten. Für ʿAṣr mit doppeltem Schatten wird keine Uhrzeit angezeigt.
- DMG-Umschrift für alle Begriffe (Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, Ḍuḥā, Auwābīn, Tahaǧǧud, Ǧamāʿa, Makrūh, Zawāl). ʿ = U+02BF, ʾ = U+02BE.
- Sichtbare Texte: kein „—“ (U+2014), kein „–“ (U+2013), auch nicht in Zeitspannen („11:45 bis 12:05“); keine KI-Floskeln.
- Zitate nur aus Shamela-Werkzeug-Ergebnissen; Matn und Fußnote getrennt; deutsche Übersetzung als eigene Übersetzung gekennzeichnet.
- Kontrast Text ≥ 4,5:1 (Hochkontrast ≥ 7:1); Tippziele ≥ 48 dp; TalkBack-Beschreibungen; Schriftskalierung bis 1,4 ohne Abschneiden; jede Bewegung über `rememberAnimationsEnabled()`.
- Nachts Grund `#000000`.
- Offline-Flavor ohne INTERNET: Fotos und Schriften gebündelt.
- Quelldateien nie über PowerShell Get-Content/Set-Content bearbeiten; Edit-Werkzeug oder Python mit UTF-8.
- Gradle immer mit `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat --console=plain …` (PowerShell).
- Tests: `:app:testOfflineDebugUnitTest` und `:app:testOnlineDebugUnitTest` grün nach jeder Aufgabe.
- Branch: `feat/app-design-review` (oder ein davon abgezweigter Branch); nur eigene Dateien committen, `ezan/` nie anfassen.

## Palette (aus der Spec, hier einmal zentral)

| Token | Tag | Nacht |
|---|---|---|
| canvas (Grund) | `#f7f1e8` | `#000000` |
| surface (Fläche) | `#ffffff` | `#19191c` |
| ink (Text) | `#1c1b1a` | `#f7f1e8` |
| stone (Nebentext) | `#4a4741` | `#a8a5a0` |
| hairline | `#e1dcd3` | `#232326` |
| accent | `#224228` | `#849671` |
| gold (Sonne, Makrūh, ʿAṣr) | `#af751b` | `#ddaa61` |
| ochre | `#bc924c` | `#bc924c` |
| error | `#e33a22` | `#e33a22` |

Asche `#838280` nur dort, wo Task 2 den Kontrast nachweist. Ouras Aktionsblau entfällt.

## Review Focus

1. **Mitternacht bis Faǧr:** Titel muss „ʿIšāʾ“ sein und die Restzeit bis Faǧr heute zählen, nicht „vor Faǧr“ leer bleiben. Test in Task 4.
2. **Letzter abgedeckter Tag:** nach ʿIšāʾ ohne Folgetag-Faǧr keine Restzeit, keine Faǧr-Zeile, kein Absturz. Tests in Task 4 und Task 5.
3. **Ein anderer Tag gewählt (Datumswechsel):** kein „noch …“, kein Live-Makrūh, Foto des ersten Gebets. Test in Task 4.
4. **Polar-/Randbreiten für die Sonnenuhr:** Sonne erreicht die ʿAṣr-Höhe nie oder geht nicht unter; Geometrie darf nicht hängen oder NaN zeichnen. Test in Task 8.
5. **Schriftgröße 1,4 auf 360 dp:** Titel wie „ʿIšāʾ“ und „noch 10 Std 59 Min“ dürfen nicht abgeschnitten werden. Layout ohne feste Höhen, Text darf umbrechen (Task 10); Prüfung im Emulator in Task 15.

---

### Task 1: DESIGN.md und Schriften mit DMG-Abdeckung

**Files:**
- Create: `DESIGN.md` (Kopie von `https://raw.githubusercontent.com/VoltAgent/awesome-design-md/main/design-md/oura/DESIGN.md`, oben ein Abschnitt „Abweichungen für die GebetszeitenApp“: Aktionsblau entfällt, Schriften ersetzt, Nachtgrund #000000, Schreibregeln, DMG)
- Create: `CLAUDE.md` mit der Zeile `@DESIGN.md` und je einer Zeile zu Schreibregeln und DMG
- Create: `app/src/main/res/font/` (Serife Light/Regular, Grotesk Regular/Medium/SemiBold, Naskh Regular), jeweils mit `OFL.txt` unter `app/src/main/assets/licenses/`
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/theme/FontCoverageTest.kt`

**Interfaces:**
- Produces: Font-Ressourcen `R.font.serif_light`, `R.font.serif_regular`, `R.font.sans_regular`, `R.font.sans_medium`, `R.font.sans_semibold`, `R.font.naskh_regular`.

- [ ] **Step 1: Schriftkandidaten herunterladen** (Freigabe für Downloads aus `github.com/google/fonts` beim Nutzer einholen, falls nicht bereits erteilt). Serife in dieser Reihenfolge prüfen: Newsreader, Fraunces, Noto Serif Display. Grotesk: Inter. Arabisch: Noto Naskh Arabic. Statische TTF-Dateien, nicht die variablen.

- [ ] **Step 2: Failing test schreiben**

```kotlin
class FontCoverageTest {
    private val dmg = "ʿʾǦǧḌḍḤḥṢṣṬṭẒẓṮṯḪḫḎḏĠġŠšĀāĪīŪū"
    private val arabic = "وقت العصر إذا صار ظل كل شيء مثله"
    private fun font(name: String) =
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, File("src/main/res/font/$name.ttf"))
    @Test fun latinFontsCoverEveryDmgGlyph() {
        for (n in listOf("serif_light", "serif_regular", "sans_regular", "sans_medium", "sans_semibold")) {
            val f = font(n)
            val missing = dmg.filter { !f.canDisplay(it) }
            assertEquals("$n fehlt: $missing", "", missing)
        }
    }
    @Test fun naskhCoversArabicSample() {
        val f = font("naskh_regular")
        assertEquals("", arabic.filter { it != ' ' && !f.canDisplay(it) })
    }
}
```

- [ ] **Step 3: Test laufen lassen** (`:app:testOfflineDebugUnitTest --tests "*FontCoverageTest*"`). Erwartet: FAIL, Dateien fehlen.

- [ ] **Step 4: Die erste Serife, die den Test besteht, als `serif_light.ttf`/`serif_regular.ttf` ablegen**, Inter als `sans_*`, Noto Naskh Arabic als `naskh_regular.ttf`. Dateinamen nur Kleinbuchstaben und Unterstriche. Gewählte Serife in DESIGN.md unter „Abweichungen“ eintragen.

- [ ] **Step 5: Test grün, beide Suiten grün.**

- [ ] **Step 6: Commit** `feat(app): DESIGN.md (Oura) und Schriften mit DMG-Abdeckung`

---

### Task 2: Palette, Typografie und Kontrast-Wächter

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/theme/Color.kt` (Palette oben; bestehende `md_*`-Werte ersetzen)
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/theme/Theme.kt` (vier Schemata auf die Palette umstellen, Typografie setzen; SideEffect für Systemleisten bleibt)
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/theme/Type.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/theme/Contrast.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/theme/PaletteContrastTest.kt`

**Interfaces:**
- Produces:
  - `object Palette { val Day: Tokens; val Night: Tokens; val DayHc: Tokens; val NightHc: Tokens }` mit `data class Tokens(val canvas: Color, val surface: Color, val ink: Color, val stone: Color, val hairline: Color, val accent: Color, val gold: Color, val ochre: Color, val error: Color)`
  - `val LocalTokens = staticCompositionLocalOf { Palette.Day }` (von allen Bildschirmen gelesen)
  - `fun contrastRatio(fg: Color, bg: Color): Double` (WCAG 2.x, sRGB-Linearisierung)
  - `val AppTypography: Typography` mit: `displayLarge` Serife Light 56 sp / 1.05 (Gebetsname), `headlineSmall` Serife Light 26 sp (Restzeit), `titleMedium` Sans Medium 16 sp, `bodyLarge` Sans Regular 16 sp, `bodyMedium` Sans Regular 14 sp, `labelSmall` Sans SemiBold 11 sp, Laufweite 1.2 sp (Großbuchstaben-Zeilen)
  - `val NaskhFamily: FontFamily`
- Material-Rollen: `background/surface = canvas`, `surfaceContainer* = surface` (Tag) bzw. `#0b0b0c … #19191c` (Nacht), `onSurface = ink`, `onSurfaceVariant = stone`, `outlineVariant = hairline`, `primary = accent`, `onPrimary = canvas`, `primaryContainer = accent` mit 12 % auf canvas, `tertiary = gold`, `error = error`.

- [ ] **Step 1: Failing test**

```kotlin
class PaletteContrastTest {
    private fun check(t: Tokens, min: Double) {
        for ((name, fg) in listOf("ink" to t.ink, "stone" to t.stone, "accent" to t.accent, "gold" to t.gold, "error" to t.error))
            for ((bgName, bg) in listOf("canvas" to t.canvas, "surface" to t.surface))
                assertTrue("$name auf $bgName: ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= min)
    }
    @Test fun dayAndNightMeetAa() { check(Palette.Day, 4.5); check(Palette.Night, 4.5) }
    @Test fun highContrastMeetsAaa() { check(Palette.DayHc, 7.0); check(Palette.NightHc, 7.0) }
    @Test fun nightCanvasIsTrueBlack() { assertEquals(Color(0xFF000000), Palette.Night.canvas) }
    @Test fun knownRatio() { assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01) }
}
```

- [ ] **Step 2: Test laufen lassen.** Erwartet: FAIL (Symbole fehlen).
- [ ] **Step 3: Implementieren.** Wo ein Spec-Wert den Test nicht besteht (Gold `#af751b` auf Creme liegt knapp unter 4,5), Wert minimal abdunkeln, bis er besteht, und die Abweichung in DESIGN.md vermerken. Hochkontrast: ink `#000000`/`#ffffff`, Akzente bis ≥ 7:1 nachdunkeln bzw. aufhellen.
- [ ] **Step 4: Test grün, beide Suiten grün.** `GebetszeitenTheme` stellt `LocalTokens` bereit.
- [ ] **Step 5: Commit** `feat(app): Oura-Palette, Typografie, Kontrast-Wächter`

---

### Task 3: Schreibregeln für sichtbare Texte

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (alle — und – in Werten ersetzen; Zeitspannen „%1$s bis %2$s“)
- Modify: alle Kotlin-Dateien unter `app/src/main/kotlin`, deren String-Literale — oder – enthalten (u. a. `ui/OnboardingFlow.kt`, `ui/MainActivity.kt` Zeitspannen-Literale, `prayer/*Notice*.kt`, `notify/*Text*.kt`)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/CopyRulesTest.kt`

**Interfaces:**
- Produces: `fun timeRange(start: String, end: String): String` in `app/src/main/kotlin/de/gebetszeiten/prayer/TimeRange.kt`, liefert `"$start bis $end"`. Alle Zeitspannen gehen hier durch.

- [ ] **Step 1: Failing test.** Liest `strings.xml` (nur `<string>`-Werte) und alle `.kt` unter `src/main/kotlin`; entfernt Kommentare wie in `SettingsLayoutTest.quelltext()`; sucht String-Literale per `Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"")`; Assertion: kein Treffer enthält `\u2014` oder `\u2013`. Fehlermeldung listet Datei und Literal. Zweiter Test: `assertEquals("11:45 bis 12:05", timeRange("11:45", "12:05"))`.
- [ ] **Step 2: Test laufen lassen.** Erwartet: FAIL mit Liste der Fundstellen.
- [ ] **Step 3: Fundstellen umformulieren** (Punkt, Komma, Doppelpunkt, „bis“). Inhalt nicht verändern, nur Satzbau. Bestehende Tests mit erwarteten Texten (`OngoingTextTest`, `NoTimesNoticeTest`, `OnboardingFlowTest` …) mitziehen.
- [ ] **Step 4: Test grün, beide Suiten grün.**
- [ ] **Step 5: Commit** `fix(app): sichtbare Texte ohne Gedanken- und Halbgeviertstriche`

---

### Task 4: Titel-Zustand der Hauptansicht (rein)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/HeroState.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/HeroStateTest.kt`

**Interfaces:**
- Consumes: `DailyPrayerTimes`, `KarahaTimes`, `NaflTimes` (`IslamicWindows`), `KarahaCountdown.state`
- Produces:

```kotlin
enum class Moment { FAJR, SHURUQ, DUHA, DHUHR, ASR, MAGHRIB, ISHA }
enum class PhotoKey { FAJR, SHURUQ, ZUHR, ASR, MAGHRIB, ISHA }
data class Upcoming(val prayer: Prayer, val at: ZonedDateTime)
data class MakruhLine(val end: ZonedDateTime, val state: KarahaCountdown.State)
data class HeroState(
    val moment: Moment,
    val photo: PhotoKey,
    val remaining: Duration?,   // null = nicht anzeigen
    val next: Upcoming?,
    val makruh: MakruhLine?,
)
fun heroState(
    today: DailyPrayerTimes, karaha: KarahaTimes, nafl: NaflTimes,
    nextFajr: ZonedDateTime?, now: ZonedDateTime, isToday: Boolean,
): HeroState
```

Regeln (aus der Spec):
- now < `today.fajr` → ISHA (des Vortags), remaining bis `today.fajr`, next FAJR heute.
- fajr ≤ now < sunrise → FAJR, remaining bis `sunrise` (Ende der Faǧr-Zeit), next DHUHR.
- sunrise ≤ now < `nafl.duhaStart` → SHURUQ, remaining bis dhuhr, next DHUHR.
- duhaStart ≤ now < dhuhr → DUHA, remaining bis dhuhr, next DHUHR.
- dhuhr, asr, maghrib: laufendes Gebet, remaining bis zum nächsten, next = nächstes Pflichtgebet.
- now ≥ isha → ISHA, remaining bis `nextFajr`, next FAJR mit `nextFajr`; beides null, wenn `nextFajr` fehlt.
- `isToday == false` → moment FAJR, photo FAJR, remaining null, makruh null, next null. `makruh`: erster der drei Karāha-Abschnitte, für den `KarahaCountdown.state(now, start, end)` nicht null ist und der aktiv ist oder höchstens 15 Minuten bevorsteht. Photo: FAJR→FAJR, SHURUQ/DUHA→SHURUQ, DHUHR→ZUHR, ASR→ASR, MAGHRIB→MAGHRIB, ISHA→ISHA.

- [ ] **Step 1: Failing tests** mit Nürnberg 28.10.2026 als Fixture (Faǧr 05:10, Šurūq 06:50, Ẓuhr 12:05, ʿAṣr 14:38, Maġrib 17:09, ʿIšāʾ 18:37, Folgetag-Faǧr 05:11; Zone Europe/Berlin; Karāha/Nafl über `IslamicWindows`):
  - `at1320IsDhuhrWith78MinutesUntilAsr`: moment DHUHR, remaining 78 min, next ASR 14:38, photo ZUHR.
  - `at0300IsIshaOfPreviousDayUntilFajr`: moment ISHA, remaining bis 05:10, next FAJR.
  - `at2140IsIshaUntilTomorrowFajr`: remaining 7 h 31 min.
  - `afterIshaWithoutNextFajrHasNoRemaining`: nextFajr null → remaining null, next null.
  - `justAfterSunriseIsShuruqWithMakruh`: 07:00 → SHURUQ, makruh.state.active true.
  - `inDuhaWindowIsDuha`: 09:00 → DUHA, photo SHURUQ, next DHUHR.
  - `fifteenMinutesBeforeZenithShowsUpcomingMakruh`: 11:31 → makruh nicht null, active false; 11:20 → makruh null.
  - `otherDayHasNoLiveParts`: isToday false → remaining null, makruh null, next null, photo FAJR.
- [ ] **Step 2: Tests laufen lassen.** FAIL.
- [ ] **Step 3: `heroState` implementieren.**
- [ ] **Step 4: Tests grün.**
- [ ] **Step 5: Commit** `feat(app): Titel-Zustand der Hauptansicht`

---

### Task 5: Tagesliste (rein)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/DayRows.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/DayRowsTest.kt`

**Interfaces:**
- Consumes: wie Task 4; `Section` aus Task 9 nur als Enum-Name (in dieser Datei definiert, damit Task 9 ihn nutzt).
- Produces:

```kotlin
enum class Section { FAJR, SHURUQ_MAKRUH, DUHA, ZUHR, ASR, ISFIRAR_MAKRUH, MAGHRIB, AWWABIN, ISHA, TAHAJJUD, JAMAA }
sealed interface DayRow {
    data class PrayerRow(val prayer: Prayer, val at: ZonedDateTime, val past: Boolean, val current: Boolean, val nextDay: Boolean = false) : DayRow
    data class SideRow(val kind: Section, val start: ZonedDateTime, val end: ZonedDateTime?, val past: Boolean) : DayRow
}
fun dayRows(
    today: DailyPrayerTimes, karaha: KarahaTimes, nafl: NaflTimes, nextFajr: ZonedDateTime?,
    now: ZonedDateTime, isToday: Boolean, showKaraha: Boolean, showCemaat: Boolean, cemaatOffsetMinutes: Int,
): List<DayRow>
```

Reihenfolge nach Zeit: Faǧr, Ǧamāʿa (`SideRow(JAMAA, sunrise minus Offset, null)`, nur wenn showCemaat), Šurūq als `PrayerRow(SUNRISE)`, Makrūh Aufgang (`SideRow(SHURUQ_MAKRUH, sunriseStart, sunriseEnd)`), Ḍuḥā (`SideRow(DUHA, …)`), Makrūh Zenit (`SideRow(ZUHR, zevalStart, zevalEnd)`, verweist auf den Ẓuhr-Abschnitt mit Zawāl), Ẓuhr, ʿAṣr, Makrūh Vergilben (`ISFIRAR_MAKRUH`), Maġrib, Auwābīn, ʿIšāʾ, Tahaǧǧud (nur wenn Start/Ende vorhanden), Faǧr Folgetag (`nextDay = true`, nur wenn isToday und now ≥ isha und nextFajr vorhanden). Makrūh-Zeilen nur bei showKaraha. `current` genau für die Zeile des `heroState(...).moment` (DHUHR→Ẓuhr-Zeile usw.; SHURUQ→Šurūq-Zeile; DUHA→Ḍuḥā-Zeile; ISHA vor Faǧr→keine Zeile current, da Vortag).

- [ ] **Step 1: Failing tests:** `fullDayAlwaysListed` (5 Pflichtgebete + Šurūq vorhanden, auch um 13:20), `pastFlagsBefore1320`, `nextDayFajrAppearsAfterIsha` (21:40, letzte Zeile nextDay true, 05:11), `noNextDayFajrOnLastCoveredDay` (nextFajr null), `makruhRowsHiddenWhenSwitchedOff`, `jamaaOnlyWhenEnabled` (Zeit 06:20 bei Offset 30), `exactlyOneCurrentAt1320`.
- [ ] **Step 2: FAIL.** **Step 3: implementieren.** **Step 4: grün.**
- [ ] **Step 5: Commit** `feat(app): Tagesliste der Hauptansicht`

---

### Task 6: Fotos bündeln

**Files:**
- Create: `app/src/main/res/drawable-nodpi/photo_fajr.webp`, `photo_shuruq.webp`, `photo_zuhr.webp`, `photo_asr.webp`, `photo_maghrib.webp`, `photo_isha.webp`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/Photos.kt`
- Modify: `app/src/main/res/values/strings.xml` (Bildunterschriften, contentDescriptions, Fotografen-Nennung)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/PhotosAssetTest.kt`

**Interfaces:**
- Consumes: `PhotoKey` (Task 4)
- Produces: `@DrawableRes fun photoRes(key: PhotoKey): Int`, `@StringRes fun photoCaptionRes(key: PhotoKey): Int`, `@StringRes fun photoDescriptionRes(key: PhotoKey): Int`, `data class PhotoCredit(val key: PhotoKey, val photographer: String, val unsplashId: String)` und `val PHOTO_CREDITS: List<PhotoCredit>`.

Quellen (Download vom Nutzer freigegeben): Faǧr `pbuMoKhf07U` (Luke Moss), Ẓuhr `kGXST_-n36w` (Agata Create), ʿAṣr `KhyzfY9h4hc` (Shaun Danny), Maġrib `x1FCSyl6GJU` (Vanburn Gonsalves), ʿIšāʾ `Cq5NaI0yKBE` (Olena Bohovyk). Šurūq/Ḍuḥā: über `search_photos` ein Motiv mit tief stehender Morgensonne suchen, dem Nutzer vor dem Einbau zeigen. Download über die `links.download_location` der API (Unsplash-Vorgabe), Verarbeitung: 1080 px Breite, Seitenverhältnis 10:7 zuschneiden, WebP Qualität 80.

Bildunterschriften (ein Satz, keine Striche): Faǧr „Das Licht breitet sich am Horizont aus.“, Šurūq „Die Sonne ist aufgegangen.“, Ẓuhr „Die Sonne hat den Zenit überschritten, der Schatten wächst wieder.“, ʿAṣr „Die Schatten sind lang geworden.“, Maġrib „Die Sonnenscheibe ist hinter dem Horizont verschwunden.“, ʿIšāʾ „Die Abenddämmerung ist erloschen.“

- [ ] **Step 1: Failing test:** alle sechs Dateien existieren; Summe ≤ 1 572 864 Byte; jede `PhotoKey` hat Bild, Unterschrift, Beschreibung; `PHOTO_CREDITS` deckt alle Keys ab.
- [ ] **Step 2: FAIL.** **Step 3: Fotos laden, verarbeiten, `Photos.kt` und Strings anlegen.** **Step 4: grün.**
- [ ] **Step 5: Commit** `feat(app): Fotos je Gebetszeit (Unsplash-Platzhalter)`

---

### Task 7: Zitate recherchieren und als Inhalt ablegen

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/VerstehenContent.kt`
- Create: `docs/superpowers/specs/2026-10-28-zitate-belege.md` (je Abschnitt: Shamela book_id, page_id, `shamela_get_citation`-Ausgabe, Matn wörtlich)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/verstehen/VerstehenContentTest.kt`

**Interfaces:**
- Consumes: `Section` (Task 5), `PhotoKey` (Task 4)
- Produces:

```kotlin
data class Quote(val arabic: String, val german: String, val source: String)
data class VerstehenEntry(val section: Section, val title: String, val photo: PhotoKey?, val quotes: List<Quote>, val showsSundial: Boolean, val note: String? = null)
val VERSTEHEN: List<VerstehenEntry>   // Reihenfolge = Section.entries
const val TRANSLATION_NOTE = "Deutsche Übersetzung: eigene Übersetzung."
```

Vorgehen: Shamela-Werkzeuge (`shamela_search_phrase`, `shamela_get_page`, `shamela_get_citation`). Zuerst aš-Šurunbulālī „Marāqī l-falāḥ“ (book_id 412, ab page_id 80), dann al-Qudūrī „Muḫtaṣar“ (124336), al-Marġīnānī „al-Hidāya“ (11820); fehlt etwas: weitere hanafitische Werke der lokalen Bibliothek (z. B. aṭ-Ṭaḥṭāwī 11993), shamela-extern nur Stufe A/B. Je Abschnitt höchstens zwei Zitate, je höchstens drei Sätze. Kein Zitat gefunden: `quotes = emptyList()`. `showsSundial` true für ZUHR und ASR. Der ʿAṣr-Eintrag trägt `note = "Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet."`.

- [ ] **Step 1: Failing test:** jede `Section` genau einmal; jeder Quote hat nicht-leeres `arabic`, `german`, `source`; kein Text enthält — oder –; ZUHR und ASR `showsSundial`; ASR hat `note` mit „Diyanet“.
- [ ] **Step 2: FAIL.** **Step 3: Recherche, Belegdatei, Inhalt.** **Step 4: grün.**
- [ ] **Step 5: Commit** `feat(app): hanafitische Zitate für „Zeiten verstehen“ mit Belegen`
- [ ] **Step 6: Belegdatei und Übersetzungen dem Nutzer zur Prüfung vorlegen** (vor dem Merge, blockiert die Weiterarbeit nicht).

---

### Task 8: Sonnenuhr-Geometrie (rein)

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/prayer/Sundial.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/SundialTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class Pt(val east: Double, val north: Double)       // in Stablängen, Fuß = (0,0)
object Sundial {
    fun shadowTip(latDeg: Double, declDeg: Double, hourAngleDeg: Double): Pt?   // null wenn Sonne < 3° Höhe
    fun asrHourAngle(latDeg: Double, declDeg: Double, k: Int): Double?          // null wenn nicht erreichbar
    fun declination(date: LocalDate): Double                                    // Näherung nach Cooper, Grad
    fun dayCurve(latDeg: Double, declDeg: Double): List<Pt>                     // Stundenwinkel -100..100 in 0,5°-Schritten, gefiltert
    fun asrCurve(latDeg: Double, k: Int): List<Pt>                              // Deklination -23,44..23,44 in 0,5°-Schritten
}
```

Formeln (aus Spec/Prototyp `dial.py`): Höhe `up = sinφ·sinδ + cosφ·cosδ·cosH`, `north = cosφ·sinδ − sinφ·cosδ·cosH`, `east = −cosδ·sinH`, Spitze `(−east/up, −north/up)`. ʿAṣr: kleinster H > 0 mit `cot(Höhe) = k + cot(Mittagshöhe)`, Mittagshöhe = 90° − φ + δ; Bisektion 60 Schritte über 0..120°, null wenn Mittagshöhe ≤ 0 oder Ziel nicht erreicht.

- [ ] **Step 1: Failing tests:** `noonShadowPointsNorth` (φ 49,45, δ −13, H 0 → east 0, north ≈ 1,917 ± 0,01); `asrSingleShadowNuremberg` (δ −13, k 1 → H 38,6 ± 0,2); `asrDoubleShadowLaterThanSingle`; `belowHorizonIsNull`; `polarNightHasNoAsr` (φ 75, δ −23,44 → null); `curvesHaveNoNaN` (φ 75 und 0, alle Punkte endlich).
- [ ] **Step 2: FAIL.** **Step 3: implementieren.** **Step 4: grün.**
- [ ] **Step 5: Commit** `feat(app): Sonnenuhr-Geometrie`

---

### Task 9: Reiter „Zeiten verstehen“ und Sonnenuhr-Ansicht

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/VerstehenScreen.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/SundialView.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/verstehen/VerstehenWiringTest.kt`

**Interfaces:**
- Consumes: `VERSTEHEN` (Task 7), `Sundial` (Task 8), `photoRes` (Task 6), `LocalTokens`, `AppTypography`, `NaskhFamily` (Task 2)
- Produces: `@Composable fun VerstehenScreen(inner: PaddingValues, settings: AppSettings, focus: Section?, onFocusConsumed: () -> Unit)`; `@Composable fun SundialView(latDeg: Double, now: ZonedDateTime, modifier: Modifier)`.

Aufbau je Eintrag: Foto (Radius 24 dp), Titel (Serife), Zitate: Arabisch (`NaskhFamily`, `TextAlign.End`, `LayoutDirection.Rtl`, Zeilenhöhe 1,6), darunter Deutsch, darunter Quelle in `labelSmall`/stone; `note` als eigener Absatz. Am Ende der Liste einmal `TRANSLATION_NOTE`. `LazyColumn`; `focus != null` → `scrollToItem(index)` und `onFocusConsumed()`. SundialView: Canvas, Farben aus `LocalTokens` (Platte surface, Linien stone/hairline, Mittagslinie ink, ʿAṣr k=1 gold durchgezogen, k=2 ochre gestrichelt mit Beschriftung „2× Schatten“, Tagesbogen accent gepunktet, Schattenspitze accent); keine Uhrzeiten; `semantics { contentDescription = … }` mit Satz „Sonnenuhr für <Ort>: Mittagslinie und die Linien für einfachen und doppelten Schatten.“; Nachts (Sonne unter Horizont) ohne Schatten.

- [ ] **Step 1: Failing wiring test:** `VerstehenScreen.kt` nutzt `VERSTEHEN`, `NaskhFamily`, `LayoutDirection.Rtl`, `scrollToItem`; `SundialView.kt` nutzt `Sundial.asrCurve(` mit `1` und `2`, enthält kein `format(` mit Uhrzeitmuster `"HH:mm"`.
- [ ] **Step 2: FAIL.** **Step 3: implementieren.** **Step 4: grün; App bauen (`:app:assembleOfflineDebug`).**
- [ ] **Step 5: Commit** `feat(app): Reiter „Zeiten verstehen“ mit Sonnenuhr`

---

### Task 10: Reiter „Heute“ neu, vier Reiter

**Files:**
- Create: `app/src/main/kotlin/de/gebetszeiten/ui/HeuteScreen.kt` (Kopf, Foto, Hero, Liste, Hinweiskarten)
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt` (`Tab.VERSTEHEN` ergänzen; `HeuteContent`, `TimesCard`, `Timeline`, `ProgressPill`, `MakruhChipRow`, `CemaatTipRow`, `NaflTipRow`, `ExpanderRow`, Karāha-`AlertDialog` entfernen; `NotificationBlockCard`/`NoTimesCard`/`DateNavigator` nach `HeuteScreen.kt` verschieben und restylen; TopAppBar schmal, Titel = Ort im Tab Heute)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/HeuteWiringTest.kt`

**Interfaces:**
- Consumes: `heroState` (Task 4), `dayRows` (Task 5), `photoRes`/`photoCaptionRes`/`photoDescriptionRes` (Task 6), `timeRange` (Task 3), `VerstehenScreen` (Task 9)
- Produces: `@Composable internal fun HeuteScreen(inner: PaddingValues, settings: AppSettings, snackbarHostState: SnackbarHostState, onFetchNow: () -> Unit, onEnableCalculation: () -> Unit, onNotificationsGranted: () -> Unit, onExplain: (Section) -> Unit)`

Gestaltung: Datumszeile Großbuchstaben (`labelSmall`, Laufweite 1,2 sp) mit Chevrons links/rechts (48 dp, `contentDescription` wie bisher) und Hijri-Datum; Foto 10:7, Radius 24 dp, `contentDescription = photoDescriptionRes`; Unterschrift `bodyMedium`/stone; Titel `displayLarge`; „noch …“ `headlineSmall` (nur wenn remaining != null und `countdownMode != COUNTDOWN_OFF`; bei aus: nur „dann …“); „dann <Name> um HH:mm“ `bodyLarge`; Makrūh-Zeile gold „Makrūh bis HH:mm, noch …“ bzw. „Makrūh ab HH:mm, in …“ (aus `KarahaCountdown.State.text`); Liste: Zeilen min. 48 dp, Haarlinie unten, PrayerRow Name links Zeit rechts, past → stone, current → Punkt 6 dp accent + SemiBold, SideRow eingerückt 16 dp, `bodyMedium`, Zeitspanne über `timeRange`, antippbar (`clickable(role = Role.Button, onClickLabel = "Erklärung anzeigen")`) → `onExplain(kind)`; jede Zeile eine TalkBack-Einheit (`clearAndSetSemantics` mit Name, Zeit, Status). Datenquellenzeile (`dataCreditRes`) bleibt unter der Liste. Snackbar bei Wechsel des amtlichen Orts bleibt. `MainScreen` hält `var explain by remember { mutableStateOf<Section?>(null) }`; `onExplain` setzt ihn und wechselt auf `Tab.VERSTEHEN`.

- [ ] **Step 1: Failing wiring test:** `MainActivity.kt` enthält `Tab.VERSTEHEN` und keinen `AlertDialog(`; `HeuteScreen.kt` ruft `heroState(` und `dayRows(` auf, enthält `onExplain(`, `photoRes(`, `timeRange(`; kein `"–"`.
- [ ] **Step 2: FAIL.** **Step 3: implementieren** (vorhandene Effekte wie `tick`-Schleife mit `repeatOnLifecycle`, `produceState` für `dayInfo`/`officialName`, Snackbar-Logik unverändert übernehmen).
- [ ] **Step 4: grün; beide Suiten; `assembleOfflineDebug`; im Emulator 13:20-, 07:00- und 21:40-Zustand ansehen (Uhrzeit per `adb shell cmd alarm set-time` oder Zeitzone), Screenshot.**
- [ ] **Step 5: Commit** `feat(app): neue Hauptansicht „Heute“ und vierter Reiter`

---

### Task 11: Monat, Qibla, Einstellungen, Ersteinrichtung im neuen Stil

**Files:**
- Modify: `ui/MonatScreen.kt` (heute: Akzentpunkt + SemiBold statt `primaryContainer`-Fläche; Haarlinien zwischen Zeilen; Kopf `labelSmall` Großbuchstaben)
- Modify: `ui/QiblaScreen.kt` (Ring stone, N-Markierung gold statt error, Pfeil accent, Gradzahl `displayLarge`)
- Modify: `ui/SettingsSheet.kt` (`SettingsSection`: keine `Card`, sondern Großbuchstaben-Überschrift `labelSmall` + Inhalt + Haarlinie; Blatt-Hintergrund canvas; `BatteryOptimizationCard` als Fläche surface mit Haarlinie; neuer Abschnitt „Über“ mit `PHOTO_CREDITS` und Schriftlizenzen)
- Modify: `ui/Onboarding.kt` (Typografie, Haarlinien)
- Test: `app/src/test/kotlin/de/gebetszeiten/ui/RestyleWiringTest.kt`; bestehender `SettingsLayoutTest` muss weiter grün sein

- [ ] **Step 1: Failing wiring test:** `MonatScreen.kt` enthält nicht `primaryContainer`; `QiblaScreen.kt` enthält nicht `colorScheme.error`; `SettingsSheet.kt`: `SettingsSection` ohne `Card(`, enthält `PHOTO_CREDITS`.
- [ ] **Step 2: FAIL.** **Step 3: umsetzen** (Steuerungen und Handler unverändert). **Step 4: grün.**
- [ ] **Step 5: Commit** `feat(app): Monat, Qibla, Einstellungen, Ersteinrichtung im neuen Stil`

---

### Task 12: Qibla-Kompass auf geografisch Nord korrigieren

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/prayer/QiblaMath.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/Compass.kt`
- Modify: `app/src/main/kotlin/de/gebetszeiten/ui/QiblaScreen.kt`, `strings.xml`
- Test: `app/src/test/kotlin/de/gebetszeiten/prayer/QiblaMathTest.kt` (erweitern), `app/src/test/kotlin/de/gebetszeiten/ui/CompassWiringTest.kt`

**Interfaces:**
- Produces: `fun QiblaMath.trueAzimuth(magneticAzimuth: Float, declinationDeg: Float): Float` (= normalisiert `magnetic + declination`); `data class DeviceHeading(val azimuth: Float, val reliable: Boolean)`; `rememberDeviceAzimuth(lat: Double, lng: Double): DeviceHeading?` (ersetzt die alte Signatur).

Compass: `GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination` einmal pro Effekt; `onAccuracyChanged`/`event.accuracy`: `reliable = accuracy >= SENSOR_STATUS_ACCURACY_MEDIUM`. QiblaScreen: bei `!reliable` Zeile „Kompass ungenau. Handy in Form einer Acht bewegen.“; immer klein darunter: „Metall, Magnethüllen und Lautsprecher können den Kompass ablenken.“

- [ ] **Step 1: Failing tests:** `trueAzimuthAddsEastDeclination` (100 + 4,5 → 104,5), `trueAzimuthWraps` (358 + 4 → 2), `westDeclinationSubtracts` (10 − 12 → 358); Wiring: `Compass.kt` enthält `GeomagneticField(` und `trueAzimuth(`, `onAccuracyChanged` ist nicht leer.
- [ ] **Step 2: FAIL.** **Step 3: implementieren.** **Step 4: grün.**
- [ ] **Step 5: Commit** `fix(app): Qibla-Kompass berücksichtigt Missweisung und Sensorgenauigkeit`

---

### Task 13: Widget neu

**Files:**
- Modify: `app/src/main/kotlin/de/gebetszeiten/widget/NextPrayerWidget.kt`
- Modify: `app/src/main/res/layout/widget_preview.xml`, `values/colors.xml`, `values-night/colors.xml`
- Test: `app/src/test/kotlin/de/gebetszeiten/widget/WidgetWiringTest.kt`

**Interfaces:**
- Consumes: `heroState` (Task 4), `dayRows` (Task 5), `photoRes` (Task 6), Palette (Task 2)

Aufbau: COMPACT: Gebetsname (24 sp, Bold), „noch …“ bzw. Chronometer bei „Genau“ (bestehende `widget_chronometer`-RemoteViews), „dann <Name> HH:mm“. FULL (≥ 220×120 dp): zusätzlich Foto als Hintergrund (`Image` mit `ContentScale.Crop`) unter einem dunklen Verlauf (Glance-Box mit halbtransparentem `#000000` 55 %), Schrift hell (`#f7f1e8`), darunter die fünf Pflichtgebete in einer Zeile oder Liste. Transparente Variante, Dringlichkeitsfarbe, Leer-Darstellung, `KarahaText`, Klick öffnet App: unverändert. Glance-Farben aus der Palette (`ColorProviders` mit den neuen `LightColors`/`DarkColors`). Vorschau-Layout: Creme-Fläche, Tinte, Akzent.

- [ ] **Step 1: Failing wiring test:** Widget nutzt `heroState(` und `photoRes(`, behält `setChronometerCountDown`, `widget_urgent`, `actionStartActivity`; Vorschau-Farben entsprechen der Palette (`#FFF7F1E8`, `#FF1C1B1A`, `#FF224228`).
- [ ] **Step 2: FAIL.** **Step 3: umsetzen.** **Step 4: grün; Widget im Emulator auf den Startbildschirm legen (klein und groß), Screenshots.**
- [ ] **Step 5: Commit** `feat(app): Widget im neuen Design mit Foto`

---

### Task 14: Benachrichtigungen neu

**Files:**
- Create: `app/src/main/res/drawable/ic_prayer_fajr.xml`, `ic_prayer_dhuhr.xml`, `ic_prayer_asr.xml`, `ic_prayer_maghrib.xml`, `ic_prayer_isha.xml` (24 dp, einfarbig weiß, Motiv passend zum Foto: Horizontlinie mit Lichtbogen, Sonne hoch, Stab mit langem Schatten, halbe Sonne am Horizont, Mondsichel mit Stern)
- Modify: `app/src/main/kotlin/de/gebetszeiten/notify/PrayerNotifier.kt`
- Create: `app/src/main/kotlin/de/gebetszeiten/notify/PrayerIcons.kt`
- Test: `app/src/test/kotlin/de/gebetszeiten/notify/PrayerIconsTest.kt`, `NotificationStyleWiringTest.kt`

**Interfaces:**
- Consumes: `photoRes`, `PhotoKey` (Task 6/4), Palette
- Produces: `@DrawableRes fun prayerIconRes(prayer: Prayer): Int` (SUNRISE → `ic_notification`), `fun photoKeyFor(prayer: Prayer): PhotoKey`

Gebets-Meldungen (Eintritt, Vorlauf): `setSmallIcon(prayerIconRes(p))`, `setColor(accent)`, `setStyle(NotificationCompat.BigPictureStyle().bigPicture(<Bitmap aus photoRes, auf 1024 px Breite skaliert>).bigLargeIcon(null as Bitmap?))`, `setLargeIcon` mit kleiner Fassung. Daueranzeige: Symbol bleibt `countdownIconRes(...)` (Restzeit), `setColor(accent)`, `BigTextStyle` bleibt (Inhalt wichtiger als Bild), `setLargeIcon` mit kleinem Foto des laufenden Abschnitts. Bitmap-Dekodierung mit `inSampleSize`, damit keine großen Bitmaps im Speicher bleiben. Kanäle, Wecklogik, `EntryClear` unverändert.

- [ ] **Step 1: Failing tests:** `prayerIconRes` liefert für jedes Pflichtgebet eine eigene Ressource; `photoKeyFor(DHUHR) == ZUHR`; Wiring: `PrayerNotifier.kt` enthält `BigPictureStyle`, `prayerIconRes(`, `setColor(`; die Daueranzeige behält `countdownIconRes(`.
- [ ] **Step 2: FAIL.** **Step 3: umsetzen.** **Step 4: grün; im Emulator eine Testmeldung auslösen (Erinnerung 1 Minute in die Zukunft über die Systemzeit), zu- und aufgeklappt fotografieren.**
- [ ] **Step 5: Commit** `feat(app): Benachrichtigungen mit Gebetssymbolen und Foto`

---

### Task 15: Gesamtprüfung und Nachweis

**Files:**
- Create: Screenshots im Notizordner `C:\Users\ehren.HHK\AppData\Roaming\Claude\side-session-notes\local_abf98731-e1fa-4e73-9be2-16a0db224b02\design-neu\`

- [ ] **Step 1:** beide Test-Suiten, `:app:assembleOfflineDebug`, `:app:assembleOnlineDebug`, `:app:lintOfflineDebug` (keine neuen Fehler).
- [ ] **Step 2:** Emulator (Memory „Emulator-UI-Verifikation“, Zeitzone Europe/Berlin): hell, dunkel, Schrift 1,3 und 1,4, 360×640 dp; Bildschirme Heute (13:20, 07:00, 21:40, 03:00), Zeiten verstehen (oben, ʿAṣr mit Sonnenuhr), Monat, Qibla, Einstellungen, Ersteinrichtung, Widget klein/groß, Benachrichtigung zu/auf. Vorher-Bilder: `design-vorher/` und `design-nachher/` im Notizordner.
- [ ] **Step 3:** `grep -rn "[—–]"` über `strings.xml`-Werte (CopyRulesTest deckt es ab, hier nur Gegenprobe).
- [ ] **Step 4:** Ergebnis dem Nutzer mit Vorher/Nachher-Collagen zeigen; Belegdatei der Zitate zur Prüfung vorlegen.
