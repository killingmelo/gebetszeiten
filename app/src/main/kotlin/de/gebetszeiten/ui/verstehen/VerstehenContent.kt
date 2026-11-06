package de.gebetszeiten.ui.verstehen

/**
 * Gemeinsame Bausteine der Seite „Verstehen“: Zitat, Glossar und Quellenangaben.
 * Die Kapitel selbst stehen in [LEHRE] (LehrInhalt.kt).
 *
 * Jeder arabische Text stammt wörtlich aus einem Ergebnis der Shamela-Werkzeuge
 * (nur Matn, nie Fußnote). Die geraden Anführungszeichen gehören zum Shamela-Text:
 * Im Kommentar markieren sie die Worte des Grundtexts. Belege je Zitat (book_id,
 * page_id, Zitierausgabe, Wortlaut, Übersetzung) stehen in
 * docs/superpowers/specs/2026-10-28-zitate-belege.md. Wer hier etwas ändert,
 * ändert die Belegdatei mit.
 */
data class Quote(val arabic: String, val german: String, val source: String)

/**
 * Ein Begriff im Glossar (aufklappbar aus dem Lehrtext). Wortsinn und Herkunft dürfen
 * ohne Quelle stehen (Entscheidung vom 05.11.2026); Koran und Ḥadīṯ nur
 * wörtlich aus den Werkzeugen, mit Fundstelle in Klammern; Regeln nie hier,
 * sondern nur als Zitat in den Kapiteln.
 */
data class GlossaryTerm(val term: String, val text: String)

const val TRANSLATION_NOTE = "Deutsche Übersetzung: eigene Übersetzung."

// Quellenangaben wie von shamela_get_citation geliefert (alle mit Druckseiten, nicht automatisch nummeriert).
internal const val MARAQI = "aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ"
internal const val QUDURI = "al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī"
internal const val HIDAYA = "al-Marġīnānī (gest. 593 n. H.), al-Hidāya"
internal const val IBN_ABIDIN = "Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār"

// Matn ad-Durr al-muḫtār über der Linie im Druck des Radd al-muḥtār (Entscheidung vom 06.11.2026):
// Bd. und S. sind die des Radd-Drucks (Ḥalabī), wie von shamela_get_citation geliefert.
internal const val DURR = "al-Ḥaṣkafī (gest. 1088 n. H.), ad-Durr al-muḫtār, gedruckt mit Radd al-muḥtār"

// Zitate, die in mehreren Reitern stehen (Belege in der Belegdatei, Abschnitt „05.11.2026“).
internal val SUNNA_MUAKKADA = Quote(
    arabic = "سن سنة مؤكدة ركعتان قبل الفجر وركعتان بعد الظهر وبعد المغرب وبعد العشاء وأربع قبل الظهر وقبل الجمعة وبعدها بتسليمة",
    german = "Sunna muʾakkada sind zwei Rakʿa vor dem Faǧr, zwei nach dem Ẓuhr, nach dem Maġrib und nach dem ʿIšāʾ, und vier vor dem Ẓuhr, vor der Ǧumʿa und nach ihr, mit einem Friedensgruß.",
    source = "$MARAQI, S. 145 f.",
)
internal val SUNNA_MANDUB = Quote(
    arabic = "وندب أربع قبل العصر والعشاء وبعده وست بعد المغرب",
    german = "Empfohlen sind vier vor dem ʿAṣr, vor dem ʿIšāʾ und nach ihm, und sechs nach dem Maġrib.",
    source = "$MARAQI, S. 146",
)

/**
 * Glossar (freigegeben am 05.11.2026). Koran und Ḥadīṯ wörtlich aus den
 * Werkzeugen übersetzt, Fundstellen in Klammern; Belege in der Belegdatei.
 * Im Lehrtext verweist [[Begriff]] auf einen Eintrag.
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
    // Neue Einträge für „Verstehen neu“, freigegeben am 06.11.2026.
    GlossaryTerm(
        "Ǧumʿa",
        "Freitag, von ǧamaʿa „versammeln“: der Tag der Versammlung, auch das Gebet an diesem Tag. " +
            "Im Koran: „Ihr, die ihr glaubt, wenn am Tag der Ǧumʿa zum Gebet gerufen wird, dann eilt " +
            "zum Gedenken Gottes und lasst den Handel. Das ist besser für euch, wenn ihr es wüsstet.“ (al-Ǧumʿa 62:9)",
    ),
    GlossaryTerm(
        "Iqāma",
        "Von aqāma „aufrichten, verrichten“: der zweite Ruf unmittelbar vor dem Gebet. Dasselbe Wort " +
            "steht im Koran für das Verrichten des Gebets: „Und verrichtet das Gebet, gebt die Zakāt und " +
            "verbeugt euch mit denen, die sich verbeugen.“ (al-Baqara 2:43)",
    ),
    GlossaryTerm(
        "Qaḍāʾ",
        "Von qaḍā „erfüllen, begleichen“: das Nachholen eines Gebets nach seiner Zeit. Das Gegenwort ist " +
            "Adāʾ, das Verrichten in der Zeit. Der Prophet sagte: „Wer ein Gebet vergisst, soll es beten, " +
            "wenn er sich daran erinnert; es gibt dafür keine andere Sühne als diese.“ (Buḫārī 597)",
    ),
    GlossaryTerm(
        "Istiwāʾ",
        "Von istawā „gerade, ausgeglichen sein“, auch „zur vollen Größe gelangen“: der höchste Stand der " +
            "Sonne am Mittag, kurz vor dem Zawāl. Im Koran über Mūsā: „Und als er seine volle Kraft erreicht " +
            "hatte und ausgewachsen war, gaben Wir ihm Urteilskraft und Wissen.“ (al-Qaṣaṣ 28:14)",
    ),
    GlossaryTerm(
        "Karāhat at-taḥrīm",
        "Karāha von kariha „nicht mögen“, taḥrīm von ḥarrama „verbieten“: die starke Verpöntheit nahe am " +
            "Verbot. Die leichte Stufe heißt Karāhat at-tanzīh. Im Koran steht makrūh nach einer Reihe von " +
            "Verboten: „All das, was davon schlecht ist, ist bei deinem Herrn verhasst.“ (al-Isrāʾ 17:38)",
    ),
    GlossaryTerm(
        "Zenit",
        "Im Alltag der höchste Stand der Sonne am Tag, arabisch Istiwāʾ; genau genommen der Punkt " +
            "senkrecht über dem Kopf, den die Sonne in Deutschland nie erreicht. Das Wort kommt aus dem " +
            "Arabischen samt ar-raʾs, „Richtung des Kopfes“. Das Sich-Neigen der Sonne danach heißt Zawāl; " +
            "mit ihm beginnt Ẓuhr.",
    ),
    GlossaryTerm(
        "Aḏān",
        "Von aḏina „hören“, auch „ankündigen“: der Ruf zum Gebet. Der Prophet sagte: „Wenn das Gebet " +
            "fällig ist, soll einer von euch für euch den Aḏān rufen, und der Älteste von euch soll euch " +
            "vorbeten.“ (Buḫārī 628)",
    ),
    GlossaryTerm(
        "Iṣfirār",
        "Von iṣfarra „gelb werden“, aṣfar „gelb“: das Vergilben der Sonne vor dem Untergang.",
    ),
    GlossaryTerm(
        "Ṭawāf",
        "Von ṭāfa „umkreisen“: das Umschreiten der Kaʿba. Im Koran: „Dann sollen sie ihre Ungepflegtheit " +
            "beenden, ihre Gelübde erfüllen und das alte Haus umschreiten.“ (al-Ḥaǧǧ 22:29)",
    ),
    GlossaryTerm(
        "Ẓāhir ar-riwāya",
        "„Die offenkundige Überlieferung“: die Lehre der Schule, wie sie in den Grundwerken von " +
            "Imām Muḥammad überliefert ist.",
    ),
).associateBy { it.term }
