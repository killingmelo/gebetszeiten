package de.gebetszeiten.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Haelt die deutschen Store-Texte an die Schreibregeln des Projekts.
 *
 * Die Texte stehen in `fastlane/metadata/android/de-DE/` und gehen von dort
 * in die Play Console. Ein Gedankenstrich oder ein „Fajr“ faellt dort erst
 * auf, wenn der Eintrag schon oeffentlich ist. Also faellt es hier auf.
 * Arbeitsverzeichnis ist das Modul (`app/`), daher die relativen Pfade.
 */
class StoreListingTextTest {

    private val ordner = File("../fastlane/metadata/android/de-DE")

    private fun lies(name: String): String =
        File(ordner, name).readText(Charsets.UTF_8).replace("\r\n", "\n").trim()

    private val title = lies("title.txt")
    private val short = lies("short_description.txt")
    private val full = lies("full_description.txt")

    @Test
    fun titel() = assertEquals("Gebetszeiten Islam", title)

    @Test
    fun kurzbeschreibung() = assertEquals(
        "Amtliche Diyanet-Zeiten, gegengeprüft. Ohne Werbung, ohne Konto, akkusparend.",
        short,
    )

    @Test
    fun laengen() {
        assertTrue("Titel ${title.length} > 30", title.length <= 30)
        assertTrue("Kurzbeschreibung ${short.length} > 80", short.length <= 80)
        assertTrue("Beschreibung ${full.length} > 4000", full.length <= 4000)
    }

    @Test
    fun keineStriche() = listOf(title, short, full).forEach {
        assertFalse("Gedanken- oder Halbgeviertstrich in: ${it.take(40)}", it.contains('—') || it.contains('–'))
    }

    /** Genau ein gekennzeichneter Satz; mehr waere Keyword-Stuffing. */
    @Test
    fun suchwoerterGenauEinmal() {
        assertEquals(1, Regex("Namaz Vakitleri").findAll(full).count())
        assertEquals(1, Regex("\\bSalah").findAll(full).count())
    }

    @Test
    fun nichtVonDiyanet() = assertTrue(full.contains("nicht von Diyanet herausgegeben"))

    /**
     * Die Uhr holt Zeiten selbst und hat dafuer keinen eigenen Schalter. Fehlt
     * der Satz, liest man aus „Der Online-Abgleich lässt sich abschalten“,
     * dass das auch fuer die Uhr gilt. Das waere eine falsche Angabe.
     */
    @Test
    fun uhrMitEigenerInternetBerechtigung() =
        assertTrue(full.contains("eigene Internet-Berechtigung und keinen eigenen Schalter"))

    @Test
    fun keineFremdUmschrift() {
        val verboten = listOf(
            "Fajr", "Isha", "Dhuhr", "Maghrib", "Duha", "Makruh",
            "Cemaat", "Awwabin", "Tahajjud", "Karaha",
        )
        verboten.forEach { assertFalse(it, Regex("\\b$it\\b").containsMatchIn(full)) }
    }
}
