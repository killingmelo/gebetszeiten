package de.gebetszeiten.wear

import androidx.wear.watchface.complications.data.ComplicationData
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

/**
 * Watch-face complication for the next prayer.
 *
 * Battery model: the countdown text is a [TimeDifferenceComplicationText] —
 * the **system** renders and ticks it during the watch face's normal redraw, so
 * our app is never woken just to update it. We also set a [TimeRange] valid only
 * until the next prayer; when it expires (the prayer arrives) the system
 * re-requests on its own, so we recompute ~5–6×/day with no alarms of our own.
 * Result: glancing costs nothing extra, yet a live "Asr · in 3 Std 24 Min" is
 * always shown.
 */
class PrayerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("3 Std").build(),
            contentDescription = PlainComplicationText.Builder("Nächstes Gebet Asr").build(),
        ).setTitle(PlainComplicationText.Builder("Asr").build()).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val zone = ZoneId.systemDefault()
        val location = WearSettings.location(applicationContext)
        val next = WearPrayer.next(location, zone, ZonedDateTime.now(zone))
        val nextInstant = next.second.toInstant()

        // System-rendered live count-down to the next prayer (no app wake-ups).
        val countdown = TimeDifferenceComplicationText.Builder(
            style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
            countDownTimeReference = CountDownTimeReference(nextInstant),
        ).build()

        return ShortTextComplicationData.Builder(
            text = countdown,
            contentDescription = PlainComplicationText.Builder("Nächstes Gebet ${next.first.label()}").build(),
        )
            .setTitle(PlainComplicationText.Builder(next.first.label()).build())
            // Valid until the prayer arrives → the system re-requests then.
            .setValidTimeRange(TimeRange.before(nextInstant))
            .build()
    }
}
