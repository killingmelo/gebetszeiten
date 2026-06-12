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
        // Toggle the wrist vibration at prayer times; gives one sample buzz
        // when enabling so the strength is immediately judgeable.
        findViewById<TextView>(R.id.vibrateLabel).setOnClickListener {
            val enabled = runBlocking { !WearSettings.vibrate(applicationContext) }
            runBlocking { WearSettings.saveVibrate(applicationContext, enabled) }
            runBlocking { WearVibration.reschedule(applicationContext) }
            if (enabled) WearVibration.buzz(this)
            render()
        }
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
            // Exact is free here: recomputed on every open. Unified "noch …"
            // wording across all surfaces.
            val min = java.time.Duration.between(now, next.second).toMinutes().coerceAtLeast(0)
            heroTime.text = if (min >= 60) "noch ${min / 60} Std ${min % 60} Min" else "noch $min Min"
            heroTime.textSize = 24f
        } else {
            heroTime.text = next.second.format(timeFormat)
            heroTime.textSize = 42f
        }
        findViewById<TextView>(R.id.cityLabel).text = city

        // Karaha indicator: shown during a makruh window ("bis") and in the
        // 15 minutes before one ("ab"). Recomputed on every open — no alarms.
        val karahaLabel = findViewById<TextView>(R.id.karahaLabel)
        val karaha = de.gebetszeiten.core.prayertimes.Karaha
        when (val status = karaha.status(karaha.windows(WearPrayer.today(location, zone)), now)) {
            is de.gebetszeiten.core.prayertimes.Karaha.Status.Active -> {
                karahaLabel.text = "⚠️ Karaha bis ${status.window.end.format(timeFormat)}"
                karahaLabel.setTextColor(getColor(R.color.wear_warn))
                karahaLabel.visibility = android.view.View.VISIBLE
            }
            is de.gebetszeiten.core.prayertimes.Karaha.Status.Soon -> {
                karahaLabel.text = "⚠️ Karaha ab ${status.window.start.format(timeFormat)}"
                karahaLabel.setTextColor(getColor(R.color.wear_dim))
                karahaLabel.visibility = android.view.View.VISIBLE
            }
            de.gebetszeiten.core.prayertimes.Karaha.Status.None ->
                karahaLabel.visibility = android.view.View.GONE
        }

        findViewById<TextView>(R.id.modeLabel).text =
            if (showRemaining) "Anzeige: Restzeit" else "Anzeige: Uhrzeit"
        val vibrate = runBlocking { WearSettings.vibrate(applicationContext) }
        findViewById<TextView>(R.id.vibrateLabel).text =
            if (vibrate) "Vibration: An" else "Vibration: Aus"

        val list = findViewById<android.widget.LinearLayout>(R.id.upcomingList)
        list.removeAllViews()
        val density = resources.displayMetrics.density
        // Upcoming prayers, plus today's sunrise while still ahead — it ends
        // the Fajr window (the only prayer whose end isn't the next prayer).
        val rows = buildList {
            upcoming.drop(1).forEach { (prayer, time) ->
                val tomorrow = time.toLocalDate() != now.toLocalDate()
                add((if (tomorrow) "${prayer.label()} · morgen" else prayer.label()) to time)
            }
            val sunrise = WearPrayer.today(location, zone).sunrise
            if (sunrise.isAfter(now)) add("Sonnenaufgang" to sunrise)
        }.sortedBy { it.second }
        rows.forEach { (label, time) ->
            list.addView(
                android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(0, (5 * density).toInt(), 0, (5 * density).toInt())
                    addView(
                        TextView(context).apply {
                            text = label
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
