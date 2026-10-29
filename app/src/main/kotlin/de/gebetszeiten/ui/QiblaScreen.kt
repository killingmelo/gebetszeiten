package de.gebetszeiten.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.QiblaMath
import de.gebetszeiten.ui.theme.LocalTokens
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(inner: PaddingValues, settings: AppSettings) {
    val bearing = QiblaMath.bearing(settings.latitude, settings.longitude)
    val distance = QiblaMath.distanceKm(settings.latitude, settings.longitude)
    val cardinal = QiblaMath.cardinal(bearing)
    val km = String.format(Locale.GERMAN, "%,d", distance.roundToInt())

    val heading = rememberDeviceAzimuth(settings.latitude, settings.longitude)
    val azimuth = heading?.azimuth
    val live = heading != null

    // Fix 3: keep a continuous angle to avoid long-arc spin on 0↔360 wrap.
    // State write is in LaunchedEffect (effects phase), NOT during composition.
    val rawTarget = -(azimuth ?: 0f)
    var continuousTarget by remember { mutableFloatStateOf(rawTarget) }
    LaunchedEffect(rawTarget) {
        val delta = ((rawTarget - continuousTarget + 540f) % 360f) - 180f
        continuousTarget += delta
    }
    val animated by animateFloatAsState(
        targetValue = continuousTarget,
        animationSpec = if (rememberAnimationsEnabled()) tween(250) else snap(),
        label = "compass",
    )

    // Ring und Beschriftung in Nebentext, Nord in Gold, der Pfeil im Akzent.
    val t = LocalTokens.current
    val ring = t.stone
    val accent = t.accent
    val north = t.gold
    // Gold erreicht als Text inzwischen 4,53:1 (Tag) und besteht AA. Der
    // Buchstabe N bleibt trotzdem in Tinte: auf dem drehenden Kompass soll er
    // so kräftig wie möglich stehen; gold ist nur die Nordmarke.
    val northLetter = t.ink
    // Die Himmelsrichtungen in Inter wie der übrige Text (der native Canvas
    // kennt sonst nur die Systemschrift).
    val context = LocalContext.current
    val letterFace = remember { ResourcesCompat.getFont(context, R.font.sans_medium) }
    val northFace = remember { ResourcesCompat.getFont(context, R.font.sans_semibold) }
    val compassLabels = listOf(
        stringResource(R.string.compass_n),
        stringResource(R.string.compass_e),
        stringResource(R.string.compass_s),
        stringResource(R.string.compass_w),
    )

    // Mittig, solange alles passt; bei großer Schrift (1,4) wird gescrollt
    // statt abgeschnitten.
    BoxWithConstraints(Modifier.padding(inner).fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val compassDesc = stringResource(R.string.qibla_compass_desc, bearing.roundToInt(), cardinal)
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .semantics { contentDescription = compassDesc },
            ) {
                rotate(animated) {
                    drawCompassRose(ring, north, northLetter, compassLabels, letterFace, northFace)
                    rotate(bearing.toFloat(), pivot = center) {
                        drawQiblaArrow(accent)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // Gradzahl groß, die Himmelsrichtung darunter auf eigener Zeile: auf
            // 360 dp blieb sonst ein einzelnes „·“ am Zeilenende stehen.
            val bearingDesc = stringResource(R.string.qibla_bearing_desc, bearing.roundToInt(), cardinal)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics { contentDescription = bearingDesc },
            ) {
                Text(
                    stringResource(R.string.qibla_bearing, bearing.roundToInt()),
                    style = MaterialTheme.typography.displayLarge,
                    color = t.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    cardinal,
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.ink,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.qibla_distance, km),
                style = MaterialTheme.typography.bodyLarge,
                color = t.stone,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!live) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.qibla_no_compass, bearing.roundToInt(), cardinal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.stone,
                    textAlign = TextAlign.Center,
                )
            }
            if (heading != null && !heading.reliable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.qibla_compass_inaccurate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.qibla_compass_interference),
                style = MaterialTheme.typography.bodySmall,
                color = t.stone,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Ring + N/O/S/W-Ticks; N-Tick in [north] hervorgehoben, der Buchstabe N in [northLetter], Buchstaben in Inter. [labels] are the
 *  localized cardinal letters (N/E/S/W order). */
private fun DrawScope.drawCompassRose(
    ring: Color,
    north: Color,
    northLetter: Color,
    labels: List<String>,
    letterFace: Typeface?,
    northFace: Typeface?,
) {
    val r = size.minDimension / 2f
    // Feiner Ring wie die Haarlinien der übrigen Ansichten.
    drawCircle(ring, radius = r - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))

    val textOffsetFromCenter = r - 34.dp.toPx()
    val textSize = 16.sp.toPx()
    val ringArgb = ring.toArgb()
    val northLetterArgb = northLetter.toArgb()

    // Allocate a single Paint once and mutate per-letter properties inside the loop
    // to avoid creating a new Paint object on every frame (4× per redraw previously).
    val cardinalPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        this.textSize = textSize
    }

    for (i in 0 until 4) {
        val isNorth = i == 0
        rotate(i * 90f, pivot = center) {
            drawLine(
                color = if (isNorth) north else ring,
                start = Offset(center.x, center.y - r),
                end = Offset(center.x, center.y - r + (if (isNorth) 22.dp.toPx() else 14.dp.toPx())),
                strokeWidth = if (isNorth) 4.dp.toPx() else 1.5.dp.toPx(),
            )
            // Reuse the single Paint; only mutate per-letter properties.
            cardinalPaint.color = if (isNorth) northLetterArgb else ringArgb
            cardinalPaint.typeface = if (isNorth) northFace else letterFace
            // In the rotated frame, "north" is always at (center.x, center.y - textOffsetFromCenter).
            drawContext.canvas.nativeCanvas.drawText(
                labels[i],
                center.x,
                center.y - textOffsetFromCenter + textSize / 3f, // +textSize/3 for optical centering
                cardinalPaint,
            )
        }
    }
}

/** Vom Zentrum nach oben zeigender, gefüllter Qibla-Pfeil. */
private fun DrawScope.drawQiblaArrow(accent: Color) {
    val r = size.minDimension / 2f
    val tip = Offset(center.x, center.y - r + 26.dp.toPx())
    val baseY = center.y + r * 0.35f
    val half = 12.dp.toPx()
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x - half, baseY)
        lineTo(center.x + half, baseY)
        close()
    }
    drawPath(path, accent)
    drawCircle(accent, radius = 5.dp.toPx(), center = center)
}
