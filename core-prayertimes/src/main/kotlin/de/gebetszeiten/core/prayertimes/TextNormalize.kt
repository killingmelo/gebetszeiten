package de.gebetszeiten.core.prayertimes

import java.text.Normalizer

/** Akzent-/Umlaut-/Türkisch-insensitive Normalisierung (lower-case → ASCII).
 *  Gemeinsam genutzt von der App-Ortssuche (`Cities`, App-Modul) und der
 *  amtlichen Zeiten-Quelle (`DiyanetProxyFetcher`, wandert in einem
 *  spaeteren Schritt ins eigene Netz-Modul) — deshalb hier in
 *  `core-prayertimes`: reines `java.text.Normalizer`, ohne Android-, Netz-
 *  oder Oberflaechenbezug, und fuer beide Seiten ohne app-Typ erreichbar. */
object TextNormalize {
    // Einmal kompiliert — normalize() läuft beim Asset-Parse 2× pro Zeile
    // (235k Zeilen), eine Regex-Kompilierung pro Aufruf wäre der Hotspot.
    private val MARKS = Regex("\\p{Mn}+")

    fun normalize(s: String): String {
        val lower = s.trim().lowercase()
        // ASCII-Fastpath: keine Akzente/Sonderzeichen → NFD/Regex unnötig.
        if (lower.all { it.code < 128 }) return lower
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(MARKS, "")
        return stripped
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ç', 'c')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ß', 's')
    }
}
