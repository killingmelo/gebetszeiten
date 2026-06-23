package de.gebetszeiten.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.QiblaMath
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(inner: PaddingValues, settings: AppSettings) {
    val bearing = QiblaMath.bearing(settings.latitude, settings.longitude)
    val distance = QiblaMath.distanceKm(settings.latitude, settings.longitude)
    val cardinal = QiblaMath.cardinal(bearing)
    val km = String.format(Locale.GERMAN, "%,d", distance.roundToInt())

    val azimuth = rememberDeviceAzimuth()
    val live = azimuth != null

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

    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val north = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.padding(inner).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            rotate(animated) {
                drawCompassRose(ring, north)
                rotate(bearing.toFloat(), pivot = center) {
                    drawQiblaArrow(accent)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "${bearing.roundToInt()}° · $cardinal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Kaaba · $km km",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!live) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Kompass nicht verfügbar — Qibla liegt bei ${bearing.roundToInt()}° $cardinal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ring + N/O/S/W-Ticks; N-Tick in [north] hervorgehoben. */
private fun DrawScope.drawCompassRose(ring: Color, north: Color) {
    val r = size.minDimension / 2f
    drawCircle(ring.copy(alpha = 0.5f), radius = r, style = Stroke(width = 3.dp.toPx()))

    val labels = listOf("N", "O", "S", "W")
    val textOffsetFromCenter = r - 34.dp.toPx()
    val textSize = 16.sp.toPx()
    val ringArgb = ring.toArgb()
    val northArgb = north.toArgb()

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
                strokeWidth = if (isNorth) 5.dp.toPx() else 3.dp.toPx(),
            )
            // Reuse the single Paint; only mutate per-letter properties.
            cardinalPaint.color = if (isNorth) northArgb else ringArgb
            cardinalPaint.isFakeBoldText = isNorth
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
