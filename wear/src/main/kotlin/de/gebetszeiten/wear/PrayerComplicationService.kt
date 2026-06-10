package de.gebetszeiten.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Watch-face complication showing the next prayer. Two modes (toggled in the
 * watch app, [WearSettings.showRemaining]):
 *  - clock time (default): static "Asr · 17:37"; nothing ever ticks.
 *  - remaining: "Asr · 1h20m" counting down — rendered/ticked by the SYSTEM
 *    during the watch face's normal redraw, so still no app wake-ups.
 * Either way a [TimeRange] valid until the prayer makes the system re-request
 * on its own at the transition. No timers, ~5–6 recomputes/day.
 */
class PrayerComplicationService : SuspendingComplicationDataSourceService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return build("Asr", PlainComplicationText.Builder("17:36").build(), "17:36", null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val zone = ZoneId.systemDefault()
        val location = WearSettings.location(applicationContext)
        val next = WearPrayer.next(location, zone, ZonedDateTime.now(zone))
        val timeStr = next.second.format(timeFormat)
        val text = if (WearSettings.showRemaining(applicationContext)) {
            // System-rendered countdown — no app involvement while it ticks.
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.SHORT_DUAL_UNIT,
                CountDownTimeReference(next.second.toInstant()),
            ).build()
        } else {
            PlainComplicationText.Builder(timeStr).build()
        }
        return build(
            title = next.first.label(),
            text = text,
            timeStr = timeStr,
            // Valid until the prayer arrives → the system re-requests then.
            validUntil = TimeRange.before(next.second.toInstant()),
        )
    }

    private fun build(
        title: String,
        text: ComplicationText,
        timeStr: String,
        validUntil: TimeRange?,
    ): ComplicationData {
        val builder = ShortTextComplicationData.Builder(
            text = text,
            contentDescription = PlainComplicationText.Builder("Nächstes Gebet $title $timeStr").build(),
        ).setTitle(PlainComplicationText.Builder(title).build())
        if (validUntil != null) builder.setValidTimeRange(validUntil)
        return builder.build()
    }
}
