package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gebetszeiten.prayer.Pt
import de.gebetszeiten.prayer.Sundial
import de.gebetszeiten.ui.theme.GebetszeitenTheme
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/* ------------------------------------------------------------------------
 * Geometrie der Ansicht (reine Mathematik, in Stablängen, Norden oben).
 * Getrennt vom Zeichnen, damit Ausschnitt und Beschriftung ohne Android
 * prüfbar sind (SundialLayoutTest).
 * ---------------------------------------------------------------------- */

/** Sichtbarer Ausschnitt der Platte in Stablängen; der Stabfuß liegt bei (0, 0). */
internal data class DialFrame(val west: Double, val east: Double, val south: Double, val north: Double) {
    val width: Double get() = east - west
    val height: Double get() = north - south
}

/** Rechteck in Stablängen (Norden oben: [top] > [bottom]). */
internal data class StickRect(val left: Double, val bottom: Double, val right: Double, val top: Double) {
    fun inside(f: DialFrame): Boolean = left >= f.west && right <= f.east && bottom >= f.south && top <= f.north
}

/** Weiter als so viele Stablängen wird nicht gezeigt; der Winterschatten wird an der Platte abgeschnitten. */
private const val MAX_EXTENT = 5.5
/** Rand um die Linien und um den Stabfuß, in Stablängen. */
private const val FRAME_PAD = 0.5
/** Platz für die Beschriftung „2× Schatten“ hinter einem Ende der k=2-Kurve, in Stablängen. */
private const val LABEL_ROOM = 1.2
/** Seitenverhältnis (Breite : Höhe) der Platte bleibt in diesem Bereich. */
private const val MIN_ASPECT = 0.9
private const val MAX_ASPECT = 1.6

/**
 * Ausschnitt wie bei einer waagrechten Moscheesonnenuhr: alles, wohin der Schatten
 * zwischen Mittag und ʿAṣr fallen kann (beide ʿAṣr-Kurven, Mittagsschatten an den
 * Sonnenwenden und zur Tagundnachtgleiche) und der Stabfuß, mit kleinem Rand und
 * Platz für die Beschriftung.
 * Symmetrisch um die Mittagslinie; zu lange Winterschatten werden bei
 * [MAX_EXTENT] Stablängen abgeschnitten.
 */
internal fun dialFrame(k1: List<Pt>, k2: List<Pt>, noonTips: List<Pt>): DialFrame {
    val features = k1 + k2 + noonTips + Pt(0.0, 0.0)
    fun clampN(v: Double) = v.coerceIn(-MAX_EXTENT, MAX_EXTENT)
    val minN = features.minOf { clampN(it.north) }
    val maxN = features.maxOf { clampN(it.north) }
    val maxE = features.maxOf { min(abs(it.east), MAX_EXTENT) }

    var halfW = maxE + FRAME_PAD
    var south = minN - FRAME_PAD
    var north = maxN + FRAME_PAD
    // Hinter dem äußeren Ende der k=2-Kurve Platz für ihre Beschriftung lassen; ist
    // das Winterende abgeschnitten (hohe Breiten), hinter dem Sommerende.
    val ends = listOfNotNull(k2.firstOrNull(), k2.lastOrNull())
    val labelEnd = ends.filter { abs(it.north) <= MAX_EXTENT }.maxByOrNull { abs(it.north) }
        ?: ends.minByOrNull { abs(it.north) }
    if (labelEnd != null) {
        if (labelEnd.north >= 0) north = maxOf(north, labelEnd.north + LABEL_ROOM)
        else south = minOf(south, labelEnd.north - LABEL_ROOM)
    }
    val aspect = 2 * halfW / (north - south)
    if (aspect > MAX_ASPECT) {
        // zu flach: oben und unten gleich viel Luft dazu
        val extra = 2 * halfW / MAX_ASPECT - (north - south)
        north += extra / 2
        south -= extra / 2
    } else if (aspect < MIN_ASPECT) {
        // zu hoch: seitlich verbreitern
        halfW = MIN_ASPECT * (north - south) / 2
    }
    return DialFrame(-halfW, halfW, south, north)
}

/** Abstand zwischen Punkt und Strecke. */
private fun distPointSegment(px: Double, py: Double, a: Pt, b: Pt): Double {
    val dx = b.east - a.east
    val dy = b.north - a.north
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((px - a.east) * dx + (py - a.north) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(px - (a.east + t * dx), py - (a.north + t * dy))
}

private fun pointInRect(x: Double, y: Double, r: StickRect) = x in r.left..r.right && y in r.bottom..r.top

/** Schneiden sich die Strecken p1-p2 und p3-p4? */
private fun segmentsCross(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Boolean {
    fun cross(o: Pt, a: Pt, b: Pt) = (a.east - o.east) * (b.north - o.north) - (a.north - o.north) * (b.east - o.east)
    val d1 = cross(p3, p4, p1)
    val d2 = cross(p3, p4, p2)
    val d3 = cross(p1, p2, p3)
    val d4 = cross(p1, p2, p4)
    return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
}

/** Kleinster Abstand zwischen einem Rechteck und einem Linienzug (0 bei Berührung). */
internal fun distanceRectPolyline(r: StickRect, line: List<Pt>): Double {
    if (line.isEmpty()) return Double.MAX_VALUE
    val corners = listOf(Pt(r.left, r.bottom), Pt(r.right, r.bottom), Pt(r.right, r.top), Pt(r.left, r.top))
    val edges = corners.indices.map { corners[it] to corners[(it + 1) % 4] }
    if (line.size == 1) {
        val p = line[0]
        if (pointInRect(p.east, p.north, r)) return 0.0
        return edges.minOf { (a, b) -> distPointSegment(p.east, p.north, a, b) }
    }
    var best = Double.MAX_VALUE
    for (i in 0 until line.size - 1) {
        val a = line[i]
        val b = line[i + 1]
        if (pointInRect(a.east, a.north, r) || pointInRect(b.east, b.north, r)) return 0.0
        if (edges.any { (c, d) -> segmentsCross(a, b, c, d) }) return 0.0
        for (c in corners) best = min(best, distPointSegment(c.east, c.north, a, b))
        for ((c, d) in edges) {
            best = min(best, distPointSegment(a.east, a.north, c, d))
            best = min(best, distPointSegment(b.east, b.north, c, d))
        }
    }
    return best
}

/**
 * Platz für die Beschriftung „2× Schatten“ (Breite [w], Höhe [h] in Stablängen):
 * neben der k=2-Kurve, innerhalb der Platte, mit Abstand [gap] zur k=1-Kurve,
 * zur Mittagslinie und zum Stabfuß; die eigene Kurve wird nicht überdeckt.
 * Gesucht wird an Punkten der Kurve, jeweils östlich, oberhalb und unterhalb.
 * Findet sich nichts, gewinnt die Lage mit dem größten Abstand zur k=1-Kurve.
 */
internal fun placeAsr2Label(frame: DialFrame, k1: List<Pt>, k2: List<Pt>, w: Double, h: Double, gap: Double): StickRect {
    val inset = gap / 2
    val inner = DialFrame(frame.west + inset, frame.east - inset, frame.south + inset, frame.north - inset)
    val visible = k2.filter { it.east in inner.west..inner.east && it.north in inner.south..inner.north }
    val noonLine = listOf(Pt(0.0, frame.south), Pt(0.0, frame.north))
    val foot = listOf(Pt(0.0, 0.0))

    fun fit(r: StickRect): StickRect {
        // in die Platte schieben, Größe bleibt
        val dx = when {
            r.left < inner.west -> inner.west - r.left
            r.right > inner.east -> inner.east - r.right
            else -> 0.0
        }
        val dy = when {
            r.bottom < inner.south -> inner.south - r.bottom
            r.top > inner.north -> inner.north - r.top
            else -> 0.0
        }
        return StickRect(r.left + dx, r.bottom + dy, r.right + dx, r.top + dy)
    }

    // Reihenfolge: Ende der Kurve, Mitte, dann der Rest
    val anchors = if (visible.isEmpty()) emptyList() else buildList {
        add(visible.maxBy { it.north })
        add(visible[visible.size / 2])
        add(visible.minBy { it.north })
        visible.forEachIndexed { i, p -> if (i % 4 == 0) add(p) }
    }
    val candidates = anchors.flatMap { a ->
        listOf(
            StickRect(a.east + gap, a.north - h / 2, a.east + gap + w, a.north + h / 2), // östlich
            StickRect(a.east - w / 2, a.north + gap, a.east + w / 2, a.north + gap + h), // oberhalb
            StickRect(a.east - w / 2, a.north - gap - h, a.east + w / 2, a.north - gap), // unterhalb
        ).map(::fit)
    }

    fun clearance(r: StickRect) = min(
        distanceRectPolyline(r, k1),
        min(distanceRectPolyline(r, noonLine), distanceRectPolyline(r, foot)),
    )

    val ok = candidates.firstOrNull { r ->
        r.inside(frame) && clearance(r) >= gap && distanceRectPolyline(r, k2) >= gap / 2
    }
    if (ok != null) return ok
    val fallback = candidates.maxByOrNull { clearance(it) }
    return fallback ?: fit(StickRect(frame.east - w, frame.north - h, frame.east, frame.north))
}

/** Stundenwinkel der Sonne in Grad (−180..180], aus Weltzeit und Länge, ohne Zeitgleichung (nur Anschauung). */
internal fun hourAngleDeg(now: ZonedDateTime, lonDeg: Double): Double {
    val utc = now.withZoneSameInstant(ZoneOffset.UTC)
    val hours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    var h = 15.0 * (hours + lonDeg / 15.0 - 12.0)
    h = ((h % 360.0) + 360.0) % 360.0
    if (h > 180.0) h -= 360.0
    return h
}

/** Alle Linien einer Sonnenuhr für eine Breite und einen Zeitpunkt. */
private class DialData(latDeg: Double, lonDeg: Double, now: ZonedDateTime) {
    val k1 = Sundial.asrCurve(latDeg, 1)
    val k2 = Sundial.asrCurve(latDeg, 2)
    private val solstices = listOf(-23.44, 0.0, 23.44)
    val dateArcs = solstices.map { Sundial.dayCurve(latDeg, it) }
    private val decl = Sundial.declination(now.withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
    val today = Sundial.dayCurve(latDeg, decl)
    val tip: Pt? = Sundial.shadowTip(latDeg, decl, hourAngleDeg(now, lonDeg))
    val frame = dialFrame(k1, k2, solstices.mapNotNull { Sundial.shadowTip(latDeg, it, 0.0) })
}

/* ------------------------------------------------------------------------
 * Zeichnen
 * ---------------------------------------------------------------------- */

private const val LABEL_2X = "2× Schatten"

/**
 * Waagrechte Sonnenuhr für die Breite des Orts, berechnet (Sundial), nicht gemalt:
 * Tagesbögen der Sonnenwenden und der Tagundnachtgleiche, Mittagslinie, ʿAṣr-Kurven
 * für einfachen und doppelten Schatten, der heutige Weg der Schattenspitze und die
 * Schattenspitze jetzt. Keine Uhrzeiten. Steht die Sonne unter 3°, kein Schatten.
 *
 * [lonDeg] braucht der Stundenwinkel, [place] die TalkBack-Beschreibung.
 */
@Composable
fun SundialView(
    latDeg: Double,
    lonDeg: Double,
    place: String,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Geometrie nur neu, wenn sich Ort oder Minute ändern
    val data = remember(latDeg, lonDeg, now.toEpochSecond() / 60) { DialData(latDeg, lonDeg, now) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = t.stone, letterSpacing = 0.4.sp)
    val measurer = rememberTextMeasurer()
    val label2x = remember(measurer, labelStyle) { measurer.measure(LABEL_2X, labelStyle) }
    val labelN = remember(measurer, labelStyle) { measurer.measure("N", labelStyle) }
    val description = "Sonnenuhr für $place: Mittagslinie und die Linien für einfachen und doppelten Schatten."

    Column(modifier.fillMaxWidth()) {
        Text(
            "Sonnenuhr für $place".uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = t.stone,
        )
        Spacer(Modifier.height(12.dp))
        val frame = data.frame
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio((frame.width / frame.height).toFloat())
                .semantics { contentDescription = description },
        ) {
            val s = (size.width / frame.width).toFloat()
            fun at(p: Pt) = Offset(((p.east - frame.west) * s).toFloat(), ((frame.north - p.north) * s).toFloat())
            val radius = CornerRadius(24.dp.toPx())

            // Platte
            drawRoundRect(t.surface, cornerRadius = radius)
            val plate = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, radius))
            }
            clipPath(plate) {
                // Tagesbögen: 21. Dezember, Tagundnachtgleiche, 21. Juni
                for (arc in data.dateArcs) {
                    drawPath(polyline(arc, ::at, breakAbove = null), t.stone.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
                }
                // Mittagslinie (Meridian durch den Stabfuß)
                drawLine(t.ink, at(Pt(0.0, frame.south)), at(Pt(0.0, frame.north)), strokeWidth = 1.25f.dp.toPx())
                // heutiger Weg der Schattenspitze, gepunktet
                drawPath(
                    polyline(data.today, ::at, breakAbove = null),
                    t.accent,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
                    ),
                )
                // ʿAṣr: einfacher Schatten durchgezogen, doppelter gestrichelt
                drawPath(
                    polyline(data.k1, ::at, breakAbove = GAP_STICKS * s),
                    t.gold,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    polyline(data.k2, ::at, breakAbove = GAP_STICKS * s),
                    t.ochre,
                    style = Stroke(
                        width = 1.75f.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
                    ),
                )
                // Schatten jetzt: Linie vom Fuß zur Spitze, Spitze als Punkt
                data.tip?.let { tip ->
                    drawLine(t.ink.copy(alpha = 0.5f), at(Pt(0.0, 0.0)), at(tip), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                // Stabfuß
                drawCircle(t.ink, radius = 3.dp.toPx(), center = at(Pt(0.0, 0.0)))
                data.tip?.let { tip ->
                    drawCircle(t.surface, radius = 7.dp.toPx(), center = at(tip))
                    drawCircle(t.accent, radius = 5.dp.toPx(), center = at(tip))
                }

                // Beschriftungen auf eigenem Grund, damit gepunktete Linien sie nicht kreuzen
                val pad = 3.dp.toPx()
                val lw = (label2x.size.width + 2 * pad) / s
                val lh = (label2x.size.height + 2 * pad) / s
                val r = placeAsr2Label(frame, data.k1, data.k2, lw.toDouble(), lh.toDouble(), (6.dp.toPx() / s).toDouble())
                val topLeft = at(Pt(r.left, r.top))
                labelOnPlate(t, topLeft, Size(lw * s, lh * s), pad) { drawText(label2x, topLeft = it) }

                // Norden oben, links neben dem oberen Ende der Mittagslinie
                val nTopLeft = Offset(
                    at(Pt(0.0, frame.north)).x - 6.dp.toPx() - labelN.size.width - 2 * pad,
                    6.dp.toPx(),
                )
                labelOnPlate(t, nTopLeft, Size(labelN.size.width + 2 * pad, labelN.size.height + 2 * pad), pad) {
                    drawText(labelN, topLeft = it)
                }
            }
            // Haarlinie um die Platte
            drawRoundRect(t.stone.copy(alpha = 0.35f), cornerRadius = radius, style = Stroke(1.dp.toPx()))
        }
        Spacer(Modifier.height(16.dp))
        Legend(t, nightNow = data.tip == null)
    }
}

/** Lücke in einer ʿAṣr-Kurve (hohe Breiten): Punkte weiter als so viele Stablängen auseinander werden nicht verbunden. */
private const val GAP_STICKS = 1.5f

private fun polyline(points: List<Pt>, at: (Pt) -> Offset, breakAbove: Float?): Path {
    val path = Path()
    var last: Offset? = null
    for (p in points) {
        val o = at(p)
        val prev = last
        if (prev == null || (breakAbove != null && (o - prev).getDistance() > breakAbove)) {
            path.moveTo(o.x, o.y)
        } else {
            path.lineTo(o.x, o.y)
        }
        last = o
    }
    return path
}

private fun DrawScope.labelOnPlate(t: Tokens, topLeft: Offset, size: Size, pad: Float, text: DrawScope.(Offset) -> Unit) {
    drawRoundRect(t.surface, topLeft = topLeft, size = size, cornerRadius = CornerRadius(4.dp.toPx()))
    text(Offset(topLeft.x + pad, topLeft.y + pad))
}

/* ------------------------------------------------------------------------
 * Legende
 * ---------------------------------------------------------------------- */

private enum class Swatch { NOON, ASR1, ASR2, TODAY, TIP, ARCS }

@Composable
private fun Legend(t: Tokens, nightNow: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendRow(t, Swatch.NOON, "Mittagslinie: hier liegt der Schatten beim Zawāl")
        LegendRow(t, Swatch.ASR1, "ʿAṣr bei einfachem Schatten")
        LegendRow(t, Swatch.ASR2, "ʿAṣr bei doppeltem Schatten")
        LegendRow(t, Swatch.TODAY, "Weg der Schattenspitze heute")
        if (nightNow) {
            LegendRow(t, null, "Die Sonne steht jetzt zu tief für einen Schatten.")
        } else {
            LegendRow(t, Swatch.TIP, "Schattenspitze jetzt")
        }
        LegendRow(t, Swatch.ARCS, "Bögen am 21. Juni, zur Tagundnachtgleiche und am 21. Dezember")
    }
}

@Composable
private fun LegendRow(t: Tokens, swatch: Swatch?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 28.dp, height = 14.dp)) {
            val y = size.height / 2
            val a = Offset(0f, y)
            val b = Offset(size.width, y)
            when (swatch) {
                Swatch.NOON -> drawLine(t.ink, a, b, 1.25f.dp.toPx())
                Swatch.ASR1 -> drawLine(t.gold, a, b, 2.dp.toPx(), cap = StrokeCap.Round)
                Swatch.ASR2 -> drawLine(
                    t.ochre, a, b, 1.75f.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
                )
                Swatch.TODAY -> drawLine(
                    t.accent, a, b, 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
                )
                Swatch.TIP -> drawCircle(t.accent, 5.dp.toPx(), Offset(size.width / 2, y))
                Swatch.ARCS -> drawLine(t.stone.copy(alpha = 0.45f), a, b, 1.dp.toPx())
                null -> Unit
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = t.stone)
    }
}

@Preview(widthDp = 360, showBackground = true, backgroundColor = 0xFFF7F1E8)
@Composable
private fun SundialPreview() {
    GebetszeitenTheme(darkTheme = false) {
        SundialView(
            latDeg = 49.45,
            lonDeg = 11.08,
            place = "Nürnberg",
            now = ZonedDateTime.of(2026, 10, 28, 14, 0, 0, 0, ZoneId.of("Europe/Berlin")),
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Preview(widthDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SundialPreviewNight() {
    GebetszeitenTheme(darkTheme = true) {
        SundialView(
            latDeg = -33.9,
            lonDeg = 18.4,
            place = "Kapstadt",
            now = ZonedDateTime.of(2026, 6, 21, 15, 0, 0, 0, ZoneId.of("Africa/Johannesburg")),
            modifier = Modifier.padding(24.dp),
        )
    }
}
