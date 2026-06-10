package de.gebetszeiten.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal, battery-first watch screen: just the next prayer's name and time.
 *
 * Update model: no timer, no scheduled alarm, no per-minute ticking. The next
 * prayer is recomputed in [onStart] — i.e. every time the screen becomes
 * visible (when you actually look at the watch). A static clock time then needs
 * no further updates and never goes stale, which is strictly more battery-saving
 * than a live count-down or a scheduled wake-up at each prayer transition.
 */
class MainActivity : Activity() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Tapping the city opens the picker; times refresh in onStart on return.
        findViewById<TextView>(R.id.cityLabel).setOnClickListener {
            startActivity(android.content.Intent(this, CityPickerActivity::class.java))
        }
        // Tapping the mode line (or the big value) flips clock time ⇄ remaining.
        val toggle = { _: android.view.View ->
            val next = runBlocking { !WearSettings.showRemaining(applicationContext) }
            runBlocking { WearSettings.saveShowRemaining(applicationContext, next) }
            render()
            // The complication mirrors the mode — refresh it once.
            androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
                .create(this, android.content.ComponentName(this, PrayerComplicationService::class.java))
                .requestUpdateAll()
        }
        findViewById<TextView>(R.id.modeLabel).setOnClickListener(toggle)
        findViewById<TextView>(R.id.heroTime).setOnClickListener(toggle)
    }

    // Recompute on every show so the next prayer is always current without any
    // background work or timers.
    override fun onStart() {
        super.onStart()
        render()
    }

    private fun render() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val location = runBlocking { WearSettings.location(applicationContext) }
        val city = runBlocking { WearSettings.city(applicationContext) }
        val showRemaining = runBlocking { WearSettings.showRemaining(applicationContext) }
        // Next prayer (hero) + the following ones (overview list below).
        val upcoming = WearPrayer.upcoming(location, zone, now, count = 6)
        val next = upcoming.first()

        findViewById<TextView>(R.id.heroName).text = next.first.label()
        val heroTime = findViewById<TextView>(R.id.heroTime)
        if (showRemaining) {
            val min = java.time.Duration.between(now, next.second).toMinutes().coerceAtLeast(0)
            heroTime.text = if (min >= 60) "in ${min / 60} Std ${min % 60} Min" else "in $min Min"
            heroTime.textSize = 24f
        } else {
            heroTime.text = next.second.format(timeFormat)
            heroTime.textSize = 42f
        }
        findViewById<TextView>(R.id.cityLabel).text = city
        findViewById<TextView>(R.id.modeLabel).text =
            if (showRemaining) "Anzeige: Restzeit" else "Anzeige: Uhrzeit"

        val list = findViewById<android.widget.LinearLayout>(R.id.upcomingList)
        list.removeAllViews()
        val density = resources.displayMetrics.density
        upcoming.drop(1).forEach { (prayer, time) ->
            val tomorrow = time.toLocalDate() != now.toLocalDate()
            list.addView(
                android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(0, (5 * density).toInt(), 0, (5 * density).toInt())
                    addView(
                        TextView(context).apply {
                            text = if (tomorrow) "${prayer.label()} · morgen" else prayer.label()
                            setTextColor(getColor(R.color.wear_dim))
                            textSize = 14f
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                            )
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = time.format(timeFormat)
                            setTextColor(getColor(R.color.wear_text))
                            textSize = 14f
                        },
                    )
                },
            )
        }
    }
}
