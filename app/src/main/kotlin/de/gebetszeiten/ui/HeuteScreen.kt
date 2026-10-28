package de.gebetszeiten.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import de.gebetszeiten.prayer.heroState
import de.gebetszeiten.prayer.hijriText
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.prayer.noTimesNotice
import de.gebetszeiten.prayer.timeRange
import de.gebetszeiten.ui.theme.LocalTokens
import de.gebetszeiten.ui.theme.Tokens
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Seitenrand für Text und Liste. */
private val GUTTER = 24.dp

/** Das Foto sitzt etwas breiter als der Text (wie im Entwurf `fotos-oura.html`). */
private const val PHOTO_INSET_DP = 16
private val PHOTO_INSET = PHOTO_INSET_DP.dp

/** Nebenzeilen stehen um so viel eingerückt unter ihrem Gebet. */
private val SIDE_INDENT = 16.dp

/** Unter dieser Bildschirmhöhe (dp) wird das Foto flacher, damit Titel, „noch …“,
 *  „dann …“ und die Makrūh-Zeile ohne Scrollen sichtbar bleiben (360×640). */
private const val COMPACT_HEIGHT_DP = 720

/**
 * Feste Fotohöhe in dp auf kleinen Bildschirmen, sonst null (dann 10:7 wie im
 * Entwurf). 16:9 allein reichte auf 360×640 nicht: die Makrūh-Zeile lag noch
 * knapp unter dem Rand. Deshalb höchstens ein Viertel der Bildschirmhöhe.
 */
internal fun compactPhotoHeightDp(screenWidthDp: Int, screenHeightDp: Int): Int? {
    if (screenHeightDp >= COMPACT_HEIGHT_DP) return null
    val sixteenByNine = (screenWidthDp - 2 * PHOTO_INSET_DP) * 9 / 16
    return minOf(sixteenByNine, screenHeightDp / 4)
}

/** Abstand zwischen Bildunterschrift und Titel in dp. */
internal fun heroGapDp(screenHeightDp: Int): Int = if (screenHeightDp < COMPACT_HEIGHT_DP) 12 else 20

/**
 * Der gewählte Tag nach einem Tageswechsel: war „heute“ gewählt, rückt er auf
 * den neuen Tag mit; ein anderer Tag, den der Nutzer gerade ansieht, bleibt.
 */
internal fun rolledSelectedDate(selected: LocalDate, previousToday: LocalDate, today: LocalDate): LocalDate =
    if (selected == previousToday) today else selected

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
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val photoHeightDp = compactPhotoHeightDp(configuration.screenWidthDp, screenHeightDp)

    val dayInfo by produceState<DayInfo?>(null, settings, tick, selectedDate) {
        // Keine Zeiten unter den aktuellen Einstellungen: `dayInfo` bleibt
        // null und die Ansicht zeigt NoTimesCard, statt Fenster aus erfundenen
        // Zeiten zu berechnen. Fehlt nur `nextFajr`, kostet das allein das,
        // was den Folgetag braucht, nicht die ganze Ansicht.
        val times = PrayerProvider.daily(context, settings, selectedDate, zone)
        val nextFajr = PrayerProvider.daily(context, settings, selectedDate.plusDays(1), zone)?.fajr
        value = if (times != null) {
            DayInfo(times, IslamicWindows.karaha(times), IslamicWindows.nafl(times, nextFajr), nextFajr)
        } else {
            null
        }
    }
    val officialName by produceState<String?>(null, settings, selectedDate, tick) {
        // Reihenfolge spiegelt resolveLocationIdChain (Bundle vor Index), die
        // Kette, die entscheidet, WELCHER Diyanet-Standort geholt wird. Das
        // Bundle schreibt den Ort richtig („Nürnberg“ statt „NURNBERG“).
        // Ob dieser Tag amtlich versorgt ist, entscheidet sich nur am
        // Bundle-/Cache-Treffer, nicht an der Einstellung des Notausgangs.
        value = de.gebetszeiten.official.BundledOfficialSource
            .locationNameFor(context, settings.latitude, settings.longitude, selectedDate)
            ?: officialCacheName(context, settings, selectedDate)
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

    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
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
        if (info != null) {
            val hero = heroState(info.times, info.karaha, info.nafl, info.nextFajr, now, isToday)
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
            Photo(hero, photoHeightDp, t)
            // An anderen Tagen kein Titel und keine Restzeit: nur Foto und Liste.
            if (isToday) {
                Spacer(Modifier.height(heroGapDp(screenHeightDp).dp))
                // „noch …“ steht hier immer; die Restzeit-Einstellung gilt nur
                // für Widget, Sperrbildschirm und Statusleiste.
                Hero(hero = hero, showKaraha = settings.showKaraha, t = t)
            }
            Spacer(Modifier.height(24.dp))
            DayList(rows, onExplain, t)
            Text(
                // Amtlich nennt die Stadt („… · Nürnberg“); der berechnete
                // String hat keinen Platzhalter, das Argument wird ignoriert.
                text = stringResource(dataCreditRes(officialName != null), officialName ?: settings.city),
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
                color = t.stone,
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

/** Foto des Abschnitts, 10:7 oder [heightDp] hoch (siehe [compactPhotoHeightDp]),
 *  Radius 24 dp, darunter die Bildunterschrift. */
@Composable
private fun Photo(hero: HeroState, heightDp: Int?, t: Tokens) {
    val key = hero.photo
    Image(
        painter = painterResource(photoRes(key)),
        contentDescription = stringResource(photoDescriptionRes(key)),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(horizontal = PHOTO_INSET)
            .fillMaxWidth()
            .then(if (heightDp != null) Modifier.height(heightDp.dp) else Modifier.aspectRatio(10f / 7f))
            .clip(RoundedCornerShape(24.dp)),
    )
    Text(
        text = stringResource(captionResFor(hero)),
        style = MaterialTheme.typography.bodyMedium,
        color = t.stone,
        modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 8.dp),
    )
}

@Composable
private fun Hero(hero: HeroState, showKaraha: Boolean, t: Tokens) {
    Column(Modifier.padding(horizontal = GUTTER)) {
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
                color = t.ink,
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

/** Der ganze Tag, Haarlinie unter jeder Zeile, keine Flächen. */
@Composable
private fun DayList(rows: List<DayRow>, onExplain: (Section) -> Unit, t: Tokens) {
    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
        rows.forEach { row ->
            when (row) {
                is DayRow.PrayerRow -> PrayerLine(row, t)
                is DayRow.SideRow -> SideLine(row, onExplain, t)
            }
        }
    }
}

private val TABULAR = "tnum"

@Composable
private fun PrayerLine(row: DayRow.PrayerRow, t: Tokens) {
    // `past` gilt auch für die laufende Zeile; verblasst wird nur, was vorbei ist.
    val faded = row.past && !row.current
    val color = if (faded) t.stone else t.ink
    val weight = if (row.current) FontWeight.SemiBold else FontWeight.Normal
    val name = if (row.prayer == Prayer.SUNRISE) {
        stringResource(R.string.moment_shuruq)
    } else {
        stringResource(row.prayer.labelRes())
    }
    val time = row.at.format(HM)
    val nextDay = stringResource(R.string.row_next_day)
    val status = prayerStatusRes(row.prayer, row.current, faded)?.let { stringResource(it) } ?: ""
    val description = if (row.nextDay) {
        stringResource(R.string.desc_prayer_next_day, name, time)
    } else {
        stringResource(R.string.desc_prayer, name, time, status)
    }
    val accent = t.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .hairlineBelow(t.hairline)
            // Punkt im Seitenrand, damit die Namen bündig bleiben.
            .drawBehind {
                if (row.current) {
                    drawCircle(accent, radius = 3.dp.toPx(), center = Offset(-12.dp.toPx(), size.height / 2))
                }
            }
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, color = color)
        if (row.nextDay) {
            Spacer(Modifier.width(8.dp))
            Text(nextDay, style = MaterialTheme.typography.bodyMedium, color = t.stone)
        }
        Spacer(Modifier.weight(1f))
        Text(
            time,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR),
            fontWeight = weight,
            color = color,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@StringRes
private fun sideLabelRes(kind: Section): Int = when (kind) {
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

private fun isMakruh(kind: Section) =
    kind == Section.SHURUQ_MAKRUH || kind == Section.ZUHR || kind == Section.ISFIRAR_MAKRUH

@Composable
private fun SideLine(row: DayRow.SideRow, onExplain: (Section) -> Unit, t: Tokens) {
    val label = stringResource(sideLabelRes(row.kind))
    val start = row.start.format(HM)
    val time = row.end?.let { timeRange(start, it.format(HM)) } ?: start
    val color = when {
        row.past -> t.stone
        isMakruh(row.kind) -> t.gold
        else -> t.ink
    }
    // Die Ǧamāʿa-Zeit ist abgeleitet (Sonnenaufgang minus Vorlauf), das sagt
    // TalkBack dazu, damit sie nicht als amtliche Zeit gilt.
    val derived = if (row.kind == Section.JAMAA) stringResource(R.string.status_jamaa_derived) else ""
    val status = derived + if (row.past) stringResource(R.string.status_past) else ""
    val description = stringResource(R.string.desc_prayer, label, time, status)
    val explainLabel = stringResource(R.string.show_explanation)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .hairlineBelow(t.hairline, inset = SIDE_INDENT)
            .clickable(role = Role.Button, onClickLabel = explainLabel) { onExplain(row.kind) }
            // Eine TalkBack-Einheit: Name, Zeit, Status, und die Aktion bleibt erhalten.
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = explainLabel) {
                    onExplain(row.kind)
                    true
                }
            }
            .padding(start = SIDE_INDENT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            time,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

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
    onEnableCalculation: () -> Unit,
    t: Tokens,
) {
    NoticeBlock(t) {
        Text(notice.headline, style = MaterialTheme.typography.titleMedium, color = t.ink)
        Text(notice.detail, style = MaterialTheme.typography.bodyMedium, color = t.stone)
        if (notice.showFetch) {
            Button(onClick = onFetchNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.no_times_fetch_now))
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
