package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der aufgeklappte Text der Dauerbenachrichtigung und ihr Untertitel.
 *
 * Warum als reine Funktionen geprueft: der Wortlaut ist das Versprechen an den
 * Nutzer („beim Herunterziehen immer Ort und genaue Zeit"), die
 * `NotificationCompat`-Verdrahtung darum herum ist ohne Geraet nicht pruefbar.
 *
 * Die Bausteine hier sind genau die, die `updateOngoing` schon heute in die
 * eingeklappte Inhaltszeile schreibt (`ongoing_at`, `ongoing_since`,
 * `ongoing_until`, `KarahaLine.text`) — hier vom Aufrufer bereits aufgeloest,
 * damit kein Android-Context noetig ist.
 */
class OngoingTextTest {

    // So, wie die vier Aufrufstellen sie aus den Ressourcen aufloesen.
    private val umZeit = "um 22:48"
    private val aktuell = "aktuell: Asr"
    private val bis = "bis 06:12"
    private val karaha = "⚠️ Karaha bis 13:21"

    @Test fun `ohne laufendes Gebet bleibt nur die genaue Zeit`() {
        assertEquals("um 22:48", ongoingBigText(umZeit))
    }

    @Test fun `mit laufendem Gebet steht es unter der Zeit`() {
        assertEquals("um 22:48\naktuell: Asr", ongoingBigText(umZeit, activeLine = aktuell))
    }

    @Test fun `das Ende des laufenden Gebets kommt nach dem laufenden Gebet`() {
        assertEquals(
            "um 22:48\naktuell: Asr\nbis 06:12",
            ongoingBigText(umZeit, activeLine = aktuell, untilLine = bis),
        )
    }

    @Test fun `die Karaha-Zeile steht zuletzt`() {
        assertEquals(
            "um 22:48\naktuell: Asr\n⚠️ Karaha bis 13:21",
            ongoingBigText(umZeit, activeLine = aktuell, karahaText = karaha),
        )
    }

    @Test fun `ohne Karaha-Zeile entsteht keine leere Zeile`() {
        assertEquals("um 22:48\naktuell: Asr", ongoingBigText(umZeit, activeLine = aktuell, karahaText = null))
    }

    @Test fun `leere Bausteine erzeugen keine leeren Zeilen`() {
        // Ein leerer String ist kein `null`, darf aber genauso wenig eine
        // Zeile ergeben — sonst klafft im Aufgeklappten eine Luecke.
        assertEquals("um 22:48", ongoingBigText(umZeit, activeLine = "", untilLine = "   ", karahaText = ""))
    }

    @Test fun `der Ort wird zum Untertitel`() {
        assertEquals("Nürnberg", ongoingSubText("Nürnberg"))
    }

    @Test fun `ein leerer Ortsname ergibt keinen Untertitel`() {
        // Eine leere Kopfzeile sieht aus wie ein Fehler — lieber gar keine.
        assertNull(ongoingSubText(""))
        assertNull(ongoingSubText("   "))
        assertNull(ongoingSubText(null))
    }

    @Test fun `Leerzeichen um den Ortsnamen fallen weg`() {
        assertEquals("Nürnberg", ongoingSubText("  Nürnberg  "))
    }

    // --- Die Entscheidung, nicht nur der Wortlaut ----------------------------
    //
    // Frueher stand hier ein Test „die genaue Uhrzeit steht in BEIDEN
    // Countdown-Modi drin", der zweimal DENSELBEN Ausdruck aufrief und ihn mit
    // sich selbst verglich. Der Modus-Unterschied lag vollstaendig in
    // `PrayerNotifier` und wurde nirgends ausgefuehrt: die Uhrzeit aus dem
    // Aufgeklappten zu entfernen liess alle Tests gruen. Jetzt ist der Modus
    // ein Argument, und alle vier Texte kommen aus einer reinen Funktion.

    // So, wie `updateOngoing` die beiden Titel aus den Ressourcen aufloest.
    private val titelStufen = "Noch 20+ Min bis Isha"
    private val titelZeit = "Isha um 22:48"

    private fun texte(
        mode: OngoingMode,
        activeLine: String? = aktuell,
        untilLine: String? = null,
        karahaText: String? = null,
        city: String? = null,
    ) = ongoingTexts(
        mode = mode,
        titleWithStep = titelStufen,
        titleWithTime = titelZeit,
        timeLine = umZeit,
        activeLine = activeLine,
        untilLine = untilLine,
        karahaText = karahaText,
        city = city,
    )

    @Test fun `im Stufen-Modus traegt der Titel die Restzeit, NICHT die Uhrzeit`() {
        val texts = texte(OngoingMode.STEPS)
        assertEquals(titelStufen, texts.title)
        assertFalse(texts.title, texts.title.contains("22:48"))
    }

    @Test fun `im Stufen-Modus MUSS die Uhrzeit im aufgeklappten Text stehen`() {
        // Das ist die Kernzusage von Task 17: im Stufen-Modus ist die genaue
        // Uhrzeit aus dem Titel verdraengt — verschwindet sie auch aus dem
        // Aufgeklappten, steht sie nirgends mehr, und genau sie verlangt der
        // Nutzer. Dieser Test faellt, wenn sie dort wegfaellt.
        val texts = texte(OngoingMode.STEPS)
        assertTrue(texts.bigText, texts.bigText.contains("22:48"))
        assertEquals("um 22:48\naktuell: Asr", texts.bigText)
    }

    @Test fun `im Genau-Modus steht die Uhrzeit im Titel UND im Aufgeklappten`() {
        // Die Wiederholung ist bewusst: die Zusage „genaue Zeit im
        // Aufgeklappten" gilt in BEIDEN Modi, nicht nur dort, wo der Titel
        // sie nicht hat.
        val texts = texte(OngoingMode.EXACT)
        assertEquals(titelZeit, texts.title)
        assertTrue(texts.title, texts.title.contains("22:48"))
        assertTrue(texts.bigText, texts.bigText.contains("22:48"))
    }

    @Test fun `ist der Countdown aus, ist alles wie im Genau-Modus`() {
        // Ohne Countdown unterscheidet sich nur der Systemzaehler in der
        // Kopfzeile — kein Text.
        assertEquals(texte(OngoingMode.EXACT), texte(OngoingMode.PLAIN))
    }

    @Test fun `die eingeklappte Zeile ist in allen drei Faellen wie bisher`() {
        // Stufen: die Restzeit steht im Titel, also wird die Uhrzeit zum
        // Detail. Sonst traegt der Titel sie, und eine zweite Nennung direkt
        // darunter waere doppelt.
        val stufen = texte(OngoingMode.STEPS, untilLine = bis, karahaText = karaha)
        assertEquals("um 22:48 · aktuell: Asr · bis 06:12 · ⚠️ Karaha bis 13:21", stufen.contentText)
        listOf(OngoingMode.EXACT, OngoingMode.PLAIN).forEach { mode ->
            val texts = texte(mode, untilLine = bis, karahaText = karaha)
            assertEquals("aktuell: Asr · bis 06:12 · ⚠️ Karaha bis 13:21", texts.contentText)
        }
    }

    @Test fun `ohne Bausteine gibt es gar keine eingeklappte Zeile`() {
        // `null` und nicht "": der Builder soll die Zeile weglassen, nicht
        // eine leere setzen.
        assertNull(texte(OngoingMode.EXACT, activeLine = null).contentText)
        // Im Stufen-Modus bleibt die Uhrzeit uebrig — sie ist dort das Detail.
        assertEquals("um 22:48", texte(OngoingMode.STEPS, activeLine = null).contentText)
    }

    @Test fun `der Ort wird auch hier zum Untertitel, ein leerer nicht`() {
        assertEquals("Nürnberg", texte(OngoingMode.STEPS, city = "  Nürnberg ").subText)
        assertNull(texte(OngoingMode.STEPS, city = "   ").subText)
        assertNull(texte(OngoingMode.STEPS, city = null).subText)
    }

    @Test fun `der Modus folgt aus den beiden Schaltern und der Reststufe`() {
        assertEquals(OngoingMode.STEPS, ongoingMode(countdown = true, exact = false, stepShort = "20+ Min"))
        assertEquals(OngoingMode.EXACT, ongoingMode(countdown = true, exact = true, stepShort = ""))
        assertEquals(OngoingMode.PLAIN, ongoingMode(countdown = false, exact = false, stepShort = ""))
        // Ohne Countdown zaehlt der Genauigkeitsschalter nicht mit: es gibt
        // keinen Zaehler, den er genau machen koennte.
        assertEquals(OngoingMode.PLAIN, ongoingMode(countdown = false, exact = true, stepShort = ""))
    }

    @Test fun `in der letzten Minute gibt es keinen Stufen-Titel`() {
        // `remainingStepShort` ist in der letzten Minute leer — ein Titel
        // „Noch  bis Isha" waere kaputt. Schon heute faellt er dort auf den
        // klassischen zurueck; hier steht es als Fall, nicht als Zufall.
        assertEquals(OngoingMode.PLAIN, ongoingMode(countdown = true, exact = false, stepShort = ""))
    }

    // --- Die Klammer ueber allem: `ongoing(...)` ---------------------------

    private fun anzeige(minuten: Long, countdown: Boolean, exact: Boolean) = ongoing(
        remaining = java.time.Duration.ofMinutes(minuten),
        countdown = countdown,
        exact = exact,
        titleWithStep = { step -> "Noch $step bis Isha" },
        titleWithTime = "Isha um 22:48",
        timeLine = "um 22:48",
        activeLine = "aktuell: Maghrib",
        city = "Nuernberg",
    )

    @Test fun `ohne Countdown bleibt es der Mond und der klassische Titel`() {
        val a = anzeige(41, countdown = false, exact = false)
        assertEquals(CountdownGlyph.None, a.glyph)
        assertEquals(OngoingMode.PLAIN, a.mode)
        assertEquals("Isha um 22:48", a.texts.title)
    }

    @Test fun `in Stufen traegt der Titel die Stufe, das Symbol die abgerundete Minute`() {
        val a = anzeige(41, countdown = true, exact = false)
        assertEquals(CountdownGlyph.Minutes(40, approx = true), a.glyph)
        assertEquals(OngoingMode.STEPS, a.mode)
        assertEquals("Noch 40+ Min bis Isha", a.texts.title)
    }

    @Test fun `in Genau traegt der Titel die Uhrzeit, das Symbol bleibt dasselbe`() {
        val a = anzeige(41, countdown = true, exact = true)
        assertEquals(CountdownGlyph.Minutes(40, approx = true), a.glyph)
        assertEquals(OngoingMode.EXACT, a.mode)
        assertEquals("Isha um 22:48", a.texts.title)
    }

    @Test fun `Stufen und Genau zeigen IMMER dasselbe Symbol`() {
        // Die Zusage, auf der die Vorschau im Onboarding steht: sie zeichnet
        // die Statusleiste EINMAL und behauptet daneben, das Symbol sei in
        // beiden Modi gleich. Waere das falsch, versprsche sie etwas, das die
        // echte Anzeige nie zeigt.
        //
        // Vierzehn Stunden in Minutenschritten — ueber den Stundendeckel bei
        // neun hinaus, durch die Zehnminuten-Stufen und die letzten neun
        // Minuten einzeln.
        for (minuten in 0L..14L * 60L) {
            assertEquals(
                "Minute $minuten: Stufen und Genau zeigen verschiedene Symbole",
                anzeige(minuten, countdown = true, exact = false).glyph,
                anzeige(minuten, countdown = true, exact = true).glyph,
            )
        }
    }

    @Test fun `verschieden ist der Text, und zwar ab der ersten vollen Minute`() {
        // Gegenprobe zum Test darueber: gaebe es gar keinen Unterschied,
        // waere der Test oben auch dann gruen, wenn die beiden Modi
        // zusammengefallen waeren.
        for (minuten in 1L..14L * 60L) {
            assertNotEquals(
                "Minute $minuten: Stufen und Genau sind textlich nicht unterscheidbar",
                anzeige(minuten, countdown = true, exact = false).texts.title,
                anzeige(minuten, countdown = true, exact = true).texts.title,
            )
        }
    }

    @Test fun `in der letzten Minute fallen Stufen und Genau auch im Text zusammen`() {
        // Dort gibt es keine Stufe mehr; der Stufen-Modus faellt auf den
        // klassischen Titel zurueck. Die Vorschau darf hier keinen
        // Unterschied vorgaukeln.
        val stufen = anzeige(0, countdown = true, exact = false)
        assertEquals(OngoingMode.PLAIN, stufen.mode)
        assertEquals("Isha um 22:48", stufen.texts.title)
        assertEquals(CountdownGlyph.Now, stufen.glyph)
    }

    @Test fun `die genaue Uhrzeit steht in jedem Modus im Aufgeklappten`() {
        listOf(
            anzeige(41, countdown = false, exact = false),
            anzeige(41, countdown = true, exact = false),
            anzeige(41, countdown = true, exact = true),
        ).forEach { assertTrue(it.texts.bigText, it.texts.bigText.contains("um 22:48")) }
    }

    @Test fun `der Ort steht in jedem Modus im Untertitel`() {
        listOf(
            anzeige(41, countdown = false, exact = false),
            anzeige(41, countdown = true, exact = false),
            anzeige(41, countdown = true, exact = true),
        ).forEach { assertEquals("Nuernberg", it.texts.subText) }
    }
}
