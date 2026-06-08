package de.gebetszeiten.wear

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
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

/** Swipeable tile showing the next prayer name and time. */
class PrayerTileService : TileService() {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val zone = ZoneId.systemDefault()
        val location = runBlocking { WearSettings.location(applicationContext) }
        val next = WearPrayer.next(location, zone, ZonedDateTime.now(zone))
        val layout = layout(requestParams.deviceConfiguration, next.first.label(), next.second.format(timeFormat))

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        ResolvableFuture.create<ResourceBuilders.Resources>().apply {
            set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        }

    private fun layout(
        device: DeviceParametersBuilders.DeviceParameters,
        name: String,
        time: String,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
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
            .build()
        return PrimaryLayout.Builder(device).setContent(column).build()
    }
}
