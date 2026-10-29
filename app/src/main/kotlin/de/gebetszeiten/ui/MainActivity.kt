package de.gebetszeiten.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gebetszeiten.core.prayertimes.officialtimes.stampMatches
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.City
import de.gebetszeiten.data.Favorite
import de.gebetszeiten.data.recentPlaceLabel
import de.gebetszeiten.prayer.Section
import de.gebetszeiten.R
import de.gebetszeiten.ui.theme.GebetszeitenTheme
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.verstehen.VerstehenScreen

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
                    // Das Tor. Solange die Ersteinrichtung nicht beantwortet
                    // ist, gibt es keine Hauptansicht — und weil bis dahin
                    // nichts gespeichert wird, verhaelt sich die App im
                    // Hintergrund weiter genau wie vorher.
                    if (settings.onboardingDone) {
                        MainScreen(viewModel)
                    } else {
                        Onboarding(settings) { viewModel.completeOnboarding(it) }
                    }
                }
            }
        }
        viewModel.ensureScheduled()
    }
}

/**
 * Der Anfrager fuer die Benachrichtigungs-Erlaubnis — als Rueckruf, nicht als
 * Selbstlaeufer.
 *
 * Hier stand einmal ein `LaunchedEffect(Unit) { launcher.launch(...) }`: der
 * Systemdialog kam ab Android 13 unvermittelt beim allerersten Zeichnen, ohne
 * ein Wort dazu, warum die App ihn braucht. Der Ergebnis-Rueckruf war leer.
 * Wer ablehnte, bekam von da an schweigend nichts — keine Gebets-Meldung,
 * keine Dauerzeile —, und die App sagte darueber nie etwas. Wer spaeter in
 * den Systemeinstellungen zustimmte, sah bis zum naechsten Gebets-Wecker
 * trotzdem nichts, weil `ensureScheduled()` nur in `onCreate` laeuft.
 *
 * Beides ist jetzt anders: gefragt wird an der Stelle, an der erklaert wurde
 * wofuer (Ersteinrichtung, Hinweisstreifen), und nach einer Zusage wird
 * sofort neu geplant, statt auf das naechste Gebet zu warten.
 */
@Composable
internal fun rememberNotificationPermissionRequest(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onResult() }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Vor Android 13 gibt es nichts zu erlauben; der Aufrufer soll
            // trotzdem erfahren, dass der Vorgang durch ist.
            onResult()
        }
    }
}

private enum class Tab { HEUTE, MONAT, QIBLA, VERSTEHEN }

/**
 * Das Favoriten-Menü der Kopfzeile. Der Knopf in `actions` und (nur im Tab
 * HEUTE) der Ortsname im Titel öffnen DASSELBE Menü — geteilt wird nur der
 * Zustand `expanded`, es gibt keine zweite Umsetzung.
 *
 * Der aktive Eintrag wird über [stampMatches] erkannt, nicht über `==`: das
 * ist dieselbe Identität, mit der `Favorites.kt` und der Zeiten-Cache arbeiten
 * (~1 km Toleranz).
 */
@Composable
private fun FavoritesMenu(
    favorites: List<Favorite>,
    currentLat: Double,
    currentLng: Double,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (City) -> Unit,
) {
    val cities = favorites.map { it.city }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        cities.forEach { c ->
            val active = stampMatches(c.latitude, c.longitude, currentLat, currentLng)
            DropdownMenuItem(
                text = {
                    Text(
                        // Gleiche Beschriftung wie die Chips im
                        // Einstellungsblatt — dieselbe Funktion, keine zweite.
                        recentPlaceLabel(c, cities),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                // Gefüllter Stern = das ist der Ort, der gerade gilt. Der
                // Zustand steht schon fett im Text, deshalb keine zweite
                // Vorlesung für die Sprachausgabe.
                leadingIcon = {
                    Icon(
                        painterResource(
                            if (active) R.drawable.ic_star else R.drawable.ic_star_outline,
                        ),
                        contentDescription = null,
                    )
                },
                onClick = { onPick(c) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: PrayerViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val tokens = LocalTokens.current
    var tab by rememberSaveable { mutableStateOf(Tab.HEUTE) }
    var showSettings by remember { mutableStateOf(false) }
    var favoritesMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Der Abschnitt, zu dem „Zeiten verstehen“ springen soll (aus „Heute“
    // angetippt); VerstehenScreen gibt ihn nach dem Sprung zurück.
    var explain by remember { mutableStateOf<Section?>(null) }
    // Ein Knopf, der ein leeres Menü öffnet, ist eine Sackgasse — ohne
    // Favoriten gibt es also weder Knopf noch antippbaren Titel.
    val hasFavorites = settings.favorites.isNotEmpty()
    // Verschwindet der letzte Favorit, während das Menü offen ist, bleibt
    // `favoritesMenuOpen` sonst auf true stehen — und der nächste angelegte
    // Favorit ließe das Menü unaufgefordert aufklappen.
    LaunchedEffect(hasFavorites) { if (!hasFavorites) favoritesMenuOpen = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Schmal und ohne eigene Fläche: die Kopfzeile trägt nur Ort und Symbole.
            TopAppBar(
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.canvas,
                    scrolledContainerColor = tokens.canvas,
                    titleContentColor = tokens.ink,
                    actionIconContentColor = tokens.stone,
                ),
                title = {
                    val title = when (tab) {
                        Tab.HEUTE -> settings.city
                        Tab.MONAT -> stringResource(R.string.tab_month)
                        Tab.QIBLA -> stringResource(R.string.tab_qibla)
                        Tab.VERSTEHEN -> stringResource(R.string.verstehen_title)
                    }
                    // Nur im Tab HEUTE IST der Titel der Ortsname — dort ist
                    // er die naheliegendste Stelle für den Umschalter. In
                    // MONAT und QIBLA steht dort der Tab-Name; ein Titel, der
                    // auf einem Tab ein Knopf ist und auf zwei anderen nicht,
                    // wäre schlechter zu bedienen als der klare Knopf rechts.
                    if (tab == Tab.HEUTE && hasFavorites) {
                        // onClickLabel, damit die Sprachausgabe sagt, WAS der
                        // Tipp auf den Ortsnamen bewirkt; Role.Button, damit
                        // sie den Titel überhaupt als Knopf ansagt. Die
                        // Trefferfläche wird auf das 48-dp-Mindestziel
                        // gebracht — der Text allein ist nur ~20 dp hoch.
                        val switchLabel = stringResource(R.string.favorites_switch)
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { role = Role.Button }
                                .clickable(onClickLabel = switchLabel) {
                                    favoritesMenuOpen = true
                                },
                        )
                    } else {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    if (hasFavorites) {
                        // Box als Anker: das Menü hängt am Knopf, egal welcher
                        // der beiden Wege es geöffnet hat.
                        Box {
                            IconButton(onClick = { favoritesMenuOpen = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_star),
                                    contentDescription = stringResource(R.string.favorites_switch),
                                )
                            }
                            FavoritesMenu(
                                favorites = settings.favorites,
                                currentLat = settings.latitude,
                                currentLng = settings.longitude,
                                expanded = favoritesMenuOpen,
                                onDismiss = { favoritesMenuOpen = false },
                                onPick = { c ->
                                    favoritesMenuOpen = false
                                    // Kompletter Umzug über denselben Weg, den
                                    // auch das Einstellungsblatt nimmt (commit
                                    // → onApply → viewModel.save): reschedule()
                                    // zieht Benachrichtigung, Wecker, Widget
                                    // und Qibla mit. recentPlaces bleibt
                                    // unberührt — ein Favoritenwechsel ist
                                    // keine Suche.
                                    viewModel.save(
                                        settings.copy(
                                            city = c.name,
                                            latitude = c.latitude,
                                            longitude = c.longitude,
                                            // Region zieht mit — sonst verlöre
                                            // der Ort sie beim Speichern und
                                            // zwei „Esenköy" wären beim
                                            // nächsten Stern nicht mehr
                                            // unterscheidbar.
                                            region = c.region,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.settings_icon_desc))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = tokens.canvas) {
                listOf(
                    Triple(Tab.HEUTE, R.drawable.ic_today, R.string.tab_today),
                    Triple(Tab.MONAT, R.drawable.ic_month, R.string.tab_month),
                    Triple(Tab.QIBLA, R.drawable.ic_qibla, R.string.tab_qibla),
                    Triple(Tab.VERSTEHEN, R.drawable.ic_verstehen, R.string.tab_verstehen),
                ).forEach { (target, icon, label) ->
                    NavigationBarItem(
                        selected = tab == target,
                        onClick = { tab = target },
                        icon = { Icon(painterResource(icon), contentDescription = null) },
                        label = { Text(stringResource(label), maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = tokens.accent,
                            selectedTextColor = tokens.accent,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = tokens.stone,
                            unselectedTextColor = tokens.stone,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        when (tab) {
            Tab.HEUTE -> HeuteScreen(
                inner,
                settings,
                snackbarHostState,
                // Dieselbe Aktion wie "Jetzt aktualisieren" im Einstellungsblatt
                // (SourceStatusSection) - kein zweiter Abruf-Mechanismus.
                onFetchNow = { viewModel.refreshOfficialNow() },
                onEnableCalculation = {
                    // Der Notausgang: springt nur ein, wenn fuer diesen Tag
                    // keine amtlichen Zeiten vorliegen (sonst waere NoTimesCard
                    // hier gar nicht sichtbar - siehe dayInfo unten).
                    viewModel.save(settings.copy(calculationFillsGaps = true))
                },
                // Nach einer erteilten Erlaubnis sofort neu planen: sonst
                // erschiene die Dauerzeile erst beim naechsten Gebets-Wecker.
                onNotificationsGranted = { viewModel.ensureScheduled() },
                // Nebenzeile angetippt: Abschnitt merken, dann der Reiter.
                onExplain = { explain = it; tab = Tab.VERSTEHEN },
            )
            Tab.MONAT -> MonatScreen(inner, settings)
            Tab.QIBLA -> QiblaScreen(inner, settings)
            Tab.VERSTEHEN -> VerstehenScreen(
                inner,
                settings,
                focus = explain,
                onFocusConsumed = { explain = null },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            // Voll geöffnet: halb expandiert bleibt mit offener Tastatur
            // kaum Platz für die Ortsvorschläge.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // Grund (canvas) statt der Material-Flächenstufe: nachts schwarz
            // wie die übrigen Ansichten, tags Creme.
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            val officialRefreshes by viewModel.officialRefreshes.collectAsState()
            LocationSettings(
                settings = settings,
                onApply = { viewModel.save(it) },
                onRefresh = { viewModel.refreshOfficialNow() },
                refreshTick = officialRefreshes,
            )
        }
    }
}
