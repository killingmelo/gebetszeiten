package de.gebetszeiten.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Watch-face complication showing the next prayer (title) and its time. */
class PrayerComplicationService : SuspendingComplicationDataSourceService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return shortText("Asr", "17:36")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val zone = ZoneId.systemDefault()
        val location = WearSettings.location(applicationContext)
        val next = WearPrayer.next(location, zone, ZonedDateTime.now(zone))
        return shortText(next.first.label(), next.second.format(timeFormat))
    }

    private fun shortText(title: String, time: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(time).build(),
            contentDescription = PlainComplicationText.Builder("$title $time").build(),
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .build()
}
