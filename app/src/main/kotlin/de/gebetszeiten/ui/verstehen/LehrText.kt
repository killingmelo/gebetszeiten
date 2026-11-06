package de.gebetszeiten.ui.verstehen

/** Stück eines ausgezeichneten Textes. */
sealed interface Stueck {
    data class Text(val s: String) : Stueck
    data class Begriff(val key: String, val anzeige: String) : Stueck
    data class Verweis(val nr: Int) : Stueck
}

/** `[[Key]]`, `[[Key|Anzeige]]` oder `{beleg-id}`. */
private val AUSZEICHNUNG = Regex("""\[\[([^\[\]|]+)(?:\|([^\[\]]+))?]]|\{([a-z0-9-]+)}""")
private val VERWEIS = Regex("""\{([a-z0-9-]+)}""")

/**
 * Zerlegt [text] in Text, Begriffe (`[[Key]]`, `[[Key|Anzeige]]`) und Verweise (`{beleg-id}`).
 * [nummer] liefert die Endnotennummer zur Beleg-ID; benachbarter Text bleibt in einem Stück.
 */
fun zerlege(text: String, nummer: (String) -> Int): List<Stueck> {
    val out = mutableListOf<Stueck>()
    val roh = StringBuilder()
    fun leeren() {
        if (roh.isNotEmpty()) { out += Stueck.Text(roh.toString()); roh.clear() }
    }
    var pos = 0
    for (m in AUSZEICHNUNG.findAll(text)) {
        roh.append(text, pos, m.range.first)
        leeren()
        val key = m.groups[1]?.value
        if (key != null) {
            out += Stueck.Begriff(key, m.groups[2]?.value ?: key)
        } else {
            out += Stueck.Verweis(nummer(m.groups[3]!!.value))
        }
        pos = m.range.last + 1
    }
    roh.append(text, pos, text.length)
    leeren()
    return out
}

/**
 * Nummeriert die Belege eines Kapitels nach erstem Auftreten: je Abschnitt merksatz, wann,
 * wieviele, warum, achtung, dann kopf und text jeder Frage in Listenfolge.
 * Ein Verweis auf eine unbekannte Beleg-ID wirft [IllegalArgumentException].
 */
fun nummern(kapitel: Kapitel): Map<String, Int> {
    val bekannt = kapitel.belege.map { it.id }.toSet()
    val result = LinkedHashMap<String, Int>()
    fun lies(text: String?) {
        if (text == null) return
        for (m in VERWEIS.findAll(text)) {
            val id = m.groupValues[1]
            require(id in bekannt) { "Unbekannter Beleg: $id" }
            if (id !in result) result[id] = result.size + 1
        }
    }
    for (a in kapitel.abschnitte) {
        lies(a.merksatz); lies(a.wann); lies(a.wieviele); lies(a.warum); lies(a.achtung)
        for (f in a.mehr) { lies(f.kopf); lies(f.text) }
    }
    return result
}

/** Breite einer Rakʿa-Gruppe in der Darstellung: mindestens 2. */
fun rakaGewicht(block: RakaBlock): Float = maxOf(block.anzahl, 2).toFloat()
