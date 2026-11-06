package de.gebetszeiten.ui.verstehen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.ui.theme.LocalHighContrast
import de.gebetszeiten.ui.theme.NaskhFamily
import de.gebetszeiten.ui.theme.Tokens
import de.gebetszeiten.ui.theme.contrastRatio
import java.util.Locale

/** Teile, die eine verpönte Zeit sind und kein Gebet: dort fragt der Kopf „Wann gilt das?“. */
private val MAKRUH_TEILE = setOf(Teil.SHURUQ, Teil.ZAWAL, Teil.ISFIRAR)

/** Kopf der Zeitfrage eines Abschnitts. */
internal fun wannKopf(teil: Teil): String = if (teil in MAKRUH_TEILE) "Wann gilt das?" else "Wann bete ich?"

/** Kopf der Frage nach dem Grund: bei verpönten Zeiten „Warum ist das so?“ (Entscheidung vom 06.11.2026). */
internal fun warumKopf(teil: Teil): String = if (teil in MAKRUH_TEILE) "Warum ist das so?" else "Warum lohnt es sich?"

/** Legendeneintrag einer Rakʿa-Art, ausgezeichnet für [zerlege]; der Begriff öffnet das Glossar. */
internal fun legende(art: RakaArt): String = when (art) {
    RakaArt.FARD -> "[[Farḍ]]"
    RakaArt.MUAKKADA -> "[[Sunna muʾakkada]]"
    RakaArt.MANDUB -> "[[Mandūb|empfohlen]]"
    RakaArt.WITR -> "Witr, [[Wāǧib|wāǧib]]"
}

/** Kurzname im Rakʿa-Block (der volle Name steht in der Legende und für die Sprachausgabe). */
private fun kurzname(art: RakaArt): String = when (art) {
    RakaArt.FARD -> "Farḍ"
    RakaArt.MUAKKADA -> "Sunna"
    RakaArt.MANDUB -> "empf."
    RakaArt.WITR -> "Witr"
}

private fun sprechname(art: RakaArt): String = when (art) {
    RakaArt.FARD -> "Farḍ"
    RakaArt.MUAKKADA -> "Sunna muʾakkada"
    RakaArt.MANDUB -> "empfohlen"
    RakaArt.WITR -> "Witr, wāǧib"
}

/** Tippbare Teile eines Lehrtexts: Glossarbegriff und Belegnummer. */
internal class Lesehilfen(
    val nummer: (String) -> Int,
    val onBegriff: (String) -> Unit,
    val onVerweis: (Int) -> Unit,
)

/**
 * Ein Abschnitt in der Leseordnung von Ton C: Himmelsband (nur mit [band]),
 * Kopf und Name, Zeitzeile, Merksatz, die W-Fragen, der Hinweis in Gold und
 * „Mehr zu …“. Ohne Zeiten ([day] null) fehlt nur die Zeitzeile.
 */
@Composable
internal fun AbschnittBlock(
    abschnitt: Abschnitt,
    band: Band?,
    erster: Boolean,
    day: VerstehenDay?,
    settings: AppSettings,
    freitag: Boolean,
    hilfen: Lesehilfen,
    t: Tokens,
) {
    val typo = MaterialTheme.typography
    Column(Modifier.fillMaxWidth()) {
        if (!erster) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
            Spacer(Modifier.height(32.dp))
        }
        band?.let {
            Himmelsband(it)
            Spacer(Modifier.height(20.dp))
        }
        Text(abschnitt.kopf.uppercase(Locale.GERMAN), style = typo.labelSmall, color = t.accent)
        Text(
            abschnitt.name,
            style = typo.displayLarge,
            color = t.ink,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        zeitzeile(abschnitt.teil, day, settings.city, settings.timeOffsets)?.let { zeile ->
            Text(
                zeile,
                style = typo.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = t.stone,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Merksatz(abschnitt.merksatz, hilfen, t)

        FrageKopf(wannKopf(abschnitt.teil), t)
        LehrText(abschnitt.wann, typo.bodyLarge, t.ink, hilfen, t)

        if (abschnitt.wieviele != null || abschnitt.rakaat.isNotEmpty()) {
            FrageKopf("Wie viele Rakʿa?", t)
            abschnitt.wieviele?.let { LehrText(it, typo.bodyLarge, t.ink, hilfen, t) }
            if (abschnitt.rakaat.isNotEmpty()) {
                val gumua = abschnitt.rakaatGumua?.takeIf { freitag }
                Spacer(Modifier.height(10.dp))
                RakaFolge(abschnitt.rakaat, t)
                val arten = (abschnitt.rakaat + gumua.orEmpty()).map { it.art }.toSet().sortedBy { it.ordinal }
                RakaLegende(arten, hilfen, t)
                gumua?.let {
                    Text(
                        "Am Freitag: Ǧumʿa",
                        style = typo.titleSmall,
                        color = t.ink,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                    )
                    RakaFolge(it, t)
                }
            }
        }

        FrageKopf(warumKopf(abschnitt.teil), t)
        LehrText(abschnitt.warum, typo.bodyLarge, t.ink, hilfen, t)

        Hinweis(abschnitt.achtung, hilfen, t)
        MehrZu(abschnitt.name, abschnitt.mehr, hilfen, t)
    }
}

/** Zwischenkopf einer W-Frage. */
@Composable
private fun FrageKopf(text: String, t: Tokens) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = t.ink,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * Leichte Tönung des Grunds in [farbe]: tags etwa 8 %, nachts 18 %, weil 8 % auf
 * Schwarz kaum zu sehen sind (im Entwurf nachts #1d241b, etwa 20 % Waldgrün).
 */
private fun toenung(t: Tokens, farbe: Color): Color =
    lerp(t.canvas, farbe, if (t.canvas.luminance() < 0.5f) 0.18f else 0.08f)

/** Merksatz in Serife im leicht grünen Kasten (siehe [toenung]). */
@Composable
private fun Merksatz(text: String, hilfen: Lesehilfen, t: Tokens) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(toenung(t, t.accent))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Merksatz".uppercase(Locale.GERMAN), style = MaterialTheme.typography.labelSmall, color = t.accent)
        Spacer(Modifier.height(4.dp))
        LehrText(text, MaterialTheme.typography.headlineSmall, t.ink, hilfen, t)
    }
}

/**
 * „Worauf achte ich?“ im Hinweiskasten in Gold. Der Kasten ist leicht golden
 * getönt, solange Gold darauf den Kontrast hält (4,5:1, im Hochkontrast 7:1);
 * sonst bleibt der Grund und ein goldener Rand zeigt den Kasten.
 */
@Composable
private fun Hinweis(text: String, hilfen: Lesehilfen, t: Tokens) {
    val form = RoundedCornerShape(12.dp)
    val tint = toenung(t, t.gold)
    val noetig = if (LocalHighContrast.current) 7.0 else 4.5
    val getoent = contrastRatio(t.gold, tint) >= noetig
    Column(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(form)
            .background(if (getoent) tint else t.canvas)
            .then(if (getoent) Modifier else Modifier.border(1.dp, t.gold, form))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            "Worauf achte ich?",
            style = MaterialTheme.typography.titleSmall,
            color = t.gold,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        LehrText(text, MaterialTheme.typography.bodyLarge, t.ink, hilfen, t)
    }
}

/** „Mehr zu …“: Zeile mit Pfeil, klappt die Fragen der zweiten Schicht auf. */
@Composable
private fun MehrZu(name: String, mehr: List<Frage>, hilfen: Lesehilfen, t: Tokens) {
    if (mehr.isEmpty()) return
    var auf by rememberSaveable(name) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
        Aufklapper("Mehr zu $name", auf, { auf = !auf }, MaterialTheme.typography.titleSmall, t)
        // AnimatedVisibility folgt der Animationsskala des Systems: bei „Animationen aus“ ohne Bewegung.
        AnimatedVisibility(visible = auf) {
            Column {
                mehr.forEach { f ->
                    LehrText(
                        f.kopf, MaterialTheme.typography.titleSmall, t.ink, hilfen, t,
                        Modifier.padding(top = 12.dp).semantics { heading() },
                    )
                    LehrText(f.text, MaterialTheme.typography.bodyLarge, t.ink, hilfen, t, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Antippbare Zeile in Waldgrün mit Pfeil nach unten (zu) oder oben (offen). */
@Composable
private fun Aufklapper(text: String, auf: Boolean, onClick: () -> Unit, stil: TextStyle, t: Tokens) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (auf) "aufgeklappt" else "zugeklappt" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = stil, color = t.accent)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val oben = if (auf) 0.65f else 0.35f
            val unten = if (auf) 0.35f else 0.65f
            val strich = 1.6.dp.toPx()
            drawLine(t.accent, Offset(w * 0.15f, w * oben), Offset(w * 0.5f, w * unten), strich, StrokeCap.Round)
            drawLine(t.accent, Offset(w * 0.5f, w * unten), Offset(w * 0.85f, w * oben), strich, StrokeCap.Round)
        }
    }
}

/** Ein Beleg der Endnoten: Nummer, Quelle in Stein, Arabisch zum Aufklappen (rechts nach links). */
@Composable
internal fun BelegEintrag(nr: Int, beleg: Beleg, offen: Boolean, onUmschalten: () -> Unit, t: Tokens) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            "$nr",
            style = MaterialTheme.typography.labelLarge,
            color = t.gold,
            modifier = Modifier.width(28.dp).semantics { contentDescription = "Beleg $nr" },
        )
        Column(Modifier.weight(1f)) {
            // Das Direktzitat auf Deutsch ist der sichtbare Teil der Endnote, die Quelle darunter.
            Text(beleg.quote.german, style = MaterialTheme.typography.bodyMedium, color = t.ink)
            Text(
                beleg.quote.source,
                style = MaterialTheme.typography.bodyMedium,
                color = t.stone,
                modifier = Modifier.padding(top = 4.dp),
            )
            Aufklapper("Arabisch", offen, onUmschalten, MaterialTheme.typography.labelLarge, t)
            AnimatedVisibility(visible = offen) {
                // Rechtsbündig: TextAlign.End wäre in einem RTL-Absatz die linke Kante.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        beleg.quote.arabic,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = NaskhFamily,
                            fontSize = 19.sp,
                            lineHeight = 1.6.em,
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl,
                        ),
                        color = t.stone,
                    )
                }
            }
        }
    }
}

/** Rakʿa als Blöcke in Gebetsreihenfolge; jeder so breit, wie er Rakʿa hat (mindestens 2). */
@Composable
private fun RakaFolge(blocks: List<RakaBlock>, t: Tokens) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { b -> RakaFeld(b, t, Modifier.weight(rakaGewicht(b))) }
    }
}

private val RAKA_FORM = RoundedCornerShape(10.dp)

@Composable
private fun RakaFeld(b: RakaBlock, t: Tokens, modifier: Modifier) {
    val schrift = when (b.art) {
        RakaArt.FARD, RakaArt.WITR -> t.canvas
        RakaArt.MUAKKADA -> t.accent
        RakaArt.MANDUB -> t.gold
    }
    val zahl = b.beschriftung ?: b.anzahl.toString()
    Column(
        modifier
            .fillMaxHeight()
            .rakaRahmen(b.art, t, RAKA_FORM, 10.dp)
            .clearAndSetSemantics { contentDescription = "$zahl Rakʿa ${sprechname(b.art)}" }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            zahl,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (b.beschriftung != null) 16.sp else 22.sp,
                lineHeight = 1.1.em,
            ),
            color = schrift,
            textAlign = TextAlign.Center,
        )
        Text(
            kurzname(b.art),
            style = MaterialTheme.typography.bodySmall,
            color = schrift,
            textAlign = TextAlign.Center,
            // Bei großer Schrift darf der Kurzname umbrechen statt abgeschnitten zu werden.
            maxLines = 2,
        )
    }
}

/** Füllung oder Rand einer Rakʿa-Art: Farḍ und Witr gefüllt, Sunna muʾakkada umrandet, empfohlen gestrichelt. */
private fun Modifier.rakaRahmen(art: RakaArt, t: Tokens, form: Shape, ecke: androidx.compose.ui.unit.Dp): Modifier =
    when (art) {
        RakaArt.FARD -> clip(form).background(t.accent)
        RakaArt.WITR -> clip(form).background(t.gold)
        RakaArt.MUAKKADA -> clip(form).border(1.5.dp, t.accent, form)
        RakaArt.MANDUB -> drawBehind {
            val w = 1.5.dp.toPx()
            drawRoundRect(
                color = t.gold,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(ecke.toPx() - w / 2f),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }

/** Legende unter der Folge; die Begriffe öffnen das Glossar. */
@Composable
private fun RakaLegende(arten: List<RakaArt>, hilfen: Lesehilfen, t: Tokens) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        arten.forEach { art ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).rakaRahmen(art, t, RoundedCornerShape(3.dp), 3.dp))
                Spacer(Modifier.width(5.dp))
                LehrText(legende(art), MaterialTheme.typography.bodySmall, t.stone, hilfen, t)
            }
        }
    }
}

/** Gesetzter Lehrtext: Anzeige und Bereiche der Begriffe (für die Punktlinie). */
private class Gesetzt(val text: AnnotatedString, val begriffe: List<IntRange>)

/**
 * Setzt [roh] über [zerlege]: Begriffe in Waldgrün (Antippen öffnet das Glossar),
 * Verweise hochgestellt in Gold (Antippen springt zum Beleg). Die Absatzsemantik
 * bleibt unberührt, damit TalkBack die Links als Aktionen anbietet; eine
 * Belegnummer liest es dabei als bloße Zahl.
 */
private fun setze(roh: String, hilfen: Lesehilfen, t: Tokens): Gesetzt {
    val stuecke = zerlege(roh, hilfen.nummer)
    val begriffe = mutableListOf<IntRange>()
    val hoch = SpanStyle(color = t.gold, fontSize = 0.7.em, fontWeight = FontWeight.SemiBold, baselineShift = BaselineShift.Superscript)
    var vorher: Stueck? = null
    val text = buildAnnotatedString {
        for (s in stuecke) {
            when (s) {
                is Stueck.Text -> {
                    append(s.s)
                }
                is Stueck.Begriff -> {
                    val start = length
                    withLink(LinkAnnotation.Clickable("begriff:${s.key}", TextLinkStyles(SpanStyle(color = t.accent))) { hilfen.onBegriff(s.key) }) {
                        append(s.anzeige)
                    }
                    begriffe += start until length
                }
                is Stueck.Verweis -> {
                    // Zwei Verweise hintereinander: mit Komma getrennt, sonst läse man „34“.
                    if (vorher is Stueck.Verweis) withStyle(hoch) { append(",") }
                    withLink(LinkAnnotation.Clickable("beleg:${s.nr}", TextLinkStyles(hoch)) { hilfen.onVerweis(s.nr) }) {
                        append(s.nr.toString())
                    }
                }
            }
            vorher = s
        }
    }
    return Gesetzt(text, begriffe)
}

/** Lehrtext mit antippbaren Begriffen (gepunktet unterstrichen) und Belegnummern. */
@Composable
private fun LehrText(
    roh: String,
    stil: TextStyle,
    farbe: Color,
    hilfen: Lesehilfen,
    t: Tokens,
    modifier: Modifier = Modifier,
) {
    // hilfen ist je Kapitel gemerkt (KapitelSeite), daher als Schlüssel stabil.
    val gesetzt = remember(roh, t, hilfen) { setze(roh, hilfen, t) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        gesetzt.text,
        style = stil,
        color = farbe,
        onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind { layout?.let { punktiere(it, gesetzt.begriffe, t.accent) } },
    )
}

/** Gepunktete Linie unter jedem Begriff, auch über einen Zeilenumbruch hinweg. */
private fun DrawScope.punktiere(l: TextLayoutResult, bereiche: List<IntRange>, farbe: Color) {
    val dicke = 1.dp.toPx()
    val effekt = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.dp.toPx()))
    val n = l.layoutInput.text.length
    for (r in bereiche) {
        if (r.isEmpty() || r.last >= n) continue
        val z0 = l.getLineForOffset(r.first)
        val z1 = l.getLineForOffset(r.last)
        for (z in z0..z1) {
            val x0 = if (z == z0) l.getBoundingBox(r.first).left else l.getLineLeft(z)
            val x1 = if (z == z1) l.getBoundingBox(r.last).right else l.getLineRight(z)
            val y = l.getLineBaseline(z) + 2.dp.toPx()
            drawLine(farbe, Offset(x0, y), Offset(x1, y), dicke, pathEffect = effekt)
        }
    }
}
