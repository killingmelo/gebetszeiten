package de.gebetszeiten.wear

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *
 * Reads and the prayer calculation run off the main thread (one batched
 * [WearSettings.snapshot] read); only the view update touches the UI thread.
 */
class MainActivity : Activity() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val scope: CoroutineScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Tapping the city opens the picker; times refresh in onStart on return.
        findViewById<TextView>(R.id.cityLabel).setOnClickListener {
            startActivity(android.content.Intent(this, CityPickerActivity::class.java))
        }
        // Tapping the mode line (or the big value) flips clock time ⇄ remaining.
        val toggleMode = View.OnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) {
                    WearSettings.saveShowRemaining(
                        applicationContext,
                        !WearSettings.showRemaining(applicationContext),
                    )
                }
                // The complication mirrors the mode — refresh it once.
                androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
                    .create(this@MainActivity, android.content.ComponentName(this@MainActivity, PrayerComplicationService::class.java))
                    .requestUpdateAll()
                refresh()
            }
        }
        findViewById<TextView>(R.id.modeLabel).setOnClickListener(toggleMode)
        findViewById<TextView>(R.id.heroTime).setOnClickListener(toggleMode)
        // Toggle the wrist vibration at prayer times; gives one sample buzz
        // when enabling so the strength is immediately judgeable.
        findViewById<TextView>(R.id.vibrateLabel).setOnClickListener {
            scope.launch {
                val enabled = withContext(Dispatchers.IO) {
                    val next = !WearSettings.vibrate(applicationContext)
                    WearSettings.saveVibrate(applicationContext, next)
                    WearVibration.reschedule(applicationContext)
                    next
                }
                if (enabled) WearVibration.buzz(this@MainActivity)
                refresh()
            }
        }
    }

    // Recompute on every show so the next prayer is always current without any
    // background work or timers.
    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** One snapshot read + prayer calc off the main thread, then apply on it. */
    private fun refresh() {
        scope.launch {
            val state = withContext(Dispatchers.Default) { buildState() }
            applyState(state)
        }
    }

    /** Everything the screen needs, computed off the UI thread. */
    private data class KarahaUi(val text: String, val warn: Boolean)
    private data class ViewState(
        val heroName: String,
        val heroTimeText: String,
        val heroTimeSize: Float,
        val heroTimeDesc: String,
        val city: String,
        val karaha: KarahaUi?,
        val modeText: String,
        val vibrateText: String,
        val rows: List<Pair<String, String>>,
    )

    private suspend fun buildState(): ViewState {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val s = WearSettings.snapshot(applicationContext)

        val upcoming = WearPrayer.upcoming(s.location, zone, now, count = 6)
        val next = upcoming.first()
        val name = next.first.label()

        val heroTimeText: String
        val heroTimeSize: Float
        val heroTimeDesc: String
        if (s.showRemaining) {
            val min = java.time.Duration.between(now, next.second).toMinutes().coerceAtLeast(0)
            heroTimeText = if (min >= 60) "noch ${min / 60} Std ${min % 60} Min" else "noch $min Min"
            heroTimeSize = 24f
            heroTimeDesc = heroTimeText
        } else {
            heroTimeText = next.second.format(timeFormat)
            heroTimeSize = 42f
            heroTimeDesc = "um $heroTimeText Uhr"
        }

        val karaha = de.gebetszeiten.core.prayertimes.Karaha
        val karahaUi = when (val status = karaha.status(karaha.windows(WearPrayer.today(s.location, zone)), now)) {
            is de.gebetszeiten.core.prayertimes.Karaha.Status.Active ->
                KarahaUi("⚠️ Karaha bis ${status.window.end.format(timeFormat)}", warn = true)
            is de.gebetszeiten.core.prayertimes.Karaha.Status.Soon ->
                KarahaUi("⚠️ Karaha ab ${status.window.start.format(timeFormat)}", warn = false)
            de.gebetszeiten.core.prayertimes.Karaha.Status.None -> null
        }

        val rows = buildList {
            upcoming.drop(1).forEach { (prayer, time) ->
                val tomorrow = time.toLocalDate() != now.toLocalDate()
                add((if (tomorrow) "${prayer.label()} · morgen" else prayer.label()) to time)
            }
            val sunrise = WearPrayer.today(s.location, zone).sunrise
            if (sunrise.isAfter(now)) add("Sonnenaufgang" to sunrise)
        }.sortedBy { it.second }.map { it.first to it.second.format(timeFormat) }

        return ViewState(
            heroName = name,
            heroTimeText = heroTimeText,
            heroTimeSize = heroTimeSize,
            heroTimeDesc = heroTimeDesc,
            city = s.city,
            karaha = karahaUi,
            modeText = if (s.showRemaining) "Anzeige: Restzeit" else "Anzeige: Uhrzeit",
            vibrateText = if (s.vibrate) "Vibration: An" else "Vibration: Aus",
            rows = rows,
        )
    }

    private fun applyState(state: ViewState) {
        findViewById<TextView>(R.id.heroName).apply {
            text = state.heroName
            contentDescription = "Nächstes Gebet: ${state.heroName}"
        }
        findViewById<TextView>(R.id.heroTime).apply {
            text = state.heroTimeText
            textSize = state.heroTimeSize
            contentDescription = "${state.heroName}, ${state.heroTimeDesc}. Zum Umschalten tippen."
        }
        findViewById<TextView>(R.id.cityLabel).apply {
            text = state.city
            contentDescription = "Ort: ${state.city}. Zum Ändern tippen."
        }

        val karahaLabel = findViewById<TextView>(R.id.karahaLabel)
        if (state.karaha == null) {
            karahaLabel.visibility = View.GONE
        } else {
            karahaLabel.text = state.karaha.text
            karahaLabel.setTextColor(getColor(if (state.karaha.warn) R.color.wear_warn else R.color.wear_dim))
            karahaLabel.visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.modeLabel).apply {
            text = state.modeText
            contentDescription = "${state.modeText}. Zum Umschalten tippen."
        }
        findViewById<TextView>(R.id.vibrateLabel).apply {
            text = state.vibrateText
            contentDescription = "${state.vibrateText}. Zum Umschalten tippen."
        }

        val list = findViewById<android.widget.LinearLayout>(R.id.upcomingList)
        list.removeAllViews()
        val density = resources.displayMetrics.density
        state.rows.forEach { (label, time) ->
            list.addView(
                android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(0, (5 * density).toInt(), 0, (5 * density).toInt())
                    // Read as one TalkBack item ("Asr 17:37"), not two.
                    isFocusable = true
                    contentDescription = "$label $time"
                    addView(
                        TextView(context).apply {
                            text = label
                            setTextColor(getColor(R.color.wear_dim))
                            textSize = 14f
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                            )
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = time
                            setTextColor(getColor(R.color.wear_text))
                            textSize = 14f
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                    )
                },
            )
        }
    }
}
