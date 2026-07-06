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
        val (showRemaining, next) = runBlocking {
            val location = WearSettings.location(applicationContext)
            WearSettings.showRemaining(applicationContext) to WearPrayer.next(applicationContext, location, zone, now)
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
            // Tapping the complication opens the watch app.
            .setTapAction(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (validUntil != null) builder.setValidTimeRange(validUntil)
        return builder.build()
    }
}
