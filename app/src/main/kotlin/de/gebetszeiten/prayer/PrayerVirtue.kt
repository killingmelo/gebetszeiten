package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.Prayer
import java.time.LocalDate

/**
 * Kurztexte „Wert dieses Gebets“: je Gebet ein bis drei Sätze aus dem Hadith,
 * mit Fundstelle in Klammern. Vom Nutzer am 29.10.2026 freigegeben; Belege und
 * Bewertungen in docs/superpowers/specs/2026-10-28-wert-der-gebete.md.
 * Šurūq ist kein Gebet und hat keinen Eintrag.
 */
val PRAYER_VIRTUES: Map<Prayer, List<String>> = mapOf(
    Prayer.FAJR to listOf(
        "Die zwei Rakʿas des Faǧr sind besser als die Welt und alles, was in ihr ist. (Muslim 725)",
        "Wer die beiden kühlen Gebete betet, Faǧr und ʿAṣr, kommt ins Paradies. (Buḫārī 574)",
        "Wer das Morgengebet betet, steht unter dem Schutz Allāhs. (Muslim 657)",
    ),
    Prayer.DHUHR to listOf(
        "Wer stets vier Rakʿas vor und vier nach dem Ẓuhr betet, ist dem Feuer verwehrt. (Abū Dāwūd 1269)",
        "Vor dem Ẓuhr ist eine Stunde, in der sich die Tore des Himmels öffnen. (Tirmiḏī 478)",
        "Der Prophet ließ die vier Rakʿas vor dem Ẓuhr nie aus. (Buḫārī 1182)",
    ),
    Prayer.ASR to listOf(
        "Wer das ʿAṣr unterlässt, dessen Werk ist zunichte. (Buḫārī 553)",
        "Wem das ʿAṣr entgeht, dem ist, als hätte er Familie und Besitz verloren. (Buḫārī 552)",
        "Der Prophet nannte das ʿAṣr „das mittlere Gebet“. (Muslim 628)",
    ),
    Prayer.MAGHRIB to listOf(
        "Die Gemeinde bleibt im Guten, solange sie das Maġrib nicht in die Nacht schiebt. (Abū Dāwūd 418)",
        "Das Maġrib ist der Witr des Tages. (Aḥmad 4847)",
    ),
    Prayer.ISHA to listOf(
        "Wer das ʿIšāʾ in Gemeinschaft betet, ist, als hätte er die halbe Nacht gebetet. (Muslim 656)",
    ),
)

/**
 * Der Text des Tages für [prayer]: fester Wechsel nach dem Epochentag, kein
 * Zufall, damit Meldung und Wiederholung am selben Tag denselben Satz zeigen.
 * `null` für Šurūq.
 */
fun virtueFor(prayer: Prayer, date: LocalDate): String? {
    val texts = PRAYER_VIRTUES[prayer] ?: return null
    return texts[Math.floorMod(date.toEpochDay(), texts.size.toLong()).toInt()]
}
