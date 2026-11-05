package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.PRAYER_VIRTUES
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.prayer.heroState
import de.gebetszeiten.ui.dmgTitle
import de.gebetszeiten.ui.photoDescriptionRes
import de.gebetszeiten.ui.photoRes
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.NaskhFamily
import de.gebetszeiten.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private val GUTTER = 24.dp
private val ENTRY_GAP = 40.dp
private const val VIRTUE_HEADING = "Wert dieses Gebets"
private const val BEGINS = "BEGINNT"
private const val ENDS = "ENDET"
private const val RAKAAT_HEAD = "RAKʿA"
private const val GLOSSARY_HEADING = "Begriffe"

/**
 * Reiter „Zeiten verstehen“: oben je Abschnitt ein Reiter (antippen oder
 * wischen), darin Foto, Titel, Beginn und Ende von heute, Zitate (Arabisch,
 * Deutsch, Quelle), bei den fünf Gebeten „Wert dieses Gebets“, bei Ẓuhr und
 * ʿAṣr die Sonnenuhr, darunter die Nebenabschnitte des Gebets. Geöffnet wird
 * der Reiter des laufenden Abschnitts. [focus] (aus „Heute“ angetippt) öffnet
 * den passenden Reiter an der richtigen Stelle und wird danach mit
 * [onFocusConsumed] zurückgegeben. Inhalte kommen aus [VERSTEHEN], die
 * Aufteilung aus [VERSTEHEN_TABS].
 */
@Composable
fun VerstehenScreen(
    inner: PaddingValues,
    settings: AppSettings,
    focus: Section?,
    onFocusConsumed: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current

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

    // Heutige Zeiten für die Kacheln; ohne amtliche Zeiten bleiben die Kacheln weg.
    val today = now.toLocalDate()
    val day by produceState<VerstehenDay?>(null, settings, today) {
        val zone = ZoneId.systemDefault()
        val times = PrayerProvider.daily(context, settings, today, zone)
        val nextFajr = PrayerProvider.daily(context, settings, today.plusDays(1), zone)?.fajr
        value = times?.let { VerstehenDay(it, IslamicWindows.karaha(it), IslamicWindows.nafl(it, nextFajr), nextFajr) }
    }

    val pager = rememberPagerState { VERSTEHEN_TABS.size }
    val scope = rememberCoroutineScope()
    // Wohin innerhalb eines Reiters gesprungen werden soll (aus „Heute“).
    var pendingFocus by remember { mutableStateOf<Section?>(null) }
    // Gespeichert über Neuaufbau (Drehen, Tag/Nacht), sonst sprang der Reiter zurück.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focus) {
        if (focus != null) {
            pager.scrollToPage(tabIndexOf(focus))
            pendingFocus = focus
            opened = true
            onFocusConsumed()
        }
    }
    // Ohne Sprung: einmal beim Öffnen auf den laufenden Abschnitt.
    LaunchedEffect(day) {
        val d = day ?: return@LaunchedEffect
        if (!opened) {
            val moment = heroState(d.times, d.karaha, d.nafl, d.nextFajr, ZonedDateTime.now(), isToday = true).moment
            pager.scrollToPage(tabOfMoment(moment))
            opened = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.canvas)
            .padding(top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding()),
    ) {
        TabRowPills(
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            t = t,
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { VERSTEHEN_TABS[it].main.name }) { page ->
            TabPage(
                tab = VERSTEHEN_TABS[page],
                index = page,
                day = day,
                settings = settings,
                now = now,
                focus = pendingFocus?.takeIf { tabIndexOf(it) == page },
                onFocusDone = { pendingFocus = null },
                t = t,
            )
        }
    }
}

/** Reiter als Pillen in einer waagrecht scrollbaren Zeile; der gewählte in Waldgrün. */
@Composable
private fun TabRowPills(selected: Int, onSelect: (Int) -> Unit, t: Tokens) {
    val scroll = rememberScrollState()
    // Der gewählte Reiter bleibt sichtbar, auch ʿIšāʾ am rechten Rand.
    LaunchedEffect(selected, scroll.maxValue) {
        scroll.animateScrollTo(scroll.maxValue * selected / (VERSTEHEN_TABS.size - 1))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VERSTEHEN_TABS.forEachIndexed { i, tab ->
            val on = i == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) t.accent else t.canvas)
                    .border(1.dp, if (on) t.accent else t.hairline, RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) t.canvas else t.ink,
                )
            }
        }
    }
}

/**
 * Ein Reiter in der Leseordnung vom 05.11.2026 („erst auf einen Blick, dann
 * tiefer“): Wer nach dem ersten Abschnitt aufhört, weiß Zeit, Rakʿa und
 * Bedeutung; alles Weitere vertieft. Inhalte und Zitate bleiben unverändert.
 */
@Composable
private fun TabPage(
    tab: VerstehenTab,
    index: Int,
    day: VerstehenDay?,
    settings: AppSettings,
    now: ZonedDateTime,
    focus: Section?,
    onFocusDone: () -> Unit,
    t: Tokens,
) {
    val listState = rememberLazyListState()
    val main = VERSTEHEN.single { it.section == tab.main }
    val sides = tab.sides.map { s -> VERSTEHEN.single { it.section == s } }
    val cautionSides = sides.filter { isCautionSide(it.section) }
    val otherSides = sides.filterNot { isCautionSide(it.section) }
    val cautionGroups = main.groups.filter { it.place == Place.CAUTION }
    val scholarGroups = main.groups.filter { it.place == Place.SCHOLARS }
    val virtues = main.section.prayer()?.let { PRAYER_VIRTUES[it] }.orEmpty()
    val mainTerm = GLOSSAR[tab.label]
    val otherTerms = tab.glossary.filter { it != tab.label }.mapNotNull { GLOSSAR[it] }

    // Reihenfolge der Einträge; ein Sprung aus „Heute“ sucht seinen Schlüssel darin.
    val keys = buildList {
        add("head")
        add("glance")
        if (virtues.isNotEmpty()) add("why")
        add("scholars")
        if (cautionGroups.isNotEmpty() || cautionSides.isNotEmpty()) add("caution")
        cautionSides.forEach { add("side-${it.section.name}") }
        if (otherSides.isNotEmpty()) add("extra")
        otherSides.forEach { add("side-${it.section.name}") }
        if (main.showsSundial || main.note != null) add("details")
        if (otherTerms.isNotEmpty()) add("glossary")
        add("translation-note")
    }
    // Ohne Bewegung: direkt an den Abschnitt springen
    LaunchedEffect(focus) {
        if (focus != null) {
            listState.scrollToItem(keys.indexOf(itemKeyOf(focus)).coerceAtLeast(0))
            onFocusDone()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(ENTRY_GAP),
    ) {
        for (key in keys) {
            item(key = key) {
                when {
                    key == "head" -> PageHead(main, tab, t)
                    key == "glance" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeading("Auf einen Blick", t)
                        day?.let { SpanTiles(tab, tabSpan(index, it), t) }
                        if (tab.rakaat.isNotEmpty()) RakaatTile(tab.rakaat, t)
                        mainTerm?.let {
                            Text(
                                it.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = t.ink,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    // „Wert dieses Gebets“ ist hier selbst der Abschnitt „warum“.
                    key == "why" -> VirtueBlock(virtues, t)
                    key == "scholars" -> Column {
                        SectionHeading("Was die Gelehrten sagen", t)
                        QuoteList(main.quotes, t)
                        scholarGroups.forEach { group ->
                            Spacer(Modifier.height(32.dp))
                            QuoteGroupBlock(group, t)
                        }
                    }
                    key == "caution" -> Column {
                        SectionHeading("Worauf achten", t)
                        cautionGroups.forEachIndexed { i, group ->
                            Spacer(Modifier.height(if (i == 0) 16.dp else 32.dp))
                            QuoteGroupBlock(group, t)
                        }
                    }
                    key == "extra" -> SectionHeading("Dazu gehört", t)
                    key.startsWith("side-") -> {
                        val side = sides.single { "side-${it.section.name}" == key }
                        EntryBlock(
                            entry = side.copy(photo = null),
                            settings = settings,
                            now = now,
                            t = t,
                            subtitle = day?.let { sideTime(side.section, it) },
                        )
                    }
                    key == "details" -> Column {
                        SectionHeading("Für Genaue", t)
                        main.note?.let { note ->
                            Spacer(Modifier.height(16.dp))
                            Text(note, style = MaterialTheme.typography.bodyLarge, color = t.ink)
                        }
                        if (main.showsSundial) {
                            Spacer(Modifier.height(24.dp))
                            SundialView(
                                latDeg = settings.latitude,
                                lonDeg = settings.longitude,
                                place = settings.city,
                                now = now,
                            )
                        }
                    }
                    key == "glossary" -> Column {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
                        Spacer(Modifier.height(24.dp))
                        GlossaryBlock(otherTerms, t)
                    }
                    else -> Text(
                        TRANSLATION_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.stone,
                    )
                }
            }
        }
    }
}

/** Foto, Titel und Wortsinn („Ẓuhr und Zawāl“, darunter „Ẓuhr · Mittag“). */
@Composable
private fun PageHead(main: VerstehenEntry, tab: VerstehenTab, t: Tokens) {
    Column(Modifier.fillMaxWidth()) {
        main.photo?.let { key ->
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
            dmgTitle(main.title),
            style = MaterialTheme.typography.headlineSmall,
            color = t.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "${tab.label} · ${tab.meaning}",
            style = MaterialTheme.typography.bodyMedium,
            color = t.stone,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Überschrift eines Abschnitts der Leseordnung, in Waldgrün über den grauen Gruppenköpfen. */
@Composable
private fun SectionHeading(text: String, t: Tokens) {
    Text(
        text.uppercase(Locale.GERMAN),
        style = MaterialTheme.typography.labelSmall,
        color = t.accent,
        modifier = Modifier.semantics { heading() },
    )
}

/** Zitate untereinander, getrennt durch Haarlinien. */
@Composable
private fun QuoteList(quotes: List<Quote>, t: Tokens) {
    quotes.forEachIndexed { i, quote ->
        if (i == 0) {
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
            Spacer(Modifier.height(24.dp))
        }
        QuoteBlock(quote, t)
    }
}

/** Zwei Kacheln: wann der Abschnitt heute beginnt und endet. */
@Composable
private fun SpanTiles(tab: VerstehenTab, span: TabSpan, t: Tokens) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SpanTile(BEGINS, span.begin, tab.beginLabel, t, Modifier.weight(1f))
        span.end?.let { SpanTile(ENDS, it, tab.endLabel, t, Modifier.weight(1f)) }
            ?: Spacer(Modifier.weight(1f))
    }
}

/** Kachel „Rakʿa“: je Zeile Bezeichnung (Davor, Farḍ, Danach …) und Anzahl. */
@Composable
private fun RakaatTile(lines: List<Pair<String, String>>, t: Tokens) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(t.surface)
            .border(1.dp, t.hairline, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(RAKAAT_HEAD, style = MaterialTheme.typography.labelSmall, color = t.stone)
        lines.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.stone,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = t.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** „Begriffe“: Wortsinn und Herkunft, bei manchen mit Koran oder Ḥadīṯ. */
@Composable
private fun GlossaryBlock(terms: List<GlossaryTerm>, t: Tokens) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            GLOSSARY_HEADING.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = t.stone,
            modifier = Modifier.semantics { heading() },
        )
        terms.forEach { term ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(term.term, style = MaterialTheme.typography.titleMedium, color = t.ink)
                Text(
                    term.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Weitere Zitate unter eigener Überschrift, wie die Hauptzitate gesetzt. */
@Composable
private fun QuoteGroupBlock(group: QuoteGroup, t: Tokens) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            group.heading.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = t.stone,
            modifier = Modifier.semantics { heading() },
        )
        group.quotes.forEachIndexed { i, quote ->
            if (i == 0) {
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
                Spacer(Modifier.height(20.dp))
            }
            QuoteBlock(quote, t)
        }
    }
}

@Composable
private fun SpanTile(head: String, time: String, label: String, t: Tokens, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(t.surface)
            .border(1.dp, t.hairline, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(head, style = MaterialTheme.typography.labelSmall, color = t.stone)
        Text(
            time,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = t.ink,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = t.stone)
    }
}

@Composable
private fun EntryBlock(
    entry: VerstehenEntry,
    settings: AppSettings,
    now: ZonedDateTime,
    t: Tokens,
    subtitle: String? = null,
    tiles: (@Composable () -> Unit)? = null,
) {
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
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = t.stone,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        tiles?.let {
            Spacer(Modifier.height(16.dp))
            it()
        }
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
        entry.groups.forEach { group ->
            Spacer(Modifier.height(32.dp))
            QuoteGroupBlock(group, t)
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
            // Abschnittskopf der Leseordnung, daher wie die übrigen in Waldgrün.
            color = t.accent,
            modifier = Modifier.semantics { heading() },
        )
        texts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.ink) }
    }
}

@Composable
private fun QuoteBlock(quote: Quote, t: Tokens) {
    Column(Modifier.fillMaxWidth()) {
        // Deutsch zuerst (für Einsteiger der Einstieg, 05.11.2026), das Arabische als Quelle darunter.
        Text(quote.german, style = MaterialTheme.typography.bodyLarge, color = t.ink)
        Spacer(Modifier.height(10.dp))
        // Arabisch genau wie geliefert (die geraden Anführungszeichen markieren den Grundtext).
        // Rechtsbündig: TextAlign.End wäre in einem RTL-Absatz die linke Kante.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                quote.arabic,
                modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(8.dp))
        Text(
            quote.source,
            // Quellen in normaler Schreibung (DMG), daher weniger Sperrung als bei Großbuchstaben-Zeilen
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = t.stone,
        )
    }
}
