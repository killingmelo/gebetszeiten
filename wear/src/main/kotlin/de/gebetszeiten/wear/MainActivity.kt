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
        // Toggle den Notausgang: lokale Berechnung nur, wo amtliche Zeiten fehlen.
        findViewById<TextView>(R.id.calculationFillsGapsLabel).setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) {
                    WearSettings.saveCalculationFillsGaps(
                        applicationContext,
                        !WearSettings.calculationFillsGaps(applicationContext),
                    )
                    // Kann den Leerfall <-> Normalfall umschalten (Fix-Runde 1,
                    // Important 2): die Vibrationskette muss neu bewertet
                    // werden, sonst bleibt sie tot, obwohl gerade wieder eine
                    // naechste Zeit verfuegbar wurde (oder umgekehrt).
                    WearVibration.reschedule(applicationContext)
                    // Komplikation UND Kachel muessen das sofort spiegeln, nicht
                    // erst bei der naechsten natuerlichen Anfrage (Fix-Runde 1,
                    // Important 1): die Komplikation hat im Leerfall gar kein
                    // Ablaufdatum (siehe PrayerComplicationService.noTimesData)
                    // und wuerde sonst unbegrenzt einfrieren; die Kachel heilte
                    // sich sonst erst nach bis zu 30 Minuten
                    // (NO_TIMES_FRESHNESS_MILLIS). Derselbe geteilte Weg wie
                    // `notifyWearOfficialRefreshed` (WearRefresh.kt) benutzt,
                    // nur ohne den Netzabruf davor - die lokale Berechnung
                    // loest den Leerfall sofort auf, kein Abruf noetig.
                    notifyWearSurfaces(applicationContext)
                }
                refresh()
            }
        }
        // Toggle the derived Sabah congregation row (sunrise − 30 min).
        findViewById<TextView>(R.id.cemaatLabel).setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) {
                    WearSettings.saveShowCemaat(
                        applicationContext,
                        !WearSettings.showCemaat(applicationContext),
                    )
                }
                refresh()
            }
        }
    }

    // Recompute on every show so the next prayer is always current without any
    // background work or timers.
    override fun onStart() {
        super.onStart()
        refresh()
        // Nach Neuinstallation/Datenloeschung ist das DataItem des Handys
        // unveraendert, onDataChanged feuert nie — bestehenden Sync einmalig
        // nachholen und nur dann neu zeichnen. Danach (Sync vorhanden) ist der
        // Aufruf ein reiner DataStore-Read, kein gms-Roundtrip.
        scope.launch {
            val applied = withContext(Dispatchers.IO) { WearSyncApplier.replayExisting(applicationContext) }
            if (applied) refresh()
        }
        // Die Uhr ruft ihre amtlichen Zeiten seit Aufgabe 6 auch selbst ab
        // (online-Flavor; im offline-Flavor ein No-op ueber
        // `WearFetchProvider.isOnline`) — unabhaengig vom Sync vom Handy.
        // Neu gezeichnet wird danach immer: die Bremse (`needsRefresh`)
        // sorgt dafuer, dass ein frisch versorgter Ort hier nichts mehr zu
        // tun hat, der Aufruf also billig bleibt.
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) { refreshWearOfficial(applicationContext) }
            // Fix-Runde 2, Important 1: derselbe gemeinsame Weg wie
            // `launchWearRefresh` (Kachel/Komplikation) und der
            // Notausgang-Toggle - ein erfolgreicher eigener Abruf HIER war
            // bis Fix-Runde 1 nur der Vibrationskette bekannt, nicht der
            // Komplikation (die ohne eigenes Ablaufdatum unbegrenzt
            // eingefroren waere) oder der Kachel.
            if (refreshed) {
                withContext(Dispatchers.IO) { notifyWearOfficialRefreshed(applicationContext) }
            }
            refresh()
        }
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
        val noTimes: Boolean,
        val heroName: String,
        val heroTimeText: String,
        val heroTimeSize: Float,
        val heroTimeDesc: String,
        val city: String,
        val karaha: KarahaUi?,
        val modeText: String,
        val vibrateText: String,
        val calculationFillsGapsText: String,
        val cemaatText: String,
        val rows: List<Pair<String, String>>,
    )

    private suspend fun buildState(): ViewState {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val s = WearSettings.snapshot(applicationContext)

        val settingsRows = SettingsRows(
            modeText = if (s.showRemaining) getString(R.string.mode_remaining) else getString(R.string.mode_clock),
            vibrateText = if (s.vibrate) getString(R.string.vibrate_on) else getString(R.string.vibrate_off),
            calculationFillsGapsText = if (s.calculationFillsGaps) {
                getString(R.string.settings_calculation_fallback)
            } else {
                getString(R.string.settings_calculation_fallback_off)
            },
            cemaatText = if (s.showCemaat) getString(R.string.cemaat_on) else getString(R.string.cemaat_off),
        )

        // Leerfall (Task 16): weder amtliche Zeiten noch der Notausgang
        // liefern etwas fuer heute ODER morgen — dieselbe Regel wie
        // `PrayerProvider.daily` am Telefon. Die Uhr zeigt dann einen
        // kurzen Satz statt Zeiten, ohne Ortsnamen (kein Platz).
        val upcoming = WearPrayer.upcoming(applicationContext, s.location, zone, now, count = 6)
        val next = upcoming.firstOrNull()
        if (next == null) {
            return ViewState(
                noTimes = true,
                heroName = "",
                heroTimeText = getString(R.string.no_times_notice),
                heroTimeSize = 18f,
                heroTimeDesc = getString(R.string.no_times_notice),
                city = s.city,
                karaha = null,
                rows = emptyList(),
                modeText = settingsRows.modeText,
                vibrateText = settingsRows.vibrateText,
                calculationFillsGapsText = settingsRows.calculationFillsGapsText,
                cemaatText = settingsRows.cemaatText,
            )
        }
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
            heroTimeDesc = getString(R.string.desc_hero_time_clock, heroTimeText)
        }

        // Kann trotz vorhandenem [next] `null` sein (z. B. morgen liegen
        // amtliche Zeiten vor, heute nicht) — Karaha und die Sonnenaufgang-/
        // Cemaat-Zeile beziehen sich auf HEUTE und entfallen dann einfach.
        val todayTimes = WearPrayer.today(applicationContext, s.location, zone)
        val karahaUi = todayTimes?.let { times ->
            val karaha = de.gebetszeiten.core.prayertimes.Karaha
            when (val status = karaha.status(karaha.windows(times), now)) {
                is de.gebetszeiten.core.prayertimes.Karaha.Status.Active ->
                    KarahaUi(getString(R.string.karaha_active, status.window.end.format(timeFormat)), warn = true)
                is de.gebetszeiten.core.prayertimes.Karaha.Status.Soon ->
                    KarahaUi(getString(R.string.karaha_soon, status.window.start.format(timeFormat)), warn = false)
                de.gebetszeiten.core.prayertimes.Karaha.Status.None -> null
            }
        }

        val rows = buildList {
            upcoming.drop(1).forEach { (prayer, time) ->
                val tomorrow = time.toLocalDate() != now.toLocalDate()
                add((if (tomorrow) getString(R.string.label_morgen_suffix, prayer.label()) else prayer.label()) to time)
            }
            todayTimes?.sunrise?.let { sunrise ->
                if (sunrise.isAfter(now)) add(getString(R.string.label_sonnenaufgang) to sunrise)
                // Abgeleitete Cemaat-Zeit (fester Vorlauf 30 Min, wie Diyanet-Praxis).
                if (s.showCemaat) {
                    val cemaat = sunrise.minusMinutes(30)
                    if (cemaat.isAfter(now)) add(getString(R.string.label_cemaat) to cemaat)
                }
            }
        }.sortedBy { it.second }.map { it.first to it.second.format(timeFormat) }

        return ViewState(
            noTimes = false,
            heroName = name,
            heroTimeText = heroTimeText,
            heroTimeSize = heroTimeSize,
            heroTimeDesc = heroTimeDesc,
            city = s.city,
            karaha = karahaUi,
            modeText = settingsRows.modeText,
            vibrateText = settingsRows.vibrateText,
            calculationFillsGapsText = settingsRows.calculationFillsGapsText,
            cemaatText = settingsRows.cemaatText,
            rows = rows,
        )
    }

    /** Die vier Einstellungs-Zeilen unten im Screen — unabhaengig vom
     *  Leerfall immer gleich, deshalb einmal berechnet statt zweimal
     *  (Leerfall- und Normalfall-`return`) hingeschrieben. */
    private data class SettingsRows(
        val modeText: String,
        val vibrateText: String,
        val calculationFillsGapsText: String,
        val cemaatText: String,
    )

    private fun applyState(state: ViewState) {
        // Leerfall: die statische Ueberschrift "Naechstes Gebet" gehoert
        // zum Namen darunter - ohne sie zu verstecken laese sich der Screen
        // "Naechstes Gebet / Keine amtlichen Zeiten" (Fix-Runde 1, Minor 1).
        findViewById<TextView>(R.id.nextPrayerHeading).visibility =
            if (state.noTimes) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.heroName).apply {
            // Leerfall: kein "naechstes Gebet", also auch kein Name darueber -
            // der Satz in heroTime steht dann fuer sich.
            visibility = if (state.noTimes) View.GONE else View.VISIBLE
            text = state.heroName
            contentDescription = getString(R.string.desc_naechstes_gebet, state.heroName)
        }
        findViewById<TextView>(R.id.heroTime).apply {
            text = state.heroTimeText
            textSize = state.heroTimeSize
            contentDescription = if (state.noTimes) state.heroTimeDesc else {
                getString(R.string.desc_hero_time, state.heroName, state.heroTimeDesc)
            }
        }
        findViewById<TextView>(R.id.cityLabel).apply {
            text = state.city
            contentDescription = getString(R.string.desc_city, state.city)
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
            contentDescription = getString(R.string.desc_toggle, state.modeText)
        }
        findViewById<TextView>(R.id.vibrateLabel).apply {
            text = state.vibrateText
            contentDescription = getString(R.string.desc_toggle, state.vibrateText)
        }
        findViewById<TextView>(R.id.calculationFillsGapsLabel).apply {
            text = state.calculationFillsGapsText
            contentDescription = getString(R.string.desc_toggle, state.calculationFillsGapsText)
        }
        findViewById<TextView>(R.id.cemaatLabel).apply {
            text = state.cemaatText
            contentDescription = getString(R.string.desc_toggle, state.cemaatText)
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
                    contentDescription = getString(R.string.desc_row, label, time)
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
