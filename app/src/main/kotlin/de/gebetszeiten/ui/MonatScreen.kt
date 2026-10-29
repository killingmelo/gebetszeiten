package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.monthTitle
import de.gebetszeiten.ui.theme.LocalTokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val DATE_WEIGHT = 1.3f
/** Prayer names for the accessibility (TalkBack) row description. */
private val ROW_PRAYERS = listOf("Faǧr", "Šurūq", "Ẓuhr", "ʿAṣr", "Maġrib", "ʿIšāʾ")

/** Seitenrand der Tabelle; der Punkt für „heute“ sitzt darin. */
private val MONTH_GUTTER = 16.dp

/** Zeichen in einer Zelle ohne Zeit: ein ruhiger Punkt statt eines Strichs. */
private const val NO_TIME_MARK = "·"

/** [times] ist immer sechs Einträge lang (Faǧr, Šurūq, Ẓuhr, ʿAṣr, Maġrib, ʿIšāʾ), damit die Tabelle nicht aus der
 *  Spur läuft; `null` heißt: [PrayerProvider.daily] lieferte an diesem Tag
 *  keine Zeiten. */
private data class MonatRow(val date: LocalDate, val times: List<String?>) {
    val hasTimes: Boolean get() = times.any { it != null }
}

@Composable
internal fun MonatScreen(inner: PaddingValues, settings: AppSettings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val t = LocalTokens.current
    var month by rememberSaveable { mutableStateOf(YearMonth.now(zone)) }
    val today = LocalDate.now(zone)

    val rows by produceState<List<MonatRow>?>(null, month, settings) {
        value = null
        value = (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            // Keine Zeiten unter den aktuellen Einstellungen: die Zeitspalten
            // dieses Tages bleiben leer, statt einen Wert zu erfinden.
            val times = PrayerProvider.daily(context, settings, date, zone)
                ?.let { listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha).map { time -> time.format(HM_MONTH) } }
                ?: List(6) { null }
            MonatRow(date, times)
        }
    }
    val list = rows
    val fontScale = LocalDensity.current.fontScale

    Column(modifier = Modifier.padding(inner).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.month_prev), tint = t.stone)
            }
            Text(
                monthTitle(month),
                style = MaterialTheme.typography.headlineSmall,
                color = t.ink,
                modifier = Modifier.semantics { heading() },
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.month_next), tint = t.stone)
            }
        }

        // EINE Zeile für den ganzen Monat, sobald mindestens ein Tag ohne
        // Zeiten dabei ist, nicht je Zeile wiederholt.
        // monthNoTimesNotice (nicht noTimesNotice.headline): ein EINZELNER
        // leerer Tag heißt nicht „keine amtlichen Zeiten für diesen Ort“,
        // nur nicht für DIESEN Tag.
        val emptyDays = list?.count { !it.hasTimes } ?: 0
        if (list != null && emptyDays > 0) {
            Text(
                de.gebetszeiten.prayer.monthNoTimesNotice(
                    city = settings.city,
                    // Dieselbe Funktion wie canFetch (SettingsSheet.kt) und auf
                    // „Heute“; im Offline-Flavor ist sie invariant false.
                    onlineEnabled = settings.canFetchOfficial(),
                    emptyDays = emptyDays,
                    totalDays = list.size,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = MONTH_GUTTER, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.error,
            )
        }

        // Tabellenkopf: Großbuchstaben wie die Abschnittsköpfe auf „Heute“.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MONTH_GUTTER)
                .hairlineBelow(t.hairline)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Text("", Modifier.weight(DATE_WEIGHT))
            val headers = listOf(
                stringResource(R.string.prayer_fajr),
                stringResource(R.string.moment_shuruq),
                stringResource(R.string.prayer_dhuhr),
                stringResource(R.string.prayer_asr),
                stringResource(R.string.prayer_maghrib),
                stringResource(R.string.prayer_isha),
            )
            headers.forEach {
                Text(
                    it.uppercase(Locale.GERMAN), Modifier.weight(1f),
                    // Enge Sperrung und kleinere Schrift: sechs Spalten teilen sich
                    // die Breite. Bei großer Systemschrift darf der Kopf umbrechen
                    // statt abgeschnitten zu werden.
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = cappedSp(10, HEADER_SCALE_CAP, fontScale),
                        letterSpacing = 0.2.sp,
                    ),
                    color = t.stone,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (list == null) {
            Text(
                stringResource(R.string.month_loading),
                Modifier.padding(horizontal = MONTH_GUTTER, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.stone,
            )
        } else {
            // Im laufenden Monat beim heutigen Tag öffnen (zwei Zeilen
            // Vorlauf), sonst lag „heute“ ab Monatsmitte unter dem Rand.
            val listState = rememberLazyListState()
            LaunchedEffect(list, month) {
                if (month == YearMonth.from(today)) {
                    listState.scrollToItem((today.dayOfMonth - 3).coerceAtLeast(0))
                } else {
                    listState.scrollToItem(0)
                }
            }
            val noTime = stringResource(R.string.month_no_time)
            val accent = t.accent
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(list) { row ->
                    val isToday = row.date == today
                    val weight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                    // TalkBack: der ganze Tag als EIN Eintrag („Mo 1.: Faǧr …, Ẓuhr …“)
                    // statt sechs Zellen ohne Beschriftung; fehlende Zeiten heißen „keine Zeit“.
                    val rowDesc = dateLabel(row.date) + ": " +
                        ROW_PRAYERS.zip(row.times).joinToString(", ") { "${it.first} ${it.second ?: noTime}" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MONTH_GUTTER)
                            .heightIn(min = 40.dp)
                            .hairlineBelow(t.hairline)
                            // Punkt im Seitenrand wie auf „Heute“, damit die Spalten bündig bleiben.
                            .drawBehind {
                                if (isToday) {
                                    drawCircle(accent, radius = 3.dp.toPx(), center = Offset(-8.dp.toPx(), size.height / 2))
                                }
                            }
                            .clearAndSetSemantics { contentDescription = rowDesc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dateLabel(row.date),
                            Modifier.weight(DATE_WEIGHT),
                            // Gedeckelt wie die Zeitspalten: bei 1,4 brach „Mi 28.“
                            // sonst in zwei Zeilen um.
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = cappedSp(14, TIME_SCALE_CAP, fontScale),
                            ),
                            fontWeight = weight,
                            color = t.ink,
                            maxLines = 1,
                            softWrap = false,
                        )
                        row.times.forEach { time ->
                            Text(
                                time ?: NO_TIME_MARK,
                                Modifier.weight(1f),
                                // bodySmall: sechs Zeitspalten auf 360 dp; bei großer
                                // Schrift lieber umbrechen als abschneiden.
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFeatureSettings = "tnum",
                                    fontSize = cappedSp(12, TIME_SCALE_CAP, fontScale),
                                ),
                                fontWeight = weight,
                                color = if (time == null) t.stone else t.ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Grenzen, bis zu denen die Systemschrift in den Zeitspalten und im Datum links
 *  mitwächst; darüber bräche eine Zeit mitten in den Ziffern um und das Datum in
 *  zwei Zeilen. */
private const val TIME_SCALE_CAP = 1.2f
private const val HEADER_SCALE_CAP = 1.1f

/** [baseSp] Sp, aber so, dass die Systemschrift höchstens bis [cap] wirkt. */
private fun cappedSp(baseSp: Int, cap: Float, fontScale: Float) =
    (baseSp * minOf(fontScale, cap) / fontScale).sp

/** „Mo 1.“: Wochentag-Kürzel und Tag. */
private fun dateLabel(date: LocalDate): String {
    val wd = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN)
    return "$wd ${date.dayOfMonth}."
}
