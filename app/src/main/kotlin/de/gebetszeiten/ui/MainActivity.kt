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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.Cities
import de.gebetszeiten.data.City
import de.gebetszeiten.official.OfficialTimesProvider
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.KarahaTimes
import de.gebetszeiten.prayer.NaflTimes
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.theme.GebetszeitenTheme
import de.gebetszeiten.ui.theme.LocalHighContrast
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Diyanet / Turkish transliteration of the Hijri month names.
private val HIJRI_MONTHS = arrayOf(
    "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
    "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
)

private fun hijriText(date: LocalDate): String {
    val h = HijrahDate.from(date)
    val d = h.get(ChronoField.DAY_OF_MONTH)
    val m = h.get(ChronoField.MONTH_OF_YEAR)
    val y = h.get(ChronoField.YEAR)
    return "$d. ${HIJRI_MONTHS[m - 1]} $y"
}

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
            GebetszeitenTheme(highContrast = settings.highContrast) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
                ) {
                    NotificationPermissionRequester()
                    PrayerScreen(viewModel)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerScreen(viewModel: PrayerViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val zone = ZoneId.systemDefault()

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }

    var selectedDate by remember { mutableStateOf(LocalDate.now(zone)) }
    val isToday = selectedDate == LocalDate.now(zone)

    val dayInfo by produceState<DayInfo?>(null, settings, tick, selectedDate) {
        val times = PrayerProvider.daily(context, settings, selectedDate, zone)
        val nextFajr = PrayerProvider.daily(context, settings, selectedDate.plusDays(1), zone).fajr
        value = DayInfo(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr), nextFajr)
    }
    var showSettings by remember { mutableStateOf(false) }
    var karahaInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = settings.city) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { inner ->
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
                    onKaraha = { karahaInfo = it },
                )
            }

            Text(
                text = context.getString(R.string.data_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            LocationSettings(
                settings = settings,
                onSave = { viewModel.save(it); showSettings = false },
            )
        }
    }

    karahaInfo?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { karahaInfo = null },
            confirmButton = { TextButton(onClick = { karahaInfo = null }) { Text("Verstanden") } },
            title = { Text(title) },
            text = { Text(text) },
        )
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.semantics { contentDescription = "Vorheriger Tag" },
        ) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onToday),
        ) {
            Text(
                text = if (isToday) "Heute" else date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = hijriText(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = "Nächster Tag" },
        ) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** "3 Std 26 Min" / "55 Min" — interval length shown in the rail gap. */
private fun durationLabel(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h Std $m Min"
        h > 0 -> "$h Std"
        else -> "$m Min"
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
) : DayBlock {
    override val sortAt: ZonedDateTime get() = time
}

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
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
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

    // Prayers carry their makruh chips. Sunrise is just a moment (no makruh).
    // The forenoon period is represented by Duha, which also carries the İşrak
    // makruh (so it sits right above the Duha pill, not on Sunrise).
    val blocks = remember(info, showKaraha) {
        val k = info.karaha
        val n = info.nafl
        buildList<DayBlock> {
            info.times.ordered().forEach { (p, t) ->
                val before = if (!showKaraha) null else when (p) {
                    Prayer.DHUHR -> MakruhBlock("Zenit", k.zevalStart, k.zevalEnd, KARAHA_ZEVAL)
                    Prayer.MAGHRIB -> MakruhBlock("vor Sonnenuntergang", k.isfirarStart, k.isfirarEnd, KARAHA_ISFIRAR)
                    else -> null
                }
                // Voluntary-prayer tips: Awwabin on Maghrib (always), Tahajjud on
                // Isha (only while Isha is the current prayer — i.e. at night).
                val tip = when (p) {
                    Prayer.MAGHRIB -> NaflBlock("Awwabin", n.awwabinStart, n.awwabinEnd, explain = NAFL_AWWABIN)
                    Prayer.ISHA -> NaflBlock("Tahajjud", n.tahajjudStart, n.tahajjudEnd, explain = NAFL_TAHAJJUD, whenCurrent = true)
                    else -> null
                }
                add(PrayerBlock(p, t, before, null, tip))
            }
            // Duha is the forenoon entry that replaces Sunrise once it has
            // passed; İşrak makruh attaches to it.
            val israk = if (showKaraha) MakruhBlock("nach Sonnenaufgang", k.sunriseStart, k.sunriseEnd, KARAHA_SUNRISE) else null
            add(NaflBlock("Duha (Kuşluk)", n.duhaStart, n.duhaEnd, forenoon = true, before = israk))
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
                    text = if (showPast) "Frühere ausblenden" else "Frühere Zeiten anzeigen",
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
                showNextCountdown = !anyCurrent,
                pillMakruh = pillMakruh,
                onKaraha = onKaraha,
            )
            if (afterIsha) {
                Text(
                    text = "Morgen · Fajr ${info.nextFajr.format(HM)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 4.dp),
                )
            }
            if (hasLater) {
                ExpanderRow(
                    text = if (showFuture) "Weitere ausblenden" else "Weitere Zeiten anzeigen",
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
                        val name = context.getString(block.prayer.labelRes())
                        val status = when {
                            isSelected -> ", aktuelles Gebet"
                            isNext -> ", nächstes Gebet"
                            isPast -> ", vergangen"
                            else -> ""
                        }
                        val capture = Modifier.onGloballyPositioned {
                            nodeCenters["P:${block.prayer.name}"] = it.positionInWindow().y + it.size.height / 2f
                        }
                        // The makruh chip sits immediately above the prayer pill —
                        // unless it's drawn inside the current pill as a band.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            mkVisible(block.before)?.takeIf { it !== pillMakruh }?.let {
                                MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
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
                                ProgressPill(
                                    fraction = frac,
                                    makruhBand = band,
                                    makruhColor = amber,
                                    onClick = pillMakruh?.let { mk -> { onKaraha(mk.explain) } },
                                ) {
                                    Column(
                                        modifier = capture.clearAndSetSemantics {
                                            contentDescription = "$name, ${block.time.format(HM)}, aktuelles Gebet, noch ${durationLabel(remMin)}" +
                                                (pillMakruh?.let { ", Makruh ${it.label} ${it.start.format(HM)} bis ${it.end.format(HM)}" } ?: "")
                                        },
                                    ) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                            Text(name, style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Spacer(Modifier.width(8.dp))
                                            Text(block.time.format(HM), style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.Bold, softWrap = false)
                                        }
                                        Text("läuft · noch ${durationLabel(remMin)}", style = MaterialTheme.typography.labelMedium, color = foreground.copy(alpha = 0.85f), modifier = Modifier.padding(top = 1.dp))
                                    }
                                }
                            } else {
                                var lane = Modifier.fillMaxWidth()
                                if (isNext) lane = lane.border(1.5.dp, primary, RoundedCornerShape(16.dp))
                                lane = lane.padding(horizontal = 12.dp, vertical = 9.dp)
                                Column(modifier = lane) {
                                    Row(
                                        modifier = capture
                                            .fillMaxWidth()
                                            .clearAndSetSemantics {
                                                contentDescription = "$name, ${block.time.format(HM)}$status"
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
                            block.tip?.takeIf { !it.whenCurrent || isSelected }?.let {
                                NaflTipRow(it, green, highlight && it.end.isBefore(now), onKaraha)
                            }
                        }
                    }
                    is NaflBlock -> {
                        val faded = highlight && block.end.isBefore(now)
                        val c = green.copy(alpha = if (faded) 0.5f else 1f)
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
                                    MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
                                }
                                if (selected) {
                                    val onpc = MaterialTheme.colorScheme.onPrimaryContainer
                                    val frac = Duration.between(block.start, now).seconds.toFloat() /
                                        Duration.between(block.start, block.end).seconds.coerceAtLeast(1)
                                    val remMin = Duration.between(now, block.end).toMinutes().coerceAtLeast(0)
                                    ProgressPill(fraction = frac) {
                                        Column(
                                            modifier = cap.clearAndSetSemantics {
                                                contentDescription = "${block.label}, freiwilliges Gebet, läuft, noch ${durationLabel(remMin)}"
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
                                            Text("läuft · noch ${durationLabel(remMin)}", style = MaterialTheme.typography.labelMedium, color = onpc.copy(alpha = 0.85f), modifier = Modifier.padding(top = 1.dp))
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                            .then(cap)
                                            .clearAndSetSemantics {
                                                contentDescription = "${block.label}, freiwilliges Gebet, ${block.start.format(HM)} bis ${block.end.format(HM)}"
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clearAndSetSemantics {
                                        contentDescription = "${block.label}, freiwilliges Gebet, ${block.start.format(HM)} bis ${block.end.format(HM)}"
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
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val c = amber.copy(alpha = if (faded) 0.55f else 1f)
    val desc = "Makruh-Zeit ${block.label}, ${block.start.format(HM)} bis ${block.end.format(HM)}" +
        if (selected) ", läuft gerade" else ""
    val shape = RoundedCornerShape(12.dp)
    var mod = Modifier
        .clickable(onClickLabel = "Erklärung anzeigen") { onKaraha(block.explain) }
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
                "Makruh · ${block.label} · ${block.start.format(HM)}–${block.end.format(HM)}",
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
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val edge = MaterialTheme.colorScheme.primary
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
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

/** Compact green nafl "tip" attached under a prayer (e.g. Awwabin on Maghrib). */
@Composable
private fun NaflTipRow(block: NaflBlock, green: Color, faded: Boolean, onInfo: (Pair<String, String>) -> Unit) {
    val c = green.copy(alpha = if (faded) 0.55f else 1f)
    val desc = "Tipp: ${block.label}, freiwilliges Gebet, ${block.start.format(HM)} bis ${block.end.format(HM)}"
    var mod = Modifier.padding(start = 12.dp)
    block.explain?.let { ex -> mod = mod.clickable(onClickLabel = "Erklärung anzeigen") { onInfo(ex) } }
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
            "${block.label} · ${block.start.format(HM)}–${block.end.format(HM)}",
            style = MaterialTheme.typography.labelSmall,
            color = c,
        )
    }
}

private val KARAHA_SUNRISE = "İşrak (nach Sonnenaufgang)" to
    "Makruh-Zeit vom Sonnenaufgang, bis die Sonne ~eine Speerlänge gestiegen ist (≈45 Min). In dieser Zeit kein (freiwilliges) Gebet; danach beginnen İşrak/Duha. (Hanafi)"
private val KARAHA_ZEVAL = "Zeval / İstiva (Zenit)" to
    "Makruh-Zeit kurz vor dem Höchststand der Sonne bis Dhuhr (≈20 Min). Während die Sonne im Zenit steht, wird nicht gebetet. (Hanafi)"
private val KARAHA_ISFIRAR = "İsfirar-ı şems (vor Sonnenuntergang)" to
    "Makruh-Zeit, wenn die Sonne vergilbt (≈40 Min vor Sonnenuntergang) bis Maghrib. Nur die heutige Asr darf hier noch (verspätet) gebetet werden. (Hanafi)"
private val NAFL_AWWABIN = "Awwabin (Evvabin)" to
    "Freiwilliges Gebet nach dem Maghrib- bis zum Isha-Gebet — empfohlen sind 6 Rekat. (Sunna/Mustahab)"
private val NAFL_TAHAJJUD = "Tahajjud" to
    "Freiwilliges Nachtgebet im letzten Drittel der Nacht (nach dem Schlaf, vor Fajr). Besonders verdienstvoll. (Sunna/Mustahab)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSettings(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var city by remember(settings.city) { mutableStateOf(settings.city) }
    var lat by remember(settings.latitude) { mutableStateOf(settings.latitude.toString()) }
    var lng by remember(settings.longitude) { mutableStateOf(settings.longitude.toString()) }
    var countdown by remember(settings.showCountdown) { mutableStateOf(settings.showCountdown) }
    var online by remember(settings.useOnline) { mutableStateOf(settings.useOnline) }
    var karaha by remember(settings.showKaraha) { mutableStateOf(settings.showKaraha) }
    var contrast by remember(settings.highContrast) { mutableStateOf(settings.highContrast) }
    var fontScale by remember(settings.fontScale) { mutableStateOf(settings.fontScale) }
    var reminders by remember(settings.reminders) { mutableStateOf(settings.reminders) }
    var leadMinutes by remember(settings.reminderLeadMinutes) { mutableStateOf(settings.reminderLeadMinutes) }
    var persistent by remember(settings.persistentNotification) { mutableStateOf(settings.persistentNotification) }
    var expanded by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<City>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(city, expanded) {
        matches = if (expanded) Cities.search(context, city, limit = 12) else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.titleLarge)

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = city,
                onValueChange = { city = it; expanded = true },
                label = { Text("Stadt") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
                matches.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.name} (${c.country})") },
                        onClick = { city = c.name; lat = c.latitude.toString(); lng = c.longitude.toString(); expanded = false },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Breite") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Länge") }, modifier = Modifier.weight(1f))
        }

        ToggleRow("Makruh-Zeiten anzeigen", karaha) { karaha = it }
        ToggleRow("Restzeit im Widget", countdown) { countdown = it }
        if (OfficialTimesProvider.isOnline) {
            ToggleRow("Offizielle Diyanet-Zeiten (online)", online) { online = it }
        }

        FontSizeSelector(fontScale) { fontScale = it }
        ToggleRow("Hoher Kontrast", contrast) { contrast = it }

        Text("Erinnerungen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Stille Benachrichtigung zur jeweiligen Gebetszeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { p ->
            ToggleRow(context.getString(p.labelRes()), p.name in reminders) { on ->
                reminders = if (on) reminders + p.name else reminders - p.name
            }
        }

        Text("Vorlauf", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Zusätzliche stille Erinnerung einige Minuten vor der Gebetszeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Aus" to 0, "5 Min" to 5, "10 Min" to 10, "15 Min" to 15, "30 Min" to 30).forEach { (label, v) ->
                FilterChip(
                    selected = leadMinutes == v,
                    onClick = { leadMinutes = v },
                    label = { Text(label) },
                )
            }
        }

        ToggleRow("Dauerhafte Anzeige (Sperrbildschirm)", persistent) { persistent = it }
        Text(
            "Stille, dauerhafte Benachrichtigung mit dem nächsten Gebet und Restzeit — sichtbar auch auf dem Sperrbildschirm. Wird vom System gezeichnet, kostet keinen zusätzlichen Akku.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BatteryOptimizationCard()

        Button(
            onClick = {
                val parsedLat = lat.toDoubleOrNull() ?: settings.latitude
                val parsedLng = lng.toDoubleOrNull() ?: settings.longitude
                onSave(
                    settings.copy(
                        latitude = parsedLat,
                        longitude = parsedLng,
                        city = city.ifBlank { "—" },
                        showCountdown = countdown,
                        useOnline = online,
                        showKaraha = karaha,
                        highContrast = contrast,
                        fontScale = fontScale,
                        reminders = reminders,
                        reminderLeadMinutes = leadMinutes,
                        persistentNotification = persistent,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Speichern")
        }
    }
}

@Composable
private fun FontSizeSelector(value: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Schriftgröße", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Normal" to 1f, "Groß" to 1.2f, "Sehr groß" to 1.4f).forEach { (label, v) ->
                FilterChip(
                    selected = abs(value - v) < 0.01f,
                    onClick = { onChange(v) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/**
 * Shown only while the app is still subject to battery optimization. OEM power
 * managers (especially OnePlus/ColorOS) may otherwise swallow the exact alarms
 * that drive reminders and the widget.
 */
@Composable
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    var exempt by remember {
        mutableStateOf(
            context.getSystemService(android.os.PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )
    }
    LaunchedEffect(Unit) {
        // Re-check while visible so the card disappears right after the user
        // grants the exemption in the system dialog. Runs only while the
        // settings sheet is open.
        while (true) {
            exempt = context.getSystemService(android.os.PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            delay(2000)
        }
    }
    if (exempt) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zuverlässige Erinnerungen", style = MaterialTheme.typography.titleSmall)
            Text(
                "Damit Erinnerungen und Widget pünktlich bleiben, sollte die Akku-Optimierung für diese App deaktiviert werden. Die App wacht trotzdem nur zu den Gebetszeiten auf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }) {
                Text("Akku-Optimierung deaktivieren")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
