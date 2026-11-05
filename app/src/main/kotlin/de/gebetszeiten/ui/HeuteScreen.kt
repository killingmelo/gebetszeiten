package de.gebetszeiten.ui

import androidx.annotation.StringRes
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.DayRow
import de.gebetszeiten.prayer.HeroState
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.KarahaTimes
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.NaflTimes
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.prayer.dataCreditRes
import de.gebetszeiten.prayer.dayRows
import de.gebetszeiten.prayer.durationLabel
import de.gebetszeiten.prayer.heroProgress
import de.gebetszeiten.prayer.heroState
import de.gebetszeiten.prayer.hijriText
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.prayer.noTimesNotice
import de.gebetszeiten.prayer.offsetMark
import de.gebetszeiten.prayer.offsetSummary
import de.gebetszeiten.prayer.timeRange
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens
import de.gebetszeiten.ui.theme.currentRow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Innenabstand der Gebetskarte links und rechts. */
private val CARD_PADDING = 18.dp

/** Seitenrand für Text und Liste. */
private val GUTTER = 24.dp

/** Das Foto sitzt etwas breiter als der Text (wie im Entwurf `fotos-oura.html`). */
private const val PHOTO_INSET_DP = 16
private val PHOTO_INSET = PHOTO_INSET_DP.dp

/**
 * Die Fotos tragen keinen Inhalt, nur Stimmung: heute ist das Bild genau so
 * hoch wie der Text darauf (plus ein schmaler freier Streifen oben), an
 * anderen Tagen ein flaches Band. So bleibt der Platz bei den Zeiten.
 */
private val HERO_PHOTO_TOP = 28.dp
private val OTHER_DAY_PHOTO_HEIGHT = 96.dp

/** Deckkraft des Grunds hinter dem Text im Bild; danach richtet sich die Kontrastprüfung. */
private const val SCRIM_ALPHA = 0.86

/**
 * Der gewählte Tag nach einem Tageswechsel: war „heute“ gewählt, rückt er auf
 * den neuen Tag mit; ein anderer Tag, den der Nutzer gerade ansieht, bleibt.
 */
internal fun rolledSelectedDate(selected: LocalDate, previousToday: LocalDate, today: LocalDate): LocalDate =
    if (selected == previousToday) today else selected

/** Tage, um die ein waagrechtes Wischen weiterblättert: nach links (dx < 0)
 *  ist morgen, nach rechts gestern; unterhalb der Schwelle bleibt der Tag. */
internal fun swipeDayDelta(totalDx: Float, thresholdPx: Float): Long = when {
    totalDx <= -thresholdPx -> 1L
    totalDx >= thresholdPx -> -1L
    else -> 0L
}

/** Ab dieser Strecke wechselt das Wischen den Tag. */
private val SWIPE_DAY_THRESHOLD = 72.dp
/** Wie stark der Inhalt dem Finger folgt (nur Andeutung, kein Blättern). */
private const val SWIPE_FOLLOW = 0.35f

/** TalkBack-Zusatz einer Gebetszeile. Šurūq ist kein Gebet und heißt „jetzt“. */
@StringRes
internal fun prayerStatusRes(prayer: Prayer, current: Boolean, faded: Boolean): Int? = when {
    current && prayer == Prayer.SUNRISE -> R.string.status_now
    current -> R.string.status_current
    faded -> R.string.status_past
    else -> null
}

private data class DayInfo(
    val times: DailyPrayerTimes,
    val karaha: KarahaTimes,
    val nafl: NaflTimes,
    // Null, wenn der Folgetag keine amtlichen Zeiten hat (z. B. letzter
    // abgedeckter Tag): HEUTE bleibt vollständig, nur was den Faǧr von morgen
    // braucht (Tahaǧǧud, die Restzeit nach ʿIšāʾ, die Folgetag-Zeile) entfällt.
    val nextFajr: ZonedDateTime?,
    // ʿIšāʾ von gestern, nur für den Balken zwischen Mitternacht und Faǧr.
    val previousIsha: ZonedDateTime?,
)

/**
 * Reiter „Heute“: Datumszeile, Foto des laufenden Abschnitts, groß der
 * Abschnitt mit „noch …“ und „dann … um …“, darunter der ganze Tag als ruhige
 * Liste mit Haarlinien. Titel und Liste kommen ausschließlich aus [heroState]
 * und [dayRows]; diese Datei rechnet keine Zeiten. Antippen einer Nebenzeile
 * ruft [onExplain] mit ihrem Abschnitt (führt zu „Zeiten verstehen“).
 */
@Composable
internal fun HeuteScreen(
    inner: PaddingValues,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onFetchNow: () -> Unit,
    /** Läuft gerade ein Abruf von Hand ([PrayerViewModel.refreshing])? */
    refreshing: Boolean,
    /** Zählt abgeschlossene Abrufe ([PrayerViewModel.officialRefreshes]);
     *  danach liest die Ansicht die Zeiten sofort neu, nicht erst beim Minutentakt. */
    refreshTick: Int,
    onEnableCalculation: () -> Unit,
    onNotificationsGranted: () -> Unit,
    onExplain: (Section) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val t = LocalTokens.current

    var tick by remember { mutableIntStateOf(0) }
    val tickLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // Nur im Vordergrund ticken: ohne diese Schranke lief die Schleife im
        // zwischengespeicherten Hintergrundprozess weiter.
        tickLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tick++ // sofort auffrischen, wenn die App wieder sichtbar wird
            while (true) {
                // Auf die Minutengrenze ausgerichtet, damit „noch …“ mit der
                // Uhr der Statusleiste umspringt und nicht bis zu einer Minute
                // hinterherläuft.
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                tick++
            }
        }
    }

    var selectedDate by remember { mutableStateOf(LocalDate.now(zone)) }
    // Bleibt die App über Mitternacht offen, rückt „heute“ beim nächsten Tick mit.
    var lastToday by remember { mutableStateOf(LocalDate.now(zone)) }
    LaunchedEffect(tick) {
        val today = LocalDate.now(zone)
        if (today != lastToday) {
            selectedDate = rolledSelectedDate(selectedDate, lastToday, today)
            lastToday = today
        }
    }
    val isToday = selectedDate == LocalDate.now(zone)

    // Erst nach dem ersten Laden entscheidet sich, ob es Zeiten gibt; vorher
    // stand kurz „Keine amtlichen Zeiten“ da, obwohl sie nur noch geladen wurden.
    var dayLoaded by remember { mutableStateOf(false) }
    val dayInfo by produceState<DayInfo?>(null, settings, tick, selectedDate, refreshTick) {
        // Keine Zeiten unter den aktuellen Einstellungen: `dayInfo` bleibt
        // null und die Ansicht zeigt NoTimesCard, statt Fenster aus erfundenen
        // Zeiten zu berechnen. Fehlt nur `nextFajr`, kostet das allein das,
        // was den Folgetag braucht, nicht die ganze Ansicht.
        val times = PrayerProvider.daily(context, settings, selectedDate, zone)
        val nextFajr = PrayerProvider.daily(context, settings, selectedDate.plusDays(1), zone)?.fajr
        val info = times?.let {
            DayInfo(it, IslamicWindows.karaha(it), IslamicWindows.nafl(it, nextFajr), nextFajr, previousIsha = null)
        }
        value = info
        dayLoaded = true
        // ʿIšāʾ von gestern nur für den Balken: kommt nach, hält die Liste nicht auf.
        if (info != null) {
            PrayerProvider.daily(context, settings, selectedDate.minusDays(1), zone)?.isha?.let {
                value = info.copy(previousIsha = it)
            }
        }
    }
    // Bis die Quelle feststeht, keine Quellenzeile: sonst stand beim ersten
    // Start kurz „Berechnet“ unter amtlichen Zeiten.
    var sourceKnown by remember { mutableStateOf(false) }
    val officialName by produceState<String?>(null, settings, selectedDate, tick, refreshTick) {
        // Reihenfolge spiegelt resolveLocationIdChain (Bundle vor Index), die
        // Kette, die entscheidet, WELCHER Diyanet-Standort geholt wird. Das
        // Bundle schreibt den Ort richtig („Nürnberg“ statt „NURNBERG“).
        // Ob dieser Tag amtlich versorgt ist, entscheidet sich nur am
        // Bundle-/Cache-Treffer, nicht an der Einstellung des Notausgangs.
        value = de.gebetszeiten.official.BundledOfficialSource
            .locationNameFor(context, settings.latitude, settings.longitude, selectedDate)
            ?: officialCacheName(context, settings, selectedDate)
        sourceKnown = true
    }

    // stringResource statt context.getString: Lint verbietet
    // Ressourcenzugriffe über LocalContext.current in Compose.
    val snackbarTemplate = stringResource(R.string.snackbar_official_loaded)
    // Nur beim WECHSEL melden, nicht bei jedem Start. lastReported startet
    // null, deshalb löst der erste beobachtete Wert keine Meldung aus.
    var lastReported by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(officialName) {
        val name = officialName
        if (name != null && lastReported != null && name != lastReported) {
            snackbarHostState.showSnackbar(snackbarTemplate.format(name))
        }
        lastReported = name
    }

    // `tick` als Schlüssel: Titel, Restzeit und Liste rücken jede Minute weiter,
    // auch wenn sich `dayInfo` dabei nicht ändert.
    val now = remember(tick, selectedDate) { ZonedDateTime.now(zone) }

    // Wischen nach links/rechts blättert wie die Pfeile; der Inhalt folgt dem
    // Finger nur angedeutet und federt zurück. Senkrechtes Scrollen bleibt,
    // weil die waagrechte Erkennung erst nach der Berührungsschwelle greift.
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_DAY_THRESHOLD.toPx() }
    val dragX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val delta = swipeDayDelta(total, swipeThresholdPx)
                        if (delta != 0L) selectedDate = selectedDate.plusDays(delta)
                        swipeScope.launch { dragX.animateTo(0f) }
                    },
                    onDragCancel = { swipeScope.launch { dragX.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    total += dx
                    swipeScope.launch { dragX.snapTo(total * SWIPE_FOLLOW) }
                }
            }
            .graphicsLayer { translationX = dragX.value }
            .verticalScroll(rememberScrollState()),
    ) {
        // `tick` als Anlass zum Nachsehen: kommt der Nutzer aus den
        // Systemeinstellungen zurück, ist der Hinweis beim nächsten Tick weg.
        NotificationBlockCard(tick, onNotificationsGranted, t)

        DateLine(
            date = selectedDate,
            isToday = isToday,
            hijriOffsetDays = settings.hijriOffsetDays,
            onPrev = { selectedDate = selectedDate.minusDays(1) },
            onNext = { selectedDate = selectedDate.plusDays(1) },
            onToday = { selectedDate = LocalDate.now(zone) },
            t = t,
        )

        val info = dayInfo
        if (!dayLoaded) {
            // Lädt noch: lieber kurz leer als ein falscher Hinweis.
        } else if (info != null) {
            val hero = heroState(info.times, info.karaha, info.nafl, info.nextFajr, now, isToday, info.previousIsha)
            val rows = dayRows(
                today = info.times,
                karaha = info.karaha,
                nafl = info.nafl,
                nextFajr = info.nextFajr,
                now = now,
                isToday = isToday,
                showKaraha = settings.showKaraha,
                showCemaat = settings.showCemaat,
                cemaatOffsetMinutes = settings.cemaatOffsetMinutes,
            )
            Spacer(Modifier.height(8.dp))
            if (isToday) {
                // „noch …“ steht hier immer; die Restzeit-Einstellung gilt nur
                // für Widget, Sperrbildschirm und Statusleiste.
                PhotoHero(
                    hero = hero,
                    now = now,
                    showKaraha = settings.showKaraha,
                    t = t,
                )
            } else {
                // An anderen Tagen kein Titel und keine Restzeit: nur Foto und Liste.
                Photo(hero)
            }
            Spacer(Modifier.height(16.dp))
            DayList(rows, onExplain, t, settings.timeOffsets)
            if (sourceKnown) Text(
                // Amtlich nennt die Stadt („… · Nürnberg“); der berechnete
                // String hat keinen Platzhalter, das Argument wird ignoriert.
                // Hat der Nutzer verschoben, steht das gleich darunter: nie still.
                text = listOfNotNull(
                    stringResource(dataCreditRes(officialName != null), officialName ?: settings.city),
                    offsetSummaryText(settings.timeOffsets),
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = t.stone,
                modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 24.dp),
            )
        } else {
            NoTimesCard(
                notice = noTimesNotice(
                    city = settings.city,
                    // Dieselbe Abfrage wie `canFetch` im Einstellungsblatt;
                    // im Offline-Flavor ist sie bereits invariant false.
                    onlineEnabled = settings.canFetchOfficial(),
                ),
                onFetchNow = onFetchNow,
                refreshing = refreshing,
                onEnableCalculation = onEnableCalculation,
                t = t,
            )
        }
    }
}

/** Name des Standorts, dessen gecachte amtliche Zeiten gerade greifen, oder
 *  null ohne Cache-Treffer. Der Name kommt aus dem Diyanet-Index (echter
 *  Standort, z. B. „Sakarya“ für Serdivan) und fällt auf den Ortsnamen der
 *  Einstellungen zurück. */
private suspend fun officialCacheName(
    context: android.content.Context,
    settings: AppSettings,
    date: LocalDate,
): String? {
    if (!settings.useOnline) return null
    de.gebetszeiten.official.OfficialTimesCache(context)
        .get(date, settings.latitude, settings.longitude) ?: return null
    val place = de.gebetszeiten.official.DiyanetPlaceIndex
        .nearest(context, settings.latitude, settings.longitude)
    return place?.displayName() ?: settings.city
}

/** Haarlinie an der Unterkante, ab [inset] (Nebenzeilen beginnen eingerückt).
 *  Auch Monat, Einstellungen und Ersteinrichtung ziehen ihre Linien hiermit. */
internal fun Modifier.hairlineBelow(color: Color, inset: Dp = 0.dp): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val y = size.height - stroke / 2
    drawLine(color, Offset(inset.toPx(), y), Offset(size.width, y), stroke)
}

/** Datumszeile in Großbuchstaben mit Hijri-Datum, links und rechts der Tageswechsel. */
@Composable
private fun DateLine(
    date: LocalDate,
    isToday: Boolean,
    hijriOffsetDays: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    t: Tokens,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.date_prev_day),
                tint = t.stone,
            )
        }
        // Tipp auf die Mitte springt zurück auf heute, nur wenn ein anderer
        // Tag gewählt ist, und mit Ansage für TalkBack.
        val todayLabel = stringResource(R.string.date_jump_today)
        val pattern = if (date.year == LocalDate.now().year) "EEEE, d. MMMM" else "EEEE, d. MMMM yyyy"
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isToday, role = Role.Button, onClickLabel = todayLabel, onClick = onToday)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern(pattern, Locale.GERMAN)).uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelSmall,
                // Kleine Köpfe in Akzentfarbe (Weiß und Akzent, 05.11.2026).
                color = t.accent,
            )
            Text(
                text = hijriText(date, hijriOffsetDays),
                style = MaterialTheme.typography.bodyMedium,
                color = t.stone,
            )
            if (!isToday) {
                Text(
                    text = todayLabel.uppercase(Locale.GERMAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.date_next_day),
                tint = t.stone,
            )
        }
    }
}

/** Foto des Abschnitts an anderen Tagen: ein flaches Band, Radius 24 dp. */
@Composable
private fun Photo(hero: HeroState) {
    val key = hero.photo
    Image(
        painter = painterResource(photoRes(key)),
        contentDescription = stringResource(photoDescriptionRes(key)),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(horizontal = PHOTO_INSET)
            .fillMaxWidth()
            .height(OTHER_DAY_PHOTO_HEIGHT)
            .clip(RoundedCornerShape(24.dp)),
    )
}

/**
 * Heute: das Foto des Abschnitts als Grund, unten darauf Titel, „noch …“ und
 * „dann …“. Ein Verlauf in der Grundfarbe trägt den Text; die Restzeit nimmt
 * ihre Farbe aus dem Bild ([heroTint]) und ist gegen genau diesen Verlauf auf
 * 4,5:1 geprüft.
 */
@Composable
private fun PhotoHero(hero: HeroState, now: ZonedDateTime, showKaraha: Boolean, t: Tokens) {
    val key = hero.photo
    val res = photoRes(key)
    val tint = rememberHeroTint(res, t)
    Box(
        modifier = Modifier
            .padding(horizontal = PHOTO_INSET)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = stringResource(photoDescriptionRes(key)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        // Das Bild ist kaum höher als der Text: der Verlauf
                        // beginnt schon leicht und deckt ab gut einem Drittel.
                        0f to t.canvas.copy(alpha = 0.15f),
                        0.4f to t.canvas.copy(alpha = SCRIM_ALPHA.toFloat()),
                        1f to t.canvas.copy(alpha = SCRIM_ALPHA.toFloat()),
                    ),
                ),
        )
        Hero(
            hero = hero,
            now = now,
            showKaraha = showKaraha,
            t = t,
            remainingColor = tint,
            // Der Text bestimmt die Höhe des Kastens; das Bild füllt ihn nur.
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = HERO_PHOTO_TOP, bottom = 18.dp),
        )
    }
}

/** Bildpunkte je Foto, einmal verkleinert gelesen; die Fotos sind fest im APK. */
private val heroPixelCache = ConcurrentHashMap<Int, IntArray>()

/** Farbe der Restzeit für das Foto [res]; bis sie berechnet ist, Tinte. */
@Composable
private fun rememberHeroTint(res: Int, t: Tokens): Color {
    val context = LocalContext.current
    val canvas = t.canvas.toArgb()
    val dark = t.canvas.luminance() < 0.5f
    val tint by produceState(t.ink, res, dark) {
        value = withContext(Dispatchers.Default) {
            val px = heroPixelCache.getOrPut(res) { samplePixels(context, res) }
            if (px.isEmpty()) {
                null
            } else {
                Color(heroTint(px, dark, background = blend(canvas, averageColor(px), SCRIM_ALPHA)))
            }
        } ?: t.ink
    }
    return tint
}

private fun samplePixels(context: android.content.Context, res: Int): IntArray {
    val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
    val bmp = BitmapFactory.decodeResource(context.resources, res, opts) ?: return IntArray(0)
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    bmp.recycle()
    return px
}

@Composable
private fun Hero(
    hero: HeroState,
    now: ZonedDateTime,
    showKaraha: Boolean,
    t: Tokens,
    remainingColor: Color = t.ink,
    modifier: Modifier = Modifier.padding(horizontal = GUTTER),
) {
    Column(modifier) {
        // Kein „JETZT“ im Kopf: der große Name und der Balken sagen es schon
        // (Entscheidung 05.11.2026); die Liste zeigt das laufende Gebet in Grün.
        Text(
            text = dmgTitle(stringResource(momentLabelRes(hero.moment))),
            style = MaterialTheme.typography.displayLarge,
            color = t.ink,
            modifier = Modifier.semantics { heading() },
        )
        val remaining = hero.remaining
        if (remaining != null) {
            val lead = stringResource(R.string.hero_remaining_lead)
            Text(
                text = buildAnnotatedString {
                    append("$lead ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                        // Abgerundet wie KarahaCountdown, damit beide Zeilen dieselbe Minute zeigen.
                        append(durationLabel(remaining.toMinutes().coerceAtLeast(0)))
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                color = remainingColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        hero.next?.let { next ->
            val lead = stringResource(R.string.hero_next_lead)
            val at = stringResource(R.string.hero_next_at, stringResource(next.prayer.labelRes()), next.at.format(HM))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = t.stone)) { append("$lead ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(at) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = t.ink,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        heroProgress(hero.since, hero.until, now)?.let { part ->
            SectionProgress(part, hero.since!!.format(HM), hero.until!!.format(HM), t)
        }
        val makruh = hero.makruh
        if (showKaraha && makruh != null) {
            val head = if (makruh.state.active) {
                stringResource(R.string.hero_makruh_until, makruh.end.format(HM))
            } else {
                stringResource(R.string.hero_makruh_from, makruh.start.format(HM))
            }
            // „noch …“ bzw. „in …“ kommt fertig aus KarahaCountdown.
            Text(
                text = "$head, ${makruh.state.text}",
                style = MaterialTheme.typography.bodyLarge,
                color = t.gold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** „Von dir verschoben: Ẓuhr +5 Min“, oder null ohne Verschiebung. */
@Composable
internal fun offsetSummaryText(offsets: de.gebetszeiten.core.prayertimes.TimeOffsets): String? {
    if (offsets.isZero) return null
    val names = de.gebetszeiten.core.prayertimes.Prayer.entries.associateWith { p ->
        if (p == Prayer.SUNRISE) stringResource(R.string.moment_shuruq) else stringResource(p.labelRes())
    }
    return offsetSummary(offsets, { names.getValue(it) }, stringResource(R.string.offset_summary))
}

/** Wie weit der laufende Abschnitt ist, mit Beginn und Ende darunter. */
@Composable
private fun SectionProgress(part: Float, from: String, to: String, t: Tokens) {
    val percent = (part * 100).toInt()
    val description = stringResource(R.string.desc_progress, percent, from, to)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(t.hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(part)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(t.accent),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(from, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR), color = t.stone)
            Spacer(Modifier.weight(1f))
            Text(to, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR), color = t.stone)
        }
    }
}

/** Der ganze Tag als Karte: fünf Gebete, Nebenzeiten klein darunter. */
@Composable
private fun DayList(
    rows: List<DayRow>,
    onExplain: (Section) -> Unit,
    t: Tokens,
    offsets: de.gebetszeiten.core.prayertimes.TimeOffsets = de.gebetszeiten.core.prayertimes.TimeOffsets.NONE,
) {
    val groups = groupUnderPrayers(rows)
    // Weiße Fläche (nachts #19191c) mit Haarlinie, wie die Karten in DESIGN.md;
    // die letzte Zeile braucht keine Linie, die Karte schließt sie ab.
    Column(
        Modifier
            .padding(horizontal = PHOTO_INSET)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(t.surface)
            .border(1.dp, t.hairline, RoundedCornerShape(24.dp)),
    ) {
        groups.forEachIndexed { i, group ->
            // Keine Linie direkt am getönten Grund: weder unter ihm noch über ihm.
            val nextIsCurrent = groups.getOrNull(i + 1)?.prayer?.current == true
            val line = i != groups.lastIndex && !group.prayer.current && !nextIsCurrent
            PrayerGroupLine(group, onExplain, t, line = line, offsets = offsets)
        }
    }
}

/**
 * Ein Gebet mit den Nebenzeiten, die zu ihm gehören (klein darunter). Šurūq ist
 * kein Gebet und steht seit dem 05.11.2026 als „bis Šurūq …“ unter Faǧr.
 */
internal data class PrayerGroup(
    val prayer: DayRow.PrayerRow,
    val subs: List<DayRow.SideRow>,
    val shuruq: DayRow.PrayerRow? = null,
)

/**
 * Die Nebenzeiten stehen nicht mehr als eigene Zeilen, sondern klein unter
 * ihrem Gebet (Wunsch vom 04.11.2026: wichtig, aber weniger als die
 * Gebetszeiten; so bleiben alle Gebete ohne Scrollen sichtbar). Jede gehört
 * zum Gebet davor (Makrūh beim Aufgang und Ḍuḥā zu Šurūq, Vergilben zu ʿAṣr,
 * Awwābīn zu Maġrib, Taḥaǧǧud zu ʿIšāʾ); die Zawāl-Zeit kommt unmittelbar
 * vor Ẓuhr und gehört deshalb zu ihm. Ohne Gebet davor: zum nächsten.
 */
internal fun groupUnderPrayers(rows: List<DayRow>): List<PrayerGroup> {
    val all = rows.filterIsInstance<DayRow.PrayerRow>()
    val shuruq = all.firstOrNull { it.prayer == Prayer.SUNRISE }
    val prayers = all.filter { it.prayer != Prayer.SUNRISE }
    val subs = prayers.associateWith { mutableListOf<DayRow.SideRow>() }
    rows.forEachIndexed { i, row ->
        if (row !is DayRow.SideRow) return@forEachIndexed
        val before = rows.subList(0, i).filterIsInstance<DayRow.PrayerRow>().lastOrNull { it in subs }
        val after = rows.subList(i + 1, rows.size).filterIsInstance<DayRow.PrayerRow>().firstOrNull { it in subs }
        val owner = if (row.kind == Section.ZUHR) after ?: before else before ?: after
        owner?.let { subs.getValue(it) += row }
    }
    return prayers.map { p ->
        PrayerGroup(p, subs.getValue(p), shuruq = shuruq.takeIf { p.prayer == Prayer.FAJR && !p.nextDay })
    }
}

/** Wie die Zeit einer Nebenzeile kurz geschrieben wird. */
internal enum class SubForm { AT, UNTIL, FROM, SPAN }

/**
 * Beginnt sie mit dem Gebet, reicht „bis“ („Makrūh bis 07:46“); reicht sie
 * bis zum nächsten Gebet, „ab“ („Makrūh ab 16:17“, „Taḥaǧǧud ab 02:05“);
 * sonst beide Enden („Ḍuḥā 07:46 bis 11:44“). Ohne Ende nur die Uhrzeit.
 */
internal fun subFormOf(side: DayRow.SideRow, prayerAt: java.time.ZonedDateTime): SubForm = when {
    side.end == null -> SubForm.AT
    side.start == prayerAt -> SubForm.UNTIL
    side.kind == Section.ISFIRAR_MAKRUH || side.kind == Section.TAHAJJUD -> SubForm.FROM
    else -> SubForm.SPAN
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerGroupLine(
    group: PrayerGroup,
    onExplain: (Section) -> Unit,
    t: Tokens,
    line: Boolean = true,
    offsets: de.gebetszeiten.core.prayertimes.TimeOffsets = de.gebetszeiten.core.prayertimes.TimeOffsets.NONE,
) {
    val shuruq = group.shuruq
    val hasSubs = group.subs.isNotEmpty()
    val hairline = t.hairline
    Column(
        Modifier
            .fillMaxWidth()
            // Das laufende Gebet: Grund leicht ins Grün gezogen, über die ganze Kartenbreite.
            .let { if (group.prayer.current) it.background(t.currentRow) else it }
            .drawBehind {
                if (line) {
                    val stroke = 1.dp.toPx()
                    val inset = CARD_PADDING.toPx()
                    val y = size.height - stroke / 2
                    drawLine(hairline, Offset(inset, y), Offset(size.width - inset, y), stroke)
                }
            }
            .padding(horizontal = CARD_PADDING, vertical = 2.dp),
    ) {
        // Mit Nebenzeiten flacher: Gebet und kleine Zeile zusammen etwa so
        // hoch wie früher Gebet plus eine Nebenzeile, sonst passte ʿIšāʾ nicht mehr.
        // Faǧr endet nicht mit dem nächsten Gebet, sondern bei Šurūq: das Ende
        // steht deshalb gleich in der Zeile („05:21 bis 07:03“), nicht als Nebenzeit.
        PrayerLine(
            group.prayer, onExplain, t,
            minHeight = if (hasSubs) 36.dp else 48.dp,
            end = shuruq?.at,
            mark = offsetMark(offsets.of(group.prayer.prayer)),
        )
        if (hasSubs) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Unter Faǧr liest sich die Makrūh-Zeit ab Šurūq als „Makrūh bis …“,
                // also zählt Šurūq als ihr Anfang; Ǧamāʿa davor bleibt am Faǧr.
                group.subs.forEach { side ->
                    val anchor = if (shuruq != null && !side.start.isBefore(shuruq.at)) shuruq.at else group.prayer.at
                    SubItem(side, anchor, onExplain, t)
                }
            }
        }
    }
}

/** Eine Nebenzeit, klein; Makrūh in Gold, Vergangenes verblasst. Antippbar wie früher die Nebenzeile. */
@Composable
private fun SubItem(row: DayRow.SideRow, prayerAt: java.time.ZonedDateTime, onExplain: (Section) -> Unit, t: Tokens) {
    val label = if (isMakruh(row.kind)) stringResource(R.string.sub_makruh) else stringResource(sideLabelRes(row.kind))
    val start = row.start.format(HM)
    val end = row.end?.format(HM)
    val text = when (subFormOf(row, prayerAt)) {
        SubForm.AT -> stringResource(R.string.sub_at, label, start)
        SubForm.UNTIL -> stringResource(R.string.sub_until, label, end ?: start)
        SubForm.FROM -> stringResource(R.string.sub_from, label, start)
        SubForm.SPAN -> stringResource(R.string.sub_at, label, timeRange(start, end ?: start))
    }
    val color = when {
        row.past -> t.stone
        isMakruh(row.kind) -> t.gold
        else -> t.stone
    }
    // TalkBack liest den vollen Namen und die ganze Spanne, nicht die Kurzform.
    val fullLabel = stringResource(sideLabelRes(row.kind))
    val time = end?.let { timeRange(start, it) } ?: start
    val derived = if (row.kind == Section.JAMAA) stringResource(R.string.status_jamaa_derived) else ""
    val status = derived + if (row.past) stringResource(R.string.status_past) else ""
    val description = stringResource(R.string.desc_prayer, fullLabel, time, status)
    val explainLabel = stringResource(R.string.show_explanation)
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .clickable(role = Role.Button, onClickLabel = explainLabel) { onExplain(row.kind) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = explainLabel) {
                    onExplain(row.kind)
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
            color = color,
        )
        Icon(
            painterResource(R.drawable.ic_help_small),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(start = 4.dp).size(12.dp),
        )
    }
}
/** Der Abschnitt in „Zeiten verstehen“, zu dem eine Gebetszeile führt. */
internal fun explainSectionOf(prayer: Prayer): Section = when (prayer) {
    Prayer.FAJR -> Section.FAJR
    // Šurūq ist kein Gebet; der Abschnitt dazu ist die Makrūh-Zeit beim Aufgang.
    Prayer.SUNRISE -> Section.SHURUQ_MAKRUH
    Prayer.DHUHR -> Section.ZUHR
    Prayer.ASR -> Section.ASR
    Prayer.MAGHRIB -> Section.MAGHRIB
    Prayer.ISHA -> Section.ISHA
}

private val TABULAR = "tnum"

@Composable
private fun PrayerLine(
    row: DayRow.PrayerRow,
    onExplain: (Section) -> Unit,
    t: Tokens,
    minHeight: Dp = 48.dp,
    end: ZonedDateTime? = null,
    /** „+5“ / „−3“, wenn der Nutzer diese Zeit verschoben hat. */
    mark: String? = null,
) {
    // `past` gilt auch für die laufende Zeile; verblasst wird nur, was vorbei ist.
    val faded = row.past && !row.current
    // Das laufende Gebet in Waldgrün und fett, ohne Marke (Entscheidung 05.11.2026).
    val color = when {
        row.current -> t.accent
        faded -> t.stone
        else -> t.ink
    }
    val weight = if (row.current) FontWeight.SemiBold else FontWeight.Normal
    val name = if (row.prayer == Prayer.SUNRISE) {
        stringResource(R.string.moment_shuruq)
    } else {
        stringResource(row.prayer.labelRes())
    }
    val time = end?.let { timeRange(row.at.format(HM), it.format(HM)) } ?: row.at.format(HM)
    val nextDay = stringResource(R.string.row_next_day)
    val status = prayerStatusRes(row.prayer, row.current, faded)?.let { stringResource(it) } ?: ""
    val description = if (row.nextDay) {
        stringResource(R.string.desc_prayer_next_day, name, time)
    } else {
        stringResource(R.string.desc_prayer, name, time, status)
    } + (mark?.let { stringResource(R.string.desc_offset, it) } ?: "")
    val explainLabel = stringResource(R.string.show_explanation)
    val section = explainSectionOf(row.prayer)
    val rowStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            // Die Haarlinie zieht PrayerGroupLine unter Gebet und Nebenzeiten zusammen.
            // Antippen führt zum Eintrag in „Zeiten verstehen“, wie früher die Nebenzeilen.
            .clickable(role = Role.Button, onClickLabel = explainLabel) { onExplain(section) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = explainLabel) {
                    onExplain(section)
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = rowStyle, fontWeight = weight, color = color)
        // Zeigt, dass die Zeile eine Erklärung hat; Zierde, TalkBack nennt die Aktion oben.
        Icon(
            painterResource(R.drawable.ic_help_small),
            contentDescription = null,
            tint = t.stone,
            modifier = Modifier.padding(start = 6.dp).size(14.dp),
        )

        if (row.nextDay) {
            Spacer(Modifier.width(8.dp))
            Text(nextDay, style = MaterialTheme.typography.bodyMedium, color = t.stone)
        }
        Spacer(Modifier.weight(1f))
        Text(
            time,
            style = rowStyle.copy(fontFeatureSettings = TABULAR),
            fontWeight = weight,
            color = color,
            modifier = Modifier.padding(start = 12.dp),
        )
        mark?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                color = t.gold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@StringRes
internal fun sideLabelRes(kind: Section): Int = when (kind) {
    Section.JAMAA -> R.string.side_jamaa
    Section.SHURUQ_MAKRUH -> R.string.side_makruh_sunrise
    Section.DUHA -> R.string.nafl_duha
    Section.ZUHR -> R.string.side_makruh_zawal
    Section.ISFIRAR_MAKRUH -> R.string.side_makruh_isfirar
    Section.AWWABIN -> R.string.nafl_awwabin
    Section.TAHAJJUD -> R.string.nafl_tahajjud
    // Pflichtgebete erscheinen als PrayerRow, nie als Nebenzeile.
    Section.FAJR -> R.string.prayer_fajr
    Section.ASR -> R.string.prayer_asr
    Section.MAGHRIB -> R.string.prayer_maghrib
    Section.ISHA -> R.string.prayer_isha
}

internal fun isMakruh(kind: Section) =
    kind == Section.SHURUQ_MAKRUH || kind == Section.ZUHR || kind == Section.ISFIRAR_MAKRUH

/**
 * Der Hinweis, der sagt, WARUM gerade nichts ankommt (Erlaubnis fehlt oder
 * Kanal im System abgeschaltet). Ohne ihn blieb die App bei abgelehnter
 * Erlaubnis stumm. [tick] ist kein Inhalt, sondern der Anlass zum Nachsehen.
 */
@Composable
private fun NotificationBlockCard(tick: Int, onGranted: () -> Unit, t: Tokens) {
    val context = LocalContext.current
    val block = remember(tick) { de.gebetszeiten.notify.PrayerNotifier.blockOf(context) }
    val text = de.gebetszeiten.notify.notificationBlockText(block) ?: return
    val action = de.gebetszeiten.notify.notificationBlockAction(block) ?: return
    // Nach einer Zusage sofort planen, statt auf das nächste Gebet zu warten.
    val erlauben = rememberNotificationPermissionRequest(onGranted)
    NoticeBlock(t) {
        Text(
            stringResource(R.string.notification_block_title),
            style = MaterialTheme.typography.titleMedium,
            color = t.ink,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = t.stone)
        Button(
            onClick = {
                if (block == de.gebetszeiten.notify.NotificationBlock.NO_PERMISSION) {
                    erlauben()
                } else {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(action) }
    }
}

/**
 * Steht statt Foto, Titel und Liste, solange [PrayerProvider.daily] `null`
 * liefert (keine amtlichen Zeiten, Notausgang aus). Der Wortlaut kommt
 * vollständig aus [noTimesNotice]; „Jetzt abrufen“ nur, wenn ein Abruf
 * überhaupt möglich ist.
 */
@Composable
private fun NoTimesCard(
    notice: de.gebetszeiten.prayer.NoTimesNotice,
    onFetchNow: () -> Unit,
    refreshing: Boolean,
    onEnableCalculation: () -> Unit,
    t: Tokens,
) {
    NoticeBlock(t) {
        Text(notice.headline, style = MaterialTheme.typography.titleMedium, color = t.ink)
        Text(notice.detail, style = MaterialTheme.typography.bodyMedium, color = t.stone)
        if (notice.showFetch) {
            // Während des Abrufs arbeitet der Knopf sichtbar und nimmt keinen
            // zweiten Tipp an; das Ergebnis meldet danach die Snackbar.
            Button(onClick = onFetchNow, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.fetch_running))
                } else {
                    Text(stringResource(R.string.no_times_fetch_now))
                }
            }
        }
        OutlinedButton(onClick = onEnableCalculation, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.no_times_enable_calculation))
        }
    }
}

/** Hinweis im Oura-Stil: keine Karte, nur eine Haarlinie darunter. */
@Composable
private fun NoticeBlock(t: Tokens, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GUTTER)
            .hairlineBelow(t.hairline)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
