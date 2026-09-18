package de.gebetszeiten.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung von `scheduleNext` — das, was kein reiner Test sehen kann.
 *
 * `mainAlarmTriggerAtMillis` entscheidet Zeit-oder-Abbestellen fuer den
 * Gebets-Wecker und ist ohne Geraet pruefbar ([PrayerAlarmSchedulerTest]).
 * Ob `scheduleNext` diese Entscheidung auch wirklich UMSETZT — den Wecker
 * abbestellt statt ihn stehen zu lassen, und die Vorlauf- und Stufen-Wecker
 * dabei ueberhaupt noch aufruft —, ist es nicht: dafuer braeuchte es
 * `AlarmManager`, also Robolectric oder ein Geraet.
 *
 * Genau in dieser Luecke lag der Fehler aus Aufgabe 9: `scheduleNext` kehrte
 * bei fehlenden Zeiten per `?: return` VOR jeder Abbestellung zurueck. Ein
 * reiner Test der Entscheidung allein haette das NICHT gefangen — die
 * Entscheidung selbst war ja nie falsch, nur wurde sie nirgends mehr
 * angewendet. Das Mittel ist das Idiom des Hauses: ein Test, der den
 * Quelltext liest — Praezedenz `OngoingWiringTest`, `NoNetworkInSharedCodeTest`,
 * `CountdownIconAssetsTest`.
 *
 * Der gelesene Pfad steht als Gradle-Eingabe in `app/build.gradle.kts`
 * (`mainQuellsatz`, deckt den ganzen Quellsatz ab). Das Arbeitsverzeichnis
 * ist das Modul (`app/`), daher der relative Pfad.
 */
class PrayerAlarmSchedulerWiringTest {

    private val scheduler = File("src/main/kotlin/de/gebetszeiten/alarm/PrayerAlarmScheduler.kt")

    @Test
    fun `scheduleNext bestellt den Gebets-Wecker ab, statt ihn stehen zu lassen`() {
        val rumpf = rumpfVon(ohneKommentareUndTexte(text()), "suspend fun scheduleNext(")
        // Der Fehler aus Aufgabe 9 in einem Wort: ein frueher `return`, der
        // die Abbestellung ueberspringt. Kommt er zurueck — gleich in
        // welcher Form —, muss dieser Test sterben.
        assertFalse(
            "scheduleNext kehrt vorzeitig zurueck — genau die Regression aus Aufgabe 9, " +
                "bei der alte Wecker stehen blieben, statt abbestellt zu werden",
            rumpf.contains("return"),
        )
        assertTrue(
            "scheduleNext bestellt den Gebets-Wecker nicht ab, wenn `mainAlarmTriggerAtMillis` null liefert",
            rumpf.contains("alarmManager.cancel(prayerAlarm)"),
        )
        assertTrue(
            "scheduleNext ruft schedulePreReminder nicht mehr unbedingt auf — der Vorlauf-Wecker " +
                "bliebe bei fehlenden Zeiten stehen",
            rumpf.contains("schedulePreReminder(context, alarmManager, settings, zone, now)"),
        )
        assertTrue(
            "scheduleNext ruft scheduleDisplayStep nicht mehr unbedingt auf — der Stufen-Wecker " +
                "bliebe bei fehlenden Zeiten stehen",
            rumpf.contains("scheduleDisplayStep(context, alarmManager, settings, zone, now)"),
        )
    }

    // --- Werkzeug (Praezedenz: OngoingWiringTest) -----------------------------

    private fun text(): String {
        assertTrue("$scheduler fehlt", scheduler.isFile)
        return scheduler.readText()
    }

    /** Die Argumentliste ab der oeffnenden Klammer an [klammer]. */
    private fun argumente(text: String, klammer: Int): String {
        var tiefe = 0
        for (i in klammer until text.length) {
            when (text[i]) {
                '(' -> tiefe++
                ')' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(klammer + 1, i)
                }
            }
        }
        throw AssertionError("unbalancierte Klammern ab Position $klammer")
    }

    /** Der Rumpf der mit [kopf] beginnenden Funktion, per Klammerzaehlung. */
    private fun rumpfVon(text: String, kopf: String): String {
        val start = text.indexOf(kopf)
        assertTrue("$kopf nicht gefunden", start >= 0)
        val signatur = argumente(text, start + kopf.length - 1)
        val auf = text.indexOf('{', start + kopf.length + signatur.length)
        assertTrue("kein Rumpf hinter $kopf", auf >= 0)
        var tiefe = 0
        for (i in auf until text.length) {
            when (text[i]) {
                '{' -> tiefe++
                '}' -> {
                    tiefe--
                    if (tiefe == 0) return text.substring(auf + 1, i)
                }
            }
        }
        throw AssertionError("unbalancierte geschweifte Klammern hinter $kopf")
    }

    /** Kommentare und Zeichenkettenliterale zaehlen nicht — siehe Begruendung
     *  in `OngoingWiringTest`. */
    private fun ohneKommentareUndTexte(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?<!:)//[^\n]*"""), "")
        .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
}
