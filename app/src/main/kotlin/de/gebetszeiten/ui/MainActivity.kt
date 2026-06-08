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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
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
import de.gebetszeiten.prayer.NextPrayer
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.ui.theme.GebetszeitenTheme
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
)

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GebetszeitenTheme {
                NotificationPermissionRequester()
                PrayerScreen(viewModel)
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
    val next by produceState<NextPrayer?>(null, settings, tick, selectedDate) {
        value = if (selectedDate == LocalDate.now(zone)) {
            PrayerProvider.next(context, settings, zone, ZonedDateTime.now(zone))
        } else {
            null
        }
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

            if (isToday) next?.let { NextPrayerHero(it, zone) }

            dayInfo?.let { info ->
                TimesCard(
                    info = info,
                    next = if (isToday) next else null,
                    now = ZonedDateTime.now(zone),
                    highlight = isToday,
                    showNafl = settings.showNafl,
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
        IconButton(onClick = onPrev) {
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
        }
        IconButton(onClick = onNext) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun NextPrayerHero(next: NextPrayer, zone: ZoneId) {
    val context = LocalContext.current
    val remaining = Duration.between(ZonedDateTime.now(zone), next.time)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Nächstes Gebet",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(context.getString(next.prayer.labelRes()), style = MaterialTheme.typography.headlineMedium)
                Text(next.time.format(HM), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = remainingText(remaining),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
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
    next: NextPrayer?,
    now: ZonedDateTime,
    highlight: Boolean,
    showNafl: Boolean,
    onKaraha: (Pair<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val amber = if (dark) Color(0xFFE6B055) else Color(0xFFB07514)
    val green = if (dark) Color(0xFF8BD6BB) else Color(0xFF2E7D32)

    // Each prayer + its makruh segment form one block (zenith/İsfirar above the
    // prayer, sunrise below). Nafl windows are their own blocks. Blocks run
    // chronologically with a clear gap between them.
    val blocks = remember(info, showNafl) {
        val k = info.karaha
        val n = info.nafl
        buildList<DayBlock> {
            info.times.ordered().forEach { (p, t) ->
                val before = when (p) {
                    Prayer.DHUHR -> Makruh("Makruh · Zenit", k.zevalStart, k.zevalEnd, KARAHA_ZEVAL)
                    Prayer.MAGHRIB -> Makruh("Makruh · vor Sonnenuntergang", k.isfirarStart, k.isfirarEnd, KARAHA_ISFIRAR)
                    else -> null
                }
                val after = if (p == Prayer.SUNRISE) {
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
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is PrayerBlock -> {
                        val isNext = highlight && next != null && block.prayer == next.prayer && !block.time.isBefore(now)
                        val passed = highlight && block.time.isBefore(now) && !isNext
                        val foreground = when {
                            isNext -> MaterialTheme.colorScheme.onPrimaryContainer
                            passed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val background = if (isNext) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val weight = if (isNext) FontWeight.Bold else FontWeight.Normal
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            block.before?.let { MakruhCaption(it, amber, highlight && it.end.isBefore(now), onKaraha) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(context.getString(block.prayer.labelRes()), style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = weight)
                                Text(block.time.format(HM), style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = weight)
                            }
                            block.after?.let { MakruhCaption(it, amber, highlight && it.end.isBefore(now), onKaraha) }
                        }
                    }
                    is NaflBlock -> {
                        val faded = highlight && block.end.isBefore(now)
                        val c = green.copy(alpha = if (faded) 0.5f else 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✦ ${block.label}", style = MaterialTheme.typography.labelMedium, color = c)
                            Text("${block.start.format(HM)}–${block.end.format(HM)}", style = MaterialTheme.typography.labelMedium, color = c)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onKaraha(m.explain) }
            .padding(horizontal = 28.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠ ${m.label}", style = MaterialTheme.typography.labelMedium, color = c)
        Text("${m.start.format(HM)}–${m.end.format(HM)}", style = MaterialTheme.typography.labelMedium, color = c)
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
    var expanded by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<City>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(city, expanded) {
        matches = if (expanded) Cities.search(context, city, limit = 12) else emptyList()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
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

        ToggleRow("Restzeit im Widget", countdown) { countdown = it }
        ToggleRow("Freiwillige Gebete anzeigen", nafl) { nafl = it }
        if (OfficialTimesProvider.isOnline) {
            ToggleRow("Offizielle Diyanet-Zeiten (online)", online) { online = it }
        }

        Button(
            onClick = {
                val parsedLat = lat.toDoubleOrNull() ?: settings.latitude
                val parsedLng = lng.toDoubleOrNull() ?: settings.longitude
                onSave(settings.copy(latitude = parsedLat, longitude = parsedLng, city = city.ifBlank { "—" }, showCountdown = countdown, showNafl = nafl, useOnline = online))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Speichern")
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
