package de.gebetszeiten.ui.verstehen

/**
 * Der Lehrinhalt der neuen Seite „Verstehen“, Reiter für Reiter. Vorerst nur Ẓuhr,
 * Wortlaut aus der freigegebenen Probe (docs/entwurf/verstehen-konzept.html, Ton C).
 * Reihenfolge der Reiter: Faǧr, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ, „Freiwillig und verpönt“.
 */
val LEHRE: List<Kapitel> = listOf(
    Kapitel(
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
                        "Wie rechnet die App die verpönte Zeit?",
                        "Als die 20 Minuten vor Ẓuhr.",
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
            Beleg(
                "zuhr-ende",
                Quote(
                    arabic = "وآخر وقتها عند أبي حنيفة إذا صار ظل كل شيء مثليه سوي فيء الزوال وقال أبو يوسف ومحمد: إذا صار ظل كل شيء مثله وأول وقت العصر إذا خرج وقت الظهر على القولين",
                    german = "Nach Abū Ḥanīfa endet ihre Zeit [die des Ẓuhr], wenn der Schatten jedes Dinges doppelt so lang ist wie das Ding, ohne den Schatten zur Zeit des Zawāl. Abū Yūsuf und Muḥammad sagen: wenn der Schatten jedes Dinges so lang ist wie das Ding. Die Zeit des ʿAṣr beginnt, wenn die Zeit des Ẓuhr endet, nach beiden Ansichten.",
                    source = "$QUDURI, S. 23",
                ),
            ),
            Beleg("zuhr-rakaat", SUNNA_MUAKKADA),
            Beleg(
                "zuhr-rakaat-nach",
                Quote(
                    arabic = "\"ركعتان بعد الظهر\" ويندب أن يضم إليها ركعتان فتصير أربعا",
                    german = "Zwei Rakʿa nach dem Ẓuhr; empfohlen ist, zwei hinzuzufügen, so dass es vier werden.",
                    source = "$MARAQI, S. 145",
                ),
            ),
            Beleg(
                "zuhr-zawal",
                Quote(
                    arabic = "الثاني \"عند استوائها\" في بطن السماء \"إلى أن تزول\" أي تميل إلى جهة الغرب",
                    german = "Die zweite [verpönte Zeit] ist, wenn sie mitten am Himmel steht, bis zum Zawāl, das heißt bis sie sich nach Westen neigt.",
                    source = "$MARAQI, S. 76",
                ),
            ),
        ),
    ),
)
