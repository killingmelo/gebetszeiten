package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.MIN_FUTURE_DAYS
import de.gebetszeiten.core.prayertimes.officialtimes.Verification
import de.gebetszeiten.core.prayertimes.officialtimes.VerificationNote
import de.gebetszeiten.core.prayertimes.officialtimes.needsRefresh
import de.gebetszeiten.official.OfficialStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/** Ohne Jahr: die Favoritenzeile traegt Ort, Abdeckung UND Versuch
 *  zusammen, und ein Fehlversuch, der noch der Rede wert ist, liegt Stunden
 *  bis Tage zurueck. Der volle Stempel steht beim aktiven Ort. */
private val SHORT_STAMP = DateTimeFormatter.ofPattern("dd.MM., HH:mm")

/**
 * Mehrzeiliger Klartext für die Statuszeile im Einstellungs-Sheet. Ohne
 * Android testbar.
 *
 * **Rein ist sie nur, wenn der Aufrufer [zone] UND [today] setzt.** Beide
 * haben einen Vorgabewert, der die Systemuhr bzw. die Systemzeitzone liest,
 * und Kotlin wertet ihn bei JEDEM argumentlosen Aufruf neu aus. Die frueher
 * hier stehende Zusage „keine versteckte Systemuhr-Abhaengigkeit" stimmt
 * seit [today] nicht mehr. Wer die Reservewarnung prueft, MUSS [today]
 * uebergeben — sonst haengt der Test am Kalender des Testrechners.
 *
 * [source] ist die vom Aufrufer (dem Einstellungs-Sheet) bereits
 * klassifizierte, tatsaechlich aktive Quelle (spiegelt `PrayerProvider.daily`)
 * — NICHT der Diyanet-Standortname allein. Nur so kann die Zeile die
 * Faelle sauber unterscheiden, in denen `status` (der Cache-Stempel) etwas
 * anderes zeigt als das, was gerade wirklich angezeigt wird: eigene
 * Berechnung gewaehlt, Online-Abruf abgeschaltet, oder eine gebuendelte
 * DE-Tabelle, fuer die nie ein Netzabruf stattfand.
 *
 * [canFetch] entscheidet ueber "Letzter Abruf"/"Fehler" — bewusst NICHT
 * `source is Bundled` (Fix-Runde 3): ob ein Abrufmechanismus ueberhaupt
 * existiert, haengt am Flavor/Schalter, nicht an der Quelle, die gerade
 * traegt. Online-Flavor + `useOnline` kann einen fehlgeschlagenen Abruf
 * haben, WAEHREND die gebuendelte DE-Tabelle traegt — der Fehlergrund darf
 * dann nicht verschwinden, sonst bleibt "Jetzt aktualisieren" (das an
 * derselben Bedingung haengt) ein Knopf ohne sichtbare Wirkung. Am Aufrufort
 * identisch mit der Knopf-Sichtbarkeit: `settings.canFetchOfficial()` — seit
 * Aufgabe 15 nur noch `settings.useOnline`, siehe die KDoc dort.
 *
 * [bundledCoverageEnd] ist der letzte Tag, den die GEBUENDELTEN Tabellen
 * abdecken — und den uebergibt der Aufrufer nur, wenn es fuer diesen Ort
 * ueberhaupt eine gebuendelte Tabelle gibt, sonst `null`. Fuer einen
 * Favoriten in Istanbul waere die Warnung Laerm: das Bundle deckt nur
 * Deutschland ab, dort war nie eine Reserve. Nicht zu verwechseln mit
 * `status.coveredUntil` — das ist die Abdeckung des ONLINE-CACHES.
 *
 * [today] wird ausschliesslich fuer [bundledCoverageEnd] gebraucht — siehe
 * oben, warum es im Test gesetzt werden MUSS.
 */
fun officialStatusText(
    status: OfficialStatus,
    source: TimesSourceBadge,
    canFetch: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    bundledCoverageEnd: LocalDate? = null,
    today: LocalDate = LocalDate.now(zone),
): String {
    val lines = mutableListOf<String>()
    when (source) {
        is TimesSourceBadge.Official -> {
            val idSuffix = status.locationId?.let { " (ID $it)" } ?: ""
            lines += "Quelle: amtliche Diyanet-Zeiten · ${source.locationName}$idSuffix"
        }
        is TimesSourceBadge.Bundled ->
            lines += "Quelle: amtliche Diyanet-Zeiten · ${source.locationName} (gebündelt)"
        TimesSourceBadge.Calculated ->
            lines += "Quelle: eigene Berechnung (Diyanet-Methode)"
        // Notausgang aus, keine amtliche Quelle da (Aufgabe 15) — dann zeigt
        // die App fuer diesen Ort/Tag gar keine Zeiten (siehe noTimesNotice).
        TimesSourceBadge.None ->
            lines += "Quelle: keine — Notausgang aus"
    }
    // "Abgedeckt bis" beschreibt die Abdeckung des ONLINE-CACHES — nur
    // zeigen, wenn der Cache auch die aktive Quelle ist. Sonst (Bundled,
    // Calculated) koennte ein veraltetes `coveredUntil` faelschlich als
    // Abdeckung der gebuendelten Tabelle bzw. der Berechnung gelesen werden.
    if (source is TimesSourceBadge.Official) {
        status.coveredUntil?.let { lines += "Abgedeckt bis: ${DATE.format(it)}" }
        // Die Pruefnotiz haengt an DERSELBEN Bedingung wie "Abgedeckt bis"
        // und aus demselben Grund: sie beschreibt den Abruf, der den
        // Online-Cache gefuellt hat. Traegt gerade die gebuendelte Tabelle
        // oder die Berechnung, laese sie sich als Aussage ueber DIESE lesen.
        // Der Wortlaut kommt aus `verificationLine` (dieselbe Datei wie
        // `fetchErrorSummary`) — ein zweiter Text fuer dieselbe Sache waere
        // ein zweiter Ort, an dem er falsch werden kann. `null` liefert sie
        // bei fehlender Notiz UND bei VerificationNote.NONE; dann faellt die
        // Zeile weg.
        verificationLine(status.verification)?.let { lines += it }
    }
    // Ohne Abrufmechanismus (offline-Flavor, oder online mit ausgeschaltetem
    // Abruf) waeren "Letzter Abruf"/"Fehler" Aussagen ueber ein Ereignis, das
    // gar nicht stattfinden kann.
    if (canFetch) {
        if (status.lastAttemptEpochMs == null) {
            // "noch kein Versuch" nur, wenn es auch keinerlei Beleg fuer einen
            // frueheren Abruf gibt. Liegen Standort oder Abdeckung vor, hat es
            // sehr wohl einen gegeben, nur ohne erhaltenes Protokoll: das Fenster
            // zwischen `putAll` und `recordAttempt`, oder ein migrierter
            // Alt-Cache, dessen Versuchs-Stempel auf einen anderen Ort zeigte
            // und deshalb verworfen wurde.
            lines += if (status.locationId != null || status.coveredUntil != null) {
                "Letzter Abruf: unbekannt"
            } else {
                "Letzter Abruf: noch kein Versuch"
            }
        } else {
            val stamp = STAMP.format(Instant.ofEpochMilli(status.lastAttemptEpochMs).atZone(zone))
            lines += "Letzter Abruf: $stamp"
        }
        status.lastError?.let { lines += "Fehler: $it" }
    }
    // Ganz unten, und zwar unabhaengig von [source]: die Zeile beschreibt die
    // RESERVE, nicht die aktive Quelle. Zwischen "Quelle:" und "Abgedeckt
    // bis" gelesen, waere sie eine Aussage ueber das, was gerade traegt —
    // dieselbe Verwechslung, gegen die "Abgedeckt bis" und die Pruefnotiz
    // oben an `source is Official` haengen. Als letzte Zeile, hinter allem
    // zur aktiven Quelle, ist sie erkennbar eine Anmerkung fuer danach.
    coverageWarning(bundledCoverageEnd, today)?.let { lines += it }
    return lines.joinToString("\n")
}

/**
 * Die eine Zeile, wenn die gebuendelte Reserve zur Neige geht — sonst `null`.
 *
 * Seit „online zuerst" sind die gebuendelten Tabellen nur noch Reserve: was
 * der Nutzer davon hat, merkt er erst ohne Netz. Genau das sagt der Satz —
 * nicht „ein Asset laeuft ab", sondern was danach passiert.
 *
 * **Und was danach passiert, ist NICHT sofort die eigene Berechnung.**
 * `PrayerProvider.daily` liest in dieser Reihenfolge: persistierter
 * Online-Cache (`OfficialTimesCache`, im DataStore, ohne Netz lesbar) →
 * gebuendelte Tabelle → eigene Berechnung. Mit `useOnline` (ab Werk an) und
 * der 60-Tage-Schwelle hat der Nutzer also auch nach dem Ende der Reserve
 * weiter amtliche Zeiten, solange der Cache reicht. Der frueher hier
 * stehende Satz „ohne Netz gibt es danach nur die eigene Berechnung" war
 * deshalb falsch — und konnte direkt unter einem „Abgedeckt bis: 04.07.2027"
 * stehen und ihm widersprechen. Der Satz nennt jetzt beide Stufen und
 * behauptet keine von ihnen: „die zuvor geladenen amtlichen Zeiten, und wo
 * keine vorliegen, die eigene Berechnung" bleibt auch im offline-Flavor
 * wahr, wo es nie einen Cache gab.
 *
 * [coverageEnd] ist der LETZTE abgedeckte Tag. `null` heisst „fuer diesen Ort
 * gibt es gar keine gebuendelte Tabelle" (das Bundle deckt nur Deutschland
 * ab) — dann gibt es nichts zu warnen. Ueber diese Anwendbarkeit entscheidet
 * der Aufrufer, ueber den Wortlaut diese Funktion.
 *
 * **Die Schwelle faellt IN die Warnung:** `null` gibt es nur, wenn MEHR als
 * [warnWithinDays] Tage abgedeckt sind. Genau auf der Schwelle wird gewarnt.
 *
 * **45 Tage und nicht 60 wie beim Online-Cache** ([MIN_FUTURE_DAYS]): die
 * 60 dort sind die Schwelle, ab der die App von selbst nachlaedt — dort ist
 * Handeln noch ohne Zutun des Nutzers moeglich. Die Reserve dagegen ist
 * nicht dringend, solange Netz da ist; sie traegt nur den Ausnahmefall.
 * Eine frueher gezeigte Zeile waere sechs Wochen lang Laerm ueber etwas, das
 * der Nutzer ohnehin nicht bemerkt. [warnWithinDays] ist benannt, damit der
 * Wert im Test nicht wiederholt werden muss.
 *
 * Keine Zahl behauptet hier etwas anderes, als sie ist: die einzige Zahl im
 * Satz ist das Abdeckungsende selbst. Eine Restlaufzeit in Tagen wird
 * bewusst NICHT genannt — sie waere eine zweite, staendig nachzurechnende
 * Zahl neben einem Datum, das dasselbe schon exakt sagt.
 *
 * Reine Funktion, [today] kommt von aussen — testbar ohne Systemuhr.
 */
fun coverageWarning(
    coverageEnd: LocalDate?,
    today: LocalDate,
    warnWithinDays: Long = 45,
): String? {
    if (coverageEnd == null) return null
    if (coverageEnd.isAfter(today.plusDays(warnWithinDays))) return null
    val ende = DATE.format(coverageEnd)
    // `isBefore(today)` und nicht `<=`: der letzte abgedeckte Tag IST noch
    // abgedeckt. „Endeten am heute" waere am letzten Tag eine Falschaussage.
    return if (coverageEnd.isBefore(today)) {
        "Offline-Reserve: gebündelte amtliche Zeiten endeten am $ende — " +
            "ohne Netz tragen jetzt die zuvor geladenen amtlichen Zeiten, " +
            "und wo keine vorliegen, die eigene Berechnung."
    } else {
        "Offline-Reserve: gebündelte amtliche Zeiten nur noch bis $ende — " +
            "danach tragen ohne Netz die zuvor geladenen amtlichen Zeiten, " +
            "und wo keine vorliegen, die eigene Berechnung."
    }
}

/**
 * EINE Zeile fuer EINEN Favoriten — die Antwort auf „ist Istanbul versorgt?",
 * ohne dass der Nutzer hinschalten muss.
 *
 * Sie steht in DIESER Datei und nicht in einer eigenen: sie macht aus
 * demselben [OfficialStatus] denselben deutschen Klartext wie
 * [officialStatusText], nur kurz — und teilt sich dessen Datumsformat. In
 * einer Nachbardatei waeren die Formatter entweder doppelt oder von aussen
 * geliehen.
 *
 * Drei Lagen, auf einen Blick unterscheidbar:
 * - versorgt: `Nürnberg · bis 04.07.2027 · bestätigt`
 * - keine Zeiten: `Istanbul · noch keine Zeiten · Kein Netz (06.08., 10:06)`
 * - Abdeckung laeuft ab: `Regensburg · nur noch 12 Tage · unbestätigt`
 *
 * Die Schwelle fuer „laeuft ab" ist [MIN_FUTURE_DAYS] und damit GENAU die,
 * an der die App selbst nachlaedt (dieselbe `isBefore`-Bedingung wie in
 * [needsRefresh], nicht eine nachgebaute Tageszaehlung). Eine Zeile, die
 * warnt, waehrend die App laengst von selbst nachlaedt, waere Laerm.
 *
 * **Sie ist EIN Eintrag je Favorit, aber nicht auf eine Bildschirmzeile
 * festgelegt** — und bekommt bewusst kein `maxLines`. Mit einem Fehlergrund
 * wird sie lang (im Flugmodus rund 110 Zeichen: „Istanbul · noch keine
 * Zeiten · Direktabruf: Kein Netz · Proxy: Kein Netz · ezanvakti: Kein Netz
 * (06.08., 10:06)"), in `bodySmall` also zwei bis drei umgebrochene Zeilen.
 * Das ist die richtige Richtung: einen Fehlergrund halb zu verstecken ist
 * schlechter als zwei Zeilen zu belegen. Die frueher hier behauptete
 * Vorgabe „der Platz ist eine Zeile" war falsch und ist der Grund fuer
 * diesen Absatz.
 *
 * Keine Zahl behauptet hier etwas anderes, als sie ist: die Restzeit kommt
 * aus `coveredUntil`, und aus der [Verification] wird KEINE Zahl uebernommen
 * — `comparedDays` ist das Konfliktfenster, nicht die Abdeckung (die Lehre
 * aus Task 8). Fuer den Gegencheck genuegt ein Kurzwort; den vollen Satz
 * (`verificationLine`) bekommt der aktive Ort, dort ist Platz dafuer.
 *
 * Reine Funktion, [today] und [zone] kommen von aussen — testbar ohne
 * Systemuhr.
 */
fun favoriteStatusLine(
    name: String,
    status: OfficialStatus,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val coveredUntil = status.coveredUntil
    val coverage = when {
        // Kein Zeitplan: dann interessiert nicht die Abdeckung, sondern was
        // der letzte Versuch ergeben hat.
        coveredUntil == null -> "noch keine Zeiten · ${attemptText(status, zone)}"
        coveredUntil.isBefore(today) -> "Abdeckung abgelaufen"
        // Dieselbe Bedingung wie in [needsRefresh]: duenn, sobald die
        // Abdeckung VOR `today + MIN_FUTURE_DAYS` endet. Genau auf der
        // Schwelle also noch nicht.
        coveredUntil.isBefore(today.plusDays(MIN_FUTURE_DAYS)) -> remainingText(today, coveredUntil)
        else -> "bis ${DATE.format(coveredUntil)}"
    }
    // Das Kurzwort nur, wo es sich auf Zeiten beziehen kann, die es
    // tatsaechlich noch gibt. „Bestätigt" hinter einer ABGELAUFENEN
    // Abdeckung waere ein Guetewort ueber Zeiten, die nichts mehr abdecken —
    // dieselbe Klasse von Falschaussage wie die vier, die Task 8 korrigieren
    // musste. Die Notiz selbst bleibt im Kopf; sie wird hier nur nicht mehr
    // behauptet.
    val note = if (coveredUntil == null || coveredUntil.isBefore(today)) {
        null
    } else {
        verificationWord(status.verification)
    }
    return listOfNotNull(name, coverage, note).joinToString(" · ")
}

/** Was der letzte Abrufversuch an einem Ort OHNE Zeiten ergeben hat. Ein
 *  Versuch ohne Fehler wird nicht zu einem Fehler umgedeutet — und wo es
 *  keinen Versuch gab, wird auch keiner behauptet. */
private fun attemptText(status: OfficialStatus, zone: ZoneId): String {
    val attempt = status.lastAttemptEpochMs
        ?: return "noch kein Versuch"
    val stamp = SHORT_STAMP.format(Instant.ofEpochMilli(attempt).atZone(zone))
    return status.lastError?.let { "$it ($stamp)" } ?: "letzter Versuch $stamp"
}

/** Restzeit in Tagen. Einzahl und „heute" im Code entschieden, nicht einem
 *  `%d` ueberlassen: „nur noch 1 Tage" und „nur noch 0 Tage" waeren falsch
 *  bzw. irrefuehrend — der letzte abgedeckte Tag IST heute. */
private fun remainingText(today: LocalDate, coveredUntil: LocalDate): String =
    when (val days = ChronoUnit.DAYS.between(today, coveredUntil)) {
        0L -> "nur noch heute"
        1L -> "nur noch 1 Tag"
        else -> "nur noch $days Tage"
    }

/** Ein Wort statt eines Satzes — je Favorit EIN Eintrag, nicht der
 *  mehrzeilige Statusblock des aktiven Orts. Der volle Wortlaut steht in
 *  [verificationLine] und bleibt dem aktiven Ort vorbehalten; hier wird
 *  nicht gekuerzt, sondern eigens benannt, damit kein halber Satz entsteht.
 *  `null` bei fehlender Notiz und bei
 *  [VerificationNote.NONE] — genau wie [verificationLine], aus demselben
 *  Grund: es gibt dann nichts zu berichten. */
private fun verificationWord(verification: Verification?): String? = when (verification?.note) {
    null, VerificationNote.NONE -> null
    VerificationNote.VERIFIED -> "bestätigt"
    VerificationNote.DRIFT -> "kleine Abweichung"
    VerificationNote.GAP_FILLED -> "aus Kontrollquellen"
    VerificationNote.CONFLICT_OVERRIDDEN -> "geprüfter Kontrollstand"
    VerificationNote.CONFLICT_UNRESOLVED -> "Quellen uneinig"
    VerificationNote.UNVERIFIED_SINGLE -> "unbestätigt"
}
