package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.prayer.KarahaTimes
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.NaflTimes
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.prayer.timeRange
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Die Reiter von „Zeiten verstehen“ (Entwurf vom 05.11.2026, freigegeben):
 * je Abschnitt ein Reiter, die Nebenabschnitte stehen im Reiter ihres Gebets,
 * so wie auf „Heute“ die Nebenzeiten unter ihrem Gebet. Die Beschriftungen
 * unter den Uhrzeiten beschreiben nur, sie sind keine Fiqh-Aussage; die steht
 * allein in den Zitaten.
 *
 * Die Uhrzeiten werden hier zu Text, damit `VerstehenScreen.kt` (mit der
 * Sonnenuhr, die keine Uhrzeit zeigen darf) selbst keine formatiert.
 */
data class VerstehenTab(
    val label: String,
    /** Wortsinn neben dem Titel („Ẓuhr · Mittag“). */
    val meaning: String,
    val main: Section,
    val sides: List<Section>,
    val beginLabel: String,
    val endLabel: String,
    /** Kachel „Rakʿa“: Zeilen aus Bezeichnung und Wert; leer bei Šurūq (kein Gebet). */
    val rakaat: List<Pair<String, String>> = emptyList(),
    /** Begriffe am Ende des Reiters, Schlüssel in [GLOSSAR]. */
    val glossary: List<String> = emptyList(),
)

// Rakʿa nach Marāqī l-falāḥ S. 139, 145 bis 147 (Zitate in den Reitern); die
// Farḍ-Zahlen sind unstrittig und stehen ohne Zitat (Entscheidung vom 05.11.2026).
// Ob die empfohlenen nach Ẓuhr und Maġrib zusätzlich sind, lässt Marāqī offen
// (Zitat bei Auwābīn); deshalb hier ohne Summe.
private const val BEFORE = "Davor"
private const val FARD = "Farḍ"
private const val AFTER = "Danach"

val VERSTEHEN_TABS: List<VerstehenTab> = listOf(
    VerstehenTab(
        "Faǧr", "Morgendämmerung", Section.FAJR, listOf(Section.JAMAA), "Morgendämmerung", "Šurūq",
        rakaat = listOf(BEFORE to "2 Sunna muʾakkada", FARD to "2"),
        glossary = listOf("Faǧr", "Rakʿa", "Farḍ", "Sunna", "Sunna muʾakkada", "Mandūb", "Wāǧib", "Isfār und Taġlīs", "Ǧamāʿa", "Iqāma"),
    ),
    VerstehenTab(
        "Šurūq", "Sonnenaufgang", Section.SHURUQ_MAKRUH, listOf(Section.DUHA), "Sonnenaufgang", "Makrūh vorbei",
        glossary = listOf("Šurūq", "Makrūh", "Nafl", "Wāǧib", "Ḍuḥā", "Qaḍāʾ", "Karāhat at-taḥrīm", "Ṭawāf", "Ẓāhir ar-riwāya"),
    ),
    VerstehenTab(
        "Ẓuhr", "Mittag", Section.ZUHR, emptyList(), "nach dem Zawāl", "ʿAṣr",
        rakaat = listOf(
            BEFORE to "4 Sunna muʾakkada",
            FARD to "4",
            AFTER to "2 Sunna muʾakkada, empfohlen 2 weitere",
            "Ǧumʿa" to "4 Sunna muʾakkada davor, 2 Farḍ, 4 Sunna muʾakkada danach",
        ),
        glossary = listOf("Ẓuhr", "Zawāl", "Makrūh", "Sunna muʾakkada", "Mandūb", "Ǧumʿa", "Zenit", "Istiwāʾ"),
    ),
    VerstehenTab(
        "ʿAṣr", "Nachmittag", Section.ASR, listOf(Section.ISFIRAR_MAKRUH), "Ende von Ẓuhr", "Maġrib",
        rakaat = listOf(BEFORE to "empfohlen 4 (oder 2)", FARD to "4"),
        glossary = listOf("ʿAṣr", "Mandūb", "Makrūh", "Iṣfirār"),
    ),
    VerstehenTab(
        "Maġrib", "Sonnenuntergang", Section.MAGHRIB, listOf(Section.AWWABIN), "Sonnenuntergang", "ʿIšāʾ",
        rakaat = listOf(FARD to "3", AFTER to "2 Sunna muʾakkada, empfohlen 6"),
        glossary = listOf("Maġrib", "Šafaq", "Fatwā", "Auwābīn", "Sunna muʾakkada", "Mandūb", "Aḏān", "Iqāma"),
    ),
    VerstehenTab(
        "ʿIšāʾ", "Abend", Section.ISHA, listOf(Section.TAHAJJUD), "Dämmerung vorbei", "Faǧr",
        rakaat = listOf(
            BEFORE to "empfohlen 4",
            FARD to "4",
            AFTER to "2 Sunna muʾakkada, empfohlen 4",
            "Witr" to "3, wāǧib",
        ),
        glossary = listOf("ʿIšāʾ", "Šafaq", "Witr", "Wāǧib", "Tahaǧǧud"),
    ),
)

/** Der Reiter, in dem [section] steht. */
fun tabIndexOf(section: Section): Int = VERSTEHEN_TABS.indexOfFirst { it.main == section || section in it.sides }

/** Schlüssel des Listeneintrags, zu dem ein Sprung aus „Heute“ führt. */
fun itemKeyOf(section: Section): String =
    if (VERSTEHEN_TABS.any { it.main == section }) "head" else "side-${section.name}"

/** Nebenabschnitte unter „Worauf achten“ (Makrūh); die übrigen stehen unter „Dazu gehört“. */
fun isCautionSide(section: Section): Boolean = section == Section.ISFIRAR_MAKRUH

/** Der Reiter zum laufenden Abschnitt; mit ihm öffnet „Verstehen“. */
fun tabOfMoment(moment: Moment): Int = when (moment) {
    Moment.FAJR -> 0
    Moment.SHURUQ, Moment.DUHA -> 1
    Moment.ZAWAL, Moment.DHUHR -> 2
    Moment.ASR -> 3
    Moment.MAGHRIB -> 4
    Moment.ISHA -> 5
}

/** Beginn und Ende als fertiger Text; ohne Faǧr von morgen fehlt das Ende von ʿIšāʾ. */
data class TabSpan(val begin: String, val end: String?)

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun tabSpan(index: Int, day: VerstehenDay): TabSpan {
    val t = day.times
    val (begin, end) = when (VERSTEHEN_TABS[index].main) {
        Section.FAJR -> t.fajr to t.sunrise
        Section.SHURUQ_MAKRUH -> t.sunrise to day.karaha.sunriseEnd
        Section.ZUHR -> t.dhuhr to t.asr
        Section.ASR -> t.asr to t.maghrib
        Section.MAGHRIB -> t.maghrib to t.isha
        else -> t.isha to day.nextFajr
    }
    return TabSpan(begin.format(HM), end?.format(HM))
}

/** Die Spanne eines Nebenabschnitts für seine Überschrift; Ǧamāʿa hängt am Vorlauf und bekommt keine. */
fun sideTime(section: Section, day: VerstehenDay): String? {
    val (start, end) = when (section) {
        Section.DUHA -> day.nafl.duhaStart to day.nafl.duhaEnd
        Section.ISFIRAR_MAKRUH -> day.karaha.isfirarStart to day.karaha.isfirarEnd
        Section.AWWABIN -> day.nafl.awwabinStart to day.nafl.awwabinEnd
        Section.TAHAJJUD -> (day.nafl.tahajjudStart ?: return null) to (day.nafl.tahajjudEnd ?: return null)
        else -> return null
    }
    return timeRange(start.format(HM), end.format(HM))
}
