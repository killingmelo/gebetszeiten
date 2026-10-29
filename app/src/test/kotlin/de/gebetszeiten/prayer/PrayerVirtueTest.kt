package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Die freigegebenen Kurztexte „Wert dieses Gebets“ (Belege in
 * docs/superpowers/specs/2026-10-28-wert-der-gebete.md): Wortlaut, Länge,
 * Schreibregeln und der feste tägliche Wechsel.
 */
class PrayerVirtueTest {

    /** Wortlaut der Freigabe vom 29.10.2026, Zeichen für Zeichen. */
    private val freigegeben: Map<Prayer, List<String>> = mapOf(
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

    private val alle: List<String> get() = PRAYER_VIRTUES.values.flatten()

    @Test
    fun `Texte genau wie freigegeben`() {
        assertEquals(freigegeben, PRAYER_VIRTUES)
    }

    @Test
    fun `nur die fuenf Gebete, Sonnenaufgang ohne Text`() {
        assertEquals(
            setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
            PRAYER_VIRTUES.keys,
        )
        assertNull(virtueFor(Prayer.SUNRISE, LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun `kein Gedanken- oder Halbgeviertstrich`() {
        alle.forEach { assertFalse(it, it.contains('\u2014') || it.contains('\u2013')) }
    }

    @Test
    fun `jeder Text hoechstens 100 Zeichen`() {
        alle.forEach { assertTrue("${it.length}: $it", it.length <= 100) }
    }

    @Test
    fun `Umschrift mit Halbring, nicht mit Apostroph oder Gravis`() {
        alle.forEach { text ->
            listOf('\u02BB', '\u2018', '\u2019', '`', '\'').forEach { falsch ->
                assertFalse("$falsch in $text", text.contains(falsch))
            }
        }
        assertTrue(alle.any { it.contains('\u02BF') })
        assertTrue(alle.any { it.contains('\u02BE') })
    }

    @Test
    fun `Wechsel von Tag zu Tag fest und mit Umbruch`() {
        val fajr = PRAYER_VIRTUES.getValue(Prayer.FAJR)
        // Epochentag 3k: Index 0, dann 1, 2 und wieder 0.
        val start = LocalDate.ofEpochDay(3L * 6_800)
        assertEquals(fajr[0], virtueFor(Prayer.FAJR, start))
        assertEquals(fajr[1], virtueFor(Prayer.FAJR, start.plusDays(1)))
        assertEquals(fajr[2], virtueFor(Prayer.FAJR, start.plusDays(2)))
        assertEquals(fajr[0], virtueFor(Prayer.FAJR, start.plusDays(3)))
        // Gleicher Tag, gleicher Text: kein Zufall.
        assertEquals(virtueFor(Prayer.DHUHR, start), virtueFor(Prayer.DHUHR, start))
        // Maġrib hat zwei Texte: sie wechseln sich ab.
        val maghrib = PRAYER_VIRTUES.getValue(Prayer.MAGHRIB)
        assertEquals(maghrib[0], virtueFor(Prayer.MAGHRIB, LocalDate.ofEpochDay(20_000)))
        assertEquals(maghrib[1], virtueFor(Prayer.MAGHRIB, LocalDate.ofEpochDay(20_001)))
        // Vor 1970 bleibt der Index gültig (floorMod statt %).
        assertEquals(fajr[2], virtueFor(Prayer.FAJR, LocalDate.ofEpochDay(-1)))
    }

    @Test
    fun `das Nachtgebet hat einen Text und zeigt immer ihn`() {
        val isha = PRAYER_VIRTUES.getValue(Prayer.ISHA).single()
        (0L..6L).forEach { assertEquals(isha, virtueFor(Prayer.ISHA, LocalDate.of(2026, 10, 29).plusDays(it))) }
    }
}
