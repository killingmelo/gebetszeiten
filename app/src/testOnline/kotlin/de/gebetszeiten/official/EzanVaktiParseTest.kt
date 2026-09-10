package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONException
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parser der dritten Quelle (ezanvakti.emushaf.net). Kein Netzaufruf —
 * [EzanVaktiFetcher.parseSchedule] ist genau deshalb rein.
 *
 * Die drei Fallen dieser Antwort, jede mit eigenem Test:
 *  1. `Gunes`/`Aksam` sind die GEBETSZEITEN, `GunesDogus`/`GunesBatis` der
 *     astronomische Auf-/Untergang — 7 Minuten Unterschied. Klein genug, um
 *     plausibel auszusehen, gross genug, um den Gegencheck (maxDriftMinutes = 2)
 *     in CONFLICT zu treiben. Die App wuerde dann den Jahresabruf verdaechtigen,
 *     obwohl unser eigener Parser der Falsche ist.
 *  2. `MiladiTarihKisaIso8601` ist trotz des Namens NICHT ISO.
 *  3. Die Fensterlaenge rolliert (32 Tage am 06.09.2026, 30 am 09.09.2026) —
 *     der Parser darf keine Laenge annehmen.
 */
class EzanVaktiParseTest {

    private val parse = EzanVaktiFetcher()::parseSchedule

    /**
     * Ein Array-Element WOERTLICH aus der echten Antwort
     * (`GET https://ezanvakti.emushaf.net/vakitler/9807`, geprueft am
     * 09.09.2026). Nicht huebsch machen und nicht kuerzen: der Test soll an
     * exakt dem scheitern, was der Endpunkt wirklich schickt.
     */
    private val echtesElement =
        """{"HicriTarihKisa":"23.3.1448","HicriTarihKisaIso8601":null,"HicriTarihUzun":"23 Rebiulevvel 1448","HicriTarihUzunIso8601":null,"AyinSekliURL":"https://namazvakti.diyanet.gov.tr/images/sd1.gif","MiladiTarihKisa":"05.09.2026","MiladiTarihKisaIso8601":"05.09.2026","MiladiTarihUzun":"05 Eylül 2026 Cumartesi","MiladiTarihUzunIso8601":"2026-09-05T00:00:00.0000000+03:00","GreenwichOrtalamaZamani":3.0,"Aksam":"19:33","Gunes":"06:22","GunesBatis":"19:26","GunesDogus":"06:29","Ikindi":"16:40","Imsak":"04:53","KibleSaati":"11:58","Ogle":"13:02","Yatsi":"20:55"}"""

    // ---- FALLE 1 -----------------------------------------------------------

    @Test
    fun `Gunes ist der Sonnenaufgang - nicht GunesDogus`() {
        val tag = parse("[$echtesElement]").values.single()
        assertEquals(LocalTime.of(6, 22), tag.sunrise)
    }

    @Test
    fun `Aksam ist der Sonnenuntergang - nicht GunesBatis`() {
        val tag = parse("[$echtesElement]").values.single()
        assertEquals(LocalTime.of(19, 33), tag.maghrib)
    }

    @Test
    fun `sieben Minuten Abstand reichen fuer CONFLICT - deshalb dieser Test`() {
        // Selbes Element, aber mit deutlich verschobenem astronomischen
        // Auf-/Untergang: liest der Parser die falschen Felder, springt das
        // Ergebnis um eine ganze Stunde und dieser Test faellt laut.
        val verschoben = echtesElement
            .replace("\"GunesDogus\":\"06:29\"", "\"GunesDogus\":\"07:29\"")
            .replace("\"GunesBatis\":\"19:26\"", "\"GunesBatis\":\"18:26\"")
        val tag = parse("[$verschoben]").values.single()
        assertEquals(LocalTime.of(6, 22), tag.sunrise)
        assertEquals(LocalTime.of(19, 33), tag.maghrib)
    }

    // ---- Alle sechs Zeiten, einzeln ---------------------------------------

    @Test
    fun `alle sechs Zeiten sitzen an der richtigen Stelle`() {
        val tag = parse("[$echtesElement]").values.single()
        assertEquals("Imsak", LocalTime.of(4, 53), tag.fajr)
        assertEquals("Gunes", LocalTime.of(6, 22), tag.sunrise)
        assertEquals("Ogle", LocalTime.of(13, 2), tag.dhuhr)
        assertEquals("Ikindi", LocalTime.of(16, 40), tag.asr)
        assertEquals("Aksam", LocalTime.of(19, 33), tag.maghrib)
        assertEquals("Yatsi", LocalTime.of(20, 55), tag.isha)
    }

    // ---- FALLE 2 -----------------------------------------------------------

    @Test
    fun `das Datum kommt aus MiladiTarihKisa im Format dd punkt MM punkt yyyy`() {
        assertEquals(
            setOf(LocalDate.of(2026, 9, 5)),
            parse("[$echtesElement]").keys,
        )
    }

    @Test
    fun `MiladiTarihKisaIso8601 wird nicht benutzt`() {
        // Das Feld heisst "...Iso8601", traegt aber dd.MM.yyyy — hier absichtlich
        // ein ABWEICHENDER Wert. Wer es liest, landet auf dem falschen Tag.
        val getuerkt = echtesElement.replace(
            "\"MiladiTarihKisaIso8601\":\"05.09.2026\"",
            "\"MiladiTarihKisaIso8601\":\"31.12.1999\"",
        )
        assertEquals(
            setOf(LocalDate.of(2026, 9, 5)),
            parse("[$getuerkt]").keys,
        )
    }

    // ---- FALLE 3 + Reihenfolge --------------------------------------------

    @Test
    fun `mehrere Elemente ergeben mehrere Tage in der Reihenfolge der Antwort`() {
        // Bewusst drei Elemente: keine Laengenannahme, das echte Fenster
        // rollierte zwischen 30 und 32 Tagen.
        val body = "[" + listOf(
            element(tarih = "05.09.2026", imsak = "04:53"),
            element(tarih = "06.09.2026", imsak = "04:54"),
            element(tarih = "07.09.2026", imsak = "04:56"),
        ).joinToString(",") + "]"
        val plan = parse(body)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 6),
                LocalDate.of(2026, 9, 7),
            ),
            plan.keys.toList(),
        )
        assertEquals(LocalTime.of(4, 56), plan[LocalDate.of(2026, 9, 7)]?.fajr)
    }

    @Test
    fun `leeres Array ergibt eine leere Map`() {
        // "leer" ist nicht "kaputt": das Quorum liest daraus "diese Quelle hat
        // nichts geliefert" und nicht "diese Quelle ist defekt".
        assertTrue(parse("[]").isEmpty())
    }

    // ---- Robustheit --------------------------------------------------------

    @Test
    fun `ein Element mit kaputtem Datum wird verworfen - die Nachbarn ueberleben`() {
        val body = "[" + listOf(
            element(tarih = "05.09.2026"),
            element(tarih = "2026-09-06"),
            element(tarih = "07.09.2026"),
        ).joinToString(",") + "]"
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7)),
            parse(body).keys.toList(),
        )
    }

    @Test
    fun `ein Element mit kaputter Zeit wird verworfen - die Nachbarn ueberleben`() {
        val body = "[" + listOf(
            element(tarih = "05.09.2026"),
            element(tarih = "06.09.2026", ikindi = "16.39"),
            element(tarih = "07.09.2026"),
        ).joinToString(",") + "]"
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7)),
            parse(body).keys.toList(),
        )
    }

    @Test
    fun `fehlt Yatsi wird der Tag verworfen und nicht mit Ersatzzeit gefuellt`() {
        val body = "[" + listOf(
            element(tarih = "05.09.2026"),
            element(tarih = "06.09.2026", yatsi = null),
        ).joinToString(",") + "]"
        // Halb erfundene Gebetszeiten waeren schlimmer als keine.
        assertEquals(listOf(LocalDate.of(2026, 9, 5)), parse(body).keys.toList())
    }

    @Test
    fun `ein JSON-null als Zeit verwirft den Tag`() {
        val kaputt = element(tarih = "05.09.2026")
            .replace("\"Yatsi\":\"20:55\"", "\"Yatsi\":null")
        assertTrue(parse("[$kaputt]").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun `ein Koerper der kein Array ist wirft`() {
        // Absichtlich KEIN Auffangen der JSONException im Parser: "leer" und
        // "kaputt" sind zwei verschiedene Zustaende, das Quorum unterscheidet sie.
        // JSONException statt Exception: sonst waere der Test auch mit einer
        // unbeteiligten NullPointerException gruen.
        parse("""{"vakitler":[]}""")
    }

    @Test(expected = JSONException::class)
    fun `eine HTML-Fehlerseite wirft`() {
        parse("<html><body>502 Bad Gateway</body></html>")
    }

    @Test
    fun `ein Element das kein Objekt ist wird verworfen, die Nachbarn ueberleben`() {
        // Bewusst verwerfen statt werfen — dieselbe Zeilen-Robustheit wie bei
        // einem kaputten Datum. Ohne diesen Test dreht die Entscheidung beim
        // naechsten Aufraeumen unbemerkt um.
        val tage = parse("""[${element(tarih = "05.09.2026")},42,"x",null,${element(tarih = "07.09.2026")}]""")
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7)),
            tage.keys.toList(),
        )
    }

    // ---- Was der STRICT-Resolver abfaengt ----------------------------------

    @Test
    fun `ein unmoegliches Datum wird verworfen, nicht still verschoben`() {
        // Der Grund fuer `uuuu` + STRICT: der Standard-Resolver rueckt
        // "31.02.2026" klammheimlich auf den 28.02. Richtige Zeiten lagen dann
        // auf dem falschen Tag — und wuerden in der LinkedHashMap womoeglich
        // einen echten Eintrag ueberschreiben.
        // Reihenfolge ist hier entscheidend: der ECHTE 28.02. steht vorn, das
        // unmoegliche Datum dahinter. Wuerde es still auf den 28.02. gerueckt,
        // ueberschriebe es in der LinkedHashMap den echten Eintrag — und
        // niemand saehe es. Mit unterscheidbaren Zeiten faellt genau das auf.
        val tage = parse(
            """[${element(tarih = "28.02.2026", imsak = "04:53")},${element(tarih = "31.02.2026", imsak = "01:11")}]""",
        )
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), tage.keys.toList())
        assertEquals("der echte Eintrag darf nicht ueberschrieben werden", LocalTime.of(4, 53), tage.values.single().fajr)
    }

    @Test
    fun `ein Schalttag im Nichtschaltjahr wird verworfen`() {
        assertTrue(parse("""[${element(tarih = "29.02.2026")}]""").isEmpty())
        // Im Schaltjahr bleibt er gueltig — STRICT lehnt nichts Echtes ab.
        assertEquals(
            listOf(LocalDate.of(2024, 2, 29)),
            parse("""[${element(tarih = "29.02.2024")}]""").keys.toList(),
        )
    }

    @Test
    fun `24 Uhr wird verworfen statt zu Mitternacht desselben Tages zu werden`() {
        // Ohne STRICT auf der Uhrzeit macht der Resolver aus "24:00" ein
        // "00:00" — Isha laege dann VOR Fajr, ohne dass irgendetwas wirft.
        assertTrue(parse("""[${element(yatsi = "24:00")}]""").isEmpty())
        // Die echten Randwerte bleiben gueltig.
        assertEquals(LocalTime.of(23, 59), parse("""[${element(yatsi = "23:59")}]""").values.single().isha)
        assertEquals(LocalTime.of(0, 0), parse("""[${element(imsak = "0:00")}]""").values.single().fajr)
    }

    // ---- Uhrzeit-Toleranz --------------------------------------------------

    @Test
    fun `eine Zeit ohne fuehrende Null wird toleriert`() {
        // Die echte Antwort fuellt immer auf (30 von 30 Elementen am 09.09.2026
        // in HH:mm). "6:22" ist damit kein beobachteter Fall, sondern Vorsorge:
        // H:mm ist eindeutig, kostet nichts, und ein Formatierungs-Schluckauf
        // der Quelle soll den dritten Zeugen des Quorums nicht stumm machen.
        val body = "[" + element(tarih = "05.09.2026", gunes = "6:22", imsak = "4:53") + "]"
        val tag = parse(body).values.single()
        assertEquals(LocalTime.of(6, 22), tag.sunrise)
        assertEquals(LocalTime.of(4, 53), tag.fajr)
    }

    // ---- Fixture-Hilfe -----------------------------------------------------

    /**
     * Synthetisches Element in der Feldform der echten Antwort.
     * `GunesDogus`/`GunesBatis` tragen hier immer Werte, die von `Gunes`/`Aksam`
     * abweichen — so faellt jeder Test auf, der versehentlich die
     * astronomischen Felder liest.
     */
    private fun element(
        tarih: String = "05.09.2026",
        tarihIso: String = "05.09.2026",
        imsak: String = "04:53",
        gunes: String = "06:22",
        ogle: String = "13:02",
        ikindi: String = "16:40",
        aksam: String = "19:33",
        yatsi: String? = "20:55",
    ): String {
        val felder = mutableListOf(
            """"MiladiTarihKisa":"$tarih"""",
            """"MiladiTarihKisaIso8601":"$tarihIso"""",
            """"Imsak":"$imsak"""",
            """"Gunes":"$gunes"""",
            """"GunesDogus":"06:29"""",
            """"Ogle":"$ogle"""",
            """"Ikindi":"$ikindi"""",
            """"Aksam":"$aksam"""",
            """"GunesBatis":"19:26"""",
        )
        if (yatsi != null) felder += """"Yatsi":"$yatsi""""
        return felder.joinToString(",", "{", "}")
    }
}
