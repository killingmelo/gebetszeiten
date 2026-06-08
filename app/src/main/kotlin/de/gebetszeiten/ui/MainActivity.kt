package de.gebetszeiten.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private data class Makruh(
    val label: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val explain: Pair<String, String>,
)

/** A prayer together with the makruh segment that belongs to it. */
private data class PrayerBlock(
    val prayer: Prayer,
    val time: ZonedDateTime,
    val before: Makruh?,
    val after: Makruh?,
) : DayBlock {
    override val sortAt: ZonedDateTime get() = before?.start ?: time
}

private data class NaflBlock(
    val label: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) : DayBlock {
    override val sortAt: ZonedDateTime get() = start
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

    // Each prayer + its makruh segment form one block (zenith/İsfirar above the
    // prayer, sunrise below). Nafl windows are their own blocks. Blocks run
    // chronologically with a clear gap between them.
    val blocks = remember(info, showNafl, showKaraha) {
        val k = info.karaha
        val n = info.nafl
        buildList<DayBlock> {
            info.times.ordered().forEach { (p, t) ->
                val before = if (!showKaraha) null else when (p) {
                    Prayer.DHUHR -> Makruh("Makruh · Zenit", k.zevalStart, k.zevalEnd, KARAHA_ZEVAL)
                    Prayer.MAGHRIB -> Makruh("Makruh · vor Sonnenuntergang", k.isfirarStart, k.isfirarEnd, KARAHA_ISFIRAR)
                    else -> null
                }
                val after = if (showKaraha && p == Prayer.SUNRISE) {
                    Makruh("Makruh · nach Sonnenaufgang", k.sunriseStart, k.sunriseEnd, KARAHA_SUNRISE)
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
        }.sortedBy { it.sortAt }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        // "Running" calendar: the currently active prayer (last one whose time
        // has passed) is selected; earlier blocks are collapsed by default.
        val active: Pair<Prayer, ZonedDateTime>? =
            if (highlight) info.times.ordered().lastOrNull { !it.second.isAfter(now) } else null
        val nextEntry: Pair<Prayer, ZonedDateTime>? =
            if (highlight) info.times.ordered().firstOrNull { it.second.isAfter(now) } else null
        val primary = MaterialTheme.colorScheme.primary
        val activeIndex =
            if (active != null) blocks.indexOfFirst { it is PrayerBlock && it.prayer == active.first } else -1
        var showPast by remember { mutableStateOf(false) }
        val visible = if (activeIndex > 0 && !showPast) blocks.drop(activeIndex) else blocks

        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (activeIndex > 0) {
                Text(
                    text = if (showPast) "Frühere ausblenden" else "Frühere Zeiten anzeigen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPast = !showPast }
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            visible.forEach { block ->
                when (block) {
                    is PrayerBlock -> {
                        val isSelected = active != null && block.prayer == active.first
                        val isPast = active != null && block.time.isBefore(active.second)
                        val foreground = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        val name = context.getString(block.prayer.labelRes())
                        val status = when {
                            isSelected -> ", aktuelles Gebet"
                            isPast -> ", vergangen"
                            else -> ""
                        }
                        // Hide makruh segments that are already over (unless showing past).
                        fun visibleMakruh(m: Makruh?) =
                            m?.takeIf { !highlight || showPast || it.end.isAfter(now) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            visibleMakruh(block.before)?.let { MakruhCaption(it, amber, highlight && it.end.isBefore(now), onKaraha) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                            // Progress of the current period toward the next prayer.
                            if (isSelected && nextEntry != null && nextEntry.second.isAfter(block.time)) {
                                val total = Duration.between(block.time, nextEntry.second).seconds.coerceAtLeast(1)
                                val elapsed = Duration.between(block.time, now).seconds.coerceIn(0, total)
                                val fraction = elapsed.toFloat() / total
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                                        .height(6.dp)
                                        .background(primary.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                        .clearAndSetSemantics {},
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(6.dp)
                                            .background(primary, RoundedCornerShape(3.dp)),
                                    )
                                }
                            }
                            if (nextEntry?.first == block.prayer) {
                                Text(
                                    text = remainingText(Duration.between(now, block.time)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                                )
                            }
                            visibleMakruh(block.after)?.let { MakruhCaption(it, amber, highlight && it.end.isBefore(now), onKaraha) }
                        }
                    }
                    is NaflBlock -> {
                        val faded = highlight && block.end.isBefore(now)
                        val c = green.copy(alpha = if (faded) 0.5f else 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .padding(horizontal = 28.dp, vertical = 2.dp)
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

@Composable
private fun MakruhCaption(m: Makruh, amber: Color, faded: Boolean, onKaraha: (Pair<String, String>) -> Unit) {
    val c = amber.copy(alpha = if (faded) 0.5f else 1f)
    val clean = m.label.removePrefix("Makruh · ")
    val desc = "Makruh-Zeit $clean, ${m.start.format(HM)} bis ${m.end.format(HM)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(onClickLabel = "Erklärung anzeigen") { onKaraha(m.explain) }
            .padding(horizontal = 28.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_makruh),
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(m.label, style = MaterialTheme.typography.labelMedium, color = c)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${m.start.format(HM)}–${m.end.format(HM)}",
            style = MaterialTheme.typography.labelMedium,
            color = c,
            softWrap = false,
        )
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
