package de.gebetszeiten.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.monthTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val DATE_WEIGHT = 1.5f
/** Prayer names for the accessibility (TalkBack) row description. */
private val ROW_PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

private data class MonatRow(val date: LocalDate, val times: List<String>)

@Composable
internal fun MonatScreen(inner: PaddingValues, settings: AppSettings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var month by rememberSaveable { mutableStateOf(YearMonth.now(zone)) }
    val today = LocalDate.now(zone)

    val rows by produceState<List<MonatRow>?>(null, month, settings) {
        value = null
        value = (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val t = PrayerProvider.daily(context, settings, date, zone)
            MonatRow(date, listOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha).map { it.format(HM_MONTH) })
        }
    }

    Column(modifier = Modifier.padding(inner).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.month_prev),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.month_next),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Tabellenkopf
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("", Modifier.weight(DATE_WEIGHT))
            val headers = listOf(
                stringResource(R.string.prayer_fajr),
                stringResource(R.string.prayer_dhuhr),
                stringResource(R.string.prayer_asr),
                stringResource(R.string.month_maghrib_short),
                stringResource(R.string.prayer_isha),
            )
            headers.forEach {
                Text(
                    it, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val list = rows
        if (list == null) {
            Text(stringResource(R.string.month_loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list) { row ->
                    val isToday = row.date == today
                    val bg = if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
                    // TalkBack: read the whole day as one item ("Mo 1.: Fajr …, Dhuhr …")
                    // instead of 6 disconnected, label-less cells.
                    val rowDesc = dateLabel(row.date) + ": " +
                        ROW_PRAYERS.zip(row.times).joinToString(", ") { "${it.first} ${it.second}" }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 7.dp)
                            .clearAndSetSemantics { contentDescription = rowDesc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dateLabel(row.date),
                            Modifier.weight(DATE_WEIGHT),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        row.times.forEach { time ->
                            Text(
                                time, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Mo 1." — Wochentag-Kürzel + Tag. */
private fun dateLabel(date: LocalDate): String {
    val wd = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN)
    return "$wd ${date.dayOfMonth}."
}
