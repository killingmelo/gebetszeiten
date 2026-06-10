package de.gebetszeiten.wear

import android.app.Activity
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.coroutines.runBlocking

/**
 * Minimal on-watch city picker: a plain scrollable list of the curated cities.
 * Tapping a city stores it and refreshes tile + complication once, then the
 * usual zero-wakeup update model carries on with the new location.
 */
class CityPickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(40))
        }

        list.addView(
            TextView(this).apply {
                text = getString(R.string.pick_city)
                setTextColor(ContextCompat.getColor(context, R.color.wear_dim))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(10))
            },
        )

        val current = runBlocking { WearSettings.city(applicationContext) }
        WearCities.all.forEach { city ->
            list.addView(
                TextView(this).apply {
                    text = city.name
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    if (city.name == current) {
                        setTextColor(ContextCompat.getColor(context, R.color.wear_accent))
                        setTypeface(typeface, Typeface.BOLD)
                    } else {
                        setTextColor(ContextCompat.getColor(context, R.color.wear_text))
                    }
                    setOnClickListener { pick(city) }
                },
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.wear_bg))
                isVerticalScrollBarEnabled = true
                addView(list)
            },
        )
    }

    private fun pick(city: WearCity) {
        runBlocking { WearSettings.save(applicationContext, city.name, city.latitude, city.longitude) }
        // New location = new times: re-arm the vibration chain.
        runBlocking { WearVibration.reschedule(applicationContext) }
        // One-off refresh so tile and complication show the new location
        // immediately instead of at their next natural validity boundary.
        TileService.getUpdater(this).requestUpdate(PrayerTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, PrayerComplicationService::class.java))
            .requestUpdateAll()
        finish()
    }
}
