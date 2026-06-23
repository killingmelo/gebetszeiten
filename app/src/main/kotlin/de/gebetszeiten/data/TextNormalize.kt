package de.gebetszeiten.data

import java.text.Normalizer

/** Akzent-/Umlaut-/Türkisch-insensitive Normalisierung (lower-case → ASCII).
 *  Gemeinsam genutzt von [Cities] und der amtlichen Zeiten-Quelle. */
object TextNormalize {
    fun normalize(s: String): String {
        val lower = s.trim().lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
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
