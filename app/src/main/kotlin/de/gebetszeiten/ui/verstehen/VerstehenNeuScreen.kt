package de.gebetszeiten.ui.verstehen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.prayer.heroState
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private val RAND = 24.dp
private const val SUCHE_PLATZHALTER = "Begriff oder Frage suchen"

/**
 * Das Kapitel aus [lehre], das einen Abschnitt zu `ziel.teil` enthält, oder null.
 * Absichtlich nach Teil statt `ziel.kapitel`: So bleibt der Sprung richtig, auch wenn
 * die Folge in [LEHRE] einmal von der Nummer im [Ziel] abweicht (heute stimmen beide überein).
 */
internal fun kapitelIndex(ziel: Ziel, lehre: List<Kapitel>): Int? =
    lehre.indexOfFirst { k -> k.abschnitte.any { it.teil == ziel.teil } }.takeIf { it >= 0 }

/**
 * Neue Seite „Verstehen“: oben die Suche, darunter die Reiter (Pillen) und je
 * Kapitel aus [LEHRE] eine Seite zum Wischen. Mit Text im Suchfeld liegt eine
 * Trefferliste über Reitern und Seiten (diese bleiben im Aufbau). [focus] (aus „Heute“ angetippt) öffnet über
 * [zielVon] Kapitel und Abschnitt und wird mit [onFocusConsumed] zurückgegeben;
 * ohne [focus] öffnet die Seite einmal beim laufenden Abschnitt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerstehenNeuScreen(
    inner: PaddingValues,
    settings: AppSettings,
    focus: Section?,
    onFocusConsumed: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current

    // Einmal pro Minute neu (Zeitzeile, Freitag), nur im Vordergrund wie auf „Heute“.
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

    // Heutige Zeiten für die Zeitzeilen; ohne amtliche Zeiten (kein Ort) bleibt day null.
    val today = now.toLocalDate()
    val day by produceState<VerstehenDay?>(null, settings, today) {
        val zone = ZoneId.systemDefault()
        val times = PrayerProvider.daily(context, settings, today, zone)
        val nextFajr = PrayerProvider.daily(context, settings, today.plusDays(1), zone)?.fajr
        value = times?.let { VerstehenDay(it, IslamicWindows.karaha(it), IslamicWindows.nafl(it, nextFajr), nextFajr) }
    }

    val pager = rememberPagerState { LEHRE.size }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Wohin innerhalb eines Kapitels gesprungen werden soll (Heute, Start, Suche).
    var pendingZiel by remember { mutableStateOf<Ziel?>(null) }
    // Gespeichert über Neuaufbau (Drehen, Tag/Nacht), sonst sprang die Seite zurück.
    var opened by rememberSaveable { mutableStateOf(false) }
    var frage by rememberSaveable { mutableStateOf("") }
    // Schlüssel des offenen Begriffsblatts.
    var begriff by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = frage.isNotEmpty()) { frage = "" }

    suspend fun springe(ziel: Ziel) {
        // Fehlt das Kapitel noch, bleibt die Seite, wo sie ist.
        val seite = kapitelIndex(ziel, LEHRE) ?: return
        pager.scrollToPage(seite)
        pendingZiel = ziel
    }

    LaunchedEffect(focus) {
        if (focus != null) {
            // Vor dem Sprung setzen: springe() wartet auf den Pager, und lädt day in dieser
            // Zeit, spränge LaunchedEffect(day) sonst zum laufenden Abschnitt.
            opened = true
            springe(zielVon(focus))
            onFocusConsumed()
        }
    }
    // Ohne Sprung: einmal beim Öffnen auf den laufenden Abschnitt.
    LaunchedEffect(day) {
        val d = day ?: return@LaunchedEffect
        if (!opened) {
            val moment = heroState(d.times, d.karaha, d.nafl, d.nextFajr, ZonedDateTime.now(), isToday = true).moment
            opened = true
            springe(zielVonMoment(moment))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.canvas)
            .padding(top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding()),
    ) {
        Suchfeld(frage, { frage = it }, t)
        // Der Pager bleibt auch während der Suche im Aufbau: Leseposition und offene
        // Abschnitte bleiben erhalten, die Trefferliste liegt darüber.
        Box(Modifier.fillMaxSize()) {
            // Während der Suche ist der Pager verdeckt: auch für TalkBack, Schalter-
            // und Tastaturbedienung aus dem Baum nehmen, sonst landet man auf
            // unsichtbarem Inhalt unter der Trefferliste.
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (frage.isNotBlank()) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                ReiterPillen(
                    selected = pager.currentPage,
                    onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                    t = t,
                )
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { LEHRE[it].reiter }) { page ->
                    KapitelSeite(
                        kapitel = LEHRE[page],
                        day = day,
                        settings = settings,
                        now = now,
                        ziel = pendingZiel?.takeIf { kapitelIndex(it, LEHRE) == page },
                        onZielDone = { pendingZiel = null },
                        onBegriff = { begriff = it },
                        t = t,
                    )
                }
            }
            if (frage.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(t.canvas)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Trefferliste(
                        treffer = remember(frage) { suche(frage, LEHRE, GLOSSAR) },
                        onTreffer = { treffer ->
                            frage = ""
                            focusManager.clearFocus()
                            scope.launch { springe(treffer.ziel) }
                        },
                        t = t,
                    )
                }
            }
        }
    }

    val eintrag = begriff?.let { GLOSSAR[it] }
    // Schlüssel ohne Glossareintrag: außerhalb der Komposition zurücksetzen.
    LaunchedEffect(begriff, eintrag) { if (begriff != null && eintrag == null) begriff = null }
    if (eintrag != null) {
        ModalBottomSheet(
            onDismissRequest = { begriff = null },
            containerColor = t.surface,
            contentColor = t.ink,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = RAND, end = RAND, bottom = 32.dp)) {
                Text(
                    eintrag.term,
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(eintrag.text, style = MaterialTheme.typography.bodyLarge, color = t.ink)
            }
        }
    }
}

/** Rundes Suchfeld mit Lupe; ein Kreuz leert es. */
@Composable
private fun Suchfeld(frage: String, onFrage: (String) -> Unit, t: Tokens) {
    OutlinedTextField(
        value = frage,
        onValueChange = onFrage,
        singleLine = true,
        shape = RoundedCornerShape(50),
        placeholder = { Text(SUCHE_PLATZHALTER, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Lupe(t) },
        trailingIcon = if (frage.isEmpty()) null else {
            {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button) { onFrage("") }
                        .semantics { contentDescription = "Suche leeren" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge, color = t.stone)
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = t.ink,
            unfocusedTextColor = t.ink,
            focusedContainerColor = t.canvas,
            unfocusedContainerColor = t.canvas,
            focusedBorderColor = t.accent,
            unfocusedBorderColor = t.hairline,
            cursorColor = t.accent,
            focusedPlaceholderColor = t.stone,
            unfocusedPlaceholderColor = t.stone,
            focusedLeadingIconColor = t.stone,
            unfocusedLeadingIconColor = t.stone,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
}

/** Lupe aus Kreis und Stiel, in Stein. */
@Composable
private fun Lupe(t: Tokens) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val strich = 1.6.dp.toPx()
        drawCircle(t.stone, radius = s * 0.32f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(strich))
        drawLine(t.stone, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.92f, s * 0.92f), strich, StrokeCap.Round)
    }
}

/** Treffer der Suche: Fundstelle und Textprobe; Antippen springt zum Abschnitt. */
@Composable
private fun Trefferliste(treffer: List<Treffer>, onTreffer: (Treffer) -> Unit, t: Tokens) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
    ) {
        if (treffer.isEmpty()) {
            item(key = "leer") {
                Text(
                    "Kein Treffer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = t.stone,
                    modifier = Modifier.padding(horizontal = RAND, vertical = 16.dp),
                )
            }
        }
        items(treffer) { tr ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onTreffer(tr) }
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = RAND, vertical = 12.dp),
            ) {
                Text(tr.ort, style = MaterialTheme.typography.titleSmall, color = t.accent)
                Text(
                    tr.ausschnitt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(Modifier.padding(horizontal = RAND).fillMaxWidth().height(1.dp).background(t.hairline))
        }
    }
}

/** Reiter als Pillen in einer waagrecht scrollbaren Zeile; der gewählte in Waldgrün. */
@Composable
private fun ReiterPillen(selected: Int, onSelect: (Int) -> Unit, t: Tokens) {
    val scroll = rememberScrollState()
    // Der gewählte Reiter bleibt sichtbar, auch der letzte am rechten Rand.
    LaunchedEffect(selected, scroll.maxValue) {
        if (LEHRE.size > 1) scroll.animateScrollTo(scroll.maxValue * selected / (LEHRE.size - 1))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LEHRE.forEachIndexed { i, kapitel ->
            val on = i == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
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
                    kapitel.reiter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) t.canvas else t.ink,
                )
            }
        }
    }
}

/**
 * Ein Kapitel als Liste: beim Tagesband-Kapitel zuerst das [Tagesband], dann je
 * Abschnitt ein [AbschnittBlock], am Ende die Belege einmal für das ganze Kapitel.
 * [ziel] scrollt ohne Bewegung zum Abschnitt und wird mit [onZielDone] gelöscht.
 */
@Composable
private fun KapitelSeite(
    kapitel: Kapitel,
    day: VerstehenDay?,
    settings: AppSettings,
    now: ZonedDateTime,
    ziel: Ziel?,
    onZielDone: () -> Unit,
    onBegriff: (String) -> Unit,
    t: Tokens,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nr = remember(kapitel) { nummern(kapitel) }
    // Belege in Endnotenfolge; nie verwiesene stehen hinten und zählen weiter.
    val belege = remember(kapitel) { kapitel.belege.sortedBy { nr[it.id] ?: Int.MAX_VALUE } }
    val tagesband = kapitel.band == Band.TAG
    // Belege, deren Arabisch aufgeklappt ist (Nummern).
    val offen = remember(kapitel) { mutableStateListOf<Int>() }

    val keys = buildList {
        if (tagesband) add("tagesband")
        kapitel.abschnitte.forEach { add("abschnitt-${it.teil.name}") }
        if (belege.isNotEmpty()) add("belege")
        belege.indices.forEach { add("beleg-${it + 1}") }
        add("hinweis")
    }
    fun zumAbschnitt(teil: Teil) {
        scope.launch { listState.scrollToItem(keys.indexOf("abschnitt-${teil.name}").coerceAtLeast(0)) }
    }
    val onVerweis: (Int) -> Unit = { n ->
        if (n !in offen) offen += n
        scope.launch { listState.scrollToItem(keys.indexOf("beleg-$n").coerceAtLeast(0)) }
    }
    // Einmal je Kapitel, damit LehrText seinen gesetzten Text behalten kann.
    val hilfen = remember(kapitel) { Lesehilfen(nummer = { id -> nr[id] ?: 0 }, onBegriff = onBegriff, onVerweis = onVerweis) }
    // Ohne Bewegung direkt an den Abschnitt springen.
    LaunchedEffect(ziel) {
        if (ziel != null) {
            listState.scrollToItem(keys.indexOf("abschnitt-${ziel.teil.name}").coerceAtLeast(0))
            onZielDone()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = RAND, end = RAND, top = 4.dp, bottom = 40.dp),
    ) {
        for (key in keys) {
            item(key = key) {
                when {
                    key == "tagesband" -> Tagesband(
                        teile = kapitel.abschnitte.map { it.teil },
                        onTeil = ::zumAbschnitt,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                    key.startsWith("abschnitt-") -> {
                        val i = kapitel.abschnitte.indexOfFirst { "abschnitt-${it.teil.name}" == key }
                        AbschnittBlock(
                            abschnitt = kapitel.abschnitte[i],
                            band = kapitel.band.takeUnless { tagesband },
                            erster = i == 0,
                            day = day,
                            settings = settings,
                            freitag = now.dayOfWeek == DayOfWeek.FRIDAY,
                            hilfen = hilfen,
                            t = t,
                        )
                    }
                    key == "belege" -> Column(Modifier.padding(top = 24.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
                        Text(
                            "Belege".uppercase(Locale.GERMAN),
                            style = MaterialTheme.typography.labelSmall,
                            color = t.accent,
                            modifier = Modifier
                                .padding(top = 20.dp, bottom = 4.dp)
                                .semantics { heading() },
                        )
                    }
                    key.startsWith("beleg-") -> {
                        val n = key.removePrefix("beleg-").toInt()
                        BelegEintrag(
                            nr = n,
                            beleg = belege[n - 1],
                            offen = n in offen,
                            onUmschalten = { if (n in offen) offen -= n else offen += n },
                            t = t,
                        )
                    }
                    else -> Text(
                        TRANSLATION_NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.stone,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}
