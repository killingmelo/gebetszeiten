package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.data.Cities
import de.gebetszeiten.data.City
import de.gebetszeiten.places.PlaceSearchProvider
import de.gebetszeiten.prayer.TimesSourceBadge
import de.gebetszeiten.prayer.timesSourceBadge
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Die Ortssuche: Suchfeld, Vorschlagsliste, Quellen-Kennzeichnung.
 *
 * Herausgeloest aus `LocationSettings`, weil die Ersteinrichtung dieselbe
 * Suche braucht. Ein zweites Suchfeld waere eine zweite Wahrheit gewesen —
 * mit eigener Entprellung, eigener Online-Rueckfallregel und einem eigenen
 * Begriff davon, was „amtlich abgedeckt" heisst.
 *
 * Die Falle, die hier drinsteckt und beim Abschreiben verloren ginge: Die
 * Vorschlaege sind INLINE gerendert, nicht als `ExposedDropdownMenu`. Dessen
 * Popup-Fenster liegt unter dem IME-Fenster — die Tastatur verdeckt dann
 * genau die Liste, die man gerade lesen will. Beide Aufrufer (Blatt wie
 * Vollbild) weichen der Tastatur aus und halten die Liste damit sichtbar.
 *
 * [whenEmpty] fuellt die Luecke, solange nicht gesucht wird: das
 * Einstellungsblatt zeigt dort Favoriten und zuletzt gewaehlte Orte, die
 * Ersteinrichtung nichts. Der Aufrufer bekommt den Treffer ueber [onPick] und
 * entscheidet selbst, was er damit tut — sofort speichern (Blatt) oder in
 * einen Entwurf legen (Ersteinrichtung).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePicker(
    useOnline: Boolean,
    calculationFillsGaps: Boolean,
    currentCity: String,
    autoFocus: Boolean = true,
    whenEmpty: @Composable () -> Unit = {},
    onPick: (City) -> Unit,
) {
    // Das Feld ist ein reines SUCHfeld: Entwurf startet leer, der aktuelle
    // Ort steht als Platzhalter. Den alten Namen beim Fokus zu leeren oder zu
    // markieren scheitert am Echo der startenden IME-Sitzung — ein leeres
    // Feld hat dieses Rennen gar nicht erst.
    var query by rememberSaveable(currentCity, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // Online-Rueckfall (nur online-Flavor + Online-Schalter): greift erst,
    // wenn die gebuendelte Liste keinen Treffer hat.
    var onlineMatches by remember { mutableStateOf<List<City>>(emptyList()) }
    var searchingOnline by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Autofokus aufs leere Suchfeld — die Tastatur soll sofort stehen, ohne
    // erst antippen zu muessen. Die kurze Verzoegerung laesst die
    // IME-Startsequenz abklingen, bevor requestFocus() kommt.
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(150)
            focusRequester.requestFocus()
        }
    }

    // Die Staedteliste einmalig vorwaermen — sonst haengt die allererste
    // Suche still an der TSV-Parse-Latenz (33k Zeilen). Der Diyanet-Index
    // (online-Flavor) und die gebuendelte DE-Tabelle (im offline-Flavor die
    // EINZIGE Badge-Quelle) ebenso, sonst haengt die erste Badge-Berechnung.
    LaunchedEffect(Unit) {
        Cities.preload(context)
        de.gebetszeiten.official.DiyanetPlaceIndex.preload(context)
        de.gebetszeiten.official.BundledOfficialSource.preload(context)
    }

    LaunchedEffect(query.text, expanded) {
        if (expanded && query.text.isNotBlank()) {
            // Tipp-Entprellung: die Coroutine wird bei jedem Tastendruck neu
            // gestartet — das delay macht daraus ein gratis Debouncing.
            delay(200)
            searching = true
            matches = Cities.search(context, query.text, limit = 12)
            searching = false
            onlineMatches = emptyList()
            val lookup = PlaceSearchProvider.lookup()
            if (matches.isEmpty() && lookup != null && useOnline && query.text.trim().length >= 3) {
                searchingOnline = true
                delay(300)
                onlineMatches = lookup.search(query.text, limit = 10)
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

    OutlinedTextField(
        value = query,
        onValueChange = { query = it; expanded = true },
        label = { Text(stringResource(R.string.settings_city)) },
        placeholder = { Text(currentCity) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon = {
            Row {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue(""); expanded = true }) {
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

    if (query.text.isBlank()) whenEmpty()

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

    // Lokale Treffer haben Vorrang; Online-Treffer erscheinen nur, wenn die
    // gebuendelte Liste leer ausgeht (mit Quellen-Hinweis).
    val shownMatches = matches.ifEmpty { onlineMatches }
    // Quelle je Treffer: beide Indizes sind vorgewaermt, das laeuft ohne Netz
    // und ohne merkbare Verzoegerung.
    val badges by produceState(emptyMap<String, TimesSourceBadge>(), shownMatches, calculationFillsGaps) {
        // Bei Schluesselwechsel zuruecksetzen — sonst blitzen kurz die Badges
        // des vorherigen Zustands auf.
        value = emptyMap()
        value = shownMatches.associate { c ->
            val key = "${c.name}|${c.latitude}|${c.longitude}"
            // Datumsabhaengig pruefen (locationNameFor), nicht nur den
            // naechsten Standort (nearestLocation): die gebuendelten Tabellen
            // decken ein Fenster ab, nicht alle Zeiten — sonst waere jeder
            // deutsche Ort faelschlich „Amtlich".
            val bundled = de.gebetszeiten.official.BundledOfficialSource
                .locationNameFor(context, c.latitude, c.longitude, LocalDate.now())
            val place = de.gebetszeiten.official.DiyanetPlaceIndex
                .nearest(context, c.latitude, c.longitude)
            key to timesSourceBadge(
                bundledName = bundled,
                officialPlace = place,
                distanceKm = place?.let {
                    de.gebetszeiten.official.DiyanetPlaceIndex.distanceKm(it, c.latitude, c.longitude)
                },
                calculationFillsGaps = calculationFillsGaps,
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
                                Text(highlightPrefix(c.name, query.text))
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
                                            // Notausgang aus, keine amtliche Quelle fuer
                                            // diesen Ort — waehlt der Nutzer ihn, zeigt
                                            // die App fuer ihn gar keine Zeiten.
                                            TimesSourceBadge.None ->
                                                stringResource(R.string.badge_none)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (badge is TimesSourceBadge.Calculated || badge is TimesSourceBadge.None) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                            }
                        },
                        onClick = {
                            query = TextFieldValue(c.name)
                            expanded = false
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onPick(c)
                        },
                    )
                }
            }
        }
    }

    if (expanded && !searching && !searchingOnline && matches.isEmpty() &&
        onlineMatches.isEmpty() && query.text.isNotBlank()
    ) {
        Text(
            stringResource(R.string.city_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
