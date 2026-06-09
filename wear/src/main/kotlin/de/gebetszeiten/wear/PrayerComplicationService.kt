package de.gebetszeiten.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Watch-face complication showing the next prayer (name) and its time, static.
 *
 * Battery model: the text never changes, so nothing has to tick — the watch
 * already shows the current time next to it, so a fixed "Asr · 17:37" is enough
 * to gauge the remaining time at a glance. We set a [TimeRange] valid only until
 * the next prayer; when it expires (the prayer arrives) the system re-requests
 * on its own and we hand back the following prayer. No timers, no app alarms,
 * ~5–6 recomputes/day, all system-driven.
 */
class PrayerComplicationService : SuspendingComplicationDataSourceService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return shortText("Asr", "17:36", null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val zone = ZoneId.systemDefault()
        val location = WearSettings.location(applicationContext)
        val next = WearPrayer.next(location, zone, ZonedDateTime.now(zone))
        return shortText(
            title = next.first.label(),
            time = next.second.format(timeFormat),
            // Valid until the prayer arrives → the system re-requests then.
            validUntil = TimeRange.before(next.second.toInstant()),
        )
    }

    private fun shortText(title: String, time: String, validUntil: TimeRange?): ComplicationData {
        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(time).build(),
            contentDescription = PlainComplicationText.Builder("Nächstes Gebet $title $time").build(),
        ).setTitle(PlainComplicationText.Builder(title).build())
        if (validUntil != null) builder.setValidTimeRange(validUntil)
        return builder.build()
    }
}
