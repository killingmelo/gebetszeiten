package de.gebetszeiten.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Watch-face complication showing the next prayer. Two modes (toggled in the
 * watch app, [WearSettings.showRemaining]):
 *
 *  - clock time (default): static "Asr · 17:37"; nothing ever ticks.
 *  - remaining: a pre-computed TIMELINE. The system renderer always rounds a
 *    time difference UP ("4 Std" with 3:04 left — misleading) and falls back
 *    to a single unit in small slots, so instead we hand the system one
 *    floor-rounded static entry per hour ("3+ Std", "2+ Std", …) and only in
 *    the final hour a minute-ticking system countdown ("42 Min"). The system
 *    switches entries itself — still no timers and no app wake-ups.
 *
 * Either way a validity bound makes the system re-request on its own at the
 * prayer transition (~5–6 recomputes/day).
 */
class PrayerComplicationService : ComplicationDataSourceService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return data("Asr", PlainComplicationText.Builder("17:36").build(), "17:36", null)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        // Sofort aus dem Cache zeichnen — kein Netz, aber wie in
        // `PrayerTileService.onTileRequest` auch kein Millisekundengeschaeft:
        // `WearPrayer.next` ruft (ueber `upcoming`) `daily()` fuer zwei Tage
        // auf, macht bis zu sechs DataStore-Reads insgesamt. Weit unter der
        // ANR-Schwelle.
        val (showRemaining, next) = runBlocking {
            val location = WearSettings.location(applicationContext)
            WearSettings.showRemaining(applicationContext) to WearPrayer.next(applicationContext, location, zone, now)
        }
        // Amtliche Zeiten NEBENHER auffrischen, nicht Teil dieser Antwort —
        // siehe Kommentar in `PrayerTileService.onTileRequest`:
        // `launchWearRefresh` laeuft im datei-eigenen Scope von
        // `WearRefresh.kt`, nicht in einem (hier gar nicht mehr vorhandenen)
        // Service-Scope, der laengst weg waere, bevor ein langsamer Abruf
        // fertig ist. Stoesst bei Erfolg jetzt IMMER Komplikation UND Kachel
        // an (Fix-Runde 2, Important 1) - `applicationContext` statt `this`,
        // damit das Lambda diese Service-INSTANZ nicht ueber ihr `onDestroy`
        // hinaus festhaelt (Fix-Runde 4).
        launchWearRefresh(applicationContext)

        // Leerfall (Task 16): weder amtliche Zeiten noch der Notausgang
        // liefern etwas fuer heute ODER morgen — kurzer Satz statt Zeit,
        // dieselbe Regel wie `WearPrayer.next`/`PrayerProvider.daily`.
        if (next == null) {
            listener.onComplicationData(noTimesData())
            return
        }
        val title = next.first.label()
        val timeStr = next.second.format(timeFormat)
        val prayerAt = next.second.toInstant()

        if (!showRemaining) {
            listener.onComplicationData(
                data(title, PlainComplicationText.Builder(timeStr).build(), timeStr, TimeRange.before(prayerAt)),
            )
            return
        }

        // Floor-rounded hour entries, then a minute countdown for the last hour.
        val entries = mutableListOf<TimelineEntry>()
        val wholeHours = Duration.between(now.toInstant(), prayerAt).toHours()
        for (h in 1..wholeHours) {
            entries += TimelineEntry(
                TimeInterval(prayerAt.minus(Duration.ofHours(h + 1)), prayerAt.minus(Duration.ofHours(h))),
                data(title, PlainComplicationText.Builder("${h}+ Std").build(), timeStr, TimeRange.before(prayerAt)),
            )
        }
        entries += TimelineEntry(
            TimeInterval(prayerAt.minus(Duration.ofHours(1)), prayerAt),
            data(
                title,
                TimeDifferenceComplicationText.Builder(
                    TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                    CountDownTimeReference(prayerAt),
                ).build(),
                timeStr,
                TimeRange.before(prayerAt),
            ),
        )
        listener.onComplicationDataTimeline(
            ComplicationDataTimeline(
                // Fallback outside all entries (shouldn't normally show): clock time.
                data(title, PlainComplicationText.Builder(timeStr).build(), timeStr, TimeRange.before(prayerAt)),
                entries,
            ),
        )
    }

    // Kein eigener Scope mehr (Fix-Runde 3): `launchWearRefresh` laeuft im
    // datei-eigenen Scope von `WearRefresh.kt` — siehe Kommentar in
    // `PrayerTileService`.

    /** Tapping the complication opens the watch app — geteilt zwischen
     *  [data] und [noTimesData], damit die App-Oeffnung nicht zweimal
     *  geschrieben wird. */
    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun data(
        title: String,
        text: ComplicationText,
        timeStr: String,
        validUntil: TimeRange?,
    ): ComplicationData {
        val builder = ShortTextComplicationData.Builder(
            text = text,
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_desc, title, timeStr)).build(),
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(openAppPendingIntent())
        if (validUntil != null) builder.setValidTimeRange(validUntil)
        return builder.build()
    }

    /** Leerfall-Komplikation (Task 16): ohne Titel (kein "naechstes Gebet")
     *  — Tippen oeffnet weiterhin die App, wie im Normalfall. Kein
     *  `setValidTimeRange`: ohne bekannte naechste Zeit gibt es keinen
     *  Zeitpunkt, an dem diese Antwort automatisch ablaeuft; das System
     *  fragt stattdessen bei Bedarf erneut an.
     *
     *  Fix-Runde 1/2/3, Important 4: `ShortTextComplicationData` ist
     *  vertraglich auf rund sieben Zeichen ausgelegt (dort standen vorher
     *  "17:37"/"Asr") — der lange Satz aus `no_times_notice` ("Keine
     *  amtlichen Zeiten", 22 Zeichen) wuerde im sichtbaren Text abgeschnitten,
     *  und selbst die erste Kuerzung ("keine Zeiten", 12 Zeichen) noch.
     *
     *  "Keine" (5 Zeichen) passte zwar in die Zeichengrenze, wurde aber
     *  bewusst NICHT gewaehlt: viele Zifferblaetter zeigen den `title` einer
     *  SHORT_TEXT-Komplikation gar nicht an, und diese Antwort setzt ohnehin
     *  keinen (siehe oben, kein `.setTitle(...)`) — im Slot stuende dann ein
     *  alleinstehendes Artikelwort ohne Bezugswort, direkt neben "Asr
     *  17:37". Das liest sich wie ein Anzeigefehler, nicht wie eine Aussage.
     *
     *  `no_times_notice_short` ist deshalb ein Geviertstrich ("—"): er liest
     *  sich als "Wert fehlt" statt als abgebrochener Satz. Die volle Aussage
     *  geht dabei NICHT verloren — sie steht als `contentDescription`, damit
     *  Vorlesedienste trotzdem den vollen Satz bekommen. */
    private fun noTimesData(): ComplicationData {
        val short = PlainComplicationText.Builder(getString(R.string.no_times_notice_short)).build()
        val full = PlainComplicationText.Builder(getString(R.string.no_times_notice)).build()
        return ShortTextComplicationData.Builder(
            text = short,
            contentDescription = full,
        )
            .setTapAction(openAppPendingIntent())
            .build()
    }
}
