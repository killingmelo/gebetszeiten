package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.Karaha
import de.gebetszeiten.prayer.PhotoKey
import de.gebetszeiten.prayer.Section

/**
 * Inhalt des Reiters „Zeiten verstehen“.
 *
 * Jeder arabische Text stammt wörtlich aus einem Ergebnis der Shamela-Werkzeuge
 * (nur Matn, nie Fußnote). Die geraden Anführungszeichen gehören zum Shamela-Text:
 * Im Kommentar markieren sie die Worte des Grundtexts. Belege je Zitat (book_id,
 * page_id, Zitierausgabe, Wortlaut, Übersetzung) stehen in
 * docs/superpowers/specs/2026-10-28-zitate-belege.md. Wer hier etwas ändert,
 * ändert die Belegdatei mit.
 */
data class Quote(val arabic: String, val german: String, val source: String)

data class VerstehenEntry(
    val section: Section,
    val title: String,
    val photo: PhotoKey?,
    val quotes: List<Quote>,
    val showsSundial: Boolean,
    val note: String? = null,
)

const val TRANSLATION_NOTE = "Deutsche Übersetzung: eigene Übersetzung."

// Quellenangaben wie von shamela_get_citation geliefert (alle mit Druckseiten, nicht automatisch nummeriert).
private const val MARAQI = "aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ"
private const val QUDURI = "al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī"
private const val HIDAYA = "al-Marġīnānī (gest. 593 n. H.), al-Hidāya"

val VERSTEHEN: List<VerstehenEntry> = listOf(
    VerstehenEntry(
        section = Section.FAJR,
        title = "Faǧr",
        photo = PhotoKey.FAJR,
        quotes = listOf(
            Quote(
                arabic = "والأوقات خمسة وقت الصبح من طلوع الفجر الصادق الى قبيل طلوع الشمس",
                german = "Die Zeiten sind fünf. Die Zeit des Morgengebets reicht vom Aufgang des echten Faǧr bis kurz vor Sonnenaufgang.",
                source = "$MARAQI, S. 72",
            ),
            Quote(
                arabic = "ولا معتبر بالفجر الكاذب وهو البياض الذي يبدو طولا ثم يعقبه الظلام",
                german = "Der falsche Faǧr zählt nicht. Das ist die Helligkeit, die längs erscheint und auf die wieder Dunkelheit folgt.",
                source = "$HIDAYA, Bd. 1, S. 40",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.SHURUQ_MAKRUH,
        title = "Šurūq und Makrūh beim Aufgang",
        photo = PhotoKey.SHURUQ,
        quotes = listOf(
            Quote(
                arabic = "أولها \"عند طلوع الشمس إلى أن ترتفع\" وتبيض قدر رمح أو رمحين",
                german = "Die erste [der verpönten Zeiten] ist beim Aufgang der Sonne, bis sie gestiegen ist und hell wird, etwa eine oder zwei Speerlängen hoch.",
                source = "$MARAQI, S. 76",
            ),
        ),
        showsSundial = false,
        note = "Die angezeigte Spanne rechnet die App: vom Sonnenaufgang bis " +
            "${Karaha.ISRAK_AFTER_SUNRISE_MIN} Minuten danach.",
    ),
    VerstehenEntry(
        section = Section.DUHA,
        title = "Ḍuḥā",
        // Kein Foto: Šurūq direkt davor zeigt dasselbe.
        photo = null,
        quotes = listOf(
            Quote(
                arabic = "وقت \"الضحى\" وابتداؤه من ارتفاع الشمس إلى قبل زوالها",
                german = "Die Zeit des Ḍuḥā beginnt mit dem Steigen der Sonne und reicht bis vor ihrem Zawāl.",
                source = "$MARAQI, S. 149",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.ZUHR,
        title = "Ẓuhr und Zawāl",
        photo = PhotoKey.ZUHR,
        quotes = listOf(
            Quote(
                arabic = "وأول وقت الظهر إذا زالت الشمس",
                german = "Die Zeit des Ẓuhr beginnt, wenn die Sonne sich [vom Höchststand] neigt.",
                source = "$QUDURI, S. 23",
            ),
            Quote(
                arabic = "الثاني \"عند استوائها\" في بطن السماء \"إلى أن تزول\" أي تميل إلى جهة الغرب",
                german = "Die zweite [verpönte Zeit] ist, wenn sie mitten am Himmel steht, bis zum Zawāl, das heißt bis sie sich nach Westen neigt.",
                source = "$MARAQI, S. 76",
            ),
        ),
        showsSundial = true,
        note = "Die angezeigte Spanne rechnet die App: von " +
            "${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor Ẓuhr bis Ẓuhr.",
    ),
    VerstehenEntry(
        section = Section.ASR,
        title = "ʿAṣr",
        photo = PhotoKey.ASR,
        quotes = listOf(
            Quote(
                arabic = "وآخر وقتها عند أبي حنيفة إذا صار ظل كل شيء مثليه سوي فيء الزوال وقال أبو يوسف ومحمد: إذا صار ظل كل شيء مثله وأول وقت العصر إذا خرج وقت الظهر على القولين",
                german = "Nach Abū Ḥanīfa endet ihre Zeit [die des Ẓuhr], wenn der Schatten jedes Dinges doppelt so lang ist wie das Ding, ohne den Schatten zur Zeit des Zawāl. Abū Yūsuf und Muḥammad sagen: wenn der Schatten jedes Dinges so lang ist wie das Ding. Die Zeit des ʿAṣr beginnt, wenn die Zeit des Ẓuhr endet, nach beiden Ansichten.",
                source = "$QUDURI, S. 23",
            ),
        ),
        showsSundial = true,
        note = "Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet.",
    ),
    VerstehenEntry(
        section = Section.ISFIRAR_MAKRUH,
        title = "Makrūh beim Vergilben der Sonne",
        // Kein Foto: ʿAṣr direkt davor zeigt dasselbe.
        photo = null,
        quotes = listOf(
            Quote(
                arabic = "الثالث \"عند اصفرارها\" وضعفها حتى تقدر العين على مقابلتها \"إلى أن تغرب\"",
                german = "Die dritte [verpönte Zeit] ist, wenn sie gelb und schwach wird, so dass das Auge ihrem Anblick standhält, bis sie untergeht.",
                source = "$MARAQI, S. 76",
            ),
        ),
        showsSundial = false,
        note = "Die angezeigte Spanne rechnet die App: von " +
            "${Karaha.ISFIRAR_BEFORE_MAGHRIB_MIN} Minuten vor Maġrib bis Maġrib.",
    ),
    VerstehenEntry(
        section = Section.MAGHRIB,
        title = "Maġrib",
        photo = PhotoKey.MAGHRIB,
        quotes = listOf(
            Quote(
                arabic = "والمغرب منه الى غروب الشفق الاحمر على المفتى به",
                german = "Die Zeit des Maġrib reicht von ihm [dem Sonnenuntergang] bis zum Verschwinden der roten Abenddämmerung, nach der Ansicht, nach der die Fatwā gegeben wird.",
                source = "$MARAQI, S. 72",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.AWWABIN,
        title = "Auwābīn",
        // Kein Foto: Maġrib direkt davor zeigt dasselbe.
        photo = null,
        quotes = listOf(
            Quote(
                arabic = "ندب \"ست\" ركعات \"بعد المغرب\" لقوله ﷺ: \"من صلى بعد المغرب ست ركعات كتب من الأوابين\"",
                german = "Empfohlen sind sechs Rakʿa nach dem Maġrib, wegen seines Wortes, Gott segne ihn und schenke ihm Heil: „Wer nach dem Maġrib sechs Rakʿa betet, wird zu den Auwābīn gezählt.“",
                source = "$MARAQI, S. 146",
            ),
            Quote(
                arabic = "والأواب هو الذي إذا أذنب ذنبا بادر إلى التوبة",
                german = "Der Auwāb ist, wer nach einer Sünde ohne Zögern zur Reue eilt.",
                source = "$MARAQI, S. 146",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.ISHA,
        title = "ʿIšāʾ",
        photo = PhotoKey.ISHA,
        quotes = listOf(
            Quote(
                arabic = "وأول وقت العشاء إذا غاب الشفق وآخر وقتها ما لم يطلع الفجر",
                german = "Die Zeit des ʿIšāʾ beginnt, wenn die Abenddämmerung (Šafaq) verschwunden ist, und endet, wenn der Faǧr aufgeht.",
                source = "$QUDURI, S. 23",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.TAHAJJUD,
        title = "Tahaǧǧud",
        // Kein Foto: ʿIšāʾ direkt davor zeigt dasselbe.
        photo = null,
        quotes = listOf(
            Quote(
                arabic = "\"وصلاة الليل\" خصوصا في الثلث الأخير منه \"أفضل من صلاة النهار\" لأنه أشق على النفس",
                german = "Das Nachtgebet, besonders im letzten Drittel der Nacht, ist vorzüglicher als das Gebet am Tag, weil es der Seele schwerer fällt.",
                source = "$MARAQI, S. 148",
            ),
        ),
        showsSundial = false,
    ),
    VerstehenEntry(
        section = Section.JAMAA,
        title = "Ǧamāʿa am Morgen",
        photo = PhotoKey.FAJR,
        quotes = listOf(
            Quote(
                arabic = "ويستحب الإسفار بالفجر للرجال",
                german = "Für Männer ist es empfohlen, den Faǧr zu beten, wenn es hell wird (Isfār).",
                source = "$MARAQI, S. 74",
            ),
            Quote(
                arabic = "ولأن في الإسفار تكثير الجماعة وفي التغليس تقليلها وما يؤدي إلى التكثير أفضل",
                german = "Denn beim Isfār kommen mehr Menschen zur Gemeinschaft, beim Taġlīs [dem Beten in der Dunkelheit] weniger, und was zu mehr führt, ist vorzüglicher.",
                source = "$MARAQI, S. 74",
            ),
        ),
        showsSundial = false,
        note = "Die Ǧamāʿa-Zeit ist keine amtliche Zeit. Sie ergibt sich aus dem " +
            "Sonnenaufgang minus dem Vorlauf aus den Einstellungen. Die eigene " +
            "Moschee kann abweichen.",
    ),
)
