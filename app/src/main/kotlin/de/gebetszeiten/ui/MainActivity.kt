package de.gebetszeiten.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
        value = DayInfo(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr))
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
                    showNafl = settings.showNafl,
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

/** A prayer with the optional makruh chips shown directly above/below it. */
private data class PrayerBlock(
    val prayer: Prayer,
    val time: ZonedDateTime,
    val before: MakruhBlock? = null, // Zenit / İsfirar — chip above the prayer
    val after: MakruhBlock? = null,  // İşrak — chip below the prayer
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
) : DayBlock {
    override val sortAt: ZonedDateTime get() = start
}

/** Sort tie-break at equal instant: prayer before nafl. */
private fun blockOrder(b: DayBlock): Int = when (b) {
    is PrayerBlock -> 0
    is NaflBlock -> 1
}

@Composable
private fun TimesCard(
    info: DayInfo,
    now: ZonedDateTime,
    highlight: Boolean,
    showNafl: Boolean,
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

    // Each prayer carries its makruh chip(s); nafl windows are their own blocks.
    val blocks = remember(info, showNafl, showKaraha) {
        val k = info.karaha
        val n = info.nafl
        buildList<DayBlock> {
            info.times.ordered().forEach { (p, t) ->
                val before = if (!showKaraha) null else when (p) {
                    Prayer.DHUHR -> MakruhBlock("Zenit", k.zevalStart, k.zevalEnd, KARAHA_ZEVAL)
                    Prayer.MAGHRIB -> MakruhBlock("vor Sonnenuntergang", k.isfirarStart, k.isfirarEnd, KARAHA_ISFIRAR)
                    else -> null
                }
                val after = if (showKaraha && p == Prayer.SUNRISE) {
                    MakruhBlock("nach Sonnenaufgang", k.sunriseStart, k.sunriseEnd, KARAHA_SUNRISE)
                } else {
                    null
                }
                add(PrayerBlock(p, t, before, after))
            }
            if (showNafl) {
                add(NaflBlock("Duha (Kuşluk)", n.duhaStart, n.duhaEnd))
                add(NaflBlock("Awwabin", n.awwabinStart, n.awwabinEnd))
                add(NaflBlock("Tahajjud", n.tahajjudStart, n.tahajjudEnd))
            }
        }.sortedWith(compareBy({ it.sortAt }, { blockOrder(it) }))
    }

    // "Running" timeline: the currently active prayer (last one whose time has
    // passed) is selected; earlier blocks are collapsed by default.
    val active: Pair<Prayer, ZonedDateTime>? =
        if (highlight) info.times.ordered().lastOrNull { !it.second.isAfter(now) } else null
    val nextEntry: Pair<Prayer, ZonedDateTime>? =
        if (highlight) info.times.ordered().firstOrNull { it.second.isAfter(now) } else null
    val activeIndex =
        if (active != null) blocks.indexOfFirst { it is PrayerBlock && it.prayer == active.first } else -1
    var showPast by remember { mutableStateOf(false) }
    val visible = if (activeIndex > 0 && !showPast) blocks.drop(activeIndex) else blocks

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            if (activeIndex > 0) {
                Text(
                    text = if (showPast) "Frühere ausblenden" else "Frühere Zeiten anzeigen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPast = !showPast }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                onKaraha = onKaraha,
            )
        }
    }
}

/** Proportional vertical timeline: prayers as nodes on a rail, makruh/nafl as
 *  coloured bands, with a live "now" dot and progress fill for today. */
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
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val nodeFill = MaterialTheme.colorScheme.surface

    val railX = 18.dp
    val contentStart = 42.dp
    val contentStartPx = with(density) { contentStart.toPx() }

    // Captured layout: root-Y centre of each prayer's name row + the box top.
    val nodeCenters = remember { mutableStateMapOf<String, Float>() }
    var boxTop by remember { mutableStateOf(0f) }

    // A makruh window is "now" (selected) when the current time is inside it.
    fun isMakruhNow(b: MakruhBlock) = highlight && !now.isBefore(b.start) && now.isBefore(b.end)
    // Hide makruh windows that are already over (unless showing past).
    fun mkVisible(m: MakruhBlock?) = m?.takeIf { !highlight || showPast || it.end.isAfter(now) }

    // (epochSecond, boxY, selected) for every measured prayer node.
    val nodes = visible.mapNotNull { b ->
        if (b is PrayerBlock) {
            val y = nodeCenters[b.prayer.name]
            if (y != null) Triple(b.time.toEpochSecond(), y - boxTop, active != null && b.prayer == active.first) else null
        } else {
            null
        }
    }
    fun mapY(epoch: Long): Float {
        if (nodes.isEmpty()) return 0f
        if (epoch <= nodes.first().first) return nodes.first().second
        if (epoch >= nodes.last().first) return nodes.last().second
        for (i in 1 until nodes.size) {
            val (t1, y1) = nodes[i - 1]
            val (t2, y2) = nodes[i]
            if (epoch <= t2) return y1 + (epoch - t1).toFloat() / (t2 - t1) * (y2 - y1)
        }
        return nodes.last().second
    }

    // Coloured bands for makruh (amber) and nafl (green) windows; an active
    // makruh band is drawn at full strength.
    data class Band(val start: Long, val end: Long, val color: Color, val strong: Boolean)
    val bands = buildList {
        visible.forEach { b ->
            when (b) {
                is PrayerBlock -> {
                    mkVisible(b.before)?.let { add(Band(it.start.toEpochSecond(), it.end.toEpochSecond(), amber, isMakruhNow(it))) }
                    mkVisible(b.after)?.let { add(Band(it.start.toEpochSecond(), it.end.toEpochSecond(), amber, isMakruhNow(it))) }
                }
                is NaflBlock -> add(Band(b.start.toEpochSecond(), b.end.toEpochSecond(), green, false))
            }
        }
    }

    val showNow = highlight && active != null && nodes.isNotEmpty()
    val nowY = if (showNow) mapY(now.toEpochSecond()) else 0f
    // Hide the floating "jetzt" text when it would collide with a node row;
    // the dot on the rail still marks the current time.
    val showNowLabel = showNow &&
        nodes.none { abs(it.second - nowY) < with(density) { 22.dp.toPx() } }

    Box(modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { boxTop = it.positionInWindow().y }) {

        // Rail, bands, progress fill, nodes and the "now" dot.
        Canvas(modifier = Modifier.matchParentSize()) {
            if (nodes.isEmpty()) return@Canvas
            val x = railX.toPx()
            val top = nodes.first().second
            val bottom = nodes.last().second
            drawLine(track, Offset(x, top), Offset(x, bottom), 4.dp.toPx(), StrokeCap.Round)
            bands.forEach { band ->
                drawLine(
                    band.color.copy(alpha = if (band.strong) 1f else 0.7f),
                    Offset(x, mapY(band.start)),
                    Offset(x, mapY(band.end)),
                    (if (band.strong) 11.dp else 8.dp).toPx(),
                    StrokeCap.Round,
                )
            }
            if (showNow) {
                drawLine(primary, Offset(x, top), Offset(x, nowY), 4.dp.toPx(), StrokeCap.Round)
            }
            nodes.forEach { (_, y, sel) ->
                if (sel) {
                    drawCircle(primary, 7.dp.toPx(), Offset(x, y))
                } else {
                    drawCircle(nodeFill, 5.dp.toPx(), Offset(x, y))
                    drawCircle(primary, 5.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                }
            }
            if (showNow) drawCircle(primary, 5.dp.toPx(), Offset(x, nowY))
        }

        // Floating "now" label, anchored to the live dot on the rail.
        if (showNowLabel) {
            Text(
                text = "jetzt ${now.format(HM)}",
                style = MaterialTheme.typography.labelMedium,
                color = primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset { IntOffset(contentStartPx.roundToInt(), (nowY - with(density) { 9.dp.toPx() }).roundToInt()) },
            )
        }

        // The rows themselves, spaced proportionally to the real time gaps.
        Column(modifier = Modifier.fillMaxWidth().padding(start = contentStart, end = 8.dp)) {
            var prev: ZonedDateTime? = null
            visible.forEach { block ->
                // Gaps grow with the real time distance, but with diminishing
                // returns (sqrt) and a cap, so long stretches stay compact while
                // the order "more time = more space" is preserved.
                val gapDp = prev?.let {
                    val min = Duration.between(it, block.sortAt).toMinutes().coerceAtLeast(0)
                    (12.0 + 3.2 * sqrt(min.toDouble())).dp.coerceIn(24.dp, 84.dp)
                } ?: 4.dp
                Spacer(Modifier.height(gapDp))
                prev = block.sortAt

                when (block) {
                    is PrayerBlock -> {
                        val isSelected = active != null && block.prayer == active.first
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
                        // Lane: current = filled green, next = outlined, else plain.
                        val laneShape = RoundedCornerShape(16.dp)
                        var lane = Modifier.fillMaxWidth()
                        if (isSelected) lane = lane.background(MaterialTheme.colorScheme.primaryContainer, laneShape)
                        if (isNext) lane = lane.border(1.5.dp, primary, laneShape)
                        lane = lane.padding(horizontal = 12.dp, vertical = 8.dp)

                        // The makruh chip sits immediately above/below the prayer
                        // pill (outside its background), attached to this prayer.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            mkVisible(block.before)?.let {
                                MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
                            }
                            Column(modifier = lane) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned {
                                            nodeCenters[block.prayer.name] = it.positionInWindow().y + it.size.height / 2f
                                        }
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
                                if (isNext) {
                                    Text(
                                        text = remainingText(Duration.between(now, block.time)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = primary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            mkVisible(block.after)?.let {
                                MakruhChipRow(it, amber, isMakruhNow(it), highlight && it.end.isBefore(now), onKaraha)
                            }
                        }
                    }
                    is NaflBlock -> {
                        val faded = highlight && block.end.isBefore(now)
                        val c = green.copy(alpha = if (faded) 0.5f else 1f)
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_nafl),
                                    contentDescription = null,
                                    tint = c,
                                    modifier = Modifier.size(14.dp),
                                )
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
                softWrap = false,
            )
        }
    }
}

private val KARAHA_SUNRISE = "İşrak (nach Sonnenaufgang)" to
    "Makruh-Zeit vom Sonnenaufgang, bis die Sonne ~eine Speerlänge gestiegen ist (≈45 Min). In dieser Zeit kein (freiwilliges) Gebet; danach beginnen İşrak/Duha. (Hanafi)"
private val KARAHA_ZEVAL = "Zeval / İstiva (Zenit)" to
    "Makruh-Zeit kurz vor dem Höchststand der Sonne bis Dhuhr (≈20 Min). Während die Sonne im Zenit steht, wird nicht gebetet. (Hanafi)"
private val KARAHA_ISFIRAR = "İsfirar-ı şems (vor Sonnenuntergang)" to
    "Makruh-Zeit, wenn die Sonne vergilbt (≈40 Min vor Sonnenuntergang) bis Maghrib. Nur die heutige Asr darf hier noch (verspätet) gebetet werden. (Hanafi)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSettings(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var city by remember(settings.city) { mutableStateOf(settings.city) }
    var lat by remember(settings.latitude) { mutableStateOf(settings.latitude.toString()) }
    var lng by remember(settings.longitude) { mutableStateOf(settings.longitude.toString()) }
    var countdown by remember(settings.showCountdown) { mutableStateOf(settings.showCountdown) }
    var nafl by remember(settings.showNafl) { mutableStateOf(settings.showNafl) }
    var online by remember(settings.useOnline) { mutableStateOf(settings.useOnline) }
    var karaha by remember(settings.showKaraha) { mutableStateOf(settings.showKaraha) }
    var contrast by remember(settings.highContrast) { mutableStateOf(settings.highContrast) }
    var fontScale by remember(settings.fontScale) { mutableStateOf(settings.fontScale) }
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
        ToggleRow("Freiwillige Gebete anzeigen", nafl) { nafl = it }
        ToggleRow("Restzeit im Widget", countdown) { countdown = it }
        if (OfficialTimesProvider.isOnline) {
            ToggleRow("Offizielle Diyanet-Zeiten (online)", online) { online = it }
        }

        FontSizeSelector(fontScale) { fontScale = it }
        ToggleRow("Hoher Kontrast", contrast) { contrast = it }

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
                        showNafl = nafl,
                        useOnline = online,
                        showKaraha = karaha,
                        highContrast = contrast,
                        fontScale = fontScale,
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
