package de.gebetszeiten.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.ResolvableFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val RESOURCES_VERSION = "1"

/**
 * Swipeable tile showing the next prayer (name + time), static.
 *
 * Battery model: instead of one value that goes stale, we return a TIMELINE
 * with one validity-bounded entry per upcoming prayer transition. The system
 * switches to the right entry on its own as each prayer arrives — so a single
 * computation covers the whole rest of the day with zero app wake-ups. A
 * freshness interval lets the system re-request once the entries are used up.
 */
class PrayerTileService : TileService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        // Sofort aus dem Cache zeichnen — kein Netz, aber auch keine reine
        // Millisekunden-Sache: `WearPrayer.upcoming` ruft `daily()` fuer
        // ZWEI Tage auf, jedes davon bis zu zwei DataStore-Lesevorgaenge
        // (useCalculated, amtlicher Cache) plus hier oben `location()` —
        // macht bis zu sechs DataStore-Reads, und beim allerersten Aufruf
        // zusaetzlich das Parsen von `locations-de.tsv` (947 Zeilen) samt
        // einer Jahrestabelle (`WearOfficialSource`, danach im Prozess
        // gecacht). Weit unter der ANR-Schwelle (5 s), aber kein
        // Millisekundengeschaeft.
        // One extra entry so every shown prayer knows its successor ("danach").
        val upcoming = runBlocking {
            val location = WearSettings.location(applicationContext)
            WearPrayer.upcoming(applicationContext, location, zone, now, count = 7)
        }
        // Amtliche Zeiten NEBENHER auffrischen (siehe KDoc an
        // `refreshWearOfficial`, "Muss NICHT-BLOCKIEREND aufgerufen
        // werden"), nicht Teil der Kachel-Antwort. `launchWearRefresh`
        // startet das im datei-eigenen, langlebigen Scope von
        // `WearRefresh.kt` — NICHT in einem Scope dieses Services: der ist
        // ein gebundener Dienst, das System loest ihn Sekunden nach dieser
        // Antwort wieder, ein eigener Service-Scope wuerde also genau dann
        // abgebrochen, wenn der Abruf tatsaechlich noch liefe (Fix-Runde 3).
        // Die Wiederholungs-Bremse darin haelt das ausser am faelligen Ort
        // billig. Nur bei einem ECHTEN neuen Zeitplan lohnt eine
        // Neuzeichnung.
        launchWearRefresh(applicationContext) {
            TileService.getUpdater(applicationContext).requestUpdate(PrayerTileService::class.java)
        }

        val timeline = TimelineBuilders.Timeline.Builder()
        var start = now
        for ((index, entry) in upcoming.take(6).withIndex()) {
            val (prayer, time) = entry
            val after = upcoming.getOrNull(index + 1)
                ?.let { getString(R.string.tile_danach, it.first.label(), it.second.format(timeFormat)) }
            val layout = layout(requestParams.deviceConfiguration, prayer.label(), time.format(timeFormat), after)
            timeline.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(
                        TimelineBuilders.TimeInterval.Builder()
                            .setStartMillis(start.toInstant().toEpochMilli())
                            .setEndMillis(time.toInstant().toEpochMilli())
                            .build(),
                    )
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
                    .build(),
            )
            start = time
        }

        // Re-request only once the provided entries are exhausted.
        val freshness = (start.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()).coerceAtLeast(0)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline.build())
            .setFreshnessIntervalMillis(freshness)
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        ResolvableFuture.create<ResourceBuilders.Resources>().apply {
            set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        }

    // Kein eigener Scope mehr (Fix-Runde 3): `launchWearRefresh` laeuft im
    // datei-eigenen Scope von `WearRefresh.kt`, der laenger lebt als dieser
    // Dienst — hier gibt es nichts mehr, das `onDestroy` abbrechen muesste.

    private fun layout(
        device: DeviceParametersBuilders.DeviceParameters,
        name: String,
        time: String,
        after: String?,
    ): LayoutElementBuilders.LayoutElement {
        // Tapping the tile opens the watch app.
        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openApp)
                    .build(),
            )
            .addContent(
                Text.Builder(this, name)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.argb(0xFFB0BEC5.toInt()))
                    .build(),
            )
            .addContent(
                Text.Builder(this, time)
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build(),
            )
            .apply {
                if (after != null) {
                    addContent(
                        Text.Builder(this@PrayerTileService, after)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(ColorBuilders.argb(0xFF78909C.toInt()))
                            .build(),
                    )
                }
            }
            .build()
        return PrimaryLayout.Builder(device).setContent(column).build()
    }
}
