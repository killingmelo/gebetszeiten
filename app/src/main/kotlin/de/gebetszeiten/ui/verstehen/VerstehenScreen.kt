package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.PRAYER_VIRTUES
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.ui.dmgTitle
import de.gebetszeiten.ui.photoDescriptionRes
import de.gebetszeiten.ui.photoRes
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.NaskhFamily
import de.gebetszeiten.ui.theme.Tokens
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.util.Locale

private val GUTTER = 24.dp
private val ENTRY_GAP = 56.dp
private const val VIRTUE_HEADING = "Wert dieses Gebets"

/**
 * Reiter „Zeiten verstehen“: je Abschnitt Foto, Titel, Zitate (Arabisch, Deutsch,
 * Quelle), bei den fünf Gebeten „Wert dieses Gebets“, bei Ẓuhr und ʿAṣr die
 * Sonnenuhr, am Ende einmal der Hinweis zur
 * Übersetzung. [focus] springt zu einem Abschnitt (aus „Heute“ angetippt) und wird
 * danach mit [onFocusConsumed] zurückgegeben.
 */
@Composable
fun VerstehenScreen(
    inner: PaddingValues,
    settings: AppSettings,
    focus: Section?,
    onFocusConsumed: () -> Unit,
) {
    val t = LocalTokens.current
    val listState = rememberLazyListState()

    // Ohne Bewegung: direkt an den Abschnitt springen
    LaunchedEffect(focus) {
        if (focus != null) {
            val index = VERSTEHEN.indexOfFirst { it.section == focus }
            if (index >= 0) listState.scrollToItem(index)
            onFocusConsumed()
        }
    }

    // Die Schattenspitze wandert: einmal pro Minute neu (Daten, keine Animation).
    // Nur im Vordergrund, wie auf „Heute“: sonst lief die Schleife im
    // zwischengespeicherten Hintergrundprozess weiter.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(ZonedDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = ZonedDateTime.now() // sofort auffrischen, wenn die App wieder sichtbar wird
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                value = ZonedDateTime.now()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(t.canvas),
        contentPadding = PaddingValues(
            start = GUTTER,
            end = GUTTER,
            top = inner.calculateTopPadding() + GUTTER,
            bottom = inner.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ENTRY_GAP),
    ) {
        items(VERSTEHEN, key = { it.section.name }) { entry ->
            EntryBlock(entry, settings, now, t)
        }
        item(key = "translation-note") {
            Text(
                TRANSLATION_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = t.stone,
            )
        }
    }
}

@Composable
private fun EntryBlock(entry: VerstehenEntry, settings: AppSettings, now: ZonedDateTime, t: Tokens) {
    Column(Modifier.fillMaxWidth()) {
        entry.photo?.let { key ->
            Image(
                painter = painterResource(photoRes(key)),
                contentDescription = stringResource(photoDescriptionRes(key)),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(20.dp))
        }
        Text(
            dmgTitle(entry.title),
            style = MaterialTheme.typography.headlineSmall,
            color = t.ink,
            modifier = Modifier.semantics { heading() },
        )
        entry.quotes.forEachIndexed { i, quote ->
            if (i == 0) {
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(t.hairline),
                )
                Spacer(Modifier.height(24.dp))
            }
            QuoteBlock(quote, t)
        }
        entry.section.prayer()?.let { PRAYER_VIRTUES[it] }?.let { texts ->
            Spacer(Modifier.height(32.dp))
            VirtueBlock(texts, t)
        }
        if (entry.showsSundial) {
            Spacer(Modifier.height(32.dp))
            SundialView(
                latDeg = settings.latitude,
                lonDeg = settings.longitude,
                place = settings.city,
                now = now,
            )
        }
        entry.note?.let { note ->
            Spacer(Modifier.height(24.dp))
            Text(note, style = MaterialTheme.typography.bodyLarge, color = t.ink)
        }
    }
}

/** Die fünf Pflichtgebete; Nebenabschnitte (Šurūq, Ḍuḥā, Makrūh …) haben keinen Wert-Text. */
private fun Section.prayer(): Prayer? = when (this) {
    Section.FAJR -> Prayer.FAJR
    Section.ZUHR -> Prayer.DHUHR
    Section.ASR -> Prayer.ASR
    Section.MAGHRIB -> Prayer.MAGHRIB
    Section.ISHA -> Prayer.ISHA
    else -> null
}

/** „Wert dieses Gebets“: alle freigegebenen Kurztexte des Gebets mit Fundstelle. */
@Composable
private fun VirtueBlock(texts: List<String>, t: Tokens) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            VIRTUE_HEADING.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = t.stone,
            modifier = Modifier.semantics { heading() },
        )
        texts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.ink) }
    }
}

@Composable
private fun QuoteBlock(quote: Quote, t: Tokens) {
    Column(Modifier.fillMaxWidth()) {
        // Arabisch genau wie geliefert (die geraden Anführungszeichen markieren den Grundtext).
        // Rechtsbündig: TextAlign.End wäre in einem RTL-Absatz die linke Kante.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                quote.arabic,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NaskhFamily,
                    fontSize = 21.sp,
                    lineHeight = 1.6.em,
                    textAlign = TextAlign.Right,
                    textDirection = TextDirection.Rtl,
                ),
                color = t.ink,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(quote.german, style = MaterialTheme.typography.bodyLarge, color = t.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            quote.source,
            // Quellen in normaler Schreibung (DMG), daher weniger Sperrung als bei Großbuchstaben-Zeilen
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = t.stone,
        )
    }
}
