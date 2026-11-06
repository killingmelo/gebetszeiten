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

/** Wo eine Zitatgruppe im Reiter steht (Leseordnung vom 05.11.2026). */
enum class Place { SCHOLARS, CAUTION }

/** Weitere Zitate unter einer eigenen Überschrift (z. B. „Rakʿa“). */
data class QuoteGroup(val heading: String, val quotes: List<Quote>, val place: Place = Place.SCHOLARS)

data class VerstehenEntry(
    val section: Section,
    val title: String,
    val photo: PhotoKey?,
    val quotes: List<Quote>,
    val showsSundial: Boolean,
    val note: String? = null,
    val groups: List<QuoteGroup> = emptyList(),
)

/**
 * Ein Begriff im Glossar am Ende jedes Reiters. Wortsinn und Herkunft dürfen
 * ohne Quelle stehen (Entscheidung vom 05.11.2026); Koran und Ḥadīṯ nur
 * wörtlich aus den Werkzeugen, mit Fundstelle in Klammern; Regeln nie hier,
 * sondern nur als Zitat in den Reitern.
 */
data class GlossaryTerm(val term: String, val text: String)

const val TRANSLATION_NOTE = "Deutsche Übersetzung: eigene Übersetzung."

// Quellenangaben wie von shamela_get_citation geliefert (alle mit Druckseiten, nicht automatisch nummeriert).
internal const val MARAQI = "aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ"
internal const val QUDURI = "al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī"
private const val HIDAYA = "al-Marġīnānī (gest. 593 n. H.), al-Hidāya"
private const val IBN_ABIDIN = "Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār"

private const val RAKA_HEADING = "Rakʿa"

// Zitate, die in mehreren Reitern stehen (Belege in der Belegdatei, Abschnitt „05.11.2026“).
internal val SUNNA_MUAKKADA = Quote(
    arabic = "سن سنة مؤكدة ركعتان قبل الفجر وركعتان بعد الظهر وبعد المغرب وبعد العشاء وأربع قبل الظهر وقبل الجمعة وبعدها بتسليمة",
    german = "Sunna muʾakkada sind zwei Rakʿa vor dem Faǧr, zwei nach dem Ẓuhr, nach dem Maġrib und nach dem ʿIšāʾ, und vier vor dem Ẓuhr, vor der Ǧumʿa und nach ihr, mit einem Friedensgruß.",
    source = "$MARAQI, S. 145 f.",
)
private val SUNNA_MANDUB = Quote(
    arabic = "وندب أربع قبل العصر والعشاء وبعده وست بعد المغرب",
    german = "Empfohlen sind vier vor dem ʿAṣr, vor dem ʿIšāʾ und nach ihm, und sechs nach dem Maġrib.",
    source = "$MARAQI, S. 146",
)

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
        groups = listOf(
            QuoteGroup(RAKA_HEADING, listOf(SUNNA_MUAKKADA)),
            QuoteGroup(
                "Sunna muʾakkada und empfohlene Sunna",
                listOf(
                    Quote(
                        arabic = "والسنة لغة الطريقة ولو سيئة واصطلاحا الطريقة المسلوكة في الدين من غير لزوم على المواظبة وهي المؤكدة إن كان النبي ﷺ تركها أحيانا وأما التي لم يواظب عليها فهي المندوبة",
                        german = "Sunna heißt sprachlich „Weg“, auch ein schlechter. Im Fachgebrauch ist es der Weg, der in der Religion ohne Verpflichtung beständig begangen wird. Sie ist muʾakkada, wenn der Prophet ﷺ sie manchmal ausließ; was er nicht beständig übte, ist mandūb.",
                        source = "$MARAQI, S. 31",
                    ),
                    Quote(
                        arabic = "مَنْ تَرَكَ سُنَنَ الصَّلَوَاتِ الْخَمْسِ قِيلَ: لَا يَأْثَمُ وَالصَّحِيحُ أَنَّهُ يَأْثَمُ",
                        german = "Wer die Sunna-Gebete der fünf Gebete auslässt: Es wurde gesagt, er sündige nicht; richtig ist, dass er sündigt.",
                        source = "$IBN_ABIDIN, Bd. 1, S. 104",
                    ),
                    Quote(
                        arabic = "وَالْمُرَادُ التَّرْكُ بِلَا عُذْرٍ عَلَى سَبِيلِ الْإِصْرَارِ",
                        german = "Gemeint ist das Auslassen ohne Entschuldigung, als beharrliche Gewohnheit.",
                        source = "$IBN_ABIDIN, Bd. 1, S. 104",
                    ),
                    Quote(
                        arabic = "وَحُكْمُهُ الثَّوَابُ عَلَى الْفِعْلِ وَعَدَمُ اللَّوْمِ عَلَى التَّرْكِ",
                        german = "[Über die empfohlene Sunna:] Seine Regel ist Lohn für das Tun und kein Tadel für das Auslassen.",
                        source = "$IBN_ABIDIN, Bd. 1, S. 123 (nach al-Imdād)",
                    ),
                ),
            ),
        ),
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
        groups = listOf(
            QuoteGroup(
                "Was in den Makrūh-Zeiten gilt",
                place = Place.CAUTION,
                quotes = listOf(
                    Quote(
                        arabic = "ثلاثة أوقات لا يصح فيها شيء من الفرائض والواجبات التي لزمت في الذمة قبل دخولها",
                        german = "Drei Zeiten gibt es, in denen keine Pflicht gültig ist, die schon vor ihrem Eintritt geschuldet war, weder Farḍ noch Wāǧib.",
                        source = "$MARAQI, S. 75 f.",
                    ),
                    Quote(
                        arabic = "والأوقات الثلاثة يكره فيها النافلة كراهة تحريم ولو كان لها سبب كالمنذور وركعتي الطواف",
                        german = "In den drei Zeiten ist das freiwillige Gebet stark verpönt (Karāhat at-taḥrīm), auch wenn es einen Anlass hat, wie ein gelobtes Gebet oder die zwei Rakʿa des Ṭawāf.",
                        source = "$MARAQI, S. 76",
                    ),
                    Quote(
                        arabic = "ويكره التنفل بعد طلوع الفجر بأكثر من سنته وبعد صلاته وبعد صلاة العصر",
                        german = "Verpönt ist das freiwillige Gebet nach dem Aufgang des Faǧr über seine Sunna hinaus, nach dem Faǧr-Gebet und nach dem ʿAṣr-Gebet.",
                        source = "$MARAQI, S. 76",
                    ),
                ),
            ),
        ),
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
        note = "Die angezeigte Spanne rechnet die App: von " +
            "${Karaha.ISRAK_AFTER_SUNRISE_MIN} Minuten nach Sonnenaufgang bis " +
            "${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor Ẓuhr.",
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
        ),
        showsSundial = true,
        note = "Die angezeigte Spanne rechnet die App: von " +
            "${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor Ẓuhr bis Ẓuhr.",
        groups = listOf(
            QuoteGroup(
                "Makrūh im Zawāl",
                listOf(
                    Quote(
                        arabic = "الثاني \"عند استوائها\" في بطن السماء \"إلى أن تزول\" أي تميل إلى جهة الغرب",
                        german = "Die zweite [verpönte Zeit] ist, wenn sie mitten am Himmel steht, bis zum Zawāl, das heißt bis sie sich nach Westen neigt.",
                        source = "$MARAQI, S. 76",
                    ),
                ),
                Place.CAUTION,
            ),
            QuoteGroup(
                RAKA_HEADING,
                listOf(
                    SUNNA_MUAKKADA,
                    Quote(
                        arabic = "\"ركعتان بعد الظهر\" ويندب أن يضم إليها ركعتان فتصير أربعا",
                        german = "Zwei Rakʿa nach dem Ẓuhr; empfohlen ist, zwei hinzuzufügen, so dass es vier werden.",
                        source = "$MARAQI, S. 145",
                    ),
                ),
            ),
        ),
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
        note = "Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet. Diyanet folgt " +
            "der Ansicht von Abū Yūsuf und Muḥammad: wenn der Schatten so lang ist wie " +
            "das Ding. Nach Abū Ḥanīfa beginnt ʿAṣr beim doppelten Schatten; die Sonnenuhr " +
            "zeigt beide Linien.",
        groups = listOf(
            QuoteGroup(
                RAKA_HEADING,
                listOf(
                    SUNNA_MANDUB,
                    Quote(
                        arabic = "وورد أنه ﷺ صلى ركعتين وورد أربعا فلذا خيره القدوري بينهما",
                        german = "[Vor dem ʿAṣr:] Überliefert ist, dass er ﷺ zwei betete, und überliefert sind auch vier; deshalb ließ al-Qudūrī die Wahl zwischen beiden.",
                        source = "$MARAQI, S. 146",
                    ),
                ),
            ),
        ),
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
        groups = listOf(
            QuoteGroup(
                "Das ʿAṣr desselben Tages",
                listOf(
                    Quote(
                        arabic = "\"كما صح عصر اليوم\" بأدائه \"عند الغروب\" لبقاء سببه وهو الجزء المتصل به الأداء من الوقت \"مع الكراهة\" للتأخير المنهي عنه لا لذات الوقت",
                        german = "So wie das ʿAṣr desselben Tages gültig ist, wenn man es beim Sonnenuntergang verrichtet, weil sein Anlass noch besteht, nämlich der Teil der Zeit, an den die Verrichtung anschließt; aber verpönt, wegen des verbotenen Aufschiebens, nicht wegen der Zeit selbst.",
                        source = "$MARAQI, S. 76",
                    ),
                ),
            ),
        ),
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
        groups = listOf(QuoteGroup(RAKA_HEADING, listOf(SUNNA_MUAKKADA, SUNNA_MANDUB))),
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
        note = "Die angezeigte Spanne rechnet die App: von Maġrib bis ʿIšāʾ.",
        groups = listOf(
            QuoteGroup(
                "Zusätzlich oder darin enthalten?",
                listOf(
                    Quote(
                        arabic = "وقد عطفنا المندوبات على المؤكدات كما في الكنز وغيره من المعتبرات وظاهره المغايرة فتكون الست في المغرب غير الركعتين المؤكدتين. وكذا في الأربع بعد الظهر. وقيل بها",
                        german = "Wir haben die empfohlenen an die bekräftigten angereiht, wie im Kanz und anderen maßgeblichen Werken. Dem Anschein nach sind sie verschieden: Die sechs beim Maġrib sind nicht die beiden bekräftigten Rakʿa, ebenso die vier nach dem Ẓuhr. Es wird aber auch gesagt, sie zählten dazu.",
                        source = "$MARAQI, S. 147",
                    ),
                ),
            ),
        ),
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
        groups = listOf(
            QuoteGroup(
                RAKA_HEADING,
                listOf(
                    SUNNA_MUAKKADA,
                    SUNNA_MANDUB,
                    Quote(
                        arabic = "الوتر واجب وهو ثلاث ركعات",
                        german = "Der Witr ist wāǧib und hat drei Rakʿa.",
                        source = "$MARAQI, S. 139",
                    ),
                ),
            ),
        ),
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
        note = "Die angezeigte Zeit rechnet die App: das letzte Drittel der Nacht zwischen Maġrib und Faǧr.",
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

/**
 * Glossar (freigegeben am 05.11.2026). Koran und Ḥadīṯ wörtlich aus den
 * Werkzeugen übersetzt, Fundstellen in Klammern; Belege in der Belegdatei.
 * Welche Begriffe ein Reiter zeigt, steht in [VERSTEHEN_TABS].
 */
val GLOSSAR: Map<String, GlossaryTerm> = listOf(
    GlossaryTerm(
        "Faǧr",
        "Morgendämmerung, von faǧara „aufbrechen“: das Licht bricht durch die Nacht. Im Koran: " +
            "„und die Lesung des Faǧr; die Lesung des Faǧr wird bezeugt.“ (al-Isrāʾ 17:78)",
    ),
    GlossaryTerm(
        "Šurūq",
        "Sonnenaufgang, von šaraqa „aufgehen“. Šurūq ist kein Gebet, es beendet die Zeit des Faǧr. " +
            "Der Prophet sagte: „Wenn die Sonne aufgeht, halte dich vom Gebet zurück, denn sie geht " +
            "zwischen den beiden Hörnern des Šayṭān auf.“ (Muslim 612)",
    ),
    GlossaryTerm(
        "Ḍuḥā",
        "Vormittag, wenn die Sonne gestiegen ist. Der Prophet sagte: „Jeden Morgen ist auf jedes " +
            "Gelenk eines jeden von euch eine Spende fällig: jedes Subḥāna llāh ist eine Spende, jedes " +
            "al-ḥamdu li-llāh ist eine Spende, jedes Lā ilāha illā llāh ist eine Spende, jedes " +
            "Allāhu akbar ist eine Spende, das Gute gebieten ist eine Spende, das Schlechte verwehren " +
            "ist eine Spende. Und zwei Rakʿa, die man am Vormittag betet, genügen für all das.“ (Muslim 720)",
    ),
    GlossaryTerm(
        "Zawāl",
        "Das Sich-Neigen der Sonne nach ihrem Höchststand, von zāla „weichen“. Mit ihm beginnt Ẓuhr. " +
            "Der Prophet sagte: „Die Zeit des Ẓuhr ist, wenn die Sonne sich neigt.“ (Muslim 612)",
    ),
    GlossaryTerm(
        "Ẓuhr",
        "Mittag. Im Koran: „Verrichte das Gebet vom Neigen der Sonne bis zum Dunkel der Nacht.“ (al-Isrāʾ 17:78)",
    ),
    GlossaryTerm("ʿAṣr", "Nachmittag, auch „Zeit, Zeitalter“. Nach ihm ist die 103. Sure benannt."),
    GlossaryTerm("Maġrib", "Sonnenuntergang, auch „Westen“, von ġaraba „untergehen“."),
    GlossaryTerm("ʿIšāʾ", "Abend nach Einbruch der Dunkelheit."),
    GlossaryTerm(
        "Šafaq",
        "Der Dämmerungsschein am Abendhimmel. Man unterscheidet den roten Schein direkt nach " +
            "Sonnenuntergang und den weißen danach.",
    ),
    GlossaryTerm(
        "Auwābīn",
        "Mehrzahl von auwāb, „einer, der immer wieder (zu Gott) zurückkehrt“, von āba „zurückkehren“. " +
            "Im Koran: „Er ist denen, die sich immer wieder zu Ihm wenden, vergebend.“ (al-Isrāʾ 17:25) " +
            "Der Name steht für zwei Gebete: die sechs Rakʿa nach dem Maġrib und in einem Ḥadīṯ für das " +
            "Ḍuḥā: „Das Gebet der Auwābīn ist, wenn die jungen Kamele die Hitze spüren.“ (Muslim 748)",
    ),
    GlossaryTerm(
        "Tahaǧǧud",
        "Von haǧada „schlafen“; tahaǧǧada heißt „den Schlaf verlassen“, nachts zum Gebet aufstehen. " +
            "Im Koran: „Und in einem Teil der Nacht halte damit das Tahaǧǧud, als zusätzliche Gabe für " +
            "dich; vielleicht erweckt dich dein Herr zu einer gelobten Stellung.“ (al-Isrāʾ 17:79)",
    ),
    GlossaryTerm(
        "Witr",
        "„Ungerade“. Der Prophet sagte: „Ihr Leute des Korans, betet den Witr, denn Gott ist einer " +
            "(witr) und liebt das Ungerade.“ (Abū Dāwūd 1416)",
    ),
    GlossaryTerm(
        "Ǧamāʿa",
        "Gemeinschaft. Der Prophet sagte: „Das Gebet in Gemeinschaft übertrifft das Gebet des " +
            "Einzelnen um siebenundzwanzig Stufen.“ (Buḫārī 645)",
    ),
    GlossaryTerm(
        "Isfār und Taġlīs",
        "Isfār von asfara „hell werden“, Taġlīs von ġalas, dem Dunkel am Ende der Nacht. Der Prophet " +
            "sagte: „Betet den Faǧr bei Helligkeit, denn das bringt größeren Lohn.“ (Tirmiḏī 154)",
    ),
    GlossaryTerm(
        "Rakʿa",
        "Von rakaʿa „sich verbeugen“; eine Einheit des Gebets mit Stehen, Verbeugung und zwei Niederwerfungen.",
    ),
    GlossaryTerm(
        "Farḍ",
        "Von faraḍa „festsetzen“: was Gott zur Pflicht gemacht hat. Gott sagt im Ḥadīṯ qudsī: „Mein " +
            "Diener nähert sich Mir mit nichts, das Mir lieber ist, als mit dem, was Ich ihm zur Pflicht " +
            "gemacht habe.“ (Buḫārī 6502)",
    ),
    GlossaryTerm(
        "Nafl",
        "Mehrzahl Nawāfil, von nafala „zusätzlich geben“: freiwilliges Gebet über die Pflicht hinaus. " +
            "Im selben Ḥadīṯ: „Und Mein Diener nähert sich Mir weiter durch freiwillige Taten, bis Ich " +
            "ihn liebe.“ (Buḫārī 6502)",
    ),
    GlossaryTerm(
        "Sunna",
        "„Weg, Brauch“; hier die Gebete, die der Prophet vor oder nach dem Pflichtgebet betete.",
    ),
    GlossaryTerm(
        "Sunna muʾakkada",
        "„Bekräftigte Sunna“. Was dazu gilt, sagen die Zitate im Reiter Faǧr.",
    ),
    GlossaryTerm(
        "Mandūb",
        "„Empfohlen“, auch mustaḥabb genannt. Was dazu gilt, sagen die Zitate im Reiter Faǧr.",
    ),
    GlossaryTerm(
        "Wāǧib",
        "„Notwendig“; im hanafitischen Sprachgebrauch verpflichtend, aber eine Stufe unter Farḍ, " +
            "zum Beispiel der Witr.",
    ),
    GlossaryTerm(
        "Makrūh",
        "Von kariha „nicht mögen“: verpönt. Karāhat at-taḥrīm ist die starke Verpöntheit nahe am Verbot.",
    ),
    GlossaryTerm(
        "Fatwā",
        "Rechtsauskunft eines Gelehrten. „Nach der die Fatwā gegeben wird“ heißt: die Ansicht, nach " +
            "der in der Schule entschieden wird.",
    ),
).associateBy { it.term }
