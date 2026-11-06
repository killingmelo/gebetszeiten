package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens

/**
 * Name eines Teils, wie er sichtbar und für die Sprachausgabe steht: DMG-Umschrift,
 * außer „Zenit“ und „Vor Sonnenuntergang“ für die verpönten Zeiten (Entscheidung vom 06.11.2026).
 */
internal fun teilName(teil: Teil): String = when (teil) {
    Teil.FAJR -> "Faǧr"
    Teil.SHURUQ -> "Šurūq"
    Teil.DUHA -> "Ḍuḥā"
    Teil.ZAWAL -> "Zenit"
    Teil.ZUHR -> "Ẓuhr"
    Teil.ASR -> "ʿAṣr"
    Teil.ISFIRAR -> "Vor Sonnenuntergang"
    Teil.MAGHRIB -> "Maġrib"
    Teil.AWWABIN -> "Auwābīn"
    Teil.ISHA -> "ʿIšāʾ"
    Teil.TAHAJJUD -> "Tahaǧǧud"
}

private fun beschreibung(band: Band): String = when (band) {
    Band.FAJR -> "Sonnenbahn: Im Osten wird der Himmel unter dem Horizont hell, die Sonne ist noch nicht zu sehen."
    Band.ZUHR -> "Sonnenbahn: die Sonne hat den höchsten Punkt gerade überschritten, der Schatten des Stabs wird wieder länger."
    Band.ASR -> "Sonnenbahn: Die Sonne steht im Westen, der Stab wirft einen Schatten. Markiert sind zwei Längen, der einfache und der doppelte Schatten des Stabs."
    Band.MAGHRIB -> "Sonnenbahn: die Sonne ist gerade unter den westlichen Horizont gesunken."
    Band.ISHA -> "Sonnenbahn: dunkler Nachthimmel mit Sternen, die Sonne ist weit unter dem Horizont."
    Band.TAG -> "Sonnenbahn: ein Bogen vom Osten über den Höchststand bis zum Westen, die Sonne steht kurz nach dem höchsten Punkt."
}

/**
 * Stilisierte Sonnenbahn für den Kopf eines Kapitels. Gezeichnet in einer
 * Fläche von 360 mal 150 Einheiten, nur mit Token-Farben, ohne Uhrzeiten
 * und ohne Bewegung.
 */
@Composable
fun Himmelsband(band: Band, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val messer = rememberTextMeasurer()
    val nacht = band == Band.ISHA
    // Dunkler Himmel: im Nachtthema der dunkle Grund (canvas), im Tagthema ink; Sterne, Horizont und Schrift nehmen das Gegenstück.
    val themaDunkel = t.canvas.luminance() < 0.5f
    val himmel = if (themaDunkel) t.canvas else t.ink
    val hell = if (themaDunkel) t.ink else t.canvas
    val etikett = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp, color = if (nacht) hell else t.stone, textAlign = TextAlign.Center,
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(360f / 150f)
            .clip(RoundedCornerShape(18.dp))
            .semantics { contentDescription = beschreibung(band) },
    ) {
        val s = size.width / 360f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawRect(if (nacht) himmel else t.surface)
        val strich = 1.5f * s
        val horizont = if (nacht) hell else t.hairline
        val bahn = Path().apply {
            moveTo(20f * s, 128f * s)
            quadraticTo(180f * s, -10f * s, 340f * s, 128f * s)
        }
        drawPath(
            bahn, t.ochre,
            style = Stroke(strich, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 4f * s))),
        )
        when (band) {
            Band.FAJR -> drawCircle(
                Brush.radialGradient(
                    listOf(t.ochre.copy(alpha = 0.55f), Color.Transparent),
                    center = p(22f, 128f), radius = 110f * s,
                ),
                radius = 110f * s, center = p(22f, 128f),
            )
            Band.ZUHR, Band.TAG -> {
                drawCircle(t.ochre, 4f * s, p(180f, 59f), style = Stroke(strich))
                drawCircle(t.gold, 11f * s, p(198f, 60f))
                stab(t, s, 180f, 28f, 13f, 0.55f)
            }
            Band.ASR -> {
                drawCircle(t.gold, 11f * s, p(262f, 78f))
                stab(t, s, 120f, 28f, 28f, 0.55f)
                stab(t, s, 120f, 0f, 56f, 0.3f)
                drawLine(t.ink, p(92f, 120f), p(92f, 136f), strich)
                drawLine(t.ink, p(64f, 120f), p(64f, 136f), strich)
            }
            Band.MAGHRIB -> {
                drawCircle(t.gold, 11f * s, p(326f, 134f))
                drawRect(t.surface, p(300f, 128f), Size(60f * s, 30f * s))
            }
            Band.ISHA -> listOf(
                40f to 30f, 90f to 70f, 130f to 22f, 170f to 55f, 215f to 28f,
                250f to 80f, 290f to 36f, 320f to 70f, 60f to 100f, 200f to 95f,
            ).forEach { (x, y) -> drawCircle(hell, 1.6f * s, p(x, y)) }
        }
        drawLine(horizont, p(12f, 128f), p(348f, 128f), strich)
        beschrifte(messer, etikett, s, band)
    }
}

/** Stab auf dem Horizont bei [x] mit Schatten der Länge [laenge] nach links. */
private fun DrawScope.stab(t: Tokens, s: Float, x: Float, hoehe: Float, laenge: Float, alpha: Float) {
    if (hoehe > 0f) drawLine(t.ink, Offset(x * s, 128f * s), Offset(x * s, (128f - hoehe) * s), 2f * s)
    drawLine(
        t.ink.copy(alpha = alpha), Offset(x * s, 128f * s), Offset((x - laenge) * s, 128f * s),
        4f * s, StrokeCap.Round,
    )
}

private fun DrawScope.beschrifte(messer: TextMeasurer, stil: TextStyle, s: Float, band: Band) {
    fun text(w: String, x: Float, y: Float) {
        val r = messer.measure(w, stil)
        drawText(r, topLeft = Offset(x * s - r.size.width / 2f, y * s - r.size.height))
    }
    text("Osten", 60f, 146f)
    text("Westen", 300f, 146f)
    if (band == Band.ZUHR || band == Band.TAG) text("Höchststand", 180f, 44f)
}

/**
 * Streifen mit den [teile] als gleich breite, antippbare Abschnitte in
 * Tagesfolge. Keine Zeiten; [onTeil] meldet den gewählten Teil.
 */
@Composable
fun Tagesband(teile: List<Teil>, onTeil: (Teil) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    // Ab großer Schrift (Systemschrift 150 % und mehr) passen sechs Felder nicht mehr
    // nebeneinander: Namen brächen mitten im Wort. Dann zwei Zeilen zu je drei Feldern.
    val proZeile = felderJeZeile(LocalDensity.current.fontScale, teile.size)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        teile.withIndex().chunked(proZeile).forEach { zeile ->
            Row(
                Modifier
                    .fillMaxWidth()
                    // Alle Felder so hoch wie das höchste, auch wenn ein Name umbricht.
                    .height(IntrinsicSize.Max),
            ) {
                zeile.forEach { (i, teil) -> Abschnittsfeld(teil, i, t, onTeil) }
                // Unvollständige letzte Zeile: Felder bleiben gleich breit.
                repeat(proZeile - zeile.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Felder je Zeile im [Tagesband]: alle in einer Zeile, ab [fontScale] 1,5 höchstens drei. */
internal fun felderJeZeile(fontScale: Float, anzahl: Int): Int =
    if (fontScale >= 1.5f) minOf(3, anzahl).coerceAtLeast(1) else anzahl.coerceAtLeast(1)

@Composable
private fun RowScope.Abschnittsfeld(teil: Teil, index: Int, t: Tokens, onTeil: (Teil) -> Unit) {
    val name = teilName(teil)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .background(t.surface)
            .border(0.5.dp, t.hairline)
            .clickable(role = Role.Button) { onTeil(teil) }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            // Lange Namen („Vor Sonnenuntergang“, „Tahaǧǧud“) passen nur ohne Sperrung und mit
            // deutscher Silbentrennung in die sechs Felder.
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.sp,
                hyphens = Hyphens.Auto,
                localeList = LocaleList("de"),
            ),
            color = if (index % 2 == 0) t.ink else t.stone,
            textAlign = TextAlign.Center,
            // Keine Zeilengrenze: bei großer Schrift darf das Feld wachsen, statt abzuschneiden.
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        )
    }
}
