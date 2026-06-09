package de.gebetszeiten.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal, battery-first watch screen: just the next prayer's name and time,
 * computed once. A static clock time needs no updates and never goes stale —
 * strictly more battery-saving than a live count-down (which would require
 * per-minute ticks or wake-ups).
 */
class MainActivity : Activity() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val location = runBlocking { WearSettings.location(applicationContext) }
        val next = WearPrayer.next(location, zone, now)

        findViewById<TextView>(R.id.heroName).text = next.first.label()
        findViewById<TextView>(R.id.heroTime).text = next.second.format(timeFormat)
    }
}
