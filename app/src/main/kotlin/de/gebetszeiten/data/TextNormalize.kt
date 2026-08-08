package de.gebetszeiten.data

import java.text.Normalizer

/** Akzent-/Umlaut-/Türkisch-insensitive Normalisierung (lower-case → ASCII).
 *  Gemeinsam genutzt von [Cities] und der amtlichen Zeiten-Quelle. */
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
