package de.gebetszeiten.ui.verstehen

import de.gebetszeiten.core.prayertimes.Karaha

/*
 * Der Lehrinhalt der neuen Seite „Verstehen“, Reiter für Reiter, im Ton C der
 * freigegebenen Probe (docs/entwurf/verstehen-konzept.html). Wortlaut aus den
 * am 06.11.2026 freigegebenen Entwürfen (.superpowers/sdd/2026-11-06-verstehen-neu/).
 * Jeder arabische Text stammt wörtlich aus einem Ergebnis der Shamela-Werkzeuge;
 * die Fundstellen stehen in docs/superpowers/specs/2026-10-28-zitate-belege.md.
 *
 * Die Kapitel stehen als private Werte vor [LEHRE], weil Kotlin Werte auf
 * Dateiebene in Textfolge belegt.
 */

// Zitate, die in mehreren Kapiteln als Beleg dienen.
private val ZEIT_ZUHR_ENDE = Quote(
    arabic = "وآخر وقتها عند أبي حنيفة إذا صار ظل كل شيء مثليه سوي فيء الزوال وقال أبو يوسف ومحمد: إذا صار ظل كل شيء مثله وأول وقت العصر إذا خرج وقت الظهر على القولين",
    german = "Nach Abū Ḥanīfa endet ihre Zeit [die des Ẓuhr], wenn der Schatten jedes Dinges doppelt so lang ist wie das Ding, ohne den Schatten zur Zeit des Zawāl. Abū Yūsuf und Muḥammad sagen: wenn der Schatten jedes Dinges so lang ist wie das Ding. Die Zeit des ʿAṣr beginnt, wenn die Zeit des Ẓuhr endet, nach beiden Ansichten.",
    source = "$QUDURI, S. 23",
)
private val NAFL_NACH_FAJR_ASR = Quote(
    arabic = "ويكره التنفل بعد طلوع الفجر بأكثر من سنته وبعد صلاته وبعد صلاة العصر",
    german = "Verpönt ist das freiwillige Gebet nach dem Aufgang des Faǧr über seine Sunna hinaus, nach dem Faǧr-Gebet und nach dem ʿAṣr-Gebet.",
    source = "$MARAQI, S. 76",
)
private val ZAWAL_ZEIT = Quote(
    arabic = "الثاني \"عند استوائها\" في بطن السماء \"إلى أن تزول\" أي تميل إلى جهة الغرب",
    german = "Die zweite [verpönte Zeit] ist, wenn sie mitten am Himmel steht, bis zum Zawāl, das heißt bis sie sich nach Westen neigt.",
    source = "$MARAQI, S. 76",
)
private val SUNNA_ZAEHLUNG = Quote(
    arabic = "وقد عطفنا المندوبات على المؤكدات كما في الكنز وغيره من المعتبرات وظاهره المغايرة فتكون الست في المغرب غير الركعتين المؤكدتين. وكذا في الأربع بعد الظهر. وقيل بها",
    german = "Wir haben die empfohlenen an die bekräftigten angereiht, wie im Kanz und anderen maßgeblichen Werken. Dem Anschein nach sind sie verschieden: Die sechs beim Maġrib sind nicht die beiden bekräftigten Rakʿa, ebenso die vier nach dem Ẓuhr. Es wird aber auch gesagt, sie zählten dazu.",
    source = "$MARAQI, S. 147",
)
private val AUWABIN_SECHS = Quote(
    arabic = "ندب \"ست\" ركعات \"بعد المغرب\" لقوله ﷺ: \"من صلى بعد المغرب ست ركعات كتب من الأوابين\"",
    german = "Empfohlen sind sechs Rakʿa nach dem Maġrib, wegen seines Wortes, Gott segne ihn und schenke ihm Heil: „Wer nach dem Maġrib sechs Rakʿa betet, wird zu den Auwābīn gezählt.“",
    source = "$MARAQI, S. 146",
)
private val ASR_GLEICHEN_TAGES = Quote(
    arabic = "\"كما صح عصر اليوم\" بأدائه \"عند الغروب\" لبقاء سببه وهو الجزء المتصل به الأداء من الوقت \"مع الكراهة\" للتأخير المنهي عنه لا لذات الوقت",
    german = "So wie das ʿAṣr desselben Tages gültig ist, wenn man es beim Sonnenuntergang verrichtet, weil sein Anlass noch besteht, nämlich der Teil der Zeit, an den die Verrichtung anschließt; aber verpönt, wegen des verbotenen Aufschiebens, nicht wegen der Zeit selbst.",
    source = "$MARAQI, S. 76",
)
private val SHAFAQ_FATWA = Quote(
    arabic = "وهو رواية عن الإمام وعليها الفتوى وبها قالا لقول ابن عمر: الشفق الحمرة وهو مروي عن أكثر الصحابة وعليه إطباق أهل اللسان ونقل رجوع الإمام إليه",
    german = "[Das Rot] ist eine Überlieferung vom Imam [Abū Ḥanīfa]; nach ihr wird die Fatwā gegeben, und die beiden [Abū Yūsuf und Muḥammad] vertreten sie, wegen des Wortes Ibn ʿUmars: Der Šafaq ist das Rot. Das ist von den meisten Gefährten überliefert, darin stimmen die Sprachkundigen überein, und es wird berichtet, dass der Imam sich dieser Ansicht zugewandt hat.",
    source = "$MARAQI, S. 72",
)
private val SHAFAQ_IMAM = Quote(
    arabic = "\"ثم الشفق هو البياض الذي في الأفق بعد الحمرة عند أبي حنيفة رحمه الله تعالى وعندهما هو الحمرة\" وهو رواية عن أبي حنيفة",
    german = "Der Šafaq ist nach Abū Ḥanīfa, Gott erbarme sich seiner, die Helligkeit am Horizont nach dem Rot. Nach den beiden [Abū Yūsuf und Muḥammad] ist es das Rot, und das ist auch eine Überlieferung von Abū Ḥanīfa.",
    source = "$HIDAYA, Bd. 1, S. 40",
)
private val SHAFAQ_AHWAT = Quote(
    arabic = "وَفِي السِّرَاجِ: قَوْلُهُمَا أَوْسَعُ وَقَوْلُهُ أَحْوَطُ",
    german = "Im Sirāǧ heißt es: Ihre Ansicht ist weiter, seine ist vorsichtiger.",
    source = "$IBN_ABIDIN, Bd. 1, S. 361",
)

private val KAPITEL_FAJR = Kapitel(
    reiter = "Faǧr",
    band = Band.FAJR,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.FAJR,
            kopf = "Morgengebet",
            name = "Faǧr",
            merksatz = "Faǧr beginnt mit dem Licht, das sich am Osthorizont in die Breite legt, und endet kurz vor Sonnenaufgang.",
            wann = "Ab dem echten [[Faǧr]] bis kurz vor [[Šurūq]].{fajr-zeit} Ein heller Streifen, der zuerst " +
                "senkrecht steht und wieder dunkel wird, zählt noch nicht; er heißt der falsche Faǧr.{fajr-kadhib}",
            wieviele = "Zwei [[Sunna muʾakkada]], dann zwei [[Farḍ]].{fajr-rakaat}",
            rakaat = listOf(
                RakaBlock(RakaArt.MUAKKADA, 2),
                RakaBlock(RakaArt.FARD, 2),
            ),
            warum = "Der Prophet ﷺ sagte: „Die zwei Rakʿa des Faǧr sind besser als die Welt und alles, was in " +
                "ihr ist.“ (Muslim 725) Wer das Morgengebet betet, steht unter dem Schutz Gottes (Muslim 657). " +
                "Und wer es in Gemeinschaft betet, ist, als hätte er die ganze Nacht gebetet (Muslim 656).",
            achtung = "Ist der Faǧr angebrochen, betet man über seine zwei Sunna hinaus nichts Freiwilliges, und " +
                "nach dem Farḍ ebenso nicht.{fajr-nafl} Die Zeit endet schon kurz vor Šurūq.{fajr-zeit}",
            mehr = listOf(
                Frage(
                    "Echter und falscher Faǧr",
                    "Der echte Faǧr geht in der Breite auf und breitet sich aus; der falsche zeigt sich längs und " +
                        "verschwindet wieder. Dass die Zeit mit dem echten beginnt, darüber ist man sich einig.{fajr-sadiq} " +
                        "Der falsche zählt nicht.{fajr-kadhib} Eine abweichende Ansicht innerhalb der Hanafiten gibt es " +
                        "hier nicht. Die App zeigt die amtliche Zeit von Diyanet.",
                ),
                Frage(
                    "Die stärkste Sunna",
                    "Nach einer Überlieferung von Abū Ḥanīfa steigt sogar, wer reitet, für die Sunna des Faǧr ab, " +
                        "weil sie stärker bekräftigt ist als die übrigen.{fajr-aakad}",
                ),
                Frage(
                    "Wenn die Iqāma schon gerufen ist",
                    "Ist die [[Iqāma]] gerufen, betet man nichts Freiwilliges mehr. Die Sunna des Faǧr ist " +
                        "ausgenommen, wenn man die [[Ǧamāʿa]] dadurch nicht verpasst.{fajr-iqama} Droht man sie zu " +
                        "verpassen, lässt man die Sunna und betet mit, denn der Lohn der Ǧamāʿa ist größer.{fajr-gamaa-vorrang}",
                ),
                Frage(
                    "Wenn man die Sunna verpasst hat",
                    "Die Sunna des Faǧr holt man nur nach, wenn man auch den Farḍ verpasst hat, und dann bis zum " +
                        "[[Zawāl]].{fajr-qada}",
                ),
                Frage(
                    "Muʾakkada und Mandūb",
                    "[[Sunna muʾakkada]] ist, was der Prophet ﷺ beständig tat und nur manchmal ausließ; was er nicht " +
                        "beständig übte, ist [[Mandūb]].{fajr-sunna-arten} Wer die Sunna-Gebete auslässt, sündigt nach " +
                        "der richtigen Ansicht,{fajr-sunna-auslassen} gemeint ist das Auslassen ohne Entschuldigung, als " +
                        "feste Gewohnheit.{fajr-sunna-beharrlich} Für die empfohlene Sunna gibt es Lohn, und wer sie " +
                        "auslässt, wird nicht getadelt.{fajr-mandub}",
                ),
                Frage(
                    "Wann bete ich den Farḍ am besten?",
                    "Männern ist empfohlen, den Faǧr zu beten, wenn es hell wird ([[Isfār und Taġlīs|Isfār]]).{fajr-isfar} " +
                        "Dann kommen mehr Menschen zur Ǧamāʿa.{fajr-isfar-grund}",
                ),
                Frage(
                    "Wie rechnet die App die Ǧamāʿa-Zeit?",
                    "Sie ist keine amtliche Zeit. Die App rechnet sie aus dem Sonnenaufgang minus dem Vorlauf aus den " +
                        "Einstellungen. Die eigene Moschee kann abweichen.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg(
            "fajr-zeit",
            Quote(
                arabic = "والأوقات خمسة وقت الصبح من طلوع الفجر الصادق الى قبيل طلوع الشمس",
                german = "Die Zeiten sind fünf. Die Zeit des Morgengebets reicht vom Aufgang des echten Faǧr bis kurz vor Sonnenaufgang.",
                source = "$MARAQI, S. 72",
            ),
        ),
        Beleg(
            "fajr-kadhib",
            Quote(
                arabic = "ولا معتبر بالفجر الكاذب وهو البياض الذي يبدو طولا ثم يعقبه الظلام",
                german = "Der falsche Faǧr zählt nicht. Das ist die Helligkeit, die längs erscheint und auf die wieder Dunkelheit folgt.",
                source = "$HIDAYA, Bd. 1, S. 40",
            ),
        ),
        Beleg(
            "fajr-sadiq",
            Quote(
                arabic = "\"الصادق\" وهو الذي يطلع عرضا منتشرا والكاذب يظهر طولا ثم يغيب وقد أجمعت الأمة على أن أوله الصبح الصادق",
                german = "Der echte [Faǧr] ist der, der in der Breite aufgeht und sich ausbreitet; der falsche zeigt sich längs und verschwindet dann. Die Gemeinschaft der Muslime ist sich einig, dass ihr [der Zeit des Morgengebets] Anfang der echte Morgen ist.",
                source = "$MARAQI, S. 72",
            ),
        ),
        Beleg("fajr-rakaat", SUNNA_MUAKKADA),
        Beleg("fajr-nafl", NAFL_NACH_FAJR_ASR),
        Beleg(
            "fajr-aakad",
            Quote(
                arabic = "وعن أبي حنيفة رحمه الله تعالى أنه ينزل لسنة الفجر لأنها آكد من غيرها",
                german = "Von Abū Ḥanīfa, Gott erbarme sich seiner, wird überliefert, dass er [der Reitende] für die Sunna des Faǧr absteigt, weil sie stärker bekräftigt ist als die anderen.",
                source = "$MARAQI, S. 153",
            ),
        ),
        Beleg(
            "fajr-iqama",
            Quote(
                arabic = "\"و\" يكره \"عند الإقامة\" لكل فريضة \"إلا سنة الفجر\" إذا أمن فوت الجماعة",
                german = "Und verpönt ist es [das freiwillige Gebet] bei der Iqāma jedes Pflichtgebets, außer der Sunna des Faǧr, wenn man sicher ist, die Gemeinschaft nicht zu verpassen.",
                source = "$MARAQI, S. 77",
            ),
        ),
        Beleg(
            "fajr-gamaa-vorrang",
            Quote(
                arabic = "\"وإن لم يأمن\" فوت الإمام باشتغاله بسنة الفجر \"تركها\" واقتدى لأن ثواب الجماعة أعظم من فضيلة ركعتي الفجر",
                german = "Ist er nicht sicher, den Imām nicht zu verpassen, wenn er sich mit der Sunna des Faǧr beschäftigt, lässt er sie und folgt dem Imām, denn der Lohn der Gemeinschaft ist größer als der Vorzug der zwei Rakʿa des Faǧr.",
                source = "$MARAQI, S. 175",
            ),
        ),
        Beleg(
            "fajr-qada",
            Quote(
                arabic = "\"ولم تقض سنة الفجر إلا بفوتها مع الفرض\" إلى الزوال",
                german = "Die Sunna des Faǧr wird nur nachgeholt, wenn sie zusammen mit dem Farḍ verpasst wurde, [und zwar] bis zum Zawāl.",
                source = "$MARAQI, S. 175",
            ),
        ),
        Beleg(
            "fajr-sunna-arten",
            Quote(
                arabic = "والسنة لغة الطريقة ولو سيئة واصطلاحا الطريقة المسلوكة في الدين من غير لزوم على المواظبة وهي المؤكدة إن كان النبي ﷺ تركها أحيانا وأما التي لم يواظب عليها فهي المندوبة",
                german = "Sunna heißt sprachlich „Weg“, auch ein schlechter. Im Fachgebrauch ist es der Weg, der in der Religion ohne Verpflichtung beständig begangen wird. Sie ist muʾakkada, wenn der Prophet ﷺ sie manchmal ausließ; was er nicht beständig übte, ist mandūb.",
                source = "$MARAQI, S. 31",
            ),
        ),
        Beleg(
            "fajr-sunna-auslassen",
            Quote(
                arabic = "مَنْ تَرَكَ سُنَنَ الصَّلَوَاتِ الْخَمْسِ قِيلَ: لَا يَأْثَمُ وَالصَّحِيحُ أَنَّهُ يَأْثَمُ",
                german = "Wer die Sunna-Gebete der fünf Gebete auslässt: Es wurde gesagt, er sündige nicht; richtig ist, dass er sündigt.",
                source = "$IBN_ABIDIN, Bd. 1, S. 104",
            ),
        ),
        Beleg(
            "fajr-sunna-beharrlich",
            Quote(
                arabic = "وَالْمُرَادُ التَّرْكُ بِلَا عُذْرٍ عَلَى سَبِيلِ الْإِصْرَارِ",
                german = "Gemeint ist das Auslassen ohne Entschuldigung, als beharrliche Gewohnheit.",
                source = "$IBN_ABIDIN, Bd. 1, S. 104",
            ),
        ),
        Beleg(
            "fajr-mandub",
            Quote(
                arabic = "وَحُكْمُهُ الثَّوَابُ عَلَى الْفِعْلِ وَعَدَمُ اللَّوْمِ عَلَى التَّرْكِ",
                german = "[Über die empfohlene Sunna:] Seine Regel ist Lohn für das Tun und kein Tadel für das Auslassen.",
                source = "$IBN_ABIDIN, Bd. 1, S. 123 (nach al-Imdād)",
            ),
        ),
        Beleg(
            "fajr-isfar",
            Quote(
                arabic = "ويستحب الإسفار بالفجر للرجال",
                german = "Für Männer ist es empfohlen, den Faǧr zu beten, wenn es hell wird (Isfār).",
                source = "$MARAQI, S. 74",
            ),
        ),
        Beleg(
            "fajr-isfar-grund",
            Quote(
                arabic = "ولأن في الإسفار تكثير الجماعة وفي التغليس تقليلها وما يؤدي إلى التكثير أفضل",
                german = "Denn beim Isfār kommen mehr Menschen zur Gemeinschaft, beim Taġlīs [dem Beten in der Dunkelheit] weniger, und was zu mehr führt, ist vorzüglicher.",
                source = "$MARAQI, S. 74",
            ),
        ),
    ),
)

private val KAPITEL_ZUHR = Kapitel(
    reiter = "Ẓuhr",
    band = Band.ZUHR,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.ZUHR,
            kopf = "Mittagsgebet",
            name = "Ẓuhr",
            merksatz = "Ẓuhr beginnt, wenn der Schatten wieder zu wachsen anfängt.",
            wann = "Ab dem [[Zawāl]], wenn die Sonne sich nach Westen neigt,{zuhr-beginn} bis ʿAṣr beginnt.{zuhr-ende}",
            wieviele = "Vier [[Sunna muʾakkada]], vier [[Farḍ]], zwei Sunna muʾakkada. " +
                "Zwei weitere sind empfohlen.{zuhr-rakaat}{zuhr-rakaat-nach}",
            rakaat = listOf(
                RakaBlock(RakaArt.MUAKKADA, 4),
                RakaBlock(RakaArt.FARD, 4),
                RakaBlock(RakaArt.MUAKKADA, 2),
                RakaBlock(RakaArt.MANDUB, 2),
            ),
            rakaatGumua = listOf(
                RakaBlock(RakaArt.MUAKKADA, 4),
                RakaBlock(RakaArt.FARD, 2),
                RakaBlock(RakaArt.MUAKKADA, 4),
            ),
            warum = "Der Prophet ﷺ sagte über die Stunde nach dem Zawāl: „Es ist eine Stunde, in der die Tore " +
                "des Himmels geöffnet werden.“ (Tirmiḏī 478) Und wer die vier davor und vier danach " +
                "beständig betet, den verwehrt Gott dem Feuer (Tirmiḏī 428).",
            achtung = "Steht die Sonne mitten am Himmel, kurz vor Ẓuhr, betet man nicht.{zuhr-zawal}",
            mehr = listOf(
                Frage(
                    "Wann endet Ẓuhr genau?",
                    "Nach Abū Yūsuf und Muḥammad, wenn der Schatten so lang ist wie das Ding selbst; " +
                        "nach Abū Ḥanīfa erst beim doppelten Schatten, jeweils ohne den Mittagsschatten.{zuhr-ende} " +
                        "Diyanet setzt ʿAṣr nach der ersten Ansicht an.",
                ),
                Frage(
                    "Die vier davor und vier danach",
                    "„Wer vier Rakʿa vor dem Ẓuhr und vier danach beständig betet, den verwehrt Gott dem Feuer.“ (Tirmiḏī 428)",
                ),
                Frage(
                    "Zählen die zwei empfohlenen extra?",
                    "Dem Anschein nach kommen die zwei empfohlenen zu den zwei bekräftigten nach dem Farḍ hinzu. " +
                        "Es wird aber auch gesagt, sie seien darin enthalten.{zuhr-zaehlung}",
                ),
                Frage(
                    "Wenn man die vier davor verpasst hat",
                    "Wer die vier vor dem Ẓuhr verpasst, holt sie in der Zeit des Ẓuhr nach, vor den zwei danach.{zuhr-qada}",
                ),
                Frage(
                    "Im Sommer später",
                    "Im Sommer ist es empfohlen, den Ẓuhr aufzuschieben, bis die Hitze nachlässt; im Winter betet man " +
                        "ihn früh.{zuhr-ibrad} Der Prophet ﷺ sagte: „Betet den Ẓuhr, wenn es kühler wird, denn die " +
                        "große Hitze kommt vom Glutatem der Hölle.“ (Buḫārī 538)",
                ),
                Frage(
                    "Am Freitag die Ǧumʿa",
                    "Am Freitag betet man zur Zeit des Ẓuhr die [[Ǧumʿa]]: vier Sunna muʾakkada, zwei Farḍ, vier " +
                        "Sunna muʾakkada.{zuhr-rakaat} Sie ist nur in der Zeit des Ẓuhr gültig, nicht davor und nicht " +
                        "danach.{zuhr-gumua-zeit}",
                ),
                Frage(
                    "Wie rechnet die App die verpönte Zeit?",
                    "Als die ${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor Ẓuhr.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg(
            "zuhr-beginn",
            Quote(
                arabic = "وأول وقت الظهر إذا زالت الشمس",
                german = "Die Zeit des Ẓuhr beginnt, wenn die Sonne sich [vom Höchststand] neigt.",
                source = "$QUDURI, S. 23",
            ),
        ),
        Beleg("zuhr-ende", ZEIT_ZUHR_ENDE),
        Beleg("zuhr-rakaat", SUNNA_MUAKKADA),
        Beleg(
            "zuhr-rakaat-nach",
            Quote(
                arabic = "\"ركعتان بعد الظهر\" ويندب أن يضم إليها ركعتان فتصير أربعا",
                german = "Zwei Rakʿa nach dem Ẓuhr; empfohlen ist, zwei hinzuzufügen, so dass es vier werden.",
                source = "$MARAQI, S. 145",
            ),
        ),
        Beleg("zuhr-zawal", ZAWAL_ZEIT),
        Beleg("zuhr-zaehlung", SUNNA_ZAEHLUNG),
        Beleg(
            "zuhr-qada",
            Quote(
                arabic = "وقضى السنة التي قبل الظهر في وقته قبل شفعه",
                german = "Die Sunna vor dem Ẓuhr holt er in dessen Zeit nach, vor ihren zwei [Rakʿa nach dem Farḍ].",
                source = "$MARAQI, S. 175",
            ),
        ),
        Beleg(
            "zuhr-ibrad",
            Quote(
                arabic = "ويستحب الإسفار بالفجر للرجال والإبراد بالظهر في الصيف وتعجيله في الشتاء",
                german = "Empfohlen ist für Männer, den Faǧr bei Helligkeit zu beten, den Ẓuhr im Sommer in die Kühle zu schieben und ihn im Winter früh zu beten.",
                source = "$MARAQI, S. 74",
            ),
        ),
        Beleg(
            "zuhr-gumua-zeit",
            Quote(
                arabic = "\"و\" الثالث \"وقت الظهر\" لقوله ﷺ \"إذا مالت الشمس فصل بالناس الجمعة\" \"فلا تصح\" الجمعة \"قبله وتبطل بخروجه\" لفوات الشرط",
                german = "Die dritte [Bedingung der Ǧumʿa] ist die Zeit des Ẓuhr, wegen seines Wortes ﷺ: „Wenn die Sonne sich neigt, bete mit den Leuten die Ǧumʿa.“ Vor ihr ist die Ǧumʿa nicht gültig, und mit ihrem Ende wird sie ungültig, weil die Bedingung fehlt.",
                source = "$MARAQI, S. 194",
            ),
        ),
    ),
)

private val KAPITEL_ASR = Kapitel(
    reiter = "ʿAṣr",
    band = Band.ASR,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.ASR,
            kopf = "Nachmittagsgebet",
            name = "ʿAṣr",
            merksatz = "Diyanet setzt ʿAṣr an, wenn der Schatten eines Dinges so lang ist wie das Ding und dazu der " +
                "Mittagsschatten; das ist die Ansicht von Abū Yūsuf und Muḥammad. Abū Ḥanīfa setzt ihn später an, " +
                "beim doppelten Schatten.{asr-beginn}",
            wann = "Wenn die Zeit des Ẓuhr endet,{asr-beginn} bis zum Sonnenuntergang.{asr-zeit} Die App zeigt die " +
                "Zeit von Diyanet, beim einfachen Schatten.",
            wieviele = "Vier [[Mandūb|empfohlene]] Rakʿa, wahlweise zwei,{asr-rakaat}{asr-wahl} dann vier [[Farḍ]].",
            rakaat = listOf(
                RakaBlock(RakaArt.MANDUB, 4, "4 oder 2"),
                RakaBlock(RakaArt.FARD, 4),
            ),
            warum = "Der Prophet ﷺ nannte das ʿAṣr „das mittlere Gebet“ (Muslim 628). Er sagte: „Wer die beiden " +
                "kühlen Gebete betet, kommt ins Paradies.“ (Buḫārī 574) Gemeint sind Faǧr und ʿAṣr. Und: „Gott " +
                "erbarme sich eines Menschen, der vor dem ʿAṣr vier betet.“ (Tirmiḏī 430)",
            achtung = "Man schiebt das ʿAṣr nicht auf, bis die Sonne ihren Glanz verliert; das ist stark " +
                "verpönt.{asr-aufschub} Nach dem ʿAṣr-Gebet betet man nichts Freiwilliges mehr.{asr-nafl}",
            mehr = listOf(
                Frage(
                    "Einfacher oder doppelter Schatten?",
                    "Nach Abū Yūsuf und Muḥammad beginnt ʿAṣr, wenn der Schatten eines Dinges so lang ist wie das " +
                        "Ding; nach Abū Ḥanīfa erst, wenn er doppelt so lang ist. Der Schatten, den das Ding schon " +
                        "mittags wirft, zählt jeweils nicht mit.{asr-beginn} aṭ-Ṭaḥāwī wählte den einfachen Schatten.{asr-zeit}",
                ),
                Frage(
                    "Welcher Ansicht folgt Diyanet?",
                    "Diyanet setzt ʿAṣr beim einfachen Schatten an, nach Abū Yūsuf und Muḥammad. Die App zeigt diese " +
                        "amtliche Zeit. Das Himmelsband oben zeigt beide Schatten, den einfachen und den doppelten.",
                ),
                Frage(
                    "Vier oder zwei?",
                    "Überliefert ist, dass der Prophet ﷺ vor dem ʿAṣr zwei betete, und auch vier; deshalb lässt " +
                        "al-Qudūrī die Wahl.{asr-wahl}",
                ),
                Frage(
                    "Früh oder spät?",
                    "Empfohlen ist, das ʿAṣr etwas aufzuschieben, im Sommer wie im Winter.{asr-taakhir} Aber nur, " +
                        "solange die Sonne ihren Glanz behält und noch blendet; wer es bis dahin aufschiebt, tut etwas " +
                        "stark Verpöntes.{asr-aufschub}",
                ),
                Frage(
                    "Gilt das ʿAṣr noch beim Sonnenuntergang?",
                    "Wer das ʿAṣr desselben Tages erst beim Sonnenuntergang betet, dessen Gebet gilt. Verpönt ist es " +
                        "trotzdem, weil er es aufgeschoben hat.{asr-gleichen-tages}",
                ),
                Frage(
                    "Wie rechnet die App die verpönte Zeit?",
                    "Als die ${Karaha.ISFIRAR_BEFORE_MAGHRIB_MIN} Minuten vor Maġrib. Mehr dazu im Kapitel " +
                        "„Freiwillig und verpönt“.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg("asr-beginn", ZEIT_ZUHR_ENDE),
        Beleg(
            "asr-zeit",
            Quote(
                arabic = "ووقت الظهر من زوال الشمس الى أن يصير ظل كل شيء مثليه أو مثله سوى ظل الاستواء واختار الثاني الطحاوي وهو قول الصاحبين ووقت العصر من ابتداء الزيادة على المثل أو المثلين الى غروب الشمس",
                german = "Die Zeit des Ẓuhr reicht vom Zawāl der Sonne, bis der Schatten jedes Dinges doppelt oder einfach so lang ist wie das Ding, ohne den Schatten beim Höchststand. Das Zweite hat aṭ-Ṭaḥāwī gewählt, und es ist die Ansicht der beiden Schüler [Abū Ḥanīfas: Abū Yūsuf und Muḥammad]. Die Zeit des ʿAṣr reicht vom Beginn des Überschreitens des einfachen oder doppelten Schattens bis zum Sonnenuntergang.",
                source = "$MARAQI, S. 72",
            ),
        ),
        Beleg("asr-rakaat", SUNNA_MANDUB),
        Beleg(
            "asr-wahl",
            Quote(
                arabic = "وورد أنه ﷺ صلى ركعتين وورد أربعا فلذا خيره القدوري بينهما",
                german = "[Vor dem ʿAṣr:] Überliefert ist, dass er ﷺ zwei betete, und überliefert sind auch vier; deshalb ließ al-Qudūrī die Wahl zwischen beiden.",
                source = "$MARAQI, S. 146",
            ),
        ),
        Beleg(
            "asr-taakhir",
            Quote(
                arabic = "\"و\" يستحب \"تأخير\" صلاة \"العصر\" صيفا وشتاء",
                german = "Und empfohlen ist, das ʿAṣr-Gebet aufzuschieben, im Sommer wie im Winter.",
                source = "$MARAQI, S. 74",
            ),
        ),
        Beleg(
            "asr-aufschub",
            Quote(
                arabic = "\"ما لم تتغير الشمس\" بذهاب ضوئها فلا يتحير فيه البصر وهو الصحيح والتأخير إلى التغير مكروه تحريما",
                german = "Solange die Sonne sich nicht verändert, das heißt ihr Glanz nicht vergangen ist, so dass der Blick von ihr nicht mehr geblendet wird; das ist die richtige Ansicht. Das Aufschieben bis zu dieser Veränderung ist stark verpönt (makrūh taḥrīman).",
                source = "$MARAQI, S. 74",
            ),
        ),
        Beleg("asr-nafl", NAFL_NACH_FAJR_ASR),
        Beleg("asr-gleichen-tages", ASR_GLEICHEN_TAGES),
    ),
)

private val KAPITEL_MAGHRIB = Kapitel(
    reiter = "Maġrib",
    band = Band.MAGHRIB,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.MAGHRIB,
            kopf = "Abendgebet",
            name = "Maġrib",
            merksatz = "Maġrib beginnt, sobald die Sonne ganz untergegangen ist.",
            wann = "Ab Sonnenuntergang, bis das Rot am Abendhimmel verschwunden ist.{maghrib-zeit} Dann beginnt ʿIšāʾ.",
            wieviele = "Drei [[Farḍ]], danach zwei [[Sunna muʾakkada]].{sunna-muakkada} Sechs Rakʿa nach dem " +
                "Maġrib sind empfohlen.{sunna-mandub}",
            rakaat = listOf(
                RakaBlock(RakaArt.FARD, 3),
                RakaBlock(RakaArt.MUAKKADA, 2),
                RakaBlock(RakaArt.MANDUB, 6),
            ),
            warum = "Der Prophet ﷺ sagte: „Meine Gemeinde bleibt im Guten, solange sie das Maġrib nicht aufschiebt, " +
                "bis die Sterne dicht beieinander stehen.“ (Abū Dāwūd 418) Und: „Das Maġrib ist der Witr des " +
                "Tages.“ (Aḥmad 4847)",
            achtung = "Man betet Maġrib bald nach dem [[Aḏān]]; bis zur [[Iqāma]] wartet man nur kurz.{maghrib-frueh} " +
                "Aufschieben ist verpönt, außer an einem bewölkten Tag oder aus einem Grund wie Reise oder " +
                "Krankheit. Ein wenig später ist nicht verpönt.{maghrib-aufschub}",
            mehr = listOf(
                Frage(
                    "Wann endet Maġrib genau?",
                    "Nach Abū Yūsuf und Muḥammad, wenn das Rot am Abendhimmel verschwunden ist. Danach wird die " +
                        "[[Fatwā]] gegeben, und auch von Abū Ḥanīfa ist das überliefert.{shafaq-fatwa} Nach Abū Ḥanīfa " +
                        "endet Maġrib erst, wenn auch die weiße Helligkeit nach dem Rot verschwunden ist.{shafaq-imam} " +
                        "Ibn ʿĀbidīn hält fest, dass man heute in den meisten Ländern nach der Ansicht der beiden " +
                        "handelt,{shafaq-amal} und zitiert: Ihre Ansicht ist weiter, die seine vorsichtiger.{shafaq-ahwat} " +
                        "Beide Arten des Scheins heißen [[Šafaq]]. Als Ende zeigt die App die amtliche ʿIšāʾ-Zeit von Diyanet.",
                ),
                Frage(
                    "Die zwei und die sechs",
                    "Nach dem Farḍ stehen zwei Sunna muʾakkada.{sunna-muakkada} Empfohlen sind sechs Rakʿa, denn der " +
                        "Prophet ﷺ sagte: Wer nach dem Maġrib sechs Rakʿa betet, wird zu den [[Auwābīn]] " +
                        "gezählt.{auwabin-sechs} Dem Anschein nach kommen die sechs zu den zwei bekräftigten hinzu; es " +
                        "wird aber auch gesagt, die zwei zählten zu den sechs.{auwabin-zaehlung} Die Folge oben zeigt " +
                        "sie deshalb nacheinander, ohne Summe.",
                ),
                Frage(
                    "Der Lohn der sechs",
                    "„Wer nach dem Maġrib sechs Rakʿa betet und zwischen ihnen nichts Schlechtes redet, dem werden " +
                        "sie wie zwölf Jahre Gottesdienst angerechnet.“ (Tirmiḏī 435) Mehr zu ihrer Zeit steht im " +
                        "Kapitel „Freiwillig und verpönt“.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg(
            "maghrib-zeit",
            Quote(
                arabic = "والمغرب منه الى غروب الشفق الاحمر على المفتى به",
                german = "Die Zeit des Maġrib reicht von ihm [dem Sonnenuntergang] bis zum Verschwinden der roten Abenddämmerung, nach der Ansicht, nach der die Fatwā gegeben wird.",
                source = "$MARAQI, S. 72",
            ),
        ),
        Beleg("sunna-muakkada", SUNNA_MUAKKADA),
        Beleg("sunna-mandub", SUNNA_MANDUB),
        Beleg("auwabin-sechs", AUWABIN_SECHS),
        Beleg("auwabin-zaehlung", SUNNA_ZAEHLUNG),
        Beleg("shafaq-fatwa", SHAFAQ_FATWA),
        Beleg("shafaq-imam", SHAFAQ_IMAM),
        Beleg(
            "shafaq-amal",
            Quote(
                arabic = "لَكِنَّ تَعَامُلَ النَّاسِ الْيَوْمَ فِي عَامَّةِ الْبِلَادِ عَلَى قَوْلِهِمَا",
                german = "Aber die Menschen handeln heute in den meisten Ländern nach der Ansicht der beiden.",
                source = "$IBN_ABIDIN, Bd. 1, S. 361",
            ),
        ),
        Beleg("shafaq-ahwat", SHAFAQ_AHWAT),
        Beleg(
            "maghrib-frueh",
            Quote(
                arabic = "\"و\" يستحب \"تعجيل\" صلاة \"المغرب\" صيفا وشتاء ولا يفصل بين الأذان والإقامة فيه إلا بقدر ثلاث آيات أو جلسة خفيفة",
                german = "Empfohlen ist, das Maġrib früh zu beten, im Sommer wie im Winter. Zwischen Aḏān und Iqāma lässt man dabei nur so viel Zeit wie für drei Verse oder ein kurzes Sitzen.",
                source = "$MARAQI, S. 75",
            ),
        ),
        Beleg(
            "maghrib-aufschub",
            Quote(
                arabic = "فكان تأخيرها مكروها \"إلا في يوم غيم\" وإلا من عذر سفر أو مرض وحضور مائدة والتأخير قليلا لا يكره",
                german = "Ihr Aufschieben ist also verpönt, außer an einem bewölkten Tag oder aus einem Grund wie Reise, Krankheit oder einem bereitstehenden Essen. Ein wenig aufzuschieben ist nicht verpönt.",
                source = "$MARAQI, S. 75",
            ),
        ),
    ),
)

private val KAPITEL_ISHA = Kapitel(
    reiter = "ʿIšāʾ",
    band = Band.ISHA,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.ISHA,
            kopf = "Nachtgebet",
            name = "ʿIšāʾ",
            merksatz = "ʿIšāʾ beginnt, wenn die Abenddämmerung erloschen ist, und reicht bis zum Faǧr.",
            wann = "Wenn der [[Šafaq]] am Abendhimmel verschwunden ist, bis der Faǧr aufgeht.{isha-zeit}",
            wieviele = "Vier [[Mandūb|empfohlene]], vier [[Farḍ]], zwei [[Sunna muʾakkada]] und noch einmal vier " +
                "empfohlene.{sunna-muakkada}{sunna-mandub} Danach der [[Witr]] mit drei Rakʿa; er ist [[Wāǧib]].{witr}",
            rakaat = listOf(
                RakaBlock(RakaArt.MANDUB, 4),
                RakaBlock(RakaArt.FARD, 4),
                RakaBlock(RakaArt.MUAKKADA, 2),
                RakaBlock(RakaArt.MANDUB, 4),
                RakaBlock(RakaArt.WITR, 3),
            ),
            warum = "Der Prophet ﷺ sagte: „Wer das ʿIšāʾ in Gemeinschaft betet, ist wie einer, der die halbe Nacht " +
                "im Gebet gestanden hat.“ (Muslim 656) Und über den Witr: „Gott hat euch ein Gebet hinzugegeben, " +
                "das besser für euch ist als rote Kamele: den Witr. Er hat ihn euch zwischen das ʿIšāʾ und den " +
                "Aufgang des Faǧr gelegt.“ (Abū Dāwūd 1418)",
            achtung = "Man schiebt ʿIšāʾ nicht über die Mitte der Nacht hinaus; das ist stark verpönt.{isha-nach-haelfte} " +
                "Den Witr betet man erst nach dem ʿIšāʾ, nie davor.{witr-tartib}",
            mehr = listOf(
                Frage(
                    "Wann beginnt ʿIšāʾ genau?",
                    "Nach Abū Yūsuf und Muḥammad, wenn das Rot am Abendhimmel verschwunden ist. Danach wird die " +
                        "[[Fatwā]] gegeben, und auch von Abū Ḥanīfa ist das überliefert.{shafaq-fatwa} Nach Abū Ḥanīfa " +
                        "beginnt ʿIšāʾ erst, wenn auch die weiße Helligkeit nach dem Rot verschwunden ist.{shafaq-imam} " +
                        "Ibn ʿĀbidīn zitiert dazu: Ihre Ansicht ist weiter, die seine vorsichtiger.{shafaq-ahwat} Als " +
                        "Beginn zeigt die App die amtliche ʿIšāʾ-Zeit von Diyanet, als Ende den amtlichen Faǧr des " +
                        "nächsten Morgens.",
                ),
                Frage(
                    "Wie spät darf man ʿIšāʾ beten?",
                    "Empfohlen ist, es bis zum ersten Drittel der Nacht aufzuschieben; nach al-Qudūrī bis kurz vor " +
                        "das Drittel.{isha-drittel} Bis zur Mitte der Nacht ist das Aufschieben im Winter " +
                        "erlaubt.{isha-haelfte} Darüber hinaus ist es stark verpönt, [[Makrūh]] nahe am " +
                        "Verbot.{isha-nach-haelfte} Gültig bleibt das Gebet bis zum Faǧr.{isha-zeit}",
                ),
                Frage(
                    "Der Witr",
                    "Der Witr ist wāǧib, nach der richtigeren Ansicht und dem letzten Wort Abū Ḥanīfas. Von ihm ist " +
                        "auch überliefert, er sei Sunna; das ist die Ansicht von Abū Yūsuf und Muḥammad.{witr-ansichten} " +
                        "Seine Zeit beginnt nach dem ʿIšāʾ und endet mit dem Faǧr.{witr-zeit} Wer sicher ist, dass er " +
                        "aufwacht, betet ihn besser am Ende der Nacht.{witr-spaet} Der Prophet ﷺ sagte: „Der Witr ist " +
                        "eine Verpflichtung; wer den Witr nicht betet, gehört nicht zu uns.“ (Abū Dāwūd 1419)",
                ),
                Frage(
                    "Die Sunna um das ʿIšāʾ",
                    "Die zwei Rakʿa nach dem Farḍ sind Sunna muʾakkada.{sunna-muakkada} Empfohlen sind vier davor " +
                        "und vier danach.{sunna-mandub} Ob die zwei zu den vier danach zählen, sagt Marāqī l-falāḥ " +
                        "nicht; die Folge oben zeigt sie deshalb nacheinander, ohne Summe.",
                ),
                Frage(
                    "Und das Gebet in der Nacht?",
                    "Das freiwillige Gebet nach dem Schlaf, das [[Tahaǧǧud]], hat einen eigenen Abschnitt im Kapitel " +
                        "„Freiwillig und verpönt“.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg(
            "isha-zeit",
            Quote(
                arabic = "وأول وقت العشاء إذا غاب الشفق وآخر وقتها ما لم يطلع الفجر",
                german = "Die Zeit des ʿIšāʾ beginnt, wenn die Abenddämmerung (Šafaq) verschwunden ist, und endet, wenn der Faǧr aufgeht.",
                source = "$QUDURI, S. 23",
            ),
        ),
        Beleg("sunna-muakkada", SUNNA_MUAKKADA),
        Beleg("sunna-mandub", SUNNA_MANDUB),
        Beleg(
            "witr",
            Quote(
                arabic = "الوتر واجب وهو ثلاث ركعات",
                german = "Der Witr ist wāǧib und hat drei Rakʿa.",
                source = "$MARAQI, S. 139",
            ),
        ),
        Beleg("shafaq-fatwa", SHAFAQ_FATWA),
        Beleg("shafaq-imam", SHAFAQ_IMAM),
        Beleg("shafaq-ahwat", SHAFAQ_AHWAT),
        Beleg(
            "isha-drittel",
            Quote(
                arabic = "\"و\" يستحب \"تأخير\" صلاة \"العشاء إلى ثلث الليل\" الأول في رواية الكنز وفي القدوري إلى ما قبل الثلث",
                german = "Empfohlen ist, das ʿIšāʾ bis zum ersten Drittel der Nacht aufzuschieben, nach der Überlieferung im Kanz; bei al-Qudūrī bis vor das Drittel.",
                source = "$MARAQI, S. 75",
            ),
        ),
        Beleg(
            "isha-haelfte",
            Quote(
                arabic = "وفي مجمع الروايات التأخير إلى النصف مباح في الشتاء",
                german = "In Maǧmaʿ ar-riwāyāt heißt es: Das Aufschieben bis zur Mitte [der Nacht] ist im Winter erlaubt.",
                source = "$MARAQI, S. 75",
            ),
        ),
        Beleg(
            "isha-nach-haelfte",
            Quote(
                arabic = "والتأخير إلى ما بعد النصف مكروه لسلامة دليل الكراهة عن المعارض والكراهة تحريمية",
                german = "Das Aufschieben über die Mitte hinaus ist verpönt, weil der Grund für die Verpöntheit ohne Gegengrund bleibt; und zwar ist es stark verpönt (Karāhat at-taḥrīm).",
                source = "$MARAQI, S. 75",
            ),
        ),
        Beleg(
            "witr-ansichten",
            Quote(
                arabic = "وصفه بقوله \"الوتر واجب\" في الأصح وهو آخر أقوال الإمام وروى عنه أنه سنة وهو قولهما",
                german = "Er beschreibt ihn mit den Worten „Der Witr ist wāǧib“, nach der richtigeren Ansicht; das ist das letzte Wort des Imams. Von ihm ist auch überliefert, er sei Sunna, und das ist die Ansicht der beiden.",
                source = "$MARAQI, S. 139",
            ),
        ),
        Beleg(
            "witr-zeit",
            Quote(
                arabic = "وأول وقت الوتر بعد العشاء وآخر وقتها ما لم يطلع الفجر",
                german = "Die Zeit des Witr beginnt nach dem ʿIšāʾ und endet, wenn der Faǧr aufgeht.",
                source = "$QUDURI, S. 23",
            ),
        ),
        Beleg(
            "witr-tartib",
            Quote(
                arabic = "\"ولا يقدم\" صلاة \"الوتر على\" صلاة \"العشاء\" لهذا الحديث و \"للترتيب اللازم\" بين فرض العشاء وواجب الوتر عند الإمام",
                german = "Der Witr wird nicht vor das ʿIšāʾ gezogen, wegen dieses Ḥadīṯ und wegen der verbindlichen Reihenfolge zwischen dem Farḍ des ʿIšāʾ und dem Wāǧib des Witr nach dem Imam.",
                source = "$MARAQI, S. 73",
            ),
        ),
        Beleg(
            "witr-spaet",
            Quote(
                arabic = "وتأخير الوتر الى آخر الليل لمن يثق بالانتباه",
                german = "[Empfohlen ist,] den Witr bis zum Ende der Nacht aufzuschieben, für den, der sicher ist, dass er aufwacht.",
                source = "$MARAQI, S. 75",
            ),
        ),
    ),
)

private val KAPITEL_FREIWILLIG = Kapitel(
    reiter = "Freiwillig und verpönt",
    band = Band.TAG,
    abschnitte = listOf(
        Abschnitt(
            teil = Teil.SHURUQ,
            kopf = "Verpönte Zeit",
            name = "Šurūq",
            merksatz = "Geht die Sonne auf, betet man nicht, bis sie eine Speerlänge hoch steht.",
            wann = "Vom Sonnenaufgang an, bis die Sonne gestiegen und hell geworden ist, etwa eine oder zwei " +
                "Speerlängen hoch.{shuruq-zeit}",
            wieviele = null,
            rakaat = emptyList(),
            warum = "Weil die Sonne dann zwischen den beiden Hörnern eines Šayṭān aufgeht und die Ungläubigen sich " +
                "vor ihr niederwerfen. Der Prophet ﷺ sagte: „Halte dich vom Gebet zurück, bis die Sonne aufgegangen " +
                "und gestiegen ist, denn sie geht zwischen den beiden Hörnern eines Šayṭān auf, und dann werfen sich " +
                "die Ungläubigen vor ihr nieder.“ (Muslim 832) Es ist die erste der drei Stunden, in denen er das " +
                "Gebet verbot: „wenn die Sonne sichtbar aufgeht, bis sie gestiegen ist“ (Muslim 831).",
            achtung = "Wer das [[Faǧr]] noch betet, wenn die Sonne aufgeht, dessen Gebet ist ungültig.{fajr-bei-aufgang} " +
                "Ein [[Nafl]]-Gebet ist in dieser Zeit stark verpönt, auch eines mit Anlass.{makruh-nafl}",
            mehr = listOf(
                Frage(
                    "Wann ist die Sonne hoch genug?",
                    "Ibn ʿĀbidīn hält die Überlieferung von Imām Muḥammad für richtig: Solange die Sonne nicht eine " +
                        "Speerlänge gestiegen ist, gilt sie als aufgehend.{shuruq-speer} Eine andere Angabe misst es am " +
                        "Auge: Solange man noch in die Sonne blicken kann, ohne geblendet zu werden, gilt sie als " +
                        "aufgehend.{shuruq-blick}",
                ),
                Frage(
                    "Was gilt in den drei verpönten Zeiten?",
                    "Ein [[Farḍ]] oder [[Wāǧib]], das man schon vorher schuldete, ist in ihnen nicht gültig, also auch " +
                        "kein Nachholen ([[Qaḍāʾ]]).{makruh-pflicht} Was erst in der Zeit selbst fällig wird, etwa ein " +
                        "Totengebet, das gerade gebracht wird, oder eine Niederwerfung für einen Vers, der eben gelesen " +
                        "wurde, ist gültig, aber verpönt.{pflicht-mit-karaha} Freiwilliges Gebet ist stark verpönt " +
                        "([[Karāhat at-taḥrīm]]), auch wenn es einen Anlass hat.{makruh-nafl}",
                ),
                Frage(
                    "Und nach dem Faǧr und nach dem ʿAṣr?",
                    "Verpönt ist auch das freiwillige Gebet nach dem Aufgang des Faǧr über seine zwei Sunna hinaus, " +
                        "nach dem Faǧr-Gebet und nach dem ʿAṣr-Gebet.{makruh-nach-fajr-asr} In diesen beiden Zeiten darf " +
                        "man aber Versäumtes nachholen, die Niederwerfung für einen Vers machen und das Totengebet " +
                        "verrichten.{qada-nach-fajr-asr} Die App zeigt diese Zeiten nicht als verpönt an, nur die drei Spannen.",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet veröffentlicht für die verpönten Zeiten keine Uhrzeit. Die App rechnet vom amtlichen " +
                        "Sonnenaufgang bis ${Karaha.ISRAK_AFTER_SUNRISE_MIN} Minuten danach. Das entspricht der " +
                        "türkischen Gewohnheit, die Speerlänge mit etwa 45 Minuten anzusetzen. Mit dieser Spanne endet " +
                        "in der App die verpönte Zeit, und Ḍuḥā beginnt.",
                ),
            ),
        ),
        Abschnitt(
            teil = Teil.DUHA,
            kopf = "Vormittagsgebet",
            name = "Ḍuḥā",
            merksatz = "Ḍuḥā ist das freiwillige Gebet des Vormittags, wenn die Sonne gestiegen ist.",
            wann = "Vom Steigen der Sonne bis kurz vor dem [[Zawāl]].{duha-zeit} Am besten betet man es, wenn ein " +
                "Viertel des Tages vergangen ist.{duha-beste-zeit}",
            wieviele = "Vier Rakʿa oder mehr, sie sind [[Mandūb]].{duha-vier} Das Mindeste sind zwei, das Meiste " +
                "zwölf.{duha-spanne}",
            rakaat = listOf(RakaBlock(RakaArt.MANDUB, 4, "4 oder mehr")),
            warum = "Der Prophet ﷺ sagte: „Jeden Morgen ist auf jedes Gelenk eines jeden von euch eine Spende fällig " +
                "[…] Und zwei Rakʿa, die man am Vormittag betet, genügen für all das.“ (Muslim 720) Abū Huraira " +
                "sagte: „Mein Freund ﷺ hat mir drei Dinge ans Herz gelegt: drei Tage in jedem Monat zu fasten, die " +
                "zwei Rakʿa des Ḍuḥā und den Witr zu beten, bevor ich schlafe.“ (Buḫārī 1981)",
            achtung = "Ḍuḥā liegt zwischen zwei verpönten Zeiten. Man beginnt erst, wenn die Sonne gestiegen ist, und " +
                "hört vor dem Höchststand auf.{shuruq-zeit}{zawal-zeit}",
            mehr = listOf(
                Frage(
                    "Zwei oder vier?",
                    "Für zwei Rakʿa spricht, dass der Prophet ﷺ Abū Huraira zwei empfahl (Buḫārī 1981). Für vier " +
                        "spricht, was ʿĀʾiša auf die Frage antwortete, wie viele er im Ḍuḥā betete: „Vier Rakʿa, und er " +
                        "fügte hinzu, so viel er wollte.“ (Muslim 719) Ibn ʿĀbidīn verbindet beides: Zwei sind die " +
                        "unterste Stufe, vier das Mindeste der Vollkommenheit.{duha-vier-kamal} Nach al-Munya sind " +
                        "acht die Mitte und das Beste.{duha-spanne}",
                ),
                Frage(
                    "Die beste Zeit",
                    "Die Zeit reicht nach der richtigen Ansicht bis zum Zawāl, nach aš-Šurunbulālī bis kurz " +
                        "davor;{duha-zeit} gewählt ist die Zeit nach dem ersten Viertel des Tages.{duha-beste-zeit} Der " +
                        "Prophet ﷺ sagte: „Das Gebet der Auwābīn ist, wenn die jungen Kamele die Hitze spüren.“ (Muslim 748)",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet liefert keine Ḍuḥā-Zeit. Die App zeigt die Spanne von ${Karaha.ISRAK_AFTER_SUNRISE_MIN} " +
                        "Minuten nach dem amtlichen Sonnenaufgang bis ${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor Ẓuhr, " +
                        "also zwischen den beiden verpönten Zeiten, die sie selbst rechnet. Das gewählte Viertel des " +
                        "Tages zeigt sie nicht eigens an.",
                ),
            ),
        ),
        Abschnitt(
            teil = Teil.ZAWAL,
            kopf = "Verpönte Zeit",
            name = "Zenit",
            merksatz = "Steht die Sonne am höchsten, betet man nicht, bis sie sich nach Westen neigt.",
            wann = "Wenn die Sonne mitten am Himmel steht, im [[Zenit]], bis sie sich nach Westen neigt.{zawal-zeit} " +
                "Dieses Sich-Neigen nach dem Höchststand heißt arabisch [[Zawāl]]; mit ihm beginnt [[Ẓuhr]].",
            wieviele = null,
            rakaat = emptyList(),
            warum = "Weil zu dieser Zeit die Hölle angefacht wird. Der Prophet ﷺ sagte: „Bete, denn das Gebet wird " +
                "bezeugt und besucht, bis der Schatten ganz beim Speer steht. Dann halte dich vom Gebet zurück, denn " +
                "zu dieser Zeit wird die Hölle angefacht.“ (Muslim 832) Es ist eine der drei Stunden, in denen er " +
                "das Gebet verbot: „wenn die Mittagshitze am höchsten steht, bis die Sonne sich neigt“ (Muslim 831).",
            achtung = "Wer kurz vor Ẓuhr noch ein freiwilliges Gebet beginnen will, wartet, bis Ẓuhr eingetreten " +
                "ist.{makruh-nafl} Auch ein versäumtes Pflichtgebet holt man in dieser Zeit nicht nach.{makruh-pflicht}",
            mehr = listOf(
                Frage(
                    "Warum heißt es „Zenit“ und nicht „Zawāl“?",
                    "Genau genommen ist nicht der Zawāl verpönt, sondern der Höchststand davor, arabisch [[Istiwāʾ]]. " +
                        "Ibn ʿĀbidīn schreibt, man sage besser „beim Höchststand“, denn zur Zeit des Zawāl ist das Gebet " +
                        "nach einhelliger Ansicht nicht verpönt; mit ihm beginnt Ẓuhr.{zawal-istiwa} Die App nennt die " +
                        "Spanne deshalb „Zenit“.",
                ),
                Frage(
                    "Wie lange dauert die Zeit?",
                    "Darüber gehen die Ansichten auseinander. Nach einer reicht sie von der Mitte des Tages bis zum " +
                        "Zawāl. Die Gelehrten Transoxaniens meinten die Mitte des gewöhnlichen Tages, die Gelehrten " +
                        "Ḫwārizms die Mitte des Tages im Fiqh-Sinn, der mit dem Faǧr beginnt; dann beginnt die Zeit " +
                        "schon ein gutes Stück vor dem Zawāl.{zawal-dauer}",
                ),
                Frage(
                    "Und am Freitag?",
                    "Abū Yūsuf sagt, am Freitag sei freiwilliges Gebet beim Höchststand nicht verpönt.{zawal-jumua} " +
                        "Das ist seine Einzelansicht. Die App macht für den Freitag keine Ausnahme.",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet veröffentlicht keine Uhrzeit für diese Spanne. Die App rechnet die " +
                        "${Karaha.ZEVAL_BEFORE_DHUHR_MIN} Minuten vor der amtlichen Ẓuhr-Zeit bis Ẓuhr.",
                ),
            ),
        ),
        Abschnitt(
            teil = Teil.ISFIRAR,
            kopf = "Verpönte Zeit",
            name = "Vor Sonnenuntergang",
            merksatz = "Wird die Sonne gelb und schwach, betet man kein freiwilliges Gebet mehr, bis sie untergegangen ist.",
            wann = "Wenn die Sonne gelb und schwach wird, so dass man in sie blicken kann, bis sie untergeht.{isfirar-zeit} " +
                "Dieses Vergilben heißt arabisch [[Iṣfirār]]. Dann beginnt [[Maġrib]].",
            wieviele = null,
            rakaat = emptyList(),
            warum = "Weil die Sonne dann zwischen den beiden Hörnern eines Šayṭān untergeht und die Ungläubigen sich " +
                "vor ihr niederwerfen. Der Prophet ﷺ sagte: „Halte dich vom Gebet zurück, bis die Sonne untergeht, " +
                "denn sie geht zwischen den beiden Hörnern eines Šayṭān unter, und dann werfen sich die Ungläubigen " +
                "vor ihr nieder.“ (Muslim 832) Es ist eine der drei Stunden, in denen er das Gebet verbot: „wenn die " +
                "Sonne sich zum Untergang neigt, bis sie untergegangen ist“ (Muslim 831). Über den, der das [[ʿAṣr]] " +
                "bis dahin aufschiebt, sagte er: „Das ist das Gebet des Heuchlers: Er sitzt und wartet auf die Sonne, " +
                "und wenn sie zwischen den beiden Hörnern des Šayṭān steht, steht er auf und pickt es in vier Rakʿa " +
                "herunter und gedenkt Gottes darin nur wenig.“ (Muslim 622)",
            achtung = "Das ʿAṣr desselben Tages bleibt bis zum Sonnenuntergang gültig, ist aber verpönt.{asr-des-tages} " +
                "Es bis zum Vergilben aufzuschieben ist stark verpönt.{asr-verzoegern} Ein ʿAṣr von einem früheren " +
                "Tag holt man in dieser Zeit nicht nach.{makruh-pflicht}",
            mehr = listOf(
                Frage(
                    "Warum ist das ʿAṣr von heute noch gültig?",
                    "Sein Anlass ist das Stück Zeit, an das man das Gebet anschließt, und das besteht noch. Verpönt " +
                        "ist es wegen des verbotenen Aufschiebens, nicht wegen der Zeit selbst. Ein ʿAṣr von einem " +
                        "früheren Tag dagegen ist voll geschuldet und kann nicht in dieser unvollständigen Zeit " +
                        "verrichtet werden.{asr-des-tages}",
                ),
                Frage(
                    "Wann beginnt das Vergilben?",
                    "Die Sonne hat sich verändert, wenn ihr Glanz schwindet und der Blick nicht mehr geblendet wird. " +
                        "Das ist die richtige Ansicht.{asr-verzoegern}",
                ),
                Frage(
                    "Freiwilliges Gebet schon ab dem ʿAṣr",
                    "Wer das ʿAṣr gebetet hat, betet bis Sonnenuntergang kein freiwilliges Gebet mehr, auch vor dem " +
                        "Vergilben nicht.{makruh-nach-fajr-asr} Versäumtes darf er nach dem ʿAṣr nachholen,{qada-nach-fajr-asr} " +
                        "aber nicht mehr in der verpönten Zeit selbst.{makruh-pflicht}",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet veröffentlicht keine Uhrzeit für das Vergilben. Die App rechnet die " +
                        "${Karaha.ISFIRAR_BEFORE_MAGHRIB_MIN} Minuten vor der amtlichen Maġrib-Zeit bis Maġrib.",
                ),
            ),
        ),
        Abschnitt(
            teil = Teil.AWWABIN,
            kopf = "Nach dem Maġrib",
            name = "Auwābīn",
            merksatz = "Auwābīn sind sechs freiwillige Rakʿa nach dem Maġrib.",
            wann = "Nach dem Maġrib-Gebet.{awwabin-sechs}",
            wieviele = "Sechs Rakʿa, sie sind [[Mandūb]].{awwabin-sechs}",
            rakaat = listOf(RakaBlock(RakaArt.MANDUB, 6)),
            warum = "Der Prophet ﷺ sagte: „Wer nach dem Maġrib sechs Rakʿa betet und zwischen ihnen nichts " +
                "Schlechtes redet, dem werden sie wie zwölf Jahre Gottesdienst angerechnet.“ (Tirmiḏī 435)",
            achtung = "Ob die zwei [[Sunna muʾakkada]] nach dem Maġrib zu den sechs zählen, ist nicht " +
                "entschieden.{awwabin-zaehlung} Wer sicher gehen will, betet sie zusätzlich.",
            mehr = listOf(
                Frage(
                    "Was heißt Auwābīn?",
                    "Der Auwāb ist, wer nach einer Sünde ohne Zögern zur Reue eilt.{awwabin-wort} Gott sagt: „Er ist " +
                        "denen, die sich immer wieder zu Ihm wenden, vergebend.“ (al-Isrāʾ 17:25)",
                ),
                Frage(
                    "Zusätzlich oder darin enthalten?",
                    "aš-Šurunbulālī reiht die empfohlenen an die bekräftigten an. Dem Anschein nach sind die sechs " +
                        "nicht die zwei Sunna muʾakkada, es wird aber auch gesagt, sie zählten dazu.{awwabin-zaehlung}",
                ),
                Frage(
                    "Auwābīn auch am Vormittag",
                    "In einem Ḥadīṯ heißt auch das [[Ḍuḥā]] so: „Das Gebet der Auwābīn ist, wenn die jungen Kamele " +
                        "die Hitze spüren.“ (Muslim 748)",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet liefert keine eigene Zeit. Die App zeigt die Spanne von Maġrib bis ʿIšāʾ, also die Zeit " +
                        "des Maġrib-Gebets selbst.",
                ),
            ),
        ),
        Abschnitt(
            teil = Teil.TAHAJJUD,
            kopf = "Nachtwache",
            name = "Tahaǧǧud",
            merksatz = "Tahaǧǧud ist das freiwillige Gebet in der Nacht, am besten im letzten Drittel.",
            wann = "Nach dem [[ʿIšāʾ]] bis zum Faǧr.{tahajjud-nach-isha} Besonders vorzüglich ist das letzte Drittel " +
                "der Nacht.{nachtgebet-drittel}",
            wieviele = "Das Nachtgebet ist [[Mandūb]]. aš-Šurunbulālī nennt nach al-Ǧauhara acht Rakʿa als das " +
                "Mindeste, das man beten sollte.{tahajjud-acht} Ibn ʿĀbidīn sagt: mindestens zwei, in der Mitte vier, " +
                "höchstens acht.{tahajjud-zahl}",
            rakaat = listOf(RakaBlock(RakaArt.MANDUB, 8, "2 bis 8")),
            warum = "Der Prophet ﷺ sagte: „Das beste Gebet nach dem Pflichtgebet ist das Gebet in der Nacht.“ " +
                "(Muslim 1163) Und: „Unser Herr steigt jede Nacht zum untersten Himmel herab, wenn das letzte Drittel " +
                "der Nacht bleibt, und sagt: Wer ruft Mich an, dass Ich ihm antworte? Wer bittet Mich, dass Ich ihm " +
                "gebe? Wer bittet Mich um Vergebung, dass Ich ihm vergebe?“ (Buḫārī 1145)",
            achtung = "Wer sich das Nachtgebet angewöhnt hat, lässt es nicht ohne Grund wieder fallen; das ist " +
                "verpönt.{tahajjud-gewohnheit}",
            mehr = listOf(
                Frage(
                    "Welcher Teil der Nacht?",
                    "aš-Šurunbulālī hebt das letzte Drittel hervor, weil es der Seele schwerer fällt.{nachtgebet-drittel} " +
                        "Ad-Durr al-muḫtār unterscheidet: Wer die Nacht in Drittel teilt, für den ist das mittlere am " +
                        "besten; wer sie halbiert, für den die zweite Hälfte.{tahajjud-drittel}",
                ),
                Frage(
                    "Muss man vorher geschlafen haben?",
                    "Ibn ʿĀbidīn versteht die Überlieferung so, dass die Zeit nach dem ʿIšāʾ beginnt. Wer vor dem " +
                        "ʿIšāʾ schläft und dann freiwillig betet, erfüllt diese Sunna nicht.{tahajjud-nach-isha}",
                ),
                Frage(
                    "Zwei oder vier auf einmal?",
                    "Nach Abū Ḥanīfa betet man am besten je vier, nach Abū Yūsuf und Muḥammad nachts je zwei. Nach " +
                        "ihrer Ansicht wird die Fatwā gegeben.{nacht-paarweise}",
                ),
                Frage(
                    "Wie viele sind es?",
                    "Nach al-Ǧauhara sollte man nachts mindestens acht Rakʿa freiwillig beten.{tahajjud-acht} Ibn " +
                        "ʿĀbidīn schließt aus den Überlieferungen: zwei als Mindestes, vier als Mitte, acht als " +
                        "Höchstes.{tahajjud-zahl} Der [[Witr]] ist ein eigenes Gebet; er steht im Kapitel ʿIšāʾ.",
                ),
                Frage(
                    "Wie rechnet die App diese Zeit?",
                    "Diyanet liefert keine Tahaǧǧud-Zeit. Die App teilt die Nacht von der amtlichen Maġrib-Zeit bis " +
                        "zum Faǧr des nächsten Tages in drei gleiche Teile und zeigt das letzte Drittel. Fehlt die " +
                        "Faǧr-Zeit des nächsten Tages, zeigt sie keine Zeit an, statt eine zu raten.",
                ),
            ),
        ),
    ),
    belege = listOf(
        Beleg(
            "shuruq-zeit",
            Quote(
                arabic = "أولها \"عند طلوع الشمس إلى أن ترتفع\" وتبيض قدر رمح أو رمحين",
                german = "Die erste [der verpönten Zeiten] ist beim Aufgang der Sonne, bis sie gestiegen ist und hell wird, etwa eine oder zwei Speerlängen hoch.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "fajr-bei-aufgang",
            Quote(
                arabic = "وإذا أشرقت الشمس وهو في صلاة الفجر بطلت",
                german = "Geht die Sonne auf, während er noch im Faǧr-Gebet ist, so ist es ungültig.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "makruh-nafl",
            Quote(
                arabic = "والأوقات الثلاثة يكره فيها النافلة كراهة تحريم ولو كان لها سبب كالمنذور وركعتي الطواف",
                german = "In den drei Zeiten ist das freiwillige Gebet stark verpönt (Karāhat at-taḥrīm), auch wenn es einen Anlass hat, wie ein gelobtes Gebet oder die zwei Rakʿa des Ṭawāf.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "shuruq-speer",
            Quote(
                arabic = "يَنْبَغِي تَصْحِيحُ مَا نَقَلُوهُ عَنْ الْأَصْلِ لِلْإِمَامِ مُحَمَّدٍ مِنْ أَنَّهُ مَا لَمْ تَرْتَفِعْ الشَّمْسُ قَدْرَ رُمْحٍ فَهِيَ فِي حُكْمِ الطُّلُوعِ",
                german = "Für richtig zu halten ist, was sie aus dem Aṣl von Imām Muḥammad überliefern: Solange die Sonne nicht eine Speerlänge gestiegen ist, gilt sie als aufgehend.",
                source = "$IBN_ABIDIN, Bd. 1, S. 371",
            ),
        ),
        Beleg(
            "shuruq-blick",
            Quote(
                arabic = "وَمَا دَامَتْ الْعَيْنُ لَا تَحَارُ فِيهَا فِي حُكْمِ الشُّرُوقِ",
                german = "Solange das Auge nicht geblendet wird, wenn es in sie blickt, gilt sie als aufgehend.",
                source = "$IBN_ABIDIN, Bd. 1, S. 371 (nach al-Baḥr)",
            ),
        ),
        Beleg(
            "makruh-pflicht",
            Quote(
                arabic = "ثلاثة أوقات لا يصح فيها شيء من الفرائض والواجبات التي لزمت في الذمة قبل دخولها",
                german = "Drei Zeiten gibt es, in denen keine Pflicht gültig ist, die schon vor ihrem Eintritt geschuldet war, weder Farḍ noch Wāǧib.",
                source = "$MARAQI, S. 75 f.",
            ),
        ),
        Beleg(
            "pflicht-mit-karaha",
            Quote(
                arabic = "\"ويصح أداء ما وجب فيها\" أي الأوقات الثلاثة لكن \"مع الكراهة\" في ظاهر الرواية \"كجنازة حضرت وسجدة آية تليت فيها\"",
                german = "Gültig ist die Verrichtung dessen, was in ihnen, das heißt in den drei Zeiten, fällig wurde, aber verpönt, nach der Ẓāhir ar-riwāya: etwa ein Totengebet, das [in der Zeit] gebracht wurde, und eine Niederwerfung für einen Vers, der in ihr gelesen wurde.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg("makruh-nach-fajr-asr", NAFL_NACH_FAJR_ASR),
        Beleg(
            "qada-nach-fajr-asr",
            Quote(
                arabic = "ويكره أن يتنفل بعد صلاة الفجر حتى تطلع الشمس وبعد صلاة العصر حتى تغرب الشمس ولا بأس بأن يصلي في هذين الوقتين الفوائت ويسجد للتلاوة ويصلي على الجنازة",
                german = "Verpönt ist es, nach dem Faǧr-Gebet freiwillig zu beten, bis die Sonne aufgeht, und nach dem ʿAṣr-Gebet, bis die Sonne untergeht. Es ist aber nichts dabei, in diesen beiden Zeiten versäumte Gebete nachzuholen, die Niederwerfung für die Lesung zu machen und das Totengebet zu verrichten.",
                source = "$QUDURI, S. 32",
            ),
        ),
        Beleg(
            "duha-zeit",
            Quote(
                arabic = "وقت \"الضحى\" وابتداؤه من ارتفاع الشمس إلى قبل زوالها",
                german = "Die Zeit des Ḍuḥā beginnt mit dem Steigen der Sonne und reicht bis vor ihrem Zawāl.",
                source = "$MARAQI, S. 149",
            ),
        ),
        Beleg(
            "duha-beste-zeit",
            Quote(
                arabic = "(وَ) نُدِبَ (أَرْبَعٌ فَصَاعِدًا فِي الضُّحَى) عَلَى الصَّحِيحِ مِنْ بَعْدِ الطُّلُوعِ إلَى الزَّوَالِ وَوَقْتُهَا الْمُخْتَارُ بَعْدَ رُبُعِ النَّهَارِ",
                german = "Empfohlen sind vier oder mehr im Ḍuḥā, nach der richtigen Ansicht von nach dem Sonnenaufgang bis zum Zawāl; die gewählte Zeit ist nach einem Viertel des Tages.",
                source = "$DURR, Bd. 2, S. 22",
            ),
        ),
        Beleg(
            "duha-vier",
            Quote(
                arabic = "وندب ركعتان بعد الوضوء قبل جفافه وأربع فصاعدا في الضحى",
                german = "Empfohlen sind zwei Rakʿa nach der Waschung, bevor sie trocknet, und vier oder mehr in der Ḍuḥā-Zeit.",
                source = "$MARAQI, S. 149",
            ),
        ),
        Beleg(
            "duha-spanne",
            Quote(
                arabic = "وَفِي الْمُنْيَةِ: أَقَلُّهَا رَكْعَتَانِ وَأَكْثَرُهَا اثْنَيْ عَشَرَ، وَأَوْسَطُهَا ثَمَانٍ وَهُوَ أَفْضَلُهَا",
                german = "In al-Munya heißt es: Das Mindeste sind zwei Rakʿa, das Meiste zwölf, die Mitte acht, und das ist das Beste.",
                source = "$DURR, Bd. 2, S. 23",
            ),
        ),
        Beleg("zawal-zeit", ZAWAL_ZEIT),
        Beleg(
            "duha-vier-kamal",
            Quote(
                arabic = "وَالتَّوْفِيقُ مَا أَشَارَ إلَيْهِ بَعْضُ الْمُحَقِّقِينَ أَنَّ الرَّكْعَتَيْنِ أَقَلُّ الْمَرَاتِبِ وَالْأَرْبَعُ أَدْنَى الْكَمَالِ",
                german = "Die Vereinbarung [beider Angaben] ist, worauf einige der Gründlichen hinwiesen: Zwei Rakʿa sind die unterste Stufe, vier das Mindeste der Vollkommenheit.",
                source = "$IBN_ABIDIN, Bd. 2, S. 23",
            ),
        ),
        Beleg(
            "zawal-istiwa",
            Quote(
                arabic = "التَّعْبِيرُ بِهِ أَوْلَى مِنْ التَّعْبِيرِ بِوَقْتِ الزَّوَالِ؛ لِأَنَّ وَقْتَ الزَّوَالِ لَا تُكْرَهُ فِيهِ الصَّلَاةُ إجْمَاعًا",
                german = "Es so [„beim Höchststand“] auszudrücken ist besser als „zur Zeit des Zawāl“, denn zur Zeit des Zawāl ist das Gebet nach einhelliger Ansicht nicht verpönt.",
                source = "$IBN_ABIDIN, Bd. 1, S. 371 (nach al-Baḥr)",
            ),
        ),
        Beleg(
            "zawal-dauer",
            Quote(
                arabic = "وَعَزَا فِي الْقُهُسْتَانِيِّ الْقَوْلَ بِأَنَّ الْمُرَادَ انْتِصَافُ النَّهَارِ الْعُرْفِيِّ إلَى أَئِمَّةِ مَا رَوَاهُ النَّهْرُ، وَبِأَنَّ الْمُرَادَ انْتِصَافُ النَّهَارِ الشَّرْعِيِّ وَهُوَ الضَّحْوَةُ الْكُبْرَى إلَى الزَّوَالِ إلَى أَئِمَّةِ جُوَارِزْمَ",
                german = "Al-Quhistānī schreibt die Ansicht, gemeint sei die Mitte des gewöhnlichen Tages, den Gelehrten Transoxaniens zu, und die Ansicht, gemeint sei die Mitte des Tages im Fiqh-Sinn, das ist die große Ḍaḥwa bis zum Zawāl, den Gelehrten Ḫwārizms.",
                source = "$IBN_ABIDIN, Bd. 1, S. 371",
            ),
        ),
        Beleg(
            "zawal-jumua",
            Quote(
                arabic = "وقال أبو يوسف لا تكره النافلة حال الاستواء يوم الجمعة",
                german = "Abū Yūsuf sagte: Das freiwillige Gebet ist beim Höchststand am Freitag nicht verpönt.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "isfirar-zeit",
            Quote(
                arabic = "الثالث \"عند اصفرارها\" وضعفها حتى تقدر العين على مقابلتها \"إلى أن تغرب\"",
                german = "Die dritte [verpönte Zeit] ist, wenn sie gelb und schwach wird, so dass das Auge ihrem Anblick standhält, bis sie untergeht.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "asr-des-tages",
            Quote(
                arabic = "\"كما صح عصر اليوم\" بأدائه \"عند الغروب\" لبقاء سببه وهو الجزء المتصل به الأداء من الوقت \"مع الكراهة\" للتأخير المنهي عنه لا لذات الوقت بخلاف عصر مضى للزومه كاملا بخروج وقته فلا يؤدى في ناقص",
                german = "So wie das ʿAṣr desselben Tages gültig ist, wenn man es beim Sonnenuntergang verrichtet, weil sein Anlass noch besteht, nämlich der Teil der Zeit, an den die Verrichtung anschließt; aber verpönt, wegen des verbotenen Aufschiebens, nicht wegen der Zeit selbst. Anders ein ʿAṣr eines vergangenen Tages: Mit dem Ende seiner Zeit ist es vollständig geschuldet und wird deshalb nicht in einer unvollständigen [Zeit] verrichtet.",
                source = "$MARAQI, S. 76",
            ),
        ),
        Beleg(
            "asr-verzoegern",
            Quote(
                arabic = "\"ما لم تتغير الشمس\" بذهاب ضوئها فلا يتحير فيه البصر وهو الصحيح والتأخير إلى التغير مكروه تحريما",
                german = "Solange die Sonne sich nicht verändert, das heißt ihr Glanz nicht vergangen ist, so dass der Blick von ihr nicht mehr geblendet wird; das ist die richtige Ansicht. Das Aufschieben bis zu dieser Veränderung ist stark verpönt (makrūh taḥrīman).",
                source = "$MARAQI, S. 74",
            ),
        ),
        Beleg("awwabin-sechs", AUWABIN_SECHS),
        Beleg("awwabin-zaehlung", SUNNA_ZAEHLUNG),
        Beleg(
            "awwabin-wort",
            Quote(
                arabic = "والأواب هو الذي إذا أذنب ذنبا بادر إلى التوبة",
                german = "Der Auwāb ist, wer nach einer Sünde ohne Zögern zur Reue eilt.",
                source = "$MARAQI, S. 146",
            ),
        ),
        Beleg(
            "tahajjud-nach-isha",
            Quote(
                arabic = "الظَّاهِرُ أَنَّ حَدِيثَ الطَّبَرَانِيِّ الْأَوَّلَ بَيَانٌ لِكَوْنِ وَقْتِهِ بَعْدَ صَلَاةِ الْعِشَاءِ، حَتَّى لَوْ نَامَ ثُمَّ تَطَوَّعَ قَبْلَهَا لَا يَحْصُلُ السُّنَّةُ",
                german = "Dem Anschein nach erklärt der erste Ḥadīṯ bei aṭ-Ṭabarānī, dass seine Zeit nach dem ʿIšāʾ-Gebet ist; wer also schläft und dann vor ihm freiwillig betet, erfüllt die Sunna nicht.",
                source = "$IBN_ABIDIN, Bd. 2, S. 24",
            ),
        ),
        Beleg(
            "nachtgebet-drittel",
            Quote(
                arabic = "\"وصلاة الليل\" خصوصا في الثلث الأخير منه \"أفضل من صلاة النهار\" لأنه أشق على النفس",
                german = "Das Nachtgebet, besonders im letzten Drittel der Nacht, ist vorzüglicher als das Gebet am Tag, weil es der Seele schwerer fällt.",
                source = "$MARAQI, S. 148",
            ),
        ),
        Beleg(
            "tahajjud-zahl",
            Quote(
                arabic = "فَيَنْبَغِي الْقَوْلُ بِأَنَّ أَقَلَّ التَّهَجُّدِ رَكْعَتَانِ وَأَوْسَطُهُ أَرْبَعٌ وَأَكْثَرُهُ ثَمَانٍ",
                german = "Zu sagen ist also: Das Mindeste des Tahaǧǧud sind zwei Rakʿa, seine Mitte vier und sein Höchstes acht.",
                source = "$IBN_ABIDIN, Bd. 2, S. 25",
            ),
        ),
        Beleg(
            "tahajjud-acht",
            Quote(
                arabic = "\"وندب صلاة الليل\" خصوصا آخره كما ذكرناه وأقل ما ينبغي أن يتنفل بالليل ثمان ركعات كذا في الجوهرة",
                german = "Empfohlen ist das Nachtgebet, besonders am Ende der Nacht, wie wir erwähnt haben. Das Mindeste, was man nachts freiwillig beten sollte, sind acht Rakʿa. So steht es in al-Ǧauhara.",
                source = "$MARAQI, S. 149",
            ),
        ),
        Beleg(
            "tahajjud-gewohnheit",
            Quote(
                arabic = "أَنَّهُ يُكْرَهُ تَرْكُ تَهَجُّدٍ اعْتَادَهُ بِلَا عُذْرٍ",
                german = "Verpönt ist es, ein Tahaǧǧud, an das man sich gewöhnt hat, ohne Entschuldigung aufzugeben.",
                source = "$IBN_ABIDIN, Bd. 2, S. 25 (nach al-Ḥilya)",
            ),
        ),
        Beleg(
            "tahajjud-drittel",
            Quote(
                arabic = "وَلَوْ جَعَلَهُ أَثْلَاثًا فَالْأَوْسَطُ أَفْضَلُ، وَلَوْ أَنْصَافًا فَالْأَخِيرُ أَفْضَلُ",
                german = "Teilt er sie [die Nacht] in Drittel, ist das mittlere am vorzüglichsten; teilt er sie in Hälften, die letzte.",
                source = "$DURR, Bd. 2, S. 25",
            ),
        ),
        Beleg(
            "nacht-paarweise",
            Quote(
                arabic = "والأفضل فيهما رباع عند أبي حنيفة وعندهما الأفضل في الليل مثنى مثنى وبه يفتى",
                german = "Am vorzüglichsten ist in beiden [Nacht und Tag] je vier nach Abū Ḥanīfa; nach den beiden [Abū Yūsuf und Muḥammad] ist in der Nacht je zwei am vorzüglichsten, und danach wird die Fatwā gegeben.",
                source = "$MARAQI, S. 148",
            ),
        ),
    ),
)

/**
 * Die sechs Kapitel in der Folge der Reiter: Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ,
 * „Freiwillig und verpönt“. Die Stelle in der Liste ist die Kapitelnummer in [Ziel].
 */
val LEHRE: List<Kapitel> = listOf(KAPITEL_FAJR, KAPITEL_ZUHR, KAPITEL_ASR, KAPITEL_MAGHRIB, KAPITEL_ISHA, KAPITEL_FREIWILLIG)
