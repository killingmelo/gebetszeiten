package de.gebetszeiten.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung von `scheduleNext` — das, was kein reiner Test sehen kann.
 *
 * `alarmPlan` entscheidet alle drei Ketten-Wecker und ist ohne Geraet
 * pruefbar ([PrayerAlarmSchedulerTest]). Ob `scheduleNext` diese Entscheidung
 * auch wirklich UMSETZT, ist es nicht: dafuer braeuchte es `AlarmManager`,
 * also Robolectric oder ein Geraet.
 *
 * Eine erste Fassung dieses Tests pruefte nur Textvorkommen ("kein `return`
 * im Rumpf", "die drei Aufrufe stehen da") — das fing die Regression aus
 * Aufgabe 9 (fruehes `return`), aber NICHT ihre naheliegende zweite Form:
 * dieselben drei Aufrufe HINTER eine eigene Bedingung verschachtelt
 * (`if (trigger != null) { setAlarm(...); schedulePreReminder(...);
 * scheduleDisplayStep(...) }`), was bei fehlenden Zeiten exakt denselben
 * Effekt haette — alle vier alten Behauptungen blieben dabei gruen.
 *
 * Eine zweite Fassung pruefte stattdessen auf `\bif\s*\(` — das fing die
 * `if`-Verschachtelung, aber nicht `when (true) { bedingung -> ...; else ->
 * {} }`, das denselben Effekt haette, ohne `if` zu schreiben. Eine Liste
 * verbotener Schluesselwoerter (`if`, `when`, naechstes waere `for`/`while`)
 * ist keine ehrliche Sicherung — sie verschiebt das Problem nur auf das
 * naechste Schluesselwort, das jemandem einfaellt.
 *
 * Stattdessen die Eigenschaft, die JEDE Form von Verschachtelung teilt, ganz
 * gleich mit welchem Schluesselwort: eine neue geschweifte Klammer im Rumpf.
 * `if`, `when`, `for`, `while`, `try` und ein Lambda-Argument brauchen alle
 * ein eigenes `{ ... }` — `rumpfVon` liefert bereits nur den Inhalt ZWISCHEN
 * der oeffnenden und der schliessenden Klammer der Funktion selbst, also darf
 * darin ueberhaupt keine weitere `{` mehr vorkommen. **`scheduleNext`
 * enthaelt ueberhaupt keine eigene Verzweigung mehr** — jede Entscheidung
 * liegt in `alarmPlan`, hier wird nur noch angewendet.
 *
 * Praezedenz fuer das Idiom (Quelltext-Lese-Test statt Robolectric):
 * `OngoingWiringTest`, `NoNetworkInSharedCodeTest`, `CountdownIconAssetsTest`.
 * Der gelesene Pfad steht als Gradle-Eingabe in `app/build.gradle.kts`
 * (`mainQuellsatz`, deckt den ganzen Quellsatz ab). Das Arbeitsverzeichnis
 * ist das Modul (`app/`), daher der relative Pfad.
 */
class PrayerAlarmSchedulerWiringTest {

    private val scheduler = File("src/main/kotlin/de/gebetszeiten/alarm/PrayerAlarmScheduler.kt")

    @Test
    fun `scheduleNext verzweigt selbst nicht und bezieht seine Entscheidung aus alarmPlan`() {
        val rumpf = rumpfVon(ohneKommentareUndTexte(text()), "suspend fun scheduleNext(")
        // Keine weitere `{`: egal mit welchem Schluesselwort (if, when, for,
        // while, try) oder als Lambda-Argument verschachtelt wuerde — jede
        // Form brauchte ein eigenes `{ ... }`, und das faellt hier auf, ohne
        // dass die Pruefung das Schluesselwort selbst kennen muss.
        assertFalse(
            "scheduleNext verzweigt selbst (eine weitere '{' im Rumpf) — die Entscheidung gehoert " +
                "vollstaendig in alarmPlan, sonst kann sich die Verschachtelungs-Regression aus der " +
                "Pruefung wieder einschleichen, gleich mit welchem Schluesselwort",
            rumpf.contains("{"),
        )
        assertFalse(
            "scheduleNext kehrt vorzeitig zurueck — genau die Regression aus Aufgabe 9",
            rumpf.contains("return"),
        )
        assertTrue(
            "scheduleNext bezieht seine Entscheidung nicht aus alarmPlan",
            rumpf.contains("alarmPlan("),
        )
        // Alle drei Ketten-Wecker muessen ueber dieselbe, ungeteilte Anwendung
        // laufen — sonst waere eine feature-spezifische Extra-Bedingung um
        // eines der drei Felder wieder moeglich, ohne dass `if` im Rumpf
        // selbst auftaucht (z. B. ein bedingter Methodenaufruf).
        assertTrue(
            "scheduleNext wendet den Gebets-Wecker nicht (mehr) über applyAlarm an",
            rumpf.contains("applyAlarm(alarmManager, plan.prayerAtMillis,"),
        )
        assertTrue(
            "scheduleNext wendet den Vorlauf-Wecker nicht (mehr) über applyAlarm an",
            rumpf.contains("applyAlarm(alarmManager, plan.preReminderAtMillis,"),
        )
        assertTrue(
            "scheduleNext wendet den Stufen-Wecker nicht (mehr) über applyAlarm an",
            rumpf.contains("applyAlarm(alarmManager, plan.displayStepAtMillis,"),
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
