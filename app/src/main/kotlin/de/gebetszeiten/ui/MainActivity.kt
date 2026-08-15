package de.gebetszeiten.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.KarahaCountdown
import de.gebetszeiten.prayer.KarahaTimes
import de.gebetszeiten.prayer.NaflTimes
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.durationLabel
import de.gebetszeiten.prayer.hijriText
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.theme.GebetszeitenTheme
import de.gebetszeiten.ui.theme.LocalHighContrast
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class DayInfo(
    val times: DailyPrayerTimes,
    val karaha: KarahaTimes,
    val nafl: NaflTimes,
    val nextFajr: ZonedDateTime, // next day's Fajr (for the Isha→Fajr night phase)
)

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            GebetszeitenTheme(darkTheme = darkTheme, highContrast = settings.highContrast) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
                ) {
                    NotificationPermissionRequester()
                    MainScreen(viewModel)
                }
            }
        }
        viewModel.ensureScheduled()
    }
}

@Composable
private fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional */ }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private enum class Tab { HEUTE, MONAT, QIBLA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: PrayerViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (tab) { Tab.HEUTE -> settings.city; Tab.MONAT -> stringResource(R.string.tab_month); Tab.QIBLA -> stringResource(R.string.tab_qibla) }) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.settings_icon_desc))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.HEUTE,
                    onClick = { tab = Tab.HEUTE },
                    icon = { Icon(painterResource(R.drawable.ic_today), contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_today)) },
                )
                NavigationBarItem(
                    selected = tab == Tab.MONAT,
                    onClick = { tab = Tab.MONAT },
                    icon = { Icon(painterResource(R.drawable.ic_month), contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_month)) },
                )
                NavigationBarItem(
                    selected = tab == Tab.QIBLA,
                    onClick = { tab = Tab.QIBLA },
                    icon = { Icon(painterResource(R.drawable.ic_qibla), contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_qibla)) },
                )
            }
        },
    ) { inner ->
        when (tab) {
            Tab.HEUTE -> HeuteContent(inner, settings)
            Tab.MONAT -> MonatScreen(inner, settings)
            Tab.QIBLA -> QiblaScreen(inner, settings)
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            // Voll geöffnet: halb expandiert bleibt mit offener Tastatur
            // kaum Platz für die Ortsvorschläge.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LocationSettings(settings = settings, onApply = { viewModel.save(it) })
        }
    }
}

@Composable
private fun HeuteContent(inner: PaddingValues, settings: AppSettings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    var tick by remember { mutableIntStateOf(0) }
    val tickLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // Tick only while visible: without the lifecycle gate this loop kept
        // running in the cached background process (needless background CPU).
        tickLifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            tick++ // refresh immediately when returning to the foreground
            while (true) {
                delay(60_000)
                tick++
            }
        }
    }

    var selectedDate by remember { mutableStateOf(LocalDate.now(zone)) }
    val isToday = selectedDate == LocalDate.now(zone)

    val dayInfo by produceState<DayInfo?>(null, settings, tick, selectedDate) {
        val times = PrayerProvider.daily(context, settings, selectedDate, zone)
        val nextFajr = PrayerProvider.daily(context, settings, selectedDate.plusDays(1), zone).fajr
        value = DayInfo(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr), nextFajr)
    }
    val officialName by produceState<String?>(null, settings, selectedDate, tick) {
        value = if (settings.useCalculated) {
            null
        } else {
            // Reihenfolge spiegelt resolveLocationIdChain (Bundle vor Index) —
            // die Kette, die entscheidet, WELCHER Diyanet-Standort geholt wird.
            // NICHT PrayerProvider.daily: dort geht es um die ZEITEN, und der
            // Online-Cache enthaelt genau die Zeiten der ID aus dem Bundle.
            // Beide Quellen fuehren denselben Ort (Nuernberg = 11024 in beiden),
            // aber das Bundle schreibt ihn richtig ("Nürnberg" statt "NURNBERG").
            de.gebetszeiten.official.BundledOfficialSource
                .locationNameFor(context, settings.latitude, settings.longitude, selectedDate)
                ?: officialCacheName(context, settings, selectedDate)
        }
    }
    var karahaInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateNavigator(
            date = selectedDate,
            isToday = isToday,
            hijriOffsetDays = settings.hijriOffsetDays,
            onPrev = { selectedDate = selectedDate.minusDays(1) },
            onNext = { selectedDate = selectedDate.plusDays(1) },
            onToday = { selectedDate = LocalDate.now(zone) },
        )
        dayInfo?.let { info ->
            TimesCard(
                info = info,
                now = ZonedDateTime.now(zone),
                highlight = isToday,
                showKaraha = settings.showKaraha,
                showCemaat = settings.showCemaat,
                cemaatOffsetMinutes = settings.cemaatOffsetMinutes,
                showRemaining = settings.showCountdown,
                onKaraha = { karahaInfo = it },
            )
        }
        Text(
            // Amtlich nennt die Stadt ("… · Nürnberg"); der berechnete String hat
            // keinen Platzhalter, das Extra-Argument wird dort gefahrlos ignoriert.
            text = stringResource(de.gebetszeiten.prayer.dataCreditRes(officialName != null), officialName ?: settings.city),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    karahaInfo?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { karahaInfo = null },
            confirmButton = { TextButton(onClick = { karahaInfo = null }) { Text(stringResource(R.string.understood)) } },
            title = { Text(title) },
            text = { Text(text) },
        )
    }
}

/** Name des Standorts, dessen gecachte amtliche Zeiten gerade greifen — oder
 *  null, wenn kein Cache-Treffer vorliegt. Der Name kommt aus dem
 *  Diyanet-Index (echter Standort, z. B. „Sakarya" für Serdivan) und fällt
 *  auf den Ortsnamen der Einstellungen zurück. */
private suspend fun officialCacheName(
    context: android.content.Context,
    settings: AppSettings,
    date: java.time.LocalDate,
): String? {
    if (!settings.useOnline) return null
    // Kein Cache-Treffer = keine amtlichen Zeiten fuer diesen Tag.
    de.gebetszeiten.official.OfficialTimesCache(context)
        .get(date, settings.latitude, settings.longitude) ?: return null
    val place = de.gebetszeiten.official.DiyanetPlaceIndex
        .nearest(context, settings.latitude, settings.longitude)
    return place?.displayName() ?: settings.city
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    isToday: Boolean,
    hijriOffsetDays: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.date_prev_day),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onToday),
        ) {
            Text(
                text = if (isToday) stringResource(R.string.date_today) else date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = hijriText(date, hijriOffsetDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.date_next_day),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun remainingText(d: Duration): String {
    if (d.isNegative || d.isZero) return "jetzt"
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return when {
        h > 0 -> "in $h Std $m Min"
        m > 0 -> "in $m Min"
        else -> "in weniger als 1 Min"
    }
}

private sealed interface DayBlock {
    val sortAt: ZonedDateTime
}

/** A prayer with optional makruh chip(s) and a nafl "tip" attached to it. */
private data class PrayerBlock(
    val prayer: Prayer,
    val time: ZonedDateTime,
    val before: MakruhBlock? = null, // Zenit / İsfirar — chip above the prayer
    val after: MakruhBlock? = null,  // (reserved) chip below the prayer
    val tip: NaflBlock? = null,      // voluntary-prayer hint (e.g. Awwabin)
    val cemaat: CemaatTip? = null,   // derived congregation time (Fajr only)
) : DayBlock {
    override val sortAt: ZonedDateTime get() = time
}

/** Abgeleitete Gemeinschaftsgebetszeit (Sabah-Cemaat): Sonnenaufgang − Vorlauf.
 *  Punktzeit, kein Fenster — keine amtliche Diyanet-Angabe. */
private data class CemaatTip(
    val label: String,
    val time: ZonedDateTime,
    val explain: Pair<String, String>,
)

/** A makruh (karaha) window — rendered as a compact chip attached to its prayer. */
private data class MakruhBlock(
    val label: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val explain: Pair<String, String>,
)

private data class NaflBlock(
    val label: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val forenoon: Boolean = false,   // Duha: a primary, anchorable forenoon entry
    val before: MakruhBlock? = null, // İşrak — chip shown above the (Duha) pill
    val explain: Pair<String, String>? = null, // tap → explanation dialog
    val whenCurrent: Boolean = false, // tip: only show while its prayer is current
) : DayBlock {
    override val sortAt: ZonedDateTime get() = start
}

/** Sort tie-break at equal instant: prayer before nafl. */
private fun blockOrder(b: DayBlock): Int = when (b) {
    is PrayerBlock -> 0
    is NaflBlock -> 1
}

/** A "primary" (prayer-level) entry: an obligatory prayer or the Duha forenoon. */
private fun isPrimary(b: DayBlock): Boolean = b is PrayerBlock || (b is NaflBlock && b.forenoon)

@Composable
private fun TimesCard(
    info: DayInfo,
    now: ZonedDateTime,
    highlight: Boolean,
    showKaraha: Boolean,
    showCemaat: Boolean,
    cemaatOffsetMinutes: Int,
    showRemaining: Boolean,
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val dark = de.gebetszeiten.ui.theme.LocalIsDark.current
    val hc = LocalHighContrast.current
    // Darkened for WCAG AA contrast of small text on the light card.
    val amber = when {
        dark -> if (hc) Color(0xFFFFCF87) else Color(0xFFE6B055)
        hc -> Color(0xFF5E3600)
        else -> Color(0xFF8A5300)
    }
    val green = when {
        dark -> if (hc) Color(0xFFB7F0D8) else Color(0xFF8BD6BB)
        hc -> Color(0xFF0A3D12)
        else -> Color(0xFF1B5E20)
    }

    // Explanation pairs + block labels resolved in composable scope, then passed
    // as remember() keys so they can be used inside the non-composable buildList.
    val karahaSunrise = stringResource(R.string.karaha_sunrise_title) to stringResource(R.string.karaha_sunrise_text)
    val karahaZeval = stringResource(R.string.karaha_zeval_title) to stringResource(R.string.karaha_zeval_text)
    val karahaIsfirar = stringResource(R.string.karaha_isfirar_title) to stringResource(R.string.karaha_isfirar_text)
    val naflAwwabin = stringResource(R.string.nafl_awwabin_title) to stringResource(R.string.nafl_awwabin_text)
    val naflTahajjud = stringResource(R.string.nafl_tahajjud_title) to stringResource(R.string.nafl_tahajjud_text)
    val labelZenit = stringResource(R.string.makruh_zenit)
    val labelBeforeSunset = stringResource(R.string.makruh_before_sunset)
    val labelAfterSunrise = stringResource(R.string.makruh_after_sunrise)
    val labelAwwabin = stringResource(R.string.nafl_awwabin)
    val labelTahajjud = stringResource(R.string.nafl_tahajjud)
    val labelDuha = stringResource(R.string.nafl_duha)
    val labelCemaat = stringResource(R.string.cemaat_label)
    val cemaatExplain = stringResource(R.string.cemaat_explain_title) to stringResource(R.string.cemaat_explain_text)

    // Prayers carry their makruh chips. Sunrise is just a moment (no makruh).
    // The forenoon period is represented by Duha, which also carries the İşrak
    // makruh (so it sits right above the Duha pill, not on Sunrise).
    val blocks = remember(
        info, showKaraha, showCemaat, cemaatOffsetMinutes,
        karahaSunrise, karahaZeval, karahaIsfirar, naflAwwabin, naflTahajjud,
        labelZenit, labelBeforeSunset, labelAfterSunrise, labelAwwabin, labelTahajjud, labelDuha,
        labelCemaat, cemaatExplain,
    ) {
        val k = info.karaha
        val n = info.nafl
        buildList<DayBlock> {
            info.times.ordered().forEach { (p, t) ->
                val before = if (!showKaraha) null else when (p) {
                    Prayer.DHUHR -> MakruhBlock(labelZenit, k.zevalStart, k.zevalEnd, karahaZeval)
                    Prayer.MAGHRIB -> MakruhBlock(labelBeforeSunset, k.isfirarStart, k.isfirarEnd, karahaIsfirar)
                    else -> null
                }
                // Voluntary-prayer tips: Awwabin on Maghrib (always), Tahajjud on
                // Isha (only while Isha is the current prayer — i.e. at night).
                val tip = when (p) {
                    Prayer.MAGHRIB -> NaflBlock(labelAwwabin, n.awwabinStart, n.awwabinEnd, explain = naflAwwabin)
                    Prayer.ISHA -> NaflBlock(labelTahajjud, n.tahajjudStart, n.tahajjudEnd, explain = naflTahajjud, whenCurrent = true)
                    else -> null
                }
                val cemaat = if (showCemaat && p == Prayer.FAJR) {
                    CemaatTip(labelCemaat, info.times.sunrise.minusMinutes(cemaatOffsetMinutes.toLong()), cemaatExplain)
                } else {
                    null
                }
                add(PrayerBlock(p, t, before, null, tip, cemaat))
            }
            // Duha is the forenoon entry that replaces Sunrise once it has
            // passed; İşrak makruh attaches to it.
            val israk = if (showKaraha) MakruhBlock(labelAfterSunrise, k.sunriseStart, k.sunriseEnd, karahaSunrise) else null
            add(NaflBlock(labelDuha, n.duhaStart, n.duhaEnd, forenoon = true, before = israk))
        }.sortedWith(compareBy({ it.sortAt }, { blockOrder(it) }))
    }

    // "Running" timeline. The current obligatory prayer is the anchor — except
    // Sunrise, which is only a moment, not a prayer: in the Sunrise→Dhuhr gap
    // the anchor (and thus the top of the collapsed list) is Duha instead.
    val active: Pair<Prayer, ZonedDateTime>? =
        if (highlight) info.times.ordered().lastOrNull { !it.second.isAfter(now) } else null
    val nextEntry: Pair<Prayer, ZonedDateTime>? =
        if (highlight) info.times.ordered().firstOrNull { it.second.isAfter(now) } else null
    val activeIndex = when {
        active == null -> -1
        active.first == Prayer.SUNRISE -> blocks.indexOfFirst { it is NaflBlock && it.forenoon }
        else -> blocks.indexOfFirst { it is PrayerBlock && it.prayer == active.first }
    }
    var showPast by remember { mutableStateOf(false) }
    var showFuture by remember { mutableStateOf(false) }

    // Default window = 3 primary times: current + next + next-next. Earlier and
    // further entries are collapsed behind expanders ("scroll mode").
    val maxPrimary = 3
    val collapsedCap = run {
        if (activeIndex < 0) return@run blocks.lastIndex
        var seen = 0
        for (i in activeIndex..blocks.lastIndex) {
            if (i == activeIndex || isPrimary(blocks[i])) {
                seen++
                if (seen == maxPrimary) return@run i
            }
        }
        blocks.lastIndex
    }
    val lower = if (activeIndex > 0 && !showPast) activeIndex else 0
    val upper = if (showFuture) blocks.lastIndex else maxOf(collapsedCap, lower)
    val visible = if (blocks.isEmpty()) blocks else blocks.subList(lower, upper + 1)
    val hasEarlier = activeIndex > 0
    val hasLater = collapsedCap < blocks.lastIndex

    // Is some entry currently "running" (→ a filling pill carries the remaining
    // time)? A prayer (not Sunrise) is active, or we're inside the Duha window.
    val inDuha = highlight && active?.first == Prayer.SUNRISE &&
        !now.isBefore(info.nafl.duhaStart) && now.isBefore(info.nafl.duhaEnd)
    val anyCurrent = (active != null && active.first != Prayer.SUNRISE) || inDuha
    // After Isha there is no further prayer today → the night runs to tomorrow's Fajr.
    val afterIsha = highlight && active?.first == Prayer.ISHA && nextEntry == null
    // A makruh window that lies within the current prayer's interval is drawn as
    // a band inside its pill (and its chip on the next prayer is suppressed).
    val pillMakruh: MakruhBlock? = if (active != null && active.first != Prayer.SUNRISE && nextEntry != null) {
        (blocks.firstOrNull { it is PrayerBlock && it.prayer == nextEntry.first } as? PrayerBlock)?.before
            ?.takeIf { !it.start.isBefore(active.second) && !it.end.isAfter(nextEntry.second) }
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            if (hasEarlier) {
                ExpanderRow(
                    text = if (showPast) stringResource(R.string.times_hide_earlier) else stringResource(R.string.times_show_earlier),
                    onClick = { showPast = !showPast },
                )
            }
            Timeline(
                visible = visible,
                active = active,
                nextEntry = nextEntry,
                now = now,
                highlight = highlight,
                showPast = showPast,
                amber = amber,
                green = green,
                nextFajr = info.nextFajr,
                showNextCountdown = !anyCurrent && showRemaining,
                showRemaining = showRemaining,
                pillMakruh = pillMakruh,
                onKaraha = onKaraha,
            )
            if (afterIsha) {
                Text(
                    text = stringResource(R.string.times_tomorrow_fajr, info.nextFajr.format(HM)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 4.dp),
                )
            }
            if (hasLater) {
                ExpanderRow(
                    text = if (showFuture) stringResource(R.string.times_hide_later) else stringResource(R.string.times_show_later),
                    onClick = { showFuture = !showFuture },
                )
            }
        }
    }
}

@Composable
private fun ExpanderRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Vertical timeline: primary entries (prayers + Duha) sit on a rail as nodes;
 *  makruh windows are compact chips attached above/below their entry. */
@Composable
private fun Timeline(
    visible: List<DayBlock>,
    active: Pair<Prayer, ZonedDateTime>?,
    nextEntry: Pair<Prayer, ZonedDateTime>?,
    now: ZonedDateTime,
    highlight: Boolean,
    showPast: Boolean,
    amber: Color,
    green: Color,
    nextFajr: ZonedDateTime,
    showNextCountdown: Boolean,
    showRemaining: Boolean,
    pillMakruh: MakruhBlock?,
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val nodeFill = MaterialTheme.colorScheme.surface

    val railX = 18.dp
    val contentStart = 42.dp

    // Captured layout: root-Y centre of each primary row, keyed per block.
    val nodeCenters = remember { mutableStateMapOf<String, Float>() }
    var boxTop by remember { mutableStateOf(0f) }

    // A makruh window is "now" (selected) when the current time is inside it.
    fun isMakruhNow(b: MakruhBlock) = highlight && !now.isBefore(b.start) && now.isBefore(b.end)
    // Hide makruh windows that are already over (unless showing past).
    fun mkVisible(m: MakruhBlock?) = m?.takeIf { !highlight || showPast || it.end.isAfter(now) }
    // Sunrise is just an astronomical moment, not a prayer time → never the
    // "current" selection. A prayer is selected only if it isn't Sunrise.
    fun isPrayerSelected(p: Prayer) = active != null && p == active.first && p != Prayer.SUNRISE
    // The Duha (forenoon) entry is the "current" one while inside its window.
    fun isDuhaNow(b: NaflBlock) = highlight && b.forenoon && !now.isBefore(b.start) && now.isBefore(b.end)

    fun nodeKey(b: DayBlock): String? = when {
        b is PrayerBlock -> "P:${b.prayer.name}"
        b is NaflBlock && b.forenoon -> "D"
        else -> null
    }

    // (boxY, selected) for every measured primary node, in top-to-bottom order.
    val nodes = visible.mapNotNull { b ->
        val key = nodeKey(b) ?: return@mapNotNull null
        val y = nodeCenters[key] ?: return@mapNotNull null
        val sel = when (b) {
            is PrayerBlock -> isPrayerSelected(b.prayer)
            is NaflBlock -> isDuhaNow(b)
        }
        (y - boxTop) to sel
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { boxTop = it.positionInWindow().y }) {

        // Rail line + node dots (current = filled, others hollow).
        Canvas(modifier = Modifier.matchParentSize()) {
            if (nodes.isEmpty()) return@Canvas
            val x = railX.toPx()
            drawLine(track, Offset(x, nodes.first().first), Offset(x, nodes.last().first), 3.dp.toPx(), StrokeCap.Round)
            nodes.forEach { (y, sel) ->
                if (sel) {
                    drawCircle(primary, 7.dp.toPx(), Offset(x, y))
                } else {
                    drawCircle(nodeFill, 5.dp.toPx(), Offset(x, y))
                    drawCircle(primary, 5.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                }
            }
        }

        // The rows themselves, spaced proportionally to the real time gaps.
        Column(modifier = Modifier.fillMaxWidth().padding(start = contentStart, end = 8.dp)) {
            var prev: ZonedDateTime? = null
            visible.forEachIndexed { index, block ->
                // Gaps are proportional to the real time distance between
                // entries (linear), with a min for legibility and a cap so the
                // one long stretch (Duha→Dhuhr) doesn't blow up the layout. The
                // interval length is written faintly into the gap — except the
                // first gap, where the next prayer's countdown already shows it.
                val gapMin = prev?.let { Duration.between(it, block.sortAt).toMinutes().coerceAtLeast(0) }
                val gapDp = gapMin?.let { (it * 0.34).dp.coerceIn(22.dp, 132.dp) } ?: 4.dp
                if (gapMin != null && gapMin >= 30 && index >= 2) {
                    Box(modifier = Modifier.fillMaxWidth().height(gapDp)) {
                        Text(
                            text = durationLabel(gapMin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                } else {
                    Spacer(Modifier.height(gapDp))
                }
                prev = block.sortAt

                when (block) {
                    is PrayerBlock -> {
                        val isSelected = isPrayerSelected(block.prayer)
                        val isNext = nextEntry?.first == block.prayer
                        val isPast = active != null && block.time.isBefore(active.second)
                        val foreground = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isNext -> primary
                            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val weight = if (isSelected || isNext) FontWeight.Bold else FontWeight.Normal
                        val name = stringResource(block.prayer.labelRes())
                        val status = when {
                            isSelected -> stringResource(R.string.status_current)
                            isNext -> stringResource(R.string.status_next)
                            isPast -> stringResource(R.string.status_past)
                            else -> ""
                        }
                        val capture = Modifier.onGloballyPositioned {
                            nodeCenters["P:${block.prayer.name}"] = it.positionInWindow().y + it.size.height / 2f
                        }
                        // The makruh chip sits immediately above the prayer pill —
                        // unless it's drawn inside the current pill as a band.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            mkVisible(block.before)?.takeIf { it !== pillMakruh }?.let {
                                MakruhChipRow(
                                    it, amber, isMakruhNow(it), highlight && it.end.isBefore(now),
                                    countdown = if (highlight) KarahaCountdown.state(now, it.start, it.end) else null,
                                    onKaraha = onKaraha,
                                )
                            }
                            if (isSelected) {
                                // Current prayer: the pill itself fills with the
                                // progress of the running period; remaining inside.
                                // For Isha (last prayer) the period runs to tomorrow's Fajr.
                                val nextT = nextEntry?.second ?: nextFajr
                                val total = Duration.between(block.time, nextT).seconds.coerceAtLeast(1).toFloat()
                                val frac = if (nextT.isAfter(block.time)) {
                                    Duration.between(block.time, now).seconds / total
                                } else {
                                    0f
                                }
                                val remMin = Duration.between(now, nextT).toMinutes().coerceAtLeast(0)
                                // A makruh window inside this prayer's interval → ridged band.
                                val band = pillMakruh?.let { mk ->
                                    val s = (Duration.between(block.time, mk.start).seconds / total).coerceIn(0f, 1f)
                                    val e = (Duration.between(block.time, mk.end).seconds / total).coerceIn(0f, 1f)
                                    if (e > s) s to e else null
                                }
                                val karaha = pillMakruh?.let { KarahaCountdown.state(now, it.start, it.end) }
                                val pillDesc = stringResource(
                                    R.string.desc_prayer_current,
                                    name, block.time.format(HM), durationLabel(remMin),
                                ) + (pillMakruh?.let {
                                    stringResource(
                                        R.string.desc_prayer_current_makruh,
                                        it.label, it.start.format(HM), it.end.format(HM),
                                    )
                                } ?: "")
                                ProgressPill(
                                    fraction = frac,
                                    makruhBand = band,
                                    makruhColor = amber,
                                    accentAmber = karaha?.active == true,
                                    onClick = pillMakruh?.let { mk -> { onKaraha(mk.explain) } },
                                ) {
                                    Column(
                                        modifier = capture.clearAndSetSemantics {
                                            contentDescription = pillDesc
                                        },
                                    ) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                            Text(name, style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Spacer(Modifier.width(8.dp))
                                            Text(block.time.format(HM), style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.Bold, softWrap = false)
                                        }
                                        if (showRemaining) {
                                            Text(stringResource(R.string.remaining_inline, durationLabel(remMin)), style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.85f), modifier = Modifier.padding(top = 1.dp))
                                        }
                                        karaha?.let { k ->
                                            Text(
                                                text = (if (k.active) stringResource(R.string.karaha_active_prefix) else stringResource(R.string.karaha_warn_prefix)) + k.text,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = amber,
                                                modifier = Modifier.padding(top = 1.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                var lane = Modifier.fillMaxWidth()
                                if (isNext) lane = lane.border(1.5.dp, primary, RoundedCornerShape(16.dp))
                                lane = lane.padding(horizontal = 12.dp, vertical = 9.dp)
                                val rowDesc = stringResource(R.string.desc_prayer, name, block.time.format(HM), status)
                                Column(modifier = lane) {
                                    Row(
                                        modifier = capture
                                            .fillMaxWidth()
                                            .clearAndSetSemantics {
                                                contentDescription = rowDesc
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(name, style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = weight, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(block.time.format(HM), style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = weight, softWrap = false)
                                    }
                                    // No pill carries the remaining time (e.g. pre-Fajr,
                                    // İşrak/Zeval gap) → show the countdown here.
                                    if (isNext && showNextCountdown) {
                                        Text(
                                            text = remainingText(Duration.between(now, block.time)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                            block.cemaat?.let {
                                CemaatTipRow(it, green, highlight && it.time.isBefore(now), onKaraha)
                            }
                            block.tip?.takeIf { !it.whenCurrent || isSelected }?.let {
                                NaflTipRow(it, green, highlight && it.end.isBefore(now), onKaraha)
                            }
                        }
                    }
                    is NaflBlock -> {
                        val faded = highlight && block.end.isBefore(now)
                        val c = green.copy(alpha = if (faded) 0.75f else 1f)
                        if (block.forenoon) {
                            // Duha = forenoon entry (replaces Sunrise). İşrak makruh
                            // chip sits above it; when current the pill fills with
                            // the Duha-window progress (like the prayer pill).
                            val selected = isDuhaNow(block)
                            val cap = Modifier.onGloballyPositioned {
                                nodeCenters["D"] = it.positionInWindow().y + it.size.height / 2f
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                mkVisible(block.before)?.let {
                                    MakruhChipRow(
                                        it, amber, isMakruhNow(it), highlight && it.end.isBefore(now),
                                        countdown = if (highlight) KarahaCountdown.state(now, it.start, it.end) else null,
                                        onKaraha = onKaraha,
                                    )
                                }
                                if (selected) {
                                    val onpc = MaterialTheme.colorScheme.onPrimaryContainer
                                    val frac = Duration.between(block.start, now).seconds.toFloat() /
                                        Duration.between(block.start, block.end).seconds.coerceAtLeast(1)
                                    val remMin = Duration.between(now, block.end).toMinutes().coerceAtLeast(0)
                                    val duhaDesc = stringResource(R.string.desc_nafl_remaining, block.label, durationLabel(remMin))
                                    ProgressPill(fraction = frac) {
                                        Column(
                                            modifier = cap.clearAndSetSemantics {
                                                contentDescription = duhaDesc
                                            },
                                        ) {
                                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(painterResource(R.drawable.ic_nafl), null, tint = onpc, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(block.label, style = MaterialTheme.typography.titleMedium, color = onpc, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text("${block.start.format(HM)}–${block.end.format(HM)}", style = MaterialTheme.typography.labelMedium, color = onpc, softWrap = false)
                                            }
                                            if (showRemaining) {
                                                Text(stringResource(R.string.remaining_inline, durationLabel(remMin)), style = MaterialTheme.typography.labelMedium, color = onpc.copy(alpha = 0.85f), modifier = Modifier.padding(top = 1.dp))
                                            }
                                        }
                                    }
                                } else {
                                    val duhaRangeDesc = stringResource(R.string.desc_nafl_range, block.label, block.start.format(HM), block.end.format(HM))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                            .then(cap)
                                            .clearAndSetSemantics {
                                                contentDescription = duhaRangeDesc
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painterResource(R.drawable.ic_nafl), null, tint = c, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(block.label, style = MaterialTheme.typography.titleMedium, color = c)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("${block.start.format(HM)}–${block.end.format(HM)}", style = MaterialTheme.typography.titleMedium, color = c, softWrap = false)
                                    }
                                }
                            }
                        } else {
                            // Awwabin / Tahajjud — secondary informational rows.
                            val naflRowDesc = stringResource(R.string.desc_nafl_range, block.label, block.start.format(HM), block.end.format(HM))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clearAndSetSemantics {
                                        contentDescription = naflRowDesc
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_nafl), null, tint = c, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(block.label, style = MaterialTheme.typography.labelMedium, color = c)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("${block.start.format(HM)}–${block.end.format(HM)}", style = MaterialTheme.typography.labelMedium, color = c, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact makruh chip, indented to align under the prayer name. When [selected]
 *  (the current time is inside the window) it is amber-filled, mirroring the
 *  prayer's selected pill. */
@Composable
private fun MakruhChipRow(
    block: MakruhBlock,
    amber: Color,
    selected: Boolean,
    faded: Boolean,
    countdown: KarahaCountdown.State?,
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val c = amber.copy(alpha = if (faded) 0.75f else 1f)
    val desc = stringResource(R.string.desc_makruh, block.label, block.start.format(HM), block.end.format(HM)) +
        (if (selected) stringResource(R.string.desc_makruh_current) else "") +
        (countdown?.let { stringResource(R.string.desc_makruh_countdown, it.text) } ?: "")
    val explainLabel = stringResource(R.string.show_explanation)
    val shape = RoundedCornerShape(12.dp)
    var mod = Modifier
        .minimumInteractiveComponentSize()
        .clickable(onClickLabel = explainLabel) { onKaraha(block.explain) }
    if (selected) mod = mod.background(amber.copy(alpha = 0.20f), shape)
    mod = mod
        .padding(horizontal = if (selected) 10.dp else 2.dp, vertical = if (selected) 5.dp else 1.dp)
        .heightIn(min = 24.dp)
        .semantics(mergeDescendants = true) { contentDescription = desc }
    // Indent so the chip lines up under the prayer name (lane has 12.dp padding).
    Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_makruh),
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(if (selected) 14.dp else 12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                stringResource(R.string.makruh_chip, block.label, block.start.format(HM), block.end.format(HM)) +
                    (countdown?.let { stringResource(R.string.makruh_chip_countdown, it.text) } ?: ""),
                style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                color = c,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** A pill whose background fills left→right to [fraction], used for the current
 *  prayer so the pill itself visualises how far the running period has advanced.
 *  [makruhBand] (start..end fraction within the pill) is drawn as a ridged amber
 *  zone — a makruh window that falls inside this prayer's interval. */
@Composable
private fun ProgressPill(
    fraction: Float,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer,
    makruhBand: Pair<Float, Float>? = null,
    makruhColor: Color = MaterialTheme.colorScheme.primary,
    accentAmber: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val edge = if (accentAmber) makruhColor else MaterialTheme.colorScheme.primary
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (rememberAnimationsEnabled()) tween(durationMillis = 650) else snap(),
        label = "pillFill",
    )
    // Ridged ("geriffelt") amber stripes via a repeating diagonal gradient.
    val ridge = Brush.linearGradient(
        0.0f to makruhColor.copy(alpha = 0.55f),
        0.5f to makruhColor.copy(alpha = 0.55f),
        0.5f to makruhColor.copy(alpha = 0.22f),
        1.0f to makruhColor.copy(alpha = 0.22f),
        start = Offset(0f, 0f),
        end = Offset(9f, 9f),
        tileMode = TileMode.Repeated,
    )
    var mod = Modifier.fillMaxWidth().clip(shape).background(fillColor.copy(alpha = 0.26f))
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Box(modifier = mod) {
        Box(modifier = Modifier.matchParentSize()) {
            if (animFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animFrac)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(fillColor.copy(alpha = 0.82f), fillColor))),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(2.5.dp)
                            .background(edge.copy(alpha = 0.5f)),
                    )
                }
            }
            // Makruh zone (drawn over the fill), positioned by fraction.
            makruhBand?.let { (s, e) ->
                val left = s.coerceIn(0f, 1f)
                val mid = (e - s).coerceIn(0.0001f, 1f)
                val right = (1f - e).coerceIn(0f, 1f)
                Row(modifier = Modifier.matchParentSize()) {
                    if (left > 0f) Spacer(Modifier.weight(left))
                    Box(Modifier.weight(mid).fillMaxHeight().background(ridge))
                    if (right > 0f) Spacer(Modifier.weight(right))
                }
            }
        }
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) { content() }
    }
}

/** Abgeleitete Cemaat-Punktzeit unter Fajr — Aufbau wie NaflTipRow, aber ohne Bereich. */
@Composable
private fun CemaatTipRow(block: CemaatTip, green: Color, faded: Boolean, onInfo: (Pair<String, String>) -> Unit) {
    val c = green.copy(alpha = if (faded) 0.75f else 1f)
    val desc = stringResource(R.string.desc_cemaat, block.label, block.time.format(HM))
    val explainLabel = stringResource(R.string.show_explanation)
    Row(
        modifier = Modifier
            .padding(start = 12.dp)
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = explainLabel) { onInfo(block.explain) }
            .heightIn(min = 22.dp)
            .padding(vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_nafl), null, tint = c, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.cemaat_time_inline, block.label, block.time.format(HM)),
            style = MaterialTheme.typography.labelSmall,
            color = c,
        )
    }
}

/** Compact green nafl "tip" attached under a prayer (e.g. Awwabin on Maghrib). */
@Composable
private fun NaflTipRow(block: NaflBlock, green: Color, faded: Boolean, onInfo: (Pair<String, String>) -> Unit) {
    val c = green.copy(alpha = if (faded) 0.75f else 1f)
    val desc = stringResource(R.string.desc_nafl_tip, block.label, block.start.format(HM), block.end.format(HM))
    val explainLabel = stringResource(R.string.show_explanation)
    var mod = Modifier.padding(start = 12.dp)
    block.explain?.let { ex ->
        mod = mod.minimumInteractiveComponentSize().clickable(onClickLabel = explainLabel) { onInfo(ex) }
    }
    Row(
        modifier = mod
            .heightIn(min = 22.dp)
            .padding(vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_nafl), null, tint = c, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.nafl_range_inline, block.label, block.start.format(HM), block.end.format(HM)),
            style = MaterialTheme.typography.labelSmall,
            color = c,
        )
    }
}

