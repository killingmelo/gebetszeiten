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

---

## Ergänzung 05.11.2026: Rakʿa, Makrūh-Regeln, Sunna-Arten, Glossar

Vom Herausgeber am 05.11.2026 freigegeben. Alle Zitate aus Werkzeug-Ergebnissen
(`shamela_get_page`), Matn (über `ــ`) und Kommentar getrennt; Fußnoten nicht zitiert.
Die Farḍ-Zahlen (2, 4, 4, 3, 4; Ǧumʿa 2) stehen ohne Zitat (Entscheidung des Herausgebers:
unstrittig, in keinem hanafitischen Werk der Bibliothek als eigener Satz gefunden).

| book_id | page_id | Druckseite | Stelle | in der App |
|---|---|---|---|---|
| 412 | 84 bis 85 | S. 75 f. | Kommentar (wiederholt den Matn in Anführung) | Šurūq, „Was in den Makrūh-Zeiten gilt“ 1 |
| 412 | 85 | S. 76 | Matn | Šurūq, Makrūh 2 (Nafila, Karāhat at-taḥrīm) und 3 (nach Faǧr und ʿAṣr) |
| 412 | 85 | S. 76 | Kommentar | Vergilben, „Das ʿAṣr desselben Tages“ |
| 412 | 169 bis 170 | S. 145 f. | Matn | Rakʿa: Sunna muʾakkada (Faǧr, Ẓuhr, Maġrib, ʿIšāʾ) |
| 412 | 169 | S. 145 | Kommentar | Ẓuhr: „zwei hinzuzufügen, so dass es vier werden“ |
| 412 | 170 | S. 146 | Matn | Rakʿa: empfohlen (ʿAṣr, Maġrib, ʿIšāʾ) |
| 412 | 170 | S. 146 | Kommentar | ʿAṣr: Wahl zwischen zwei und vier (al-Qudūrī) |
| 412 | 171 | S. 147 | Kommentar | Auwābīn: zusätzlich oder darin enthalten |
| 412 | 163 | S. 139 | Matn | ʿIšāʾ: Witr wāǧib, drei Rakʿa |
| 412 | 27 | S. 31 | Kommentar | Faǧr: Unterschied muʾakkada und mandūb |
| 21613 | 99 | Bd. 1, S. 104 | Radd al-muḥtār (Ibn ʿĀbidīn), Ḥalabī-Druck | Faǧr: Sünde beim Auslassen, „ohne Entschuldigung, beharrlich“ |
| 21613 | 118 | Bd. 1, S. 123 | Radd al-muḥtār, nach al-Imdād | Faǧr: empfohlene Sunna, kein Tadel |

Wortlaut der Matn-Stelle S. 76 im Druck: „كما ح عصر اليوم“ (Satzfehler); zitiert wird deshalb die
Kommentarfassung „"كما صح عصر اليوم" بأدائه …“ derselben Seite.

Zur Zählfrage (S. 147): Ibn al-Humām wählte in Fatḥ al-qadīr die Ansicht „darin enthalten“
(laut aṭ-Ṭaḥṭāwī, Ḥāšiya, book_id 11993, page_id 420, S. 391); in der App steht nur Marāqī.
Für die vier empfohlenen nach ʿIšāʾ sagt Marāqī nichts zur Zählung; die Kachel nennt sie ohne Summe.

### Glossar: Koran und Ḥadīṯ (hadith-unlocked)

| Begriff | Stelle | Grad laut Werkzeug |
|---|---|---|
| Faǧr, Ẓuhr | Koran 17:78 | |
| Auwābīn | Koran 17:25; Muslim 748a | ṣaḥīḥ (Muslim) |
| Tahaǧǧud | Koran 17:79 | |
| Šurūq, Zawāl | Muslim 612d | ṣaḥīḥ (Muslim) |
| Ḍuḥā | Muslim 720 | ṣaḥīḥ (Muslim) |
| Isfār und Taġlīs | Tirmiḏī 154 | ḥasan ṣaḥīḥ (at-Tirmiḏī); Ibn ʿAbd al-Barr: „nicht stark“ |
| Ǧamāʿa | Buḫārī 645 | muttafaq ʿalaih |
| Witr | Abū Dāwūd 1416 | ṣaḥīḥ (al-Albānī), ḥasan (at-Tirmiḏī) |
| Farḍ, Nafl | Buḫārī 6502 | ṣaḥīḥ |

Wortsinn und Herkunft der Begriffe stehen ohne Quelle (Entscheidung vom 05.11.2026: reine
Wortbedeutungen ja, Regeln nur als Zitat).

### Diyanet und ʿAṣr

Hinweis im ʿAṣr-Reiter: Diyanet setzt ʿAṣr nach „asr-ı evvel“ (einfacher Schatten, Abū Yūsuf
und Muḥammad). Belegt über Sekundärquellen, die die Antwort des Fetva-Kurulu wiedergeben
(sorularlaislamiyet.com/node/91604, islamveihsan.com „Asr-ı Evvel ve Asr-ı Sani“); die Seite
kurul.diyanet.gov.tr selbst war nicht abrufbar. Zu ʿIšāʾ (roter oder weißer Šafaq) wurde keine
verlässliche Aussage gefunden; die App sagt dazu nichts.

---

## 06.11.2026 (Verstehen neu)

Gehört zu `app/src/main/kotlin/de/gebetszeiten/ui/verstehen/LehrInhalt.kt` (neue Seite „Verstehen“,
sechs Kapitel). Vom Herausgeber am 06.11.2026 freigegeben (Entwürfe in
`.superpowers/sdd/2026-11-06-verstehen-neu/`). Alle arabischen Texte stammen aus Ergebnissen von
`shamela_get_page` (Feld `body`, nie `foot`); Stellen mit den Sonder-Ligaturen ﵀ ﵁ ﵇ ﵊ sind so
geschnitten, dass keine davon im Zitat steht. Die Beleg-IDs gelten je Kapitel; dieselbe ID kann in
zwei Kapiteln stehen (Šafaq-Belege bei Maġrib und ʿIšāʾ).

Hier stehen nur die neuen und die wieder aufgenommenen Stellen. Alle übrigen Belege der Lehre sind
Zitate, die schon oben stehen (Abschnitte FAJR bis JAMAA und „Ergänzung 05.11.2026“), unverändert
übernommen.

Quellenangabe für den Matn von ad-Durr al-muḫtār (Entscheidung vom 06.11.2026): „al-Ḥaṣkafī
(gest. 1088 n. H.), ad-Durr al-muḫtār, gedruckt mit Radd al-muḥtār“ mit Band und Seite dieses Drucks
(Konstante `DURR`). Die Worte Ibn ʿĀbidīns (unter `ــ`) stehen weiter unter „Radd al-muḥtār“.

### Zitierausgaben der neuen Seiten (`shamela_get_citation`, alle `auto_numbered: false`)

| book_id | page_id | `formatted` | Quellenangabe in der App |
|---|---|---|---|
| 412 | 81 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٣. | …, S. 73 |
| 412 | 86 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ٧٧. | …, S. 77 |
| 412 | 163 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٣٩. | …, S. 139 |
| 412 | 179 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٥٣. | …, S. 153 |
| 412 | 208 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٧٥. | …, S. 175 |
| 412 | 231 | الشرنبلالي (ت ١٠٦٩هـ). مراقي الفلاح شرح نور الإيضاح. ١٠٦٩هـ، ص ١٩٤. | …, S. 194 |
| 124336 | 22 | القدوري (ت ٤٢٨هـ). مختصر القدوري. ٤٢٨هـ، ص ٣٢. | al-Qudūrī …, S. 32 |
| 21613 | 356 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ١، ص ٣٦١. | Ibn ʿĀbidīn …, Radd al-muḥtār, Bd. 1, S. 361 |
| 21613 | 366 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ١، ص ٣٧١. | …, Bd. 1, S. 371 |
| 21613 | 678 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ٢، ص ٢٢. | al-Ḥaṣkafī …, ad-Durr al-muḫtār, Bd. 2, S. 22 |
| 21613 | 679 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ٢، ص ٢٣. | ad-Durr al-muḫtār bzw. Radd al-muḥtār, Bd. 2, S. 23 |
| 21613 | 680 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ٢، ص ٢٤. | Radd al-muḥtār, Bd. 2, S. 24 |
| 21613 | 681 | ابن عابدين (ت ١٢٥٢هـ). حاشية ابن عابدين = رد المحتار - ط الحلبي. ١٢٥٢هـ، ج ٢، ص ٢٥. | ad-Durr al-muḫtār bzw. Radd al-muḥtār, Bd. 2, S. 25 |

Für 412/86, 208 und 231 halten die Entwürfe die Zitierangabe gekürzt fest („… ص ٧٧.“ usw.); der
Anfang ist derselbe wie bei den übrigen Seiten von book_id 412.

Die Klammer nach page_id nennt die Quellenangabe, wie sie in der App steht.

### Neue Stellen

**Faǧr, `fajr-sadiq`.** book_id 412, page_id 80 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 72), Marāqī l-falāḥ, Kommentar (unter `ــ`)

> "الصادق" وهو الذي يطلع عرضا منتشرا والكاذب يظهر طولا ثم يغيب وقد أجمعت الأمة على أن أوله الصبح الصادق

Übersetzung: Der echte [Faǧr] ist der, der in der Breite aufgeht und sich ausbreitet; der falsche zeigt sich längs und verschwindet dann. Die Gemeinschaft der Muslime ist sich einig, dass ihr [der Zeit des Morgengebets] Anfang der echte Morgen ist.

**Faǧr, `fajr-aakad`.** book_id 412, page_id 179 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 153), Grundtext Nūr al-īḍāḥ (über `ــ`)

> وعن أبي حنيفة رحمه الله تعالى أنه ينزل لسنة الفجر لأنها آكد من غيرها

Übersetzung: Von Abū Ḥanīfa, Gott erbarme sich seiner, wird überliefert, dass er [der Reitende] für die Sunna des Faǧr absteigt, weil sie stärker bekräftigt ist als die anderen.

**Faǧr, `fajr-iqama`.** book_id 412, page_id 86 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 77), Marāqī l-falāḥ, Kommentar

> "و" يكره "عند الإقامة" لكل فريضة "إلا سنة الفجر" إذا أمن فوت الجماعة

Übersetzung: Und verpönt ist es [das freiwillige Gebet] bei der Iqāma jedes Pflichtgebets, außer der Sunna des Faǧr, wenn man sicher ist, die Gemeinschaft nicht zu verpassen.

**Faǧr, `fajr-gamaa-vorrang`.** book_id 412, page_id 208 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 175), Marāqī l-falāḥ, Kommentar

> "وإن لم يأمن" فوت الإمام باشتغاله بسنة الفجر "تركها" واقتدى لأن ثواب الجماعة أعظم من فضيلة ركعتي الفجر

Übersetzung: Ist er nicht sicher, den Imām nicht zu verpassen, wenn er sich mit der Sunna des Faǧr beschäftigt, lässt er sie und folgt dem Imām, denn der Lohn der Gemeinschaft ist größer als der Vorzug der zwei Rakʿa des Faǧr.

**Faǧr, `fajr-qada`.** book_id 412, page_id 208 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 175), Marāqī l-falāḥ, Kommentar (wiederholt den Grundtext in Anführung)

> "ولم تقض سنة الفجر إلا بفوتها مع الفرض" إلى الزوال

Übersetzung: Die Sunna des Faǧr wird nur nachgeholt, wenn sie zusammen mit dem Farḍ verpasst wurde, [und zwar] bis zum Zawāl.

**Ẓuhr, `zuhr-qada`.** book_id 412, page_id 208 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 175), Grundtext Nūr al-īḍāḥ (über `ــ`)

> وقضى السنة التي قبل الظهر في وقته قبل شفعه

Übersetzung: Die Sunna vor dem Ẓuhr holt er in dessen Zeit nach, vor ihren zwei [Rakʿa nach dem Farḍ].

**Ẓuhr, `zuhr-ibrad`.** book_id 412, page_id 82 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 74), Grundtext Nūr al-īḍāḥ; der Anfang ist das vorhandene Isfār-Zitat, ohne Auslassung weitergeführt

> ويستحب الإسفار بالفجر للرجال والإبراد بالظهر في الصيف وتعجيله في الشتاء

Übersetzung: Empfohlen ist für Männer, den Faǧr bei Helligkeit zu beten, den Ẓuhr im Sommer in die Kühle zu schieben und ihn im Winter früh zu beten.

**Ẓuhr, `zuhr-gumua-zeit`.** book_id 412, page_id 231 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 194), Marāqī l-falāḥ, Kommentar

> "و" الثالث "وقت الظهر" لقوله ﷺ "إذا مالت الشمس فصل بالناس الجمعة" "فلا تصح" الجمعة "قبله وتبطل بخروجه" لفوات الشرط

Übersetzung: Die dritte [Bedingung der Ǧumʿa] ist die Zeit des Ẓuhr, wegen seines Wortes ﷺ: „Wenn die Sonne sich neigt, bete mit den Leuten die Ǧumʿa.“ Vor ihr ist die Ǧumʿa nicht gültig, und mit ihrem Ende wird sie ungültig, weil die Bedingung fehlt.

**ʿAṣr, `asr-taakhir`.** book_id 412, page_id 82 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 74), Marāqī l-falāḥ, Kommentar; endet vor „لأنه“ mit Sonder-Ligatur

> "و" يستحب "تأخير" صلاة "العصر" صيفا وشتاء

Übersetzung: Und empfohlen ist, das ʿAṣr-Gebet aufzuschieben, im Sommer wie im Winter.

**Maġrib, `shafaq-fatwa`.** book_id 412, page_id 80 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 72), Marāqī l-falāḥ, Kommentar (unter `ــ`); auch im Kapitel ʿIšāʾ

> وهو رواية عن الإمام وعليها الفتوى وبها قالا لقول ابن عمر: الشفق الحمرة وهو مروي عن أكثر الصحابة وعليه إطباق أهل اللسان ونقل رجوع الإمام إليه

Übersetzung: [Das Rot] ist eine Überlieferung vom Imam [Abū Ḥanīfa]; nach ihr wird die Fatwā gegeben, und die beiden [Abū Yūsuf und Muḥammad] vertreten sie, wegen des Wortes Ibn ʿUmars: Der Šafaq ist das Rot. Das ist von den meisten Gefährten überliefert, darin stimmen die Sprachkundigen überein, und es wird berichtet, dass der Imam sich dieser Ansicht zugewandt hat.

**Maġrib, `shafaq-amal`.** book_id 21613, page_id 356 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 361), Radd al-muḥtār, Worte Ibn ʿĀbidīns (unter `ــ`)

> لَكِنَّ تَعَامُلَ النَّاسِ الْيَوْمَ فِي عَامَّةِ الْبِلَادِ عَلَى قَوْلِهِمَا

Übersetzung: Aber die Menschen handeln heute in den meisten Ländern nach der Ansicht der beiden.

**Maġrib, `shafaq-ahwat`.** book_id 21613, page_id 356 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 361), Radd al-muḥtār (unter `ــ`), derselbe Absatz; auch im Kapitel ʿIšāʾ

> وَفِي السِّرَاجِ: قَوْلُهُمَا أَوْسَعُ وَقَوْلُهُ أَحْوَطُ

Übersetzung: Im Sirāǧ heißt es: Ihre Ansicht ist weiter, seine ist vorsichtiger.

**Maġrib, `maghrib-frueh`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Marāqī l-falāḥ, Kommentar

> "و" يستحب "تعجيل" صلاة "المغرب" صيفا وشتاء ولا يفصل بين الأذان والإقامة فيه إلا بقدر ثلاث آيات أو جلسة خفيفة

Übersetzung: Empfohlen ist, das Maġrib früh zu beten, im Sommer wie im Winter. Zwischen Aḏān und Iqāma lässt man dabei nur so viel Zeit wie für drei Verse oder ein kurzes Sitzen.

**Maġrib, `maghrib-aufschub`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Marāqī l-falāḥ, Kommentar, derselbe Absatz

> فكان تأخيرها مكروها "إلا في يوم غيم" وإلا من عذر سفر أو مرض وحضور مائدة والتأخير قليلا لا يكره

Übersetzung: Ihr Aufschieben ist also verpönt, außer an einem bewölkten Tag oder aus einem Grund wie Reise, Krankheit oder einem bereitstehenden Essen. Ein wenig aufzuschieben ist nicht verpönt.

**ʿIšāʾ, `isha-drittel`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Marāqī l-falāḥ, Kommentar

> "و" يستحب "تأخير" صلاة "العشاء إلى ثلث الليل" الأول في رواية الكنز وفي القدوري إلى ما قبل الثلث

Übersetzung: Empfohlen ist, das ʿIšāʾ bis zum ersten Drittel der Nacht aufzuschieben, nach der Überlieferung im Kanz; bei al-Qudūrī bis vor das Drittel.

**ʿIšāʾ, `isha-haelfte`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Marāqī l-falāḥ, Kommentar, derselbe Absatz

> وفي مجمع الروايات التأخير إلى النصف مباح في الشتاء

Übersetzung: In Maǧmaʿ ar-riwāyāt heißt es: Das Aufschieben bis zur Mitte [der Nacht] ist im Winter erlaubt.

**ʿIšāʾ, `isha-nach-haelfte`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Marāqī l-falāḥ, Kommentar, derselbe Absatz

> والتأخير إلى ما بعد النصف مكروه لسلامة دليل الكراهة عن المعارض والكراهة تحريمية

Übersetzung: Das Aufschieben über die Mitte hinaus ist verpönt, weil der Grund für die Verpöntheit ohne Gegengrund bleibt; und zwar ist es stark verpönt (Karāhat at-taḥrīm).

**ʿIšāʾ, `witr-ansichten`.** book_id 412, page_id 163 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 139), Marāqī l-falāḥ, Kommentar

> وصفه بقوله "الوتر واجب" في الأصح وهو آخر أقوال الإمام وروى عنه أنه سنة وهو قولهما

Übersetzung: Er beschreibt ihn mit den Worten „Der Witr ist wāǧib“, nach der richtigeren Ansicht; das ist das letzte Wort des Imams. Von ihm ist auch überliefert, er sei Sunna, und das ist die Ansicht der beiden.

**ʿIšāʾ, `witr-zeit`.** book_id 124336, page_id 13 (al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī, S. 23), Matn al-Qudūrī

> وأول وقت الوتر بعد العشاء وآخر وقتها ما لم يطلع الفجر

Übersetzung: Die Zeit des Witr beginnt nach dem ʿIšāʾ und endet, wenn der Faǧr aufgeht.

**ʿIšāʾ, `witr-tartib`.** book_id 412, page_id 81 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 73), Marāqī l-falāḥ, Kommentar; „لهذا الحديث“ meint den unmittelbar davor zitierten Ḥadīṯ über den Witr

> "ولا يقدم" صلاة "الوتر على" صلاة "العشاء" لهذا الحديث و "للترتيب اللازم" بين فرض العشاء وواجب الوتر عند الإمام

Übersetzung: Der Witr wird nicht vor das ʿIšāʾ gezogen, wegen dieses Ḥadīṯ und wegen der verbindlichen Reihenfolge zwischen dem Farḍ des ʿIšāʾ und dem Wāǧib des Witr nach dem Imam.

**ʿIšāʾ, `witr-spaet`.** book_id 412, page_id 83 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 75), Grundtext Nūr al-īḍāḥ (über `ــ`), abhängig von „ويستحب“ auf S. 74

> وتأخير الوتر الى آخر الليل لمن يثق بالانتباه

Übersetzung: [Empfohlen ist,] den Witr bis zum Ende der Nacht aufzuschieben, für den, der sicher ist, dass er aufwacht.

**Freiwillig und verpönt, `fajr-bei-aufgang`.** book_id 412, page_id 85 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 76), Marāqī l-falāḥ, Kommentar (unter `ــ`)

> وإذا أشرقت الشمس وهو في صلاة الفجر بطلت

Übersetzung: Geht die Sonne auf, während er noch im Faǧr-Gebet ist, so ist es ungültig.

**Freiwillig und verpönt, `shuruq-speer`.** book_id 21613, page_id 366 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 371), Radd al-muḥtār, Worte Ibn ʿĀbidīns nach „أَقُولُ“

> يَنْبَغِي تَصْحِيحُ مَا نَقَلُوهُ عَنْ الْأَصْلِ لِلْإِمَامِ مُحَمَّدٍ مِنْ أَنَّهُ مَا لَمْ تَرْتَفِعْ الشَّمْسُ قَدْرَ رُمْحٍ فَهِيَ فِي حُكْمِ الطُّلُوعِ

Übersetzung: Für richtig zu halten ist, was sie aus dem Aṣl von Imām Muḥammad überliefern: Solange die Sonne nicht eine Speerlänge gestiegen ist, gilt sie als aufgehend.

**Freiwillig und verpönt, `shuruq-blick`.** book_id 21613, page_id 366 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 371 (nach al-Baḥr)), Radd al-muḥtār, Ibn ʿĀbidīn gibt al-Baḥr wieder

> وَمَا دَامَتْ الْعَيْنُ لَا تَحَارُ فِيهَا فِي حُكْمِ الشُّرُوقِ

Übersetzung: Solange das Auge nicht geblendet wird, wenn es in sie blickt, gilt sie als aufgehend.

**Freiwillig und verpönt, `pflicht-mit-karaha`.** book_id 412, page_id 85 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 76), Marāqī l-falāḥ, Kommentar

> "ويصح أداء ما وجب فيها" أي الأوقات الثلاثة لكن "مع الكراهة" في ظاهر الرواية "كجنازة حضرت وسجدة آية تليت فيها"

Übersetzung: Gültig ist die Verrichtung dessen, was in ihnen, das heißt in den drei Zeiten, fällig wurde, aber verpönt, nach der Ẓāhir ar-riwāya: etwa ein Totengebet, das [in der Zeit] gebracht wurde, und eine Niederwerfung für einen Vers, der in ihr gelesen wurde.

**Freiwillig und verpönt, `qada-nach-fajr-asr`.** book_id 124336, page_id 22 (al-Qudūrī (gest. 428 n. H.), Muḫtaṣar al-Qudūrī, S. 32), Matn al-Qudūrī; danach folgt „ولا يصلي ركعتي الطواف“, nicht zitiert

> ويكره أن يتنفل بعد صلاة الفجر حتى تطلع الشمس وبعد صلاة العصر حتى تغرب الشمس ولا بأس بأن يصلي في هذين الوقتين الفوائت ويسجد للتلاوة ويصلي على الجنازة

Übersetzung: Verpönt ist es, nach dem Faǧr-Gebet freiwillig zu beten, bis die Sonne aufgeht, und nach dem ʿAṣr-Gebet, bis die Sonne untergeht. Es ist aber nichts dabei, in diesen beiden Zeiten versäumte Gebete nachzuholen, die Niederwerfung für die Lesung zu machen und das Totengebet zu verrichten.

**Freiwillig und verpönt, `duha-beste-zeit`.** book_id 21613, page_id 678 (al-Ḥaṣkafī (gest. 1088 n. H.), ad-Durr al-muḫtār, gedruckt mit Radd al-muḥtār, Bd. 2, S. 22), Matn ad-Durr al-muḫtār (über `ــ`) im Druck des Radd; runde Klammern so im Werkzeug-Ergebnis (Worte des Tanwīr al-abṣār)

> (وَ) نُدِبَ (أَرْبَعٌ فَصَاعِدًا فِي الضُّحَى) عَلَى الصَّحِيحِ مِنْ بَعْدِ الطُّلُوعِ إلَى الزَّوَالِ وَوَقْتُهَا الْمُخْتَارُ بَعْدَ رُبُعِ النَّهَارِ

Übersetzung: Empfohlen sind vier oder mehr im Ḍuḥā, nach der richtigen Ansicht von nach dem Sonnenaufgang bis zum Zawāl; die gewählte Zeit ist nach einem Viertel des Tages.

**Freiwillig und verpönt, `duha-spanne`.** book_id 21613, page_id 679 (al-Ḥaṣkafī (gest. 1088 n. H.), ad-Durr al-muḫtār, gedruckt mit Radd al-muḥtār, Bd. 2, S. 23), Matn ad-Durr al-muḫtār (über `ــ`); „اثْنَيْ عَشَرَ“ so im Werkzeug-Ergebnis

> وَفِي الْمُنْيَةِ: أَقَلُّهَا رَكْعَتَانِ وَأَكْثَرُهَا اثْنَيْ عَشَرَ، وَأَوْسَطُهَا ثَمَانٍ وَهُوَ أَفْضَلُهَا

Übersetzung: In al-Munya heißt es: Das Mindeste sind zwei Rakʿa, das Meiste zwölf, die Mitte acht, und das ist das Beste.

**Freiwillig und verpönt, `duha-vier-kamal`.** book_id 21613, page_id 679 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 2, S. 23), Radd al-muḥtār, Kommentar

> وَالتَّوْفِيقُ مَا أَشَارَ إلَيْهِ بَعْضُ الْمُحَقِّقِينَ أَنَّ الرَّكْعَتَيْنِ أَقَلُّ الْمَرَاتِبِ وَالْأَرْبَعُ أَدْنَى الْكَمَالِ

Übersetzung: Die Vereinbarung [beider Angaben] ist, worauf einige der Gründlichen hinwiesen: Zwei Rakʿa sind die unterste Stufe, vier das Mindeste der Vollkommenheit.

**Freiwillig und verpönt, `zawal-istiwa`.** book_id 21613, page_id 366 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 371 (nach al-Baḥr)), Radd al-muḥtār, Kommentar zu „وَاسْتِوَاءٍ“, nach al-Baḥr

> التَّعْبِيرُ بِهِ أَوْلَى مِنْ التَّعْبِيرِ بِوَقْتِ الزَّوَالِ؛ لِأَنَّ وَقْتَ الزَّوَالِ لَا تُكْرَهُ فِيهِ الصَّلَاةُ إجْمَاعًا

Übersetzung: Es so [„beim Höchststand“] auszudrücken ist besser als „zur Zeit des Zawāl“, denn zur Zeit des Zawāl ist das Gebet nach einhelliger Ansicht nicht verpönt.

**Freiwillig und verpönt, `zawal-dauer`.** book_id 21613, page_id 366 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 1, S. 371), Radd al-muḥtār, Kommentar; „مَا رَوَاهُ النَّهْرُ“ und „جُوَارِزْمَ“ so im Werkzeug-Ergebnis (gemeint: ما وراء النهر, خوارزم)

> وَعَزَا فِي الْقُهُسْتَانِيِّ الْقَوْلَ بِأَنَّ الْمُرَادَ انْتِصَافُ النَّهَارِ الْعُرْفِيِّ إلَى أَئِمَّةِ مَا رَوَاهُ النَّهْرُ، وَبِأَنَّ الْمُرَادَ انْتِصَافُ النَّهَارِ الشَّرْعِيِّ وَهُوَ الضَّحْوَةُ الْكُبْرَى إلَى الزَّوَالِ إلَى أَئِمَّةِ جُوَارِزْمَ

Übersetzung: Al-Quhistānī schreibt die Ansicht, gemeint sei die Mitte des gewöhnlichen Tages, den Gelehrten Transoxaniens zu, und die Ansicht, gemeint sei die Mitte des Tages im Fiqh-Sinn, das ist die große Ḍaḥwa bis zum Zawāl, den Gelehrten Ḫwārizms.

**Freiwillig und verpönt, `zawal-jumua`.** book_id 412, page_id 85 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 76), Marāqī l-falāḥ, Kommentar

> وقال أبو يوسف لا تكره النافلة حال الاستواء يوم الجمعة

Übersetzung: Abū Yūsuf sagte: Das freiwillige Gebet ist beim Höchststand am Freitag nicht verpönt.

**Freiwillig und verpönt, `asr-des-tages`.** book_id 412, page_id 85 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 76), Marāqī l-falāḥ, Kommentar; um „بخلاف … ناقص“ verlängert (ohne Auslassung). Die kurze Fassung (Kapitel ʿAṣr, `asr-gleichen-tages`) bleibt

> "كما صح عصر اليوم" بأدائه "عند الغروب" لبقاء سببه وهو الجزء المتصل به الأداء من الوقت "مع الكراهة" للتأخير المنهي عنه لا لذات الوقت بخلاف عصر مضى للزومه كاملا بخروج وقته فلا يؤدى في ناقص

Übersetzung: So wie das ʿAṣr desselben Tages gültig ist, wenn man es beim Sonnenuntergang verrichtet, weil sein Anlass noch besteht, nämlich der Teil der Zeit, an den die Verrichtung anschließt; aber verpönt, wegen des verbotenen Aufschiebens, nicht wegen der Zeit selbst. Anders ein ʿAṣr eines vergangenen Tages: Mit dem Ende seiner Zeit ist es vollständig geschuldet und wird deshalb nicht in einer unvollständigen [Zeit] verrichtet.

**Freiwillig und verpönt, `tahajjud-nach-isha`.** book_id 21613, page_id 680 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 2, S. 24), Radd al-muḥtār, Worte Ibn ʿĀbidīns nach „أَقُولُ“

> الظَّاهِرُ أَنَّ حَدِيثَ الطَّبَرَانِيِّ الْأَوَّلَ بَيَانٌ لِكَوْنِ وَقْتِهِ بَعْدَ صَلَاةِ الْعِشَاءِ، حَتَّى لَوْ نَامَ ثُمَّ تَطَوَّعَ قَبْلَهَا لَا يَحْصُلُ السُّنَّةُ

Übersetzung: Dem Anschein nach erklärt der erste Ḥadīṯ bei aṭ-Ṭabarānī, dass seine Zeit nach dem ʿIšāʾ-Gebet ist; wer also schläft und dann vor ihm freiwillig betet, erfüllt die Sunna nicht.

**Freiwillig und verpönt, `tahajjud-zahl`.** book_id 21613, page_id 681 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 2, S. 25), Radd al-muḥtār, nach „أَقُولُ“

> فَيَنْبَغِي الْقَوْلُ بِأَنَّ أَقَلَّ التَّهَجُّدِ رَكْعَتَانِ وَأَوْسَطُهُ أَرْبَعٌ وَأَكْثَرُهُ ثَمَانٍ

Übersetzung: Zu sagen ist also: Das Mindeste des Tahaǧǧud sind zwei Rakʿa, seine Mitte vier und sein Höchstes acht.

**Freiwillig und verpönt, `tahajjud-gewohnheit`.** book_id 21613, page_id 681 (Ibn ʿĀbidīn (gest. 1252 n. H.), Radd al-muḥtār, Bd. 2, S. 25 (nach al-Ḥilya)), Radd al-muḥtār, Ibn ʿĀbidīn gibt al-Ḥilya wieder

> أَنَّهُ يُكْرَهُ تَرْكُ تَهَجُّدٍ اعْتَادَهُ بِلَا عُذْرٍ

Übersetzung: Verpönt ist es, ein Tahaǧǧud, an das man sich gewöhnt hat, ohne Entschuldigung aufzugeben.

**Freiwillig und verpönt, `tahajjud-drittel`.** book_id 21613, page_id 681 (al-Ḥaṣkafī (gest. 1088 n. H.), ad-Durr al-muḫtār, gedruckt mit Radd al-muḥtār, Bd. 2, S. 25), Matn ad-Durr al-muḫtār (über `ــ`)

> وَلَوْ جَعَلَهُ أَثْلَاثًا فَالْأَوْسَطُ أَفْضَلُ، وَلَوْ أَنْصَافًا فَالْأَخِيرُ أَفْضَلُ

Übersetzung: Teilt er sie [die Nacht] in Drittel, ist das mittlere am vorzüglichsten; teilt er sie in Hälften, die letzte.

**Freiwillig und verpönt, `nacht-paarweise`.** book_id 412, page_id 172 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 148), Grundtext Nūr al-īḍāḥ (über `ــ`)

> والأفضل فيهما رباع عند أبي حنيفة وعندهما الأفضل في الليل مثنى مثنى وبه يفتى

Übersetzung: Am vorzüglichsten ist in beiden [Nacht und Tag] je vier nach Abū Ḥanīfa; nach den beiden [Abū Yūsuf und Muḥammad] ist in der Nacht je zwei am vorzüglichsten, und danach wird die Fatwā gegeben.

**ʿAṣr, `asr-aufschub`.** book_id 412, page_id 82 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 74), Marāqī l-falāḥ, Kommentar; Belegdatei ISFIRAR_MAKRUH 2 (`asr-verzoegern`), am 06.11.2026 erneut verglichen

> "ما لم تتغير الشمس" بذهاب ضوئها فلا يتحير فيه البصر وهو الصحيح والتأخير إلى التغير مكروه تحريما

Übersetzung: Solange die Sonne sich nicht verändert, das heißt ihr Glanz nicht vergangen ist, so dass der Blick von ihr nicht mehr geblendet wird; das ist die richtige Ansicht. Das Aufschieben bis zu dieser Veränderung ist stark verpönt (makrūh taḥrīman).

### Wieder aufgenommen (am 28.10.2026 aus der App genommen)

**ʿAṣr, `asr-zeit`.** book_id 412, page_id 80 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 72), Grundtext; Belegdatei ASR 2, am 06.11.2026 gegen 412/80 geprüft

> ووقت الظهر من زوال الشمس الى أن يصير ظل كل شيء مثليه أو مثله سوى ظل الاستواء واختار الثاني الطحاوي وهو قول الصاحبين ووقت العصر من ابتداء الزيادة على المثل أو المثلين الى غروب الشمس

Übersetzung: Die Zeit des Ẓuhr reicht vom Zawāl der Sonne, bis der Schatten jedes Dinges doppelt oder einfach so lang ist wie das Ding, ohne den Schatten beim Höchststand. Das Zweite hat aṭ-Ṭaḥāwī gewählt, und es ist die Ansicht der beiden Schüler [Abū Ḥanīfas: Abū Yūsuf und Muḥammad]. Die Zeit des ʿAṣr reicht vom Beginn des Überschreitens des einfachen oder doppelten Schattens bis zum Sonnenuntergang.

**Maġrib, `shafaq-imam`.** book_id 11820, page_id 46 (al-Marġīnānī (gest. 593 n. H.), al-Hidāya, Bd. 1, S. 40), al-Hidāya; Belegdatei ISHA 2; auch im Kapitel ʿIšāʾ

> "ثم الشفق هو البياض الذي في الأفق بعد الحمرة عند أبي حنيفة رحمه الله تعالى وعندهما هو الحمرة" وهو رواية عن أبي حنيفة

Übersetzung: Der Šafaq ist nach Abū Ḥanīfa, Gott erbarme sich seiner, die Helligkeit am Horizont nach dem Rot. Nach den beiden [Abū Yūsuf und Muḥammad] ist es das Rot, und das ist auch eine Überlieferung von Abū Ḥanīfa.

**Freiwillig und verpönt, `duha-vier`.** book_id 412, page_id 174 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 149), Grundtext Nūr al-īḍāḥ; Belegdatei DUHA 1, am 06.11.2026 erneut geprüft

> وندب ركعتان بعد الوضوء قبل جفافه وأربع فصاعدا في الضحى

Übersetzung: Empfohlen sind zwei Rakʿa nach der Waschung, bevor sie trocknet, und vier oder mehr in der Ḍuḥā-Zeit.

**Freiwillig und verpönt, `asr-verzoegern`.** book_id 412, page_id 82 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 74), Marāqī l-falāḥ, Kommentar; Belegdatei ISFIRAR_MAKRUH 2, am 06.11.2026 erneut verglichen

> "ما لم تتغير الشمس" بذهاب ضوئها فلا يتحير فيه البصر وهو الصحيح والتأخير إلى التغير مكروه تحريما

Übersetzung: Solange die Sonne sich nicht verändert, das heißt ihr Glanz nicht vergangen ist, so dass der Blick von ihr nicht mehr geblendet wird; das ist die richtige Ansicht. Das Aufschieben bis zu dieser Veränderung ist stark verpönt (makrūh taḥrīman).

**Freiwillig und verpönt, `tahajjud-acht`.** book_id 412, page_id 174 (aš-Šurunbulālī (gest. 1069 n. H.), Marāqī l-falāḥ, S. 149), Marāqī l-falāḥ, Kommentar; Belegdatei TAHAJJUD 1, am 06.11.2026 erneut geprüft

> "وندب صلاة الليل" خصوصا آخره كما ذكرناه وأقل ما ينبغي أن يتنفل بالليل ثمان ركعات كذا في الجوهرة

Übersetzung: Empfohlen ist das Nachtgebet, besonders am Ende der Nacht, wie wir erwähnt haben. Das Mindeste, was man nachts freiwillig beten sollte, sind acht Rakʿa. So steht es in al-Ǧauhara.

