package de.gebetszeiten.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em

/** ʿ (U+02BF, ʿAin) und ʾ (U+02BE, Hamza) der DMG-Umschrift. */
private fun isDmgHalfRing(c: Char) = c == 'ʿ' || c == 'ʾ'

/**
 * Titel in Serifenschrift mit größerem ʿ und ʾ. In Noto Serif Display Light
 * stehen die beiden Halbringe winzig und abgesetzt über der Zeile; hier
 * bekommen sie normales Gewicht, etwas mehr Größe und rücken leicht nach
 * unten an den Buchstaben. Der Text selbst bleibt unverändert (TalkBack liest
 * dasselbe).
 */
fun dmgTitle(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    text.forEachIndexed { i, c ->
        if (isDmgHalfRing(c)) addStyle(DMG_HALF_RING, i, i + 1)
    }
}

private val DMG_HALF_RING = SpanStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 1.15.em,
    baselineShift = BaselineShift(-0.18f),
)
