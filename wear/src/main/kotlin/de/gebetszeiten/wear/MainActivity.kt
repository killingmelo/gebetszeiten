package de.gebetszeiten.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Minimal on-watch screen: next prayer headline plus today's six times. */
class MainActivity : Activity() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val location = runBlocking { WearSettings.location(applicationContext) }

        val next = WearPrayer.next(location, zone, now)
        findViewById<TextView>(R.id.title).text =
            "${next.first.label()} · ${next.second.format(timeFormat)}"

        val today = WearPrayer.today(location, zone)
        findViewById<TextView>(R.id.times).text = today.ordered().joinToString("\n") { (p, t) ->
            "${p.label()}   ${t.format(timeFormat)}"
        }
    }
}
