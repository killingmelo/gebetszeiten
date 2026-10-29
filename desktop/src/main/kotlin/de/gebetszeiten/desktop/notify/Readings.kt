package de.gebetszeiten.desktop.notify

import java.io.InputStream
import java.time.LocalDate

/**
 * Ein Lesetext zu einem Anlass: arabischer Auszug, eigene sinngemaesse
 * Uebersetzung, Quelle (aus Shamela), optional ein Hinweis (Graduierung,
 * „Seitenzaehlung Shamela“).
 */
data class Reading(
    val occasion: String,
    val arabic: String,
    val german: String,
    val source: String,
    val note: String,
)

/**
 * Die Sammlung aus `hinweise/texte.tsv` (Kopfzeile, dann
 * `anlass  arabisch  deutsch  quelle  buch_id  seite  hinweis`, TAB-getrennt,
 * ` ¶ ` = Absatz). Fehlt die Datei oder eine Zeile ist kaputt, fehlt nur das.
 */
class Readings(private val all: List<Reading>) {

    /** Der Text des Tages fuer [occasion]: wechselt taeglich, bleibt am Tag gleich. */
    fun pick(occasion: Occasion, date: LocalDate): Reading? {
        val list = all.filter { it.occasion == occasion.key }
        if (list.isEmpty()) return null
        return list[Math.floorMod(date.toEpochDay() + occasion.ordinal, list.size.toLong()).toInt()]
    }

    val size: Int get() = all.size

    companion object {
        const val RESOURCE = "hinweise/texte.tsv"

        fun load(open: (String) -> InputStream?): Readings = runCatching {
            open(RESOURCE)?.bufferedReader(Charsets.UTF_8)?.useLines { parse(it.toList()) }
        }.getOrNull() ?: Readings(emptyList())

        fun parse(lines: List<String>): Readings = Readings(
            lines.drop(1).mapNotNull { line ->
                val f = line.removePrefix("﻿").split('\t')
                if (f.size < 4 || f[0].isBlank() || f[2].isBlank() || f[3].isBlank()) return@mapNotNull null
                Reading(
                    occasion = f[0].trim(),
                    arabic = f[1].trim().paragraphs(),
                    german = f[2].trim().paragraphs(),
                    source = f[3].trim(),
                    note = f.getOrNull(6)?.trim().orEmpty(),
                )
            },
        )

        private fun String.paragraphs() = replace(" ¶ ", "\n").expandHonorifics()

        /**
         * Shamela schreibt Ehrformeln als Ligaturen aus Unicode 14, die keine
         * Windows-Schrift hat (Kaestchen). Ausgeschrieben steht derselbe Wortlaut.
         * ﷺ (U+FDFA) bleibt: das koennen Segoe UI und Traditional Arabic.
         */
        private val HONORIFICS = mapOf(
            '﵀' to "رحمه الله",
            '﵁' to "رضي الله عنه",
            '﵂' to "رضي الله عنها",
            '﵃' to "رضي الله عنهم",
            '﵄' to "رضي الله عنهما",
            '﵇' to "عليه السلام",
            '﵈' to "عليهم السلام",
            '﵉' to "عليهما السلام",
            '﵌' to "صلى الله عليه وعلى آله وسلم",
            '﷿' to "عز وجل",
        )

        internal fun String.expandHonorifics(): String = buildString {
            for (ch in this@expandHonorifics) append(HONORIFICS[ch] ?: ch)
        }
    }
}
