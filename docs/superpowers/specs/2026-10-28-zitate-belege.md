# Belege für die Zitate in „Zeiten verstehen“

Stand: 28.10.2026. Gehört zu `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/VerstehenContent.kt`.
Jede Zeile dort muss sich hier wiederfinden.

## Vorgehen und Lesehilfe

- Alle arabischen Texte stammen aus Ergebnissen von `shamela_get_page` / `shamela_get_pages_range`
  der lokalen Shamela-Bibliothek. Nichts ist aus dem Gedächtnis ergänzt.
- Zitiert wird nur aus dem Feld `body` (Matn). Fußnoten (`foot`) sind Worte des Herausgebers und
  werden nicht zitiert. Auf den verwendeten Seiten wurden folgende Fußnoten bewusst **nicht**
  übernommen: 412/81 (Polargebiete), 412/82 (Definition des Ibrād), 412/85 („اهـ طحطاوي“).
- Aufbau von „Marāqī l-falāḥ“ (book_id 412) in Shamela: Oben auf jeder Seite steht der Grundtext
  „Nūr al-īḍāḥ“, nach der Linie `ــ` folgt der Kommentar „Marāqī l-falāḥ“. Beide sind von
  aš-Šurunbulālī und gehören zum Feld `body`. Im Kommentar setzt die Ausgabe die Worte des
  Grundtexts in gerade Anführungszeichen `"…"`; diese Zeichen sind im App-Text unverändert
  übernommen.
- In „al-Hidāya“ (book_id 11820) stehen die Worte des Grundtexts „Bidāyat al-mubtadiʾ“ (ebenfalls
  al-Marġīnānī) in Anführungszeichen, der Rest ist der Kommentar al-Hidāya.
- Einzige Änderung am Wortlaut: Zeilenumbrüche innerhalb eines Zitats sind durch ein Leerzeichen
  ersetzt (betrifft nur das Qudūrī-Zitat bei ʿAṣr). Tashkīl lieferte das Werkzeug keines.
- Ausgelassene Teile sind unten mit `[…]` im **Kontext** gezeigt; der App-Text ist immer ein
  zusammenhängendes Stück ohne Auslassung.
- Alle Zitierangaben kamen mit `auto_numbered: false`, also Druckseiten, kein Hinweis
  „Shamela-Seitenzählung“ nötig. Ausgabe, Verlag, Ort und Herausgeber liefert Shamela nicht
  (`notes`: „edition number / publisher / city of publication / editor / muḥaqqiq not available in
  master.db“).
- Übersetzungen: eigene Übersetzung, in eckigen Klammern stehen Ergänzungen, die nicht im
  arabischen Text stehen.

## Zitierausgaben (`shamela_get_citation`, style `full`)

| book_id | page_id | `formatted` | Quellenangabe in der App |
|---|---|---|---|
| 412 | 80 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٢. | aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 72 |
| 412 | 82 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٤. | …, S. 74 |
| 412 | 83 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٥. | …, S. 75 |
| 412 | 85 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٦. | …, S. 76 |
| 412 | 170 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٤٦. | …, S. 146 |
| 412 | 172 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٤٨. | …, S. 148 |
| 412 | 174 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٤٩. | …, S. 149 |
| 124336 | 13 | القدوري (ت ٤٢٨هـ). مختصر القدوري. ٤٢٨هـ، ص ٢٣. | al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī, S. 23 |
| 11820 | 46 | المرغيناني (ت ٥٩٣هـ). الهداية في شرح بداية المبتدي. ٥٩٣هـ، ج ١، ص ٤٠. | al-Marġīnānī (gest. 593 n. H.), al-Hidāya, Bd. 1, S. 40 |

---

## FAJR: „Faǧr“

**1.** book_id 412, page_id 80 (S. 72), Grundtext (über `ــ`)

> والأوقات خمسة وقت الصبح من طلوع الفجر الصادق الى قبيل طلوع الشمس

Kontext: … وتجب بأول الوقت وجوبا موسعا **[Zitat]** ووقت الظهر من زوال الشمس …

Übersetzung: Die Zeiten sind fünf. Die Zeit des Morgengebets reicht vom Aufgang des echten Faǧr bis kurz vor Sonnenaufgang.

**2.** book_id 11820, page_id 46 (Bd. 1, S. 40), Kommentar al-Hidāya

> ولا معتبر بالفجر الكاذب وهو البياض الذي يبدو طولا ثم يعقبه الظلام

Kontext: … "ما بين هذين الوقتين وقت لك ولأمتك" **[Zitat]** لقوله ﵊ "لا يغرنكم أذان بلال …

Übersetzung: Der falsche Faǧr zählt nicht. Das ist die Helligkeit, die längs erscheint und auf die wieder Dunkelheit folgt.

## SHURUQ_MAKRUH: „Šurūq und Makrūh beim Aufgang“

**1.** book_id 412, page_id 85 (S. 76), Kommentar

> أولها "عند طلوع الشمس إلى أن ترتفع" وتبيض قدر رمح أو رمحين

Kontext: دخولها" أي الأوقات المكروهة **[Zitat]** "و" الثاني "عند استوائها" …

Übersetzung: Die erste [der verpönten Zeiten] ist beim Aufgang der Sonne, bis sie gestiegen ist und hell wird, etwa eine oder zwei Speerlängen hoch.

**2.** book_id 412, page_id 85 (S. 76), Grundtext **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> والأوقات الثلاثة يكره فيها النافلة كراهة تحريم ولو كان لها سبب كالمنذور وركعتي الطواف

Kontext: … كما ح عصر اليوم عند الغروب مع الكراهة **[Zitat]** ويكره التنفل بعد طلوع الفجر …

Übersetzung: In den drei Zeiten ist freiwilliges Gebet streng verpönt, dem Verbot nahe (Karāhat taḥrīm), auch wenn es einen Anlass hat, etwa ein gelobtes Gebet oder die zwei Rakʿa nach dem Umlauf um die Kaʿba.

## DUHA: „Ḍuḥā“

**1.** book_id 412, page_id 174 (S. 149), Grundtext **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> وندب ركعتان بعد الوضوء قبل جفافه وأربع فصاعدا في الضحى

Kontext: **[Zitat]** وندب صلاة الليل وصلاة الاستخارة …

Übersetzung: Empfohlen sind zwei Rakʿa nach der Waschung, bevor sie trocknet, und vier oder mehr in der Ḍuḥā-Zeit.

**2.** book_id 412, page_id 174 (S. 149), Kommentar

> وقت "الضحى" وابتداؤه من ارتفاع الشمس إلى قبل زوالها

Kontext: … فلذا قلنا ندب أربع "فصاعد في" **[Zitat]** فيزيد على الأربع اثنتي عشرة ركعة …

Übersetzung: Die Zeit des Ḍuḥā beginnt mit dem Steigen der Sonne und reicht bis vor ihrem Zawāl.

## ZUHR: „Ẓuhr und Zawāl“ (mit Sonnenuhr)

**1.** book_id 124336, page_id 13 (S. 23), Matn al-Qudūrī

> وأول وقت الظهر إذا زالت الشمس

Kontext: … وآخر وقتها ما لت تتطلع الشمس **[Zitat]** وآخر وقتها عند أبي حنيفة …

Übersetzung: Die Zeit des Ẓuhr beginnt, wenn die Sonne sich [vom Höchststand] neigt.

**2.** book_id 412, page_id 85 (S. 76), Kommentar

> الثاني "عند استوائها" في بطن السماء "إلى أن تزول" أي تميل إلى جهة الغرب

Kontext: … وتبيض قدر رمح أو رمحين "و" **[Zitat]** "و" الثالث "عند اصفرارها" …

Übersetzung: Die zweite [verpönte Zeit] ist, wenn sie mitten am Himmel steht, bis zum Zawāl, das heißt bis sie sich nach Westen neigt.

## ASR: „ʿAṣr“ (mit Sonnenuhr)

`note`: „Die angezeigte ʿAṣr-Zeit ist die amtliche Zeit von Diyanet.“

Beide Zitate geben die zwei Positionen so wieder, wie die Quellen sie zuschreiben: einfacher
Schatten (Miṯl) bei Abū Yūsuf und Muḥammad, doppelter Schatten (Miṯlān) bei Abū Ḥanīfa;
aš-Šurunbulālī nennt im Grundtext beide und dass aṭ-Ṭaḥāwī den einfachen gewählt hat. Eine
eigene Gewichtung ist nicht hinzugefügt. Zur Information für die Prüfung, **nicht** in der App:
Der Kommentar auf derselben Seite (412/80) sagt zum doppelten Schatten „وهو الصحيح وعليه جل
المشايخ والمتون“ und später „والأخذ به أحوط“; al-Hidāya (11820/46) nennt den einfachen Schatten
zusätzlich „رواية عن أبي حنيفة“.

**1.** book_id 124336, page_id 13 (S. 23), Matn al-Qudūrī

> وآخر وقتها عند أبي حنيفة إذا صار ظل كل شيء مثليه سوي فيء الزوال وقال أبو يوسف ومحمد: إذا صار ظل كل شيء مثله وأول وقت العصر إذا خرج وقت الظهر على القولين

Hinweise: Im Werkzeug-Ergebnis stehen nach „الزوال“ und nach „مثله“ Zeilenumbrüche (hier
Leerzeichen). „سوي“ steht so in Shamela (Schreibvariante von „سوى“), unverändert übernommen.

Kontext: وأول وقت الظهر إذا زالت الشمس **[Zitat]** وآخر وقتها ما لم تغرب الشمس …

Übersetzung: Nach Abū Ḥanīfa endet ihre Zeit [die des Ẓuhr], wenn der Schatten jedes Dinges doppelt so lang ist wie das Ding, ohne den Schatten zur Zeit des Zawāl. Abū Yūsuf und Muḥammad sagen: wenn der Schatten jedes Dinges so lang ist wie das Ding. Die Zeit des ʿAṣr beginnt, wenn die Zeit des Ẓuhr endet, nach beiden Ansichten.

**2.** book_id 412, page_id 80 (S. 72), Grundtext **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> ووقت الظهر من زوال الشمس الى أن يصير ظل كل شيء مثليه أو مثله سوى ظل الاستواء واختار الثاني الطحاوي وهو قول الصاحبين ووقت العصر من ابتداء الزيادة على المثل أو المثلين الى غروب الشمس

Kontext: … الى قبيل طلوع الشمس **[Zitat]** والمغرب منه الى غروب الشفق …

Übersetzung: Die Zeit des Ẓuhr reicht vom Zawāl der Sonne, bis der Schatten jedes Dinges doppelt oder einfach so lang ist wie das Ding, ohne den Schatten beim Höchststand. Das Zweite hat aṭ-Ṭaḥāwī gewählt, und es ist die Ansicht der beiden Schüler [Abū Ḥanīfas: Abū Yūsuf und Muḥammad]. Die Zeit des ʿAṣr reicht vom Beginn des Überschreitens des einfachen oder doppelten Schattens bis zum Sonnenuntergang.

(„الصاحبين“ sind laut Kommentar derselben Seite „أبي يوسف ومحمد“; die Klammer stützt sich darauf.)

## ISFIRAR_MAKRUH: „Makrūh beim Vergilben der Sonne“

**1.** book_id 412, page_id 85 (S. 76), Kommentar

> الثالث "عند اصفرارها" وضعفها حتى تقدر العين على مقابلتها "إلى أن تغرب"

Kontext: … أي تميل إلى جهة الغرب "و" **[Zitat]** لقول عقبة بن عامر …

Übersetzung: Die dritte [verpönte Zeit] ist, wenn sie gelb und schwach wird, so dass das Auge ihrem Anblick standhält, bis sie untergeht.

**2.** book_id 412, page_id 82 (S. 74), Kommentar **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> "ما لم تتغير الشمس" بذهاب ضوئها فلا يتحير فيه البصر وهو الصحيح والتأخير إلى التغير مكروه تحريما

Kontext: "و" يستحب "تأخير" صلاة "العصر" صيفا وشتاء … ويتمكن من النفل قبله **[Zitat]** قال رسول الله ﷺ: "تلك صلاة المنافقين" …

Übersetzung: [Den ʿAṣr aufzuschieben ist empfohlen,] solange sich die Sonne nicht verändert hat, indem ihr Glanz schwindet und der Blick nicht mehr geblendet wird. Das ist die richtige Ansicht. Das Aufschieben bis zu dieser Veränderung ist streng verpönt, dem Verbot nahe (Makrūh taḥrīman).

## MAGHRIB: „Maġrib“

**1.** book_id 412, page_id 80 (S. 72), Grundtext

> والمغرب منه الى غروب الشفق الاحمر على المفتى به

Kontext: … ووقت العصر من ابتداء الزيادة على المثل أو المثلين الى غروب الشمس **[Zitat]** والعشاء والوتر منه.

Übersetzung: Die Zeit des Maġrib reicht von ihm [dem Sonnenuntergang] bis zum Verschwinden der roten Abenddämmerung, nach der Ansicht, nach der die Fatwā gegeben wird.

**2.** book_id 412, page_id 83 (S. 75), Grundtext **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> وتعجيل المغرب إلا في يوم غيم فتؤخر فيه

Kontext: (Fortsetzung von S. 74 „ويستحب …“) **[Zitat]** وتأخير العشاء الى ثلث الليل …

Übersetzung: [Empfohlen ist,] den Maġrib früh zu beten, außer an einem bewölkten Tag. Dann wird er aufgeschoben.

## AWWABIN: „Auwābīn“

**1.** book_id 412, page_id 170 (S. 146), Kommentar (der Ḥadīṯ wird vom Autor angeführt; eine
Bewertung des Ḥadīṯ ist nicht Teil dieses Zitats)

> ندب "ست" ركعات "بعد المغرب" لقوله ﷺ: "من صلى بعد المغرب ست ركعات كتب من الأوابين"

Kontext: … "و" **[Zitat]** وتلا قوله تعالى ﴿فَإِنَّهُ كَانَ لِلْأَوَّابِينَ غَفُوراً﴾ …

Übersetzung: Empfohlen sind sechs Rakʿa nach dem Maġrib, wegen seines Wortes, Gott segne ihn und schenke ihm Heil: „Wer nach dem Maġrib sechs Rakʿa betet, wird zu den Auwābīn gezählt.“

**2.** book_id 412, page_id 170 (S. 146), Kommentar

> والأواب هو الذي إذا أذنب ذنبا بادر إلى التوبة

Kontext: … [الاسراء: من الآية٢٥] **[Zitat]** وعن أبي هريرة …

Übersetzung: Der Auwāb ist, wer nach einer Sünde ohne Zögern zur Reue eilt.

## ISHA: „ʿIšāʾ“

**1.** book_id 124336, page_id 13 (S. 23), Matn al-Qudūrī

> وأول وقت العشاء إذا غاب الشفق وآخر وقتها ما لم يطلع الفجر

Kontext: … عند أبي حنيفة وقال أبو يوسف ومحمد: هو الحمرة **[Zitat]** وأول وقت الوتر بعد العشاء …

Übersetzung: Die Zeit des ʿIšāʾ beginnt, wenn die Abenddämmerung (Šafaq) verschwunden ist, und endet, wenn der Faǧr aufgeht.

**2.** book_id 11820, page_id 46 (Bd. 1, S. 40), Grundtext Bidāyat al-mubtadiʾ in Anführungszeichen, danach Kommentar al-Hidāya **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> "ثم الشفق هو البياض الذي في الأفق بعد الحمرة عند أبي حنيفة رحمه الله تعالى وعندهما هو الحمرة" وهو رواية عن أبي حنيفة

Kontext: … وما رواه كان للتحرز عن الكراهة **[Zitat]** وهو قول الشافعي لقوله ﵊ "الشفق الحمرة" …

Übersetzung: Der Šafaq ist nach Abū Ḥanīfa, Gott erbarme sich seiner, die Helligkeit am Horizont nach dem Rot. Nach den beiden [Abū Yūsuf und Muḥammad] ist es das Rot, und das ist auch eine Überlieferung von Abū Ḥanīfa.

(Hinweis: Beim Maġrib zitiert die App aš-Šurunbulālī mit „roter Šafaq, danach wird Fatwā gegeben“.
Beide Stellen stehen mit ihrer jeweiligen Zuschreibung für sich.)

## TAHAJJUD: „Tahaǧǧud“

Die Quellen sprechen von „صلاة الليل“ (Nachtgebet); das Wort Tahaǧǧud steht nicht im Zitat und ist
in der Übersetzung nicht eingesetzt.

**1.** book_id 412, page_id 174 (S. 149), Kommentar **Nicht mehr in der App** (am 28.10.2026 entfernt, weil es ohne Kontext eher verwirrt).

> "وندب صلاة الليل" خصوصا آخره كما ذكرناه وأقل ما ينبغي أن يتنفل بالليل ثمان ركعات كذا في الجوهرة

Kontext: … بنى الله له بيتا في الجنة" **[Zitat]** وفضلها لا يحصر …

Übersetzung: Empfohlen ist das Nachtgebet, besonders am Ende der Nacht, wie wir erwähnt haben. Das Mindeste, was man nachts freiwillig beten sollte, sind acht Rakʿa. So steht es in al-Ǧauhara.

**2.** book_id 412, page_id 172 (S. 148), Kommentar

> "وصلاة الليل" خصوصا في الثلث الأخير منه "أفضل من صلاة النهار" لأنه أشق على النفس

Kontext: … وهو قوله ﵊: "صلاة الليل مثنى مثنى " **[Zitat]** وقال تعالى: ﴿تَتَجَافَى جُنُوبُهُمْ …

(Im Werkzeug-Ergebnis steht davor `""` : das schließende Zeichen des Ḥadīṯ und das öffnende des
Lemmas; zitiert ist ab dem öffnenden.)

Übersetzung: Das Nachtgebet, besonders im letzten Drittel der Nacht, ist vorzüglicher als das Gebet am Tag, weil es der Seele schwerer fällt.

## JAMAA: „Ǧamāʿa am Morgen“

**1.** book_id 412, page_id 82 (S. 74), Grundtext

> ويستحب الإسفار بالفجر للرجال

Kontext: **[Zitat]** والإبراد بالظهر في الصيف …

Übersetzung: Für Männer ist es empfohlen, den Faǧr zu beten, wenn es hell wird (Isfār).

**2.** book_id 412, page_id 82 (S. 74), Kommentar

> ولأن في الإسفار تكثير الجماعة وفي التغليس تقليلها وما يؤدي إلى التكثير أفضل

Kontext: … وقال ﵇: "نوروا بالفجر يبارك لكم" **[Zitat]** وليسهل تحصيل ما ورد عن أنس …

Übersetzung: Denn beim Isfār kommen mehr Menschen zur Gemeinschaft, beim Taġlīs [dem Beten in der Dunkelheit] weniger, und was zu mehr führt, ist vorzüglicher.

---

## Offene Punkte für die Prüfung

- Gesucht wurde in al-Qudūrī und al-Hidāya nur zu den Zeiten (Stichwort „مثليه“); weitere
  Werke (aṭ-Ṭaḥṭāwī, Ibn ʿĀbidīn, shamela-extern) waren nicht nötig, da jeder Abschnitt aus den
  drei bevorzugten Werken belegt ist.
- Stellen mit den Sonder-Ligaturen ﵀ ﵁ ﵇ (Unicode 14) wurden gemieden, weil ältere
  Android-Schriften sie nicht darstellen. ﷺ (U+FDFA) kommt einmal vor (Auwābīn).
- Bitte besonders prüfen: „كراهة تحريم“ / „مكروه تحريما“ als „streng verpönt, dem Verbot nahe“ (Karāhat taḥrīm / Makrūh taḥrīman; eigene Kategorie unterhalb von ḥarām),
  „المفتى به“ als „Ansicht, nach der die Fatwā gegeben wird“, „الصاحبين“ als „die beiden Schüler [Abū Ḥanīfas]“.
