package de.gebetszeiten.wear

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import de.gebetszeiten.core.prayertimes.Prayer
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** On-watch screen styled like the phone app: a prominent next-prayer hero
 *  plus the day's times with the next one accented. */
class MainActivity : Activity() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val location = runBlocking { WearSettings.location(applicationContext) }
        val next = WearPrayer.next(location, zone, now)
        val today = WearPrayer.today(location, zone)

        findViewById<TextView>(R.id.heroName).text = next.first.label()
        findViewById<TextView>(R.id.heroTime).text = next.second.format(timeFormat)
        findViewById<TextView>(R.id.heroRemaining).text =
            remainingText(Duration.between(now, next.second))

        val container = findViewById<LinearLayout>(R.id.timesContainer)
        val text = getColor(R.color.wear_text)

        // Only what's still ahead today, after the next prayer (the hero already
        // shows the next one). No past times, no sunrise, and not the next itself
        // (excluded by prayer identity — robust to sub-minute calc differences).
        val upcoming = today.ordered().filter {
            it.first != Prayer.SUNRISE && it.first != next.first && it.second.isAfter(now)
        }
        findViewById<View>(R.id.divider).visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE
        upcoming.forEach { (prayer, time) ->
            container.addView(row(prayer.label(), time.format(timeFormat), text, bold = false))
        }
    }

    private fun row(name: String, time: String, color: Int, bold: Boolean): LinearLayout {
        val pad = dp(5)
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp(6), pad, dp(6), pad)
        }
        val nameView = TextView(this).apply {
            this.text = name
            textSize = 15f
            setTextColor(color)
            setTypeface(typeface, style)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val timeView = TextView(this).apply {
            this.text = time
            textSize = 15f
            setTextColor(color)
            setTypeface(typeface, style)
            gravity = Gravity.END
        }
        rowLayout.addView(nameView)
        rowLayout.addView(timeView)
        return rowLayout
    }

    private fun remainingText(d: Duration): String {
        if (d.isNegative || d.isZero) return "jetzt"
        val h = d.toHours()
        val m = d.toMinutes() % 60
        return when {
            h > 0 -> "in $h Std $m Min"
            m > 0 -> "in $m Min"
            else -> "gleich"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
