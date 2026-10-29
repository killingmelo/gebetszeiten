package de.gebetszeiten.desktop.notify

import de.gebetszeiten.core.prayertimes.HijriDay
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Ein islamischer Anlass. [key] verbindet ihn mit den Lesetexten
 * (`hinweise/texte.tsv`, Spalte `anlass`).
 */
enum class Occasion(val key: String, val title: String) {
    CUMA_GECE("cuma_gece", "Nacht zum Freitag"),
    CUMA("cuma", "Ǧumʿa"),
    CUMA_NAMAZ("cuma_namaz", "Ǧumʿa-Gebet"),
    VAKIT_ENDET("vakit_endet", "Gebetszeit endet"),
    SUNNET_ORUC("sunnet_oruc", "Sunna-Fasten"),
    EYYAM_BIZ("eyyam_biz", "al-Ayyām al-Bīḍ"),
    KUSLUK("kusluk", "Ḍuḥā"),
    TEHECCUD("teheccud", "Tahaǧǧud"),
    KARAHA("karaha", "Karāha"),
    SAHUR("sahur", "Saḥūr"),
    IFTAR("iftar", "Ifṭār"),
    RAMAZAN("ramazan", "Ramaḍān"),
    KADIR("kadir", "Lailat al-Qadr"),
    MEVLID("mevlid", "Maulid an-Nabī"),
    REGAIB("regaib", "Lailat ar-Raġāʾib"),
    MIRAC("mirac", "Lailat al-Miʿrāǧ"),
    BERAT("berat", "Lailat al-Barāʾa"),
    HICRI_YILBASI("hicri_yilbasi", "Hiǧra-Neujahr"),
    ASURE("asure", "ʿĀšūrāʾ"),
    ZILHICCE_ON("zilhicce_on", "Ḏū l-Ḥiǧǧa"),
    AREFE("arefe", "Tag von ʿArafa"),
    BAYRAM_RAMAZAN("bayram_ramazan", "ʿĪd al-Fiṭr"),
    BAYRAM_KURBAN("bayram_kurban", "ʿĪd al-Aḍḥā"),
    GENEL("genel", "Text des Tages"),
}

/**
 * Besondere Nacht, die am Abend von [date] beginnt (die Nacht gehoert zum
 * Folgetag [next]). Wie Diyanet: Maulid 12. Rabīʿ al-auwal, Raġāʾib Nacht zum
 * ersten Freitag im Raǧab, Miʿrāǧ 27. Raǧab, Barāʾa 15. Šaʿbān, Qadr 27. Ramaḍān.
 */
fun kandilTonight(date: LocalDate, next: HijriDay?): Occasion? {
    next ?: return null
    return when {
        next.month == 3 && next.day == 12 -> Occasion.MEVLID
        next.month == 7 && next.day == 27 -> Occasion.MIRAC
        next.month == 8 && next.day == 15 -> Occasion.BERAT
        next.month == 9 && next.day == 27 -> Occasion.KADIR
        // Erster Freitag im Raǧab: morgen ist Freitag und der 1. bis 7. Raǧab.
        next.month == 7 && next.day <= 7 && date.plusDays(1).dayOfWeek == DayOfWeek.FRIDAY -> Occasion.REGAIB
        else -> null
    }
}

/** Besonderer Tag [h] (morgens angekuendigt), oder null. */
fun specialDay(h: HijriDay?): Occasion? {
    h ?: return null
    return when {
        h.month == 1 && h.day == 1 -> Occasion.HICRI_YILBASI
        h.month == 1 && h.day == 10 -> Occasion.ASURE
        h.month == 9 && h.day == 1 -> Occasion.RAMAZAN
        h.month == 10 && h.day in 1..3 -> Occasion.BAYRAM_RAMAZAN
        h.month == 12 && h.day == 1 -> Occasion.ZILHICCE_ON
        h.month == 12 && h.day == 9 -> Occasion.AREFE
        h.month == 12 && h.day in 10..13 -> Occasion.BAYRAM_KURBAN
        else -> null
    }
}

/** An den Festtagen ist Fasten verboten; im Ramaḍān fastet man ohnehin. */
fun fastingSensible(h: HijriDay?): Boolean = when {
    h == null -> true
    h.month == 9 -> false
    h.month == 10 && h.day == 1 -> false
    h.month == 12 && h.day in 10..13 -> false
    else -> true
}

/** Text zum besonderen Tag (sachlich, ohne Zitat). */
fun specialDayText(o: Occasion, h: HijriDay): String = when (o) {
    Occasion.HICRI_YILBASI -> "Heute beginnt das Hiǧra-Jahr ${h.year}."
    Occasion.ASURE -> "Heute ist ʿĀšūrāʾ (10. Muḥarram). Sunna ist das Fasten am 9. und 10. oder am 10. und 11."
    Occasion.RAMAZAN -> "Der erste Tag des Ramaḍān. Ramaḍān mubārak!"
    Occasion.BAYRAM_RAMAZAN -> "ʿĪd al-Fiṭr, ${h.day}. Tag. ʿĪd mubārak! Am ersten Festtag wird nicht gefastet."
    Occasion.ZILHICCE_ON -> "Die ersten zehn Tage des Ḏū l-Ḥiǧǧa haben begonnen: Tage des Gedenkens (Takbīr, Tahlīl) und des Fastens bis ʿArafa."
    Occasion.AREFE -> "Heute ist der Tag von ʿArafa. Das Fasten ist Sunna; ab dem Faǧr beginnen die Takbīrāt at-Tašrīq."
    Occasion.BAYRAM_KURBAN -> "ʿĪd al-Aḍḥā, ${h.day - 9}. Tag. ʿĪd mubārak! Takbīrāt at-Tašrīq nach jedem Pflichtgebet."
    else -> o.title
}

/** Text zur besonderen Nacht. */
fun kandilText(o: Occasion): String = when (o) {
    Occasion.KADIR -> "Heute Nacht: Lailat al-Qadr (Nacht zum 27. Ramaḍān). Die Nacht mit Gebet, Qurʾān und Bittgebet verbringen."
    Occasion.MEVLID -> "Heute Nacht: Nacht zum 12. Rabīʿ al-auwal (Maulid an-Nabī)."
    Occasion.REGAIB -> "Heute Nacht: Lailat ar-Raġāʾib (Nacht zum ersten Freitag im Raǧab)."
    Occasion.MIRAC -> "Heute Nacht: Lailat al-Miʿrāǧ (Nacht zum 27. Raǧab)."
    Occasion.BERAT -> "Heute Nacht: Lailat al-Barāʾa (Nacht zum 15. Šaʿbān)."
    else -> "Heute Nacht: ${o.title}."
}

/** Kurzzeile fuer das Tages-Popup („Heute“), oder null an gewoehnlichen Tagen. */
fun todayLine(date: LocalDate, h: HijriDay?, next: HijriDay?): String? {
    val parts = mutableListOf<String>()
    specialDay(h)?.let { parts += it.title }
    if (h != null && h.month == 9) parts += "Ramaḍān, ${h.day}. Tag"
    kandilTonight(date, next)?.let { parts += "${it.title} heute Nacht" }
    if (date.dayOfWeek == DayOfWeek.FRIDAY) parts += "Ǧumʿa"
    if (h != null && h.day in 13..15 && fastingSensible(h)) parts += "al-Ayyām al-Bīḍ"
    return parts.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
