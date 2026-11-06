package de.gebetszeiten.ui.verstehen

/** Gebetsteile, die ein Abschnitt der Lehrseite behandeln kann. */
enum class Teil { FAJR, ZUHR, ASR, MAGHRIB, ISHA, DUHA, AWWABIN, TAHAJJUD, SHURUQ, ZAWAL, ISFIRAR }

/** Farbband eines Kapitels (ein Gebet oder der ganze Tag). */
enum class Band { FAJR, ZUHR, ASR, MAGHRIB, ISHA, TAG }

/** Art einer Rakʿa-Gruppe: Farḍ, bekräftigte Sunna, empfohlene Sunna, Witr. */
enum class RakaArt { FARD, MUAKKADA, MANDUB, WITR }

/** Eine Rakʿa-Gruppe; [beschriftung] überschreibt die Zahl, etwa „4 oder 2“. */
data class RakaBlock(val art: RakaArt, val anzahl: Int, val beschriftung: String? = null)

/** Beleg (Endnote) mit Zitat aus [Quote]. */
data class Beleg(val id: String, val quote: Quote)

/** Aufklappbare Frage; [text] trägt die Auszeichnung aus [zerlege]. */
data class Frage(val kopf: String, val text: String)

/** Ein Abschnitt der Lehrseite, etwa „Mittagsgebet“ mit Name „Ẓuhr“. Alle Texte tragen die Auszeichnung aus [zerlege]. */
data class Abschnitt(
    val teil: Teil,
    val kopf: String,
    val name: String,
    val merksatz: String,
    val wann: String,
    val wieviele: String?,
    val rakaat: List<RakaBlock>,
    val rakaatGumua: List<RakaBlock>? = null,
    val warum: String,
    val achtung: String,
    val mehr: List<Frage>,
)

/** Ein Reiter: Abschnitte plus die Belege, auf die ihre Texte mit `{id}` verweisen. */
data class Kapitel(
    val reiter: String,
    val band: Band,
    val abschnitte: List<Abschnitt>,
    val belege: List<Beleg>,
)
