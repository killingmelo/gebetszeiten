package de.gebetszeiten.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicLightColorScheme
import de.gebetszeiten.data.Cities
import de.gebetszeiten.data.City
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.prayer.NextPrayer
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.labelRes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(LocalContext.current)
            } else {
                MaterialTheme.colorScheme
            }
            MaterialTheme(colorScheme = colors) {
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
    ) { /* result ignored; notifications are optional */ }
    // Must launch after composition completes, not during it.
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerScreen(viewModel: PrayerViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val zone = ZoneId.systemDefault()
    val today by produceState<DailyPrayerTimes?>(initialValue = null, settings) {
        value = PrayerProvider.daily(context, settings, LocalDate.now(zone), zone)
    }
    val next by produceState<NextPrayer?>(initialValue = null, settings) {
        value = PrayerProvider.next(context, settings, zone, ZonedDateTime.now(zone))
    }
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = settings.city) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            today?.ordered()?.forEach { (prayer, time) ->
                val isNext = prayer == next?.prayer && time == next?.time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = context.getString(prayer.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = time.format(timeFormat),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            LocationEditor(settings, onSave = viewModel::save)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationEditor(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var city by remember(settings.city) { mutableStateOf(settings.city) }
    var lat by remember(settings.latitude) { mutableStateOf(settings.latitude.toString()) }
    var lng by remember(settings.longitude) { mutableStateOf(settings.longitude.toString()) }
    var countdown by remember(settings.showCountdown) { mutableStateOf(settings.showCountdown) }
    var expanded by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<City>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(city, expanded) {
        matches = if (expanded) Cities.search(context, city, limit = 12) else emptyList()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Ort", style = MaterialTheme.typography.titleMedium)

            // Filterable city picker; selecting a city fills the coordinates.
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; expanded = true },
                    label = { Text("Stadt") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
                    matches.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} (${c.country})") },
                            onClick = {
                                city = c.name
                                lat = c.latitude.toString()
                                lng = c.longitude.toString()
                                expanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Breitengrad") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Längengrad") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Restzeit im Widget anzeigen", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = countdown, onCheckedChange = { countdown = it })
            }
            Button(
                onClick = {
                    val parsedLat = lat.toDoubleOrNull() ?: settings.latitude
                    val parsedLng = lng.toDoubleOrNull() ?: settings.longitude
                    onSave(AppSettings(parsedLat, parsedLng, city.ifBlank { "—" }, countdown))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }

            Text(
                text = context.getString(de.gebetszeiten.R.string.data_credit),
                style = MaterialTheme.typography.bodySmall,
            )

            if (de.gebetszeiten.official.OfficialTimesProvider.isOnline) {
                Text(
                    text = "Online-Variante: exakte offizielle Diyanet-Zeiten via abdus.dev-Proxy (benötigt Internet).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
