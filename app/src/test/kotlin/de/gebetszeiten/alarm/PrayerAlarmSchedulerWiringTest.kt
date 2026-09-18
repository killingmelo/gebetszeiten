package de.gebetszeiten.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Verdrahtung von `scheduleNext` — das, was kein reiner Test sehen kann.
 *
 * `alarmPlan` entscheidet alle drei Ketten-Wecker und ist ohne Geraet
 * pruefbar ([PrayerAlarmSchedulerTest]) — **dort liegt der eigentliche
 * Schutz.** Dieser Waechter hier haelt nur die VERDRAHTUNG trivial: dass
 * `scheduleNext` den fertigen Plan unbedingt anwendet, statt selbst noch
 * einmal zu entscheiden. Er ist die zweite Verteidigungslinie, nicht die
 * erste.
 *
 * Drei Fassungen, drei Luecken, jede durch eine tatsaechlich nachgebaute
 * Mutation gefunden (Rohausgaben im Bericht zu Aufgabe 13):
 * 1. Textvorkommen ("kein `return`", "die drei Aufrufe stehen da") fing die
 *    Regression aus Aufgabe 9 (fruehes `return`), nicht ihre Verschachtelung
 *    hinter einem `if`.
 * 2. `\bif\s*\(` fing die `if`-Verschachtelung, nicht `when { bedingung ->
 *    ...; else -> {} }` — dieselbe Verschachtelung, anderes Schluesselwort.
 * 3. `!rumpf.contains("{")` fing auch `when`, aber auf Kosten von zwei neuen
 *    Fehlern: ein blockloses `if ohne_klammern applyAlarm(...)` (gueltiges
 *    Kotlin, keine `{` im Rumpf, stellt exakt die urspruengliche Regression
 *    wieder her) waere UNBEMERKT durchgerutscht, und jede harmlose
 *    Scope-Funktion (`?.let { ... }`, `.also { ... }`) haette den Test rot
 *    gefaerbt, ohne dass irgendetwas kaputt war — ein Test, der bei
 *    harmlosen Aenderungen rot wird, wird abgeschaltet und schuetzt dann gar
 *    nichts mehr.
 *
 * Diese (vierte) Fassung prueft zwei UNABHAENGIGE Dinge:
 * - **Schluesselwoerter, nicht Klammern:** `if`, `when`, `for`, `while` als
 *   ganzes Wort im bereinigten Rumpf. Das faengt auch das blocklose `if` von
 *   oben (das Schluesselwort steht ja da, ganz ohne `{`) und laesst Lambdas
 *   in Ruhe (kein Schluesselwort drin).
 * - **Genau drei `applyAlarm(`-Aufrufe.** Faengt, dass einer wegfaellt oder
 *   dazukommt — das sieht die Schluesselwortpruefung allein nicht, ein
 *   vierter Aufruf braucht kein einziges der vier Schluesselwoerter.
 *
 * **Die ehrliche Grenze:** ein quelltextlesender Test kann Verzweigung nicht
 * VOLLSTAENDIG ausschliessen. `?:`, `takeIf`, `?.let { ... }` mit einer
 * bedingten Fortsetzung, oder ein bedingter Ausdruck als Argument (`if (x)
 * a else b` OHNE eigene Zeile) kaemen ohne die vier gepruefte Schluesselwoerter
 * aus und blieben unentdeckt. Wird dieser Waechter durch so etwas erneut
 * umgangen, ist das eine bekannte, akzeptierte Luecke dieses Idioms — nicht
 * ein weiterer Grund, die Pruefung noch enger zu schnueren. Der Schutz, der
 * wirklich zaehlt, liegt in [PrayerAlarmSchedulerTest] (`alarmPlan` selbst).
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
        // Schluesselwoerter statt Klammern: faengt auch ein blockloses `if`
        // ohne `{ ... }` (gueltiges Kotlin), ohne harmlose Lambda-Argumente
        // (`?.let { ... }`, `.also { ... }`) mitzutreffen.
        assertFalse(
            "scheduleNext verzweigt selbst (if/when/for/while im Rumpf) — die Entscheidung gehoert " +
                "vollstaendig in alarmPlan, sonst kann sich die Verschachtelungs-Regression aus der " +
                "Pruefung wieder einschleichen",
            Regex("""\b(if|when|for|while)\b""").containsMatchIn(rumpf),
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
        // laufen. Die Schluesselwortpruefung allein saehe weder einen
        // wegfallenden noch einen zusaetzlichen `applyAlarm`-Aufruf — beides
        // braucht kein einziges der vier gepruefte Schluesselwoerter.
        assertEquals(
            "scheduleNext wendet nicht mehr genau drei Wecker ueber applyAlarm an",
            3,
            Regex("""applyAlarm\(""").findAll(rumpf).count(),
        )
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
