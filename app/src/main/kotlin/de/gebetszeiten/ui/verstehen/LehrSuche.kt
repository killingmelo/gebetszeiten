package de.gebetszeiten.ui.verstehen

import java.text.Normalizer

/** Ein Suchtreffer: [ziel] zum Springen, [ort] als Fundstelle („Ẓuhr · Wann bete ich?“), [ausschnitt] als Textprobe. */
data class Treffer(val ziel: Ziel, val ort: String, val ausschnitt: String)

private const val MAX_TREFFER = 20
private const val AUSSCHNITT_BREITE = 80
private const val KONTEXT_DAVOR = 30

/** Zeichen, die ersatzlos wegfallen (Hamza, Ain, Apostrophe). */
private val WEG = setOf('ʿ', 'ʾ', '\'', '’', '‘', 'ʼ')

/** Zeichen, die NFD nicht zerlegt, in ASCII. */
private val ASCII = mapOf('ǧ' to "g", 'š' to "s", 'ḫ' to "h", 'ġ' to "g", 'ḏ' to "d", 'ṯ' to "t", 'ß' to "ss")

/** Normalisierte Zeichenfolge samt der Position jedes Zeichens im Original. */
private class Norm(val text: String, val herkunft: IntArray)

private fun normMitHerkunft(s: String): Norm {
    val sb = StringBuilder()
    val pos = ArrayList<Int>()
    for ((i, c) in s.withIndex()) {
        if (c in WEG) continue
        val chunk = StringBuilder()
        for (d in Normalizer.normalize(c.toString(), Normalizer.Form.NFD)) {
            if (Character.getType(d) == Character.NON_SPACING_MARK.toInt()) continue
            if (d in WEG) continue
            val low = d.lowercaseChar()
            chunk.append(ASCII[low] ?: low.toString())
        }
        for (d in chunk) { sb.append(d); pos += i }
    }
    // Deutsche Schreibung: „dsch“ zu „g“, „sch“ zu „s“ (Herkunft: Beginn der Gruppe).
    val out = StringBuilder()
    val outPos = ArrayList<Int>()
    var i = 0
    while (i < sb.length) {
        val dsch = sb.startsWith("dsch", i)
        val sch = !dsch && sb.startsWith("sch", i)
        when {
            dsch -> { out.append('g'); outPos += pos[i]; i += 4 }
            sch -> { out.append('s'); outPos += pos[i]; i += 3 }
            else -> { out.append(sb[i]); outPos += pos[i]; i++ }
        }
    }
    return Norm(out.toString(), outPos.toIntArray())
}

/**
 * Vergleichsform für die Suche: ohne Diakritika, ʿ und ʾ, kleingeschrieben,
 * ǧ/š/ḫ/ġ/ḏ/ṯ als g/s/h/g/d/t, „dsch“ als g und „sch“ als s. So findet „Ischa“ ʿIšāʾ.
 */
fun normalisiere(s: String): String = normMitHerkunft(s).text

/** Anzeigetext ohne Auszeichnung: Begriffe in ihrer Anzeige, Verweise entfallen. */
private fun klartext(text: String): String =
    zerlege(text) { 0 }.joinToString("") {
        when (it) {
            is Stueck.Text -> it.s
            is Stueck.Begriff -> it.anzeige
            is Stueck.Verweis -> ""
        }
    }

/** Bis zu rund 80 Zeichen um die Fundstelle; kürzere Texte ganz. */
private fun ausschnitt(anzeige: String, start: Int): String {
    if (anzeige.length <= AUSSCHNITT_BREITE) return anzeige
    val von = maxOf(0, minOf(start - KONTEXT_DAVOR, anzeige.length - AUSSCHNITT_BREITE))
    val bis = minOf(anzeige.length, von + AUSSCHNITT_BREITE)
    return (if (von > 0) "…" else "") + anzeige.substring(von, bis).trim() + (if (bis < anzeige.length) "…" else "")
}

private class Kandidat(val rang: Int, val treffer: Treffer)

/**
 * Durchsucht [lehre] und [glossar] nach [frage] (ohne Rücksicht auf Groß-/Kleinschreibung,
 * Diakritika und DMG-Zeichen, siehe [normalisiere]). Treffer in Reiter, Name oder
 * Fragekopf kommen vor Treffern im Text; Glossarbegriffe führen zum ersten Abschnitt, der sie
 * verwendet. Höchstens 20 Treffer; eine leere Frage ergibt eine leere Liste.
 */
fun suche(frage: String, lehre: List<Kapitel>, glossar: Map<String, GlossaryTerm>): List<Treffer> {
    val q = normalisiere(frage.trim())
    if (q.isEmpty()) return emptyList()
    val kandidaten = mutableListOf<Kandidat>()

    /** Fügt einen Treffer hinzu, wenn [anzeige] die Frage enthält. */
    fun pruefe(rang: Int, ziel: Ziel, ort: String, anzeige: String, ganz: Boolean = false) {
        val n = normMitHerkunft(anzeige)
        val i = n.text.indexOf(q)
        if (i < 0) return
        val roh = if (ganz) 0 else n.herkunft[i]
        kandidaten += Kandidat(rang, Treffer(ziel, ort, ausschnitt(anzeige, roh)))
    }

    for ((k, kap) in lehre.withIndex()) {
        for ((a, ab) in kap.abschnitte.withIndex()) {
            val ziel = Ziel(k, ab.teil)
            val vorher = kandidaten.size
            pruefe(0, ziel, ab.name, ab.name)
            if (kandidaten.size == vorher && a == 0) pruefe(0, ziel, kap.reiter, kap.reiter)
            fun feld(label: String, text: String?) {
                if (text != null) pruefe(2, ziel, "${ab.name} · $label", klartext(text))
            }
            feld("Merksatz", ab.merksatz)
            feld(wannKopf(ab.teil), ab.wann)
            feld("Wie viele Rakʿa?", ab.wieviele)
            feld(warumKopf(ab.teil), ab.warum)
            feld("Worauf achte ich?", ab.achtung)
            for (f in ab.mehr) {
                val kopf = klartext(f.kopf)
                pruefe(1, ziel, "${ab.name} · $kopf", kopf, ganz = true)
                feld(kopf, f.text)
            }
        }
        for (b in kap.belege) {
            val ab = kap.abschnitte.firstOrNull { verweist(it, b.id) } ?: kap.abschnitte.firstOrNull() ?: continue
            pruefe(2, Ziel(k, ab.teil), "${ab.name} · Beleg", b.quote.german)
        }
    }

    for ((key, begriff) in glossar) {
        val imTerm = normalisiere(begriff.term).contains(q)
        if (!imTerm && !normalisiere(begriff.text).contains(q)) continue
        val stelle = lehre.withIndex().firstNotNullOfOrNull { (k, kap) ->
            kap.abschnitte.firstOrNull { verwendet(it, key) }?.let { k to it }
        } ?: continue
        val ziel = Ziel(stelle.first, stelle.second.teil)
        val ort = "${stelle.second.name} · Begriff ${begriff.term}"
        if (imTerm) {
            kandidaten += Kandidat(1, Treffer(ziel, ort, ausschnitt(begriff.text, 0)))
        } else {
            pruefe(2, ziel, ort, begriff.text)
        }
    }

    return kandidaten.sortedBy { it.rang }.take(MAX_TREFFER).map { it.treffer }
}

private fun texte(a: Abschnitt): List<String> =
    listOfNotNull(a.merksatz, a.wann, a.wieviele, a.warum, a.achtung) + a.mehr.flatMap { listOf(it.kopf, it.text) }

private fun verweist(a: Abschnitt, id: String): Boolean = texte(a).any { it.contains("{$id}") }

private fun verwendet(a: Abschnitt, key: String): Boolean =
    texte(a).any { t -> zerlege(t) { 0 }.any { it is Stueck.Begriff && it.key == key } }
