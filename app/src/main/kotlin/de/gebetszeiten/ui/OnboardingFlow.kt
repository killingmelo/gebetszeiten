package de.gebetszeiten.ui

import de.gebetszeiten.data.AppSettings

/**
 * Die drei Schritte der Ersteinrichtung — in dieser Reihenfolge, und keiner
 * ist ueberspringbar.
 *
 * Warum es sie ueberhaupt gibt: Die App hatte bis hierher **kein**
 * Onboarding. Sie startete direkt in die Hauptansicht, mit fest
 * einkompiliertem Nuernberg, und feuerte ab Android 13 unvermittelt den
 * Berechtigungsdialog. Zwei Dinge waren dadurch praktisch unauffindbar:
 * der eigene Ort (wer in Hamburg installierte, sah Nuernberger Zeiten) und
 * die Dauerzeile auf dem Sperrbildschirm.
 *
 * Warum PFLICHT statt Vorschlag: Die naheliegende Abkuerzung waere gewesen,
 * die Dauerzeile ab Werk einzuschalten. Das haette Bestandsnutzern ungefragt
 * etwas angeheftet — und ihnen zugleich etwas weggenommen, denn
 * `PrayerAlarmReceiver` unterdrueckt die fuenf stillen Eintritts-Meldungen,
 * sobald die Dauerzeile laeuft und der Stil still ist (beides
 * Werkseinstellung). Solange niemand geantwortet hat, bleibt deshalb alles,
 * wie es war; die Antwort entscheidet, nicht die Vorgabe.
 */
enum class OnboardingStep { ORT, ANZEIGE, ERINNERUNGEN }

/** Der naechste Schritt, oder `null` = fertig. */
fun nextStep(current: OnboardingStep): OnboardingStep? = when (current) {
    OnboardingStep.ORT -> OnboardingStep.ANZEIGE
    OnboardingStep.ANZEIGE -> OnboardingStep.ERINNERUNGEN
    OnboardingStep.ERINNERUNGEN -> null
}

/** Der vorige Schritt, oder `null` = schon am Anfang (dann fuehrt Zurueck
 *  NICHT hinaus; die Ersteinrichtung ist Pflicht). */
fun previousStep(current: OnboardingStep): OnboardingStep? = when (current) {
    OnboardingStep.ORT -> null
    OnboardingStep.ANZEIGE -> OnboardingStep.ORT
    OnboardingStep.ERINNERUNGEN -> OnboardingStep.ANZEIGE
}

/**
 * Ob „Weiter" moeglich ist.
 *
 * Nur der Ort kann fehlen — und er kann es nie wirklich, weil der Entwurf mit
 * dem aktuellen Ort vorbelegt startet. Die Bedingung steht trotzdem hier und
 * nicht als `true` im Knopf: sie ist die Stelle, an der eine kuenftige
 * Pflichtangabe hingehoert, und ein Test haelt fest, dass die anderen beiden
 * Schritte bewusst ohne Pflichtangabe auskommen.
 */
fun canContinue(step: OnboardingStep, draft: AppSettings): Boolean = when (step) {
    OnboardingStep.ORT -> draft.city.isNotBlank()
    OnboardingStep.ANZEIGE -> true
    OnboardingStep.ERINNERUNGEN -> true
}

/**
 * Der Wortlaut eines Schritts: Ueberschrift und ein Satz darunter.
 *
 * Deutscher Text in Kotlin statt `strings.xml`, weil er zusammen mit seiner
 * Entscheidung geprueft wird — dass die Bestandskohorte nirgends „so richtest
 * du die App ein" liest und die Neukohorte nirgends „nichts hat sich
 * geaendert", ist eine Zusage, kein Layout. Muster: `NoTimesNotice.kt`,
 * `NotificationBlock.kt`.
 */
data class OnboardingCopy(val headline: String, val detail: String)

fun onboardingCopy(step: OnboardingStep, fresh: Boolean): OnboardingCopy = when (step) {
    OnboardingStep.ORT -> OnboardingCopy(
        headline = if (fresh) "Wo betest du?" else "Stimmt dein Ort noch?",
        detail = "Amtliche Diyanet-Zeiten gelten je Ort — ein paar Kilometer " +
            "machen Minuten aus. Tippen statt Standortfreigabe: die App fragt " +
            "nie nach deiner Position.",
    )

    OnboardingStep.ANZEIGE -> OnboardingCopy(
        headline = if (fresh) {
            "Dein nächstes Gebet im Blick"
        } else {
            "Das gibt es — gefunden hat es kaum jemand"
        },
        detail = "Eine stille, dauerhafte Benachrichtigung zeigt das nächste " +
            "Gebet auf dem Sperrbildschirm, mit einem Symbol oben in der " +
            "Statusleiste. Wichtig zu wissen: Solange der Erinnerungsstil " +
            "„Still\" ist, ersetzt diese eine Zeile die einzelnen Meldungen zur " +
            "Gebetszeit — sie ändert sich dann, statt neu aufzutauchen.",
    )

    OnboardingStep.ERINNERUNGEN -> OnboardingCopy(
        headline = "Woran soll erinnert werden?",
        detail = "Gilt zusätzlich zur Anzeige oben. Alles lässt sich später in " +
            "den Einstellungen ändern.",
    )
}

/**
 * Die Beschriftung des Weiter-Knopfes. Im letzten Schritt heisst er nicht
 * „Weiter" — sonst waere unklar, dass danach nichts mehr kommt.
 */
fun onboardingContinueLabel(step: OnboardingStep): String =
    if (nextStep(step) == null) "Fertig" else "Weiter"
