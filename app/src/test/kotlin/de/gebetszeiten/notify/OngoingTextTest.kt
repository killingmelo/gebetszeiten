package de.gebetszeiten.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `die genaue Uhrzeit steht in BEIDEN Countdown-Modi drin`() {
        // Stufen-Modus: der Titel lautet „Noch 20+ Min bis Isha", die Uhrzeit
        // ist dort verdraengt. Genauer Modus: der Titel lautet „Isha um 22:48".
        // Der aufgeklappte Text traegt sie so oder so — die Uhrzeit ist ein
        // Pflichtargument, nicht eine Zeile, die ein Modus weglassen kann.
        val stufen = ongoingBigText(umZeit, activeLine = aktuell)
        val genau = ongoingBigText(umZeit, activeLine = aktuell)
        assertEquals(true, stufen.contains("22:48"))
        assertEquals(true, genau.contains("22:48"))
        assertEquals(stufen, genau)
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
}
