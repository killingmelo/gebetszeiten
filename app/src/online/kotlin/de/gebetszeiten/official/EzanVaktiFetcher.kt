package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Dritte Quelle: `ezanvakti.emushaf.net` — dieselbe Diyanet-Standort-ID wie
 * der Jahresabruf, aber JSON statt HTML und ein eigener Betreiber. Damit ist
 * sie ein echter 1:1-Gegenzeuge und nicht nur eine zweite Meinung über
 * denselben Scrape.
 *
 * Geliefert wird ein rollierendes Fenster von wenigen Wochen (32 Tage am
 * 06.09.2026, 30 am 09.09.2026) — siehe [parseSchedule].
 */
class EzanVaktiFetcher {

    private val base = "https://ezanvakti.emushaf.net/vakitler"

    /** Rollierendes Fenster für eine bekannte Diyanet-ID. Netzaufruf dünn,
     *  Parser rein — wie beim Nachbarn [DiyanetProxyFetcher]. */
    suspend fun fetchById(locationId: Int): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            parseSchedule(httpGet("$base/$locationId"))
        }

    /**
     * Rein und testbar: Antwortkörper → Zeitplan. Kein Netz, kein Android.
     *
     * Kein Wurf bei einem leeren Array — das ergibt eine leere Map, und das
     * Quorum liest daraus „diese Quelle hat nichts geliefert". Ein Körper, der
     * gar kein JSON-Array ist, darf und soll dagegen werfen: „leer" und
     * „kaputt" sind zwei verschiedene Zustände, und nur der Aufrufer
     * (`attempt`) darf daraus „Quelle gescheitert" machen. Deshalb wird hier
     * die `JSONException` NICHT gefangen.
     *
     * Keine Prüfung der Fensterlänge: das Fenster rolliert und war schon 30
     * wie 32 Tage lang. Der Parser nimmt, was da ist.
     */
    internal fun parseSchedule(body: String): Map<LocalDate, SixTimes> {
        val arr = JSONArray(body)
        val out = LinkedHashMap<LocalDate, SixTimes>(arr.length())
        for (i in 0 until arr.length()) {
            val (datum, zeiten) = parseTag(arr.opt(i)) ?: continue
            out[datum] = zeiten
        }
        return out
    }

    /**
     * Ein Tag oder `null`. Eine Zeile mit unlesbarem Datum, unlesbarer Zeit
     * oder FEHLENDEM Pflichtfeld wird verworfen, die übrigen überleben —
     * genau wie bei `ScheduleText.parse`: ein einzelner Schluckauf der
     * Gegenquelle darf den Gegencheck nicht ganz ausfallen lassen. Ein
     * fehlendes Feld wird bewusst NICHT mit einer Ersatzzeit gefüllt: halb
     * erfundene Gebetszeiten wären schlimmer als keine.
     */
    private fun parseTag(element: Any?): Pair<LocalDate, SixTimes>? {
        val o = element as? JSONObject ?: return null
        return try {
            // FALLE: `MiladiTarihKisaIso8601` heißt so, ist aber KEIN ISO —
            // das Feld trägt "05.09.2026", also dasselbe dd.MM.yyyy wie
            // `MiladiTarihKisa`. `LocalDate.parse` darauf wirft. Der Nachbar
            // DiyanetProxyFetcher liest ein echtes ISO-Datum
            // (`getString("date").substring(0, 10)`) — das darf hier NICHT
            // kopiert werden. Das einzige echte ISO-Feld wäre
            // `MiladiTarihUzunIso8601` ("2026-09-05T00:00:00.0000000+03:00"),
            // ein Umweg über einen Zeitzonen-Offset, den wir nicht brauchen
            // und der eine eigene Fehlerquelle wäre.
            LocalDate.parse(o.getString("MiladiTarihKisa"), DATUM) to SixTimes(
                // FALLE: `Gunes` und `Aksam` sind die GEBETSZEITEN.
                // `GunesDogus`/`GunesBatis` sind der astronomische Auf- und
                // Untergang und dürfen NIE gelesen werden — sie liegen 7
                // Minuten daneben (06:22 gegen 06:29, 19:33 gegen 19:26).
                // Klein genug, um plausibel auszusehen, groß genug, um den
                // Gegencheck (maxDriftMinutes = 2) in CONFLICT zu treiben:
                // die App würde dann den Jahresabruf verdächtigen, obwohl
                // unser eigener Parser der Falsche ist.
                fajr = zeit(o, "Imsak"),
                sunrise = zeit(o, "Gunes"),
                dhuhr = zeit(o, "Ogle"),
                asr = zeit(o, "Ikindi"),
                maghrib = zeit(o, "Aksam"),
                isha = zeit(o, "Yatsi"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** `getString` (nicht `optString`): ein fehlendes Feld und ein JSON-`null`
     *  werfen hier, und der ganze Tag fällt in [parseTag] heraus. */
    private fun zeit(o: JSONObject, feld: String): LocalTime =
        LocalTime.parse(o.getString(feld), UHRZEIT)

    private companion object {
        /** `uuuu` statt `yyyy` und STRICT: so wird ein Unmögliches wie
         *  "31.02.2026" abgelehnt statt still auf den 28.02. gerückt — ein
         *  verschobenes Datum würde richtige Zeiten auf den falschen Tag legen. */
        val DATUM: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT)

        /** `H:mm` nimmt "06:22" wie "6:22". Die echte Antwort füllt immer auf,
         *  das ist also reine Vorsorge: die Form bleibt eindeutig (Trennzeichen
         *  `:`, Minute fest zweistellig), und ein Formatierungs-Schluckauf der
         *  Quelle soll den dritten Zeugen des Quorums nicht stumm machen.
         *
         *  STRICT auch hier, und das ist NICHT bloß Symmetrie zu [DATUM]: der
         *  Standard-Resolver lässt `"24:00"` durch und macht daraus `00:00` —
         *  ein `"Yatsi":"24:00"` läge damit auf Mitternacht DESSELBEN Tages,
         *  also Isha vor Fajr, ohne dass irgendetwas wirft. Mit STRICT fällt
         *  der Tag heraus, statt eine unmögliche Reihenfolge zu erzeugen. */
        val UHRZEIT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("H:mm")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
