package de.gebetszeiten.ui

// Das Einstellungs-Sheet: Ortswahl, Anzeige, Erinnerungen. Aus MainActivity
// herausgelöst (dort waren es 1.623 Zeilen), weil die Ortswahl mit den
// Quellen-Badges weiter wächst.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.Cities
import de.gebetszeiten.data.City
import de.gebetszeiten.data.withRecentPlace
import de.gebetszeiten.official.OfficialTimesProvider
import de.gebetszeiten.places.PlaceSearchProvider
import de.gebetszeiten.prayer.TimesSourceBadge
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.prayer.timesSourceBadge
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.util.Locale

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            },
        )
    }
}

/** Stadtname mit fett hervorgehobenem, übereinstimmendem Wortanfang. */
private fun highlightPrefix(name: String, query: String): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    if (q.isNotEmpty() && name.startsWith(q, ignoreCase = true)) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.take(q.length)) }
        append(name.substring(q.length))
    } else {
        append(name)
    }
}

/** Lokalisierter Ländername statt kryptischem ISO-Code („DE" → „Deutschland"). */
private fun countryDisplayName(code: String): String = runCatching {
    Locale.Builder().setRegion(code).build().displayCountry
}.getOrDefault("").ifBlank { code }

/** Chips für zuletzt gewählte Orte — nur sichtbar, solange das Suchfeld leer ist. */
@Composable
private fun RecentPlacesRow(places: List<City>, current: String, onPick: (City) -> Unit) {
    Text(
        stringResource(R.string.city_recent),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        places.forEach { c ->
            FilterChip(
                selected = c.name == current,
                onClick = { onPick(c) },
                label = { Text(c.name) },
            )
        }
    }
}

/**
 * Breiten-/Längenfelder mit Validierung und optionalem Anwenden-Knopf.
 * Fehlerprüfung passiert lokal (aus [lat]/[lng] ableitbar) — nur die
 * geänderten Werte und die fertige Anwenden-Aktion müssen von außen kommen.
 */
@Composable
private fun ManualCoordinatesFields(
    lat: String,
    onLatChange: (String) -> Unit,
    lng: String,
    onLngChange: (String) -> Unit,
    showApply: Boolean,
    onApply: () -> Unit,
) {
    val latErr = de.gebetszeiten.data.Coordinates.latError(lat)
    val lngErr = de.gebetszeiten.data.Coordinates.lngError(lng)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = lat, onValueChange = onLatChange, label = { Text(stringResource(R.string.settings_latitude)) }, isError = latErr, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        OutlinedTextField(value = lng, onValueChange = onLngChange, label = { Text(stringResource(R.string.settings_longitude)) }, isError = lngErr, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
    }
    if (latErr || lngErr) {
        Text(
            stringResource(R.string.settings_coordinate_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (showApply) {
        Button(
            onClick = onApply,
            enabled = !latErr && !lngErr,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_apply_location))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationSettings(settings: AppSettings, onApply: (AppSettings) -> Unit) {
    // Location is the only draft state (typing half a coordinate must not
    // trigger a reschedule) — everything else applies instantly via commit().
    // Das Stadt-Feld ist ein reines Suchfeld: Entwurf startet leer, der
    // aktuelle Ort steht als Placeholder. Den alten Namen beim Fokus zu
    // leeren/markieren scheitert am Echo der startenden IME-Session —
    // ein leeres Feld hat dieses Race gar nicht erst.
    var city by rememberSaveable(settings.city, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var lat by rememberSaveable(settings.latitude) { mutableStateOf(settings.latitude.toString()) }
    var lng by rememberSaveable(settings.longitude) { mutableStateOf(settings.longitude.toString()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // Online-Fallback (nur online-Flavor + Online-Schalter): greift erst,
    // wenn die gebündelte Liste keinen Treffer hat.
    var onlineMatches by remember { mutableStateOf<List<City>>(emptyList()) }
    var searchingOnline by remember { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Named "commit" (not "apply") to avoid clashing with Kotlin's stdlib apply.
    val commit: (AppSettings.() -> AppSettings) -> Unit = { change -> onApply(settings.change()) }
    val locationDirty = (city.text.isNotBlank() && city.text != settings.city) ||
        lat.toDoubleOrNull() != settings.latitude || lng.toDoubleOrNull() != settings.longitude

    // Autofokus aufs leere Suchfeld beim Öffnen — die Tastatur soll sofort
    // stehen, ohne erst antippen zu müssen. Anders als das Race oben (das
    // beim *Ändern des Textwerts* auf Fokus hin auftritt) wird hier nur der
    // Fokus angefordert, kein Text gesetzt — trotzdem eine kurze Verzögerung,
    // damit die IME-Startsequenz des Sheets sicher abgeklungen ist, bevor wir
    // requestFocus() aufrufen (Fallback laut Spezifikation; ungetestet ohne
    // Gerät/Emulator in dieser Umgebung).
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    // Die Städteliste einmalig vorwärmen — sonst hängt die allererste
    // Suche still an der TSV-Parse-Latenz (33k Zeilen). Der Diyanet-Index
    // (online-Flavor) ebenso, sonst hängt die erste Badge-Berechnung.
    LaunchedEffect(Unit) {
        Cities.preload(context)
        de.gebetszeiten.official.DiyanetPlaceIndex.preload(context)
    }

    LaunchedEffect(city.text, expanded) {
        if (expanded && city.text.isNotBlank()) {
            // Tipp-Debounce: die Coroutine wird bei jedem Tastendruck neu
            // gestartet — das delay macht daraus ein gratis Debouncing.
            delay(200)
            searching = true
            matches = Cities.search(context, city.text, limit = 12)
            searching = false
            onlineMatches = emptyList()
            val lookup = PlaceSearchProvider.lookup()
            if (matches.isEmpty() && lookup != null && settings.useOnline && city.text.trim().length >= 3) {
                searchingOnline = true
                delay(300)
                onlineMatches = lookup.search(city.text, limit = 10)
                searchingOnline = false
            } else {
                searchingOnline = false
            }
        } else {
            matches = emptyList()
            onlineMatches = emptyList()
            searching = false
            searchingOnline = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // Ohne imePadding reicht der Scroll-Viewport hinter die Tastatur —
            // Inhalt unterhalb der Tastaturkante wäre unerreichbar.
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_instant_apply),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Sektions-Titel nennt den aktiven Ort — das Stadt-Feld selbst ist ein
        // leeres Suchfeld und zeigt unfokussiert keinen Wert an.
        SettingsSection("${stringResource(R.string.location_title)} · ${settings.city}") {
            // Inline-Vorschläge statt ExposedDropdownMenu: dessen Popup-Fenster
            // liegt unter dem IME-Fenster, die Tastatur verdeckt daher die
            // Liste. Der Sheet-Inhalt weicht der Tastatur aus — die Liste
            // im Sheet bleibt damit immer sichtbar.
            OutlinedTextField(
                value = city,
                onValueChange = { city = it; expanded = true },
                label = { Text(stringResource(R.string.settings_city)) },
                placeholder = { Text(settings.city) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    Row {
                        if (city.text.isNotEmpty()) {
                            IconButton(onClick = { city = TextFieldValue(""); expanded = true }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.city_clear))
                            }
                        }
                        val toggleLabel = stringResource(R.string.city_suggestions_toggle)
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.semantics { contentDescription = toggleLabel },
                        ) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { expanded = it.isFocused },
            )
            if (city.text.isBlank() && settings.recentPlaces.isNotEmpty()) {
                RecentPlacesRow(settings.recentPlaces, settings.city) { c ->
                    commit {
                        copy(
                            city = c.name,
                            latitude = c.latitude,
                            longitude = c.longitude,
                            recentPlaces = withRecentPlace(recentPlaces, c),
                        )
                    }
                }
            }
            if (expanded && (searching || searchingOnline)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (searchingOnline) {
                    Text(
                        stringResource(R.string.city_searching_online),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Lokale Treffer haben Vorrang; Online-Treffer erscheinen nur,
            // wenn die gebündelte Liste leer ausgeht (mit Quellen-Hinweis).
            val shownMatches = matches.ifEmpty { onlineMatches }
            // Quelle pro Treffer: beide Indizes sind vorgewärmt, das läuft
            // ohne Netz und ohne merkbare Verzögerung.
            val badges by produceState(emptyMap<String, TimesSourceBadge>(), shownMatches, settings.useCalculated) {
                value = shownMatches.associate { c ->
                    val key = "${c.name}|${c.latitude}|${c.longitude}"
                    val bundled = de.gebetszeiten.official.BundledOfficialSource
                        .nearestLocation(context, c.latitude, c.longitude)?.name
                    val place = de.gebetszeiten.official.DiyanetPlaceIndex
                        .nearest(context, c.latitude, c.longitude)
                    key to timesSourceBadge(
                        bundledName = bundled,
                        officialPlace = place,
                        distanceKm = place?.let {
                            de.gebetszeiten.official.DiyanetPlaceIndex.distanceKm(it, c.latitude, c.longitude)
                        },
                        useCalculated = settings.useCalculated,
                    )
                }
            }
            if (expanded && !searching && !searchingOnline && shownMatches.isNotEmpty()) {
                if (matches.isEmpty()) {
                    Text(
                        stringResource(R.string.city_results_online),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Column {
                        shownMatches.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(highlightPrefix(c.name, city.text))
                                        Text(
                                            // Region unterscheidet gleichnamige Kleinorte
                                            // („Esenköy — Yalova · Türkei" vs. „… Aydın · Türkei").
                                            listOfNotNull(c.region, countryDisplayName(c.country)).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val badge = badges["${c.name}|${c.latitude}|${c.longitude}"]
                                        if (badge != null) {
                                            Text(
                                                when (badge) {
                                                    is TimesSourceBadge.Bundled ->
                                                        stringResource(R.string.badge_bundled, badge.locationName)
                                                    is TimesSourceBadge.Official ->
                                                        stringResource(R.string.badge_official, badge.locationName, badge.distanceKm)
                                                    TimesSourceBadge.Calculated ->
                                                        stringResource(R.string.badge_calculated)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (badge is TimesSourceBadge.Calculated) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    city = TextFieldValue(c.name)
                                    lat = c.latitude.toString(); lng = c.longitude.toString()
                                    expanded = false
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    // Picked from the list = complete data → applies directly.
                                    commit {
                                        copy(
                                            city = c.name,
                                            latitude = c.latitude,
                                            longitude = c.longitude,
                                            recentPlaces = withRecentPlace(recentPlaces, c),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (expanded && !searching && !searchingOnline && matches.isEmpty() && onlineMatches.isEmpty() && city.text.isNotBlank()) {
                Text(
                    stringResource(R.string.city_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Koordinatenfelder sind der Ausnahmefall (Suche/Chips reichen
            // sonst) — hinter einem Aufklapper, statt immer sichtbar zu sein.
            TextButton(onClick = { manual = !manual }) {
                Text(stringResource(if (manual) R.string.coords_hide else R.string.coords_show))
            }
            if (manual) {
                ManualCoordinatesFields(
                    lat = lat,
                    onLatChange = { lat = it },
                    lng = lng,
                    onLngChange = { lng = it },
                    showApply = locationDirty,
                    onApply = {
                        val parsedLat = lat.toDoubleOrNull() ?: settings.latitude
                        val parsedLng = lng.toDoubleOrNull() ?: settings.longitude
                        // Leeres Suchfeld = Name unverändert (nur Koordinaten angepasst).
                        commit { copy(city = city.text.ifBlank { settings.city }, latitude = parsedLat, longitude = parsedLng) }
                    },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_display)) {
            ToggleRow(stringResource(R.string.settings_show_makruh), settings.showKaraha) { commit { copy(showKaraha = it) } }
            if (settings.showKaraha) {
                Text(
                    stringResource(R.string.settings_makruh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToggleRow(stringResource(R.string.settings_show_cemaat), settings.showCemaat) { commit { copy(showCemaat = it) } }
            if (settings.showCemaat) {
                Text(
                    stringResource(R.string.settings_cemaat_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.settings_cemaat_offset), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 20, 30, 45, 60).forEach { v ->
                        FilterChip(
                            selected = settings.cemaatOffsetMinutes == v,
                            onClick = { commit { copy(cemaatOffsetMinutes = v) } },
                            label = { Text(stringResource(R.string.cemaat_offset_chip, v)) },
                        )
                    }
                }
            }

            ToggleRow(stringResource(R.string.settings_use_calculated), settings.useCalculated) {
                commit { copy(useCalculated = it) }
            }
            Text(
                stringResource(R.string.settings_use_calculated_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.settings_remaining), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Text(
                stringResource(R.string.settings_remaining_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow(stringResource(R.string.settings_in_app), settings.showCountdown) { commit { copy(showCountdown = it) } }
            CountdownModeSelector(stringResource(R.string.settings_widget), settings.widgetCountdown) { commit { copy(widgetCountdown = it) } }
            CountdownModeSelector(stringResource(R.string.settings_lockscreen), settings.notificationCountdown) {
                commit { copy(notificationCountdown = it) }
            }
            if (!settings.persistentNotification) {
                Text(
                    stringResource(R.string.settings_lockscreen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.settings_wear_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (OfficialTimesProvider.isOnline) {
                ToggleRow(stringResource(R.string.settings_official_online), settings.useOnline) { commit { copy(useOnline = it) } }
            }

            FontSizeSelector(settings.fontScale) { commit { copy(fontScale = it) } }
            ToggleRow(stringResource(R.string.settings_high_contrast), settings.highContrast) { commit { copy(highContrast = it) } }
        }

        SettingsSection(stringResource(R.string.settings_section_reminders)) {
            Text(
                stringResource(R.string.settings_reminders_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { p ->
                ToggleRow(stringResource(p.labelRes()), p.name in settings.reminders) { on ->
                    commit { copy(reminders = if (on) reminders + p.name else reminders - p.name) }
                }
            }

            Text(stringResource(R.string.settings_style), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.reminder_style_silent) to AppSettings.STYLE_SILENT,
                    stringResource(R.string.reminder_style_vibrate) to AppSettings.STYLE_VIBRATE,
                    stringResource(R.string.reminder_style_sound) to AppSettings.STYLE_SOUND,
                ).forEach { (label, v) ->
                    FilterChip(
                        selected = settings.reminderStyle == v,
                        onClick = { commit { copy(reminderStyle = v) } },
                        label = { Text(label) },
                    )
                }
            }

            Text(stringResource(R.string.settings_lead), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_lead_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.lead_off) to 0, stringResource(R.string.lead_5) to 5, stringResource(R.string.lead_10) to 10, stringResource(R.string.lead_15) to 15, stringResource(R.string.lead_30) to 30).forEach { (label, v) ->
                    FilterChip(
                        selected = settings.reminderLeadMinutes == v,
                        onClick = { commit { copy(reminderLeadMinutes = v) } },
                        label = { Text(label) },
                    )
                }
            }

            ToggleRow(stringResource(R.string.settings_persistent), settings.persistentNotification) {
                commit { copy(persistentNotification = it) }
            }
            Text(
                stringResource(R.string.settings_persistent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.theme_system) to AppSettings.THEME_SYSTEM,
                    stringResource(R.string.theme_light) to AppSettings.THEME_LIGHT,
                    stringResource(R.string.theme_dark) to AppSettings.THEME_DARK,
                ).forEach { (label, v) ->
                    FilterChip(
                        selected = settings.themeMode == v,
                        onClick = { commit { copy(themeMode = v) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_amoled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.settings_hijri), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-2, -1, 0, 1, 2).forEach { v ->
                    FilterChip(
                        selected = settings.hijriOffsetDays == v,
                        onClick = { commit { copy(hijriOffsetDays = v) } },
                        label = { Text(if (v > 0) "+$v" else "$v") },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_hijri_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ToggleRow(stringResource(R.string.settings_widget_transparent), settings.widgetTransparent) {
                commit { copy(widgetTransparent = it) }
            }
        }

        BatteryOptimizationCard()
    }
}

/** Aus / Stufen / Genau chips for one remaining-time surface. */
@Composable
private fun CountdownModeSelector(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.countdown_off) to AppSettings.COUNTDOWN_OFF,
                stringResource(R.string.countdown_steps) to AppSettings.PRECISION_STEPS,
                stringResource(R.string.countdown_exact) to AppSettings.PRECISION_EXACT,
            ).forEach { (chip, v) ->
                FilterChip(
                    selected = value == v,
                    onClick = { onChange(v) },
                    label = { Text(chip) },
                )
            }
        }
    }
}

@Composable
private fun FontSizeSelector(value: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.font_size), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.font_normal) to 1f, stringResource(R.string.font_large) to 1.2f, stringResource(R.string.font_xlarge) to 1.4f).forEach { (label, v) ->
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
    val cardLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // Re-check once per resume (returning from the system dialog) instead
        // of polling on a timer.
        cardLifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            exempt = context.getSystemService(android.os.PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }
    }
    if (exempt) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.battery_body),
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
                Text(stringResource(R.string.battery_disable))
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
