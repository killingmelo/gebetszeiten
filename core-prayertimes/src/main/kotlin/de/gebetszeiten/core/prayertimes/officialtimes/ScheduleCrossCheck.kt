package de.gebetszeiten.core.prayertimes.officialtimes

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.min

/** Die Quellen, aus denen amtliche Zeiten kommen koennen. Die Reihenfolge
 *  ist Teil der Zusicherungen: sie ordnet Fehlermeldungen und entscheidet
 *  Gleichstaende (siehe [resolveQuorum]). */
enum class SourceId { DIRECT, PROXY_ABDUS, EZANVAKTI }

/** Ergebnis EINES Quellenabrufs. Leerer Zeitplan + [error] = gescheitert. */
data class SourceResult(
    val source: SourceId,
    val schedule: Map<LocalDate, SixTimes>,
    val error: String? = null,
)

enum class Verdict { NO_OVERLAP, AGREE, MINOR_DRIFT, CONFLICT }

data class CrossCheckResult(
    val verdict: Verdict,
    /** Groesse der Schnittmenge — die Zahl, mit der die Anzeige „N Tage
     *  verglichen" sagen kann, statt Vollstaendigkeit zu suggerieren. */
    val comparedDays: Int,
    val differingDays: Int,
    val maxAbsMinutes: Int,
    val firstDiff: LocalDate?,
)

/** 24 h in Minuten — der Modul, in dem der Minutenabstand gerechnet wird. */
private const val MINUTES_PER_DAY = 24 * 60

/** Die sechs Zeiten als Liste von Zugriffen. Genau EINE Stelle, an der
 *  aufgezaehlt wird, welche Felder verglichen werden — ein neues Feld in
 *  [SixTimes] muss nur hier nachgetragen werden, und kein Vergleich kann
 *  eines davon vergessen. */
private val FIELDS: List<(SixTimes) -> LocalTime> = listOf(
    SixTimes::fajr,
    SixTimes::sunrise,
    SixTimes::dhuhr,
    SixTimes::asr,
    SixTimes::maghrib,
    SixTimes::isha,
)

/**
 * Zwei Zeitplaene gegeneinander pruefen.
 *
 * **Verglichen wird nur die SCHNITTMENGE der Tage.** Die Jahresseite liefert
 * ~400 Tage, die Proxy-Quellen 31 bis 32 — ohne Schnittmengen-Beschraenkung
 * waere jeder Vergleich ein Konflikt. Ist die Schnittmenge leer, ist der
 * Vergleich nicht beurteilbar ([Verdict.NO_OVERLAP]) und ausdruecklich KEINE
 * Zustimmung.
 *
 * **Klassifikation nach FORM, nicht nach einer Schwelle.** Ein kaputter
 * Parser erzeugt SYSTEMATISCHE Abweichungen ueber VIELE Tage; eine echte
 * Diyanet-Korrektur betrifft EIN BIS ZWEI Tage um EIN BIS ZWEI Minuten.
 * Deshalb hat [Verdict.MINOR_DRIFT] zwei Bedingungen, die BEIDE gelten
 * muessen: wenige Tage UND wenige Minuten. Eine einzelne Schwelle koennte
 * das nicht leisten — „hoechstens zwei Minuten" allein wuerde einen
 * Parser-Fehler durchlassen, der das ganze Jahr um eine Minute verschiebt,
 * und „hoechstens drei Tage" allein einen, der an einem Tag zwoelf Stunden
 * daneben liegt:
 *
 * | Lage | Verdict |
 * |---|---|
 * | Schnittmenge leer | [Verdict.NO_OVERLAP] (alle Zahlen 0/null) |
 * | kein Tag weicht ab | [Verdict.AGREE] |
 * | `differingDays <= maxDriftDays` **und** `maxAbsMinutes <= maxDriftMinutes` | [Verdict.MINOR_DRIFT] |
 * | sonst | [Verdict.CONFLICT] |
 *
 * Ein Tag „weicht ab", wenn IRGENDEINE der sechs Zeiten abweicht — ein Tag
 * mit drei abweichenden Feldern zaehlt einmal. [CrossCheckResult.maxAbsMinutes]
 * ist das Maximum ueber alle sechs Felder aller verglichenen Tage,
 * [CrossCheckResult.firstDiff] der CHRONOLOGISCH fruehste abweichende Tag
 * (nicht der zuerst eingefuegte — die Maps kommen aus Netzantworten in
 * beliebiger Reihenfolge).
 *
 * Reine Funktion: kein Netz, keine Uhr, kein Android.
 */
fun crossCheck(
    a: Map<LocalDate, SixTimes>,
    b: Map<LocalDate, SixTimes>,
    maxDriftDays: Int = 3,
    maxDriftMinutes: Int = 2,
): CrossCheckResult {
    val common = a.keys.intersect(b.keys)
    if (common.isEmpty()) {
        return CrossCheckResult(Verdict.NO_OVERLAP, 0, 0, 0, null)
    }

    var differingDays = 0
    var maxAbsMinutes = 0
    var firstDiff: LocalDate? = null
    // Chronologisch, damit `firstDiff` nicht von der Einfuegereihenfolge
    // abhaengt.
    for (date in common.sorted()) {
        val left = a.getValue(date)
        val right = b.getValue(date)
        val worstOfDay = FIELDS.maxOf { field -> minutesApart(field(left), field(right)) }
        if (worstOfDay > 0) {
            differingDays++
            if (firstDiff == null) firstDiff = date
        }
        if (worstOfDay > maxAbsMinutes) maxAbsMinutes = worstOfDay
    }

    val verdict = when {
        differingDays == 0 -> Verdict.AGREE
        differingDays <= maxDriftDays && maxAbsMinutes <= maxDriftMinutes -> Verdict.MINOR_DRIFT
        else -> Verdict.CONFLICT
    }
    return CrossCheckResult(
        verdict = verdict,
        comparedDays = common.size,
        differingDays = differingDays,
        maxAbsMinutes = maxAbsMinutes,
        firstDiff = firstDiff,
    )
}

/**
 * Minutenabstand ZIRKULAER: `min(|x-y|, 1440-|x-y|)`.
 *
 * Grund: [LocalTime] kennt keinen Tagesuebergang. Meldet eine Quelle Isha um
 * `23:59` und die andere um `00:01`, waere der naive Abstand 1438 Minuten und
 * der Vergleich ein Konflikt, obwohl es zwei Minuten sind. Ein echter
 * Zwoelf-Stunden-Fehler (AM/PM-Verwechslung) bleibt auch zirkulaer bei 12 h
 * und damit ein Konflikt — die Rundung verzeiht also genau den
 * Tagesuebergang und nichts darueber hinaus.
 *
 * Sekunden werden abgeschnitten: die amtlichen Zeiten sind minutengenau.
 */
private fun minutesApart(x: LocalTime, y: LocalTime): Int {
    val direct = abs(x.toSecondOfDay() / 60 - y.toSecondOfDay() / 60)
    return min(direct, MINUTES_PER_DAY - direct)
}
