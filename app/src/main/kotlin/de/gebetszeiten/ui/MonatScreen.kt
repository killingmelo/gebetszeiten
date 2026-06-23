package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.gebetszeiten.R
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.monthTitle
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun MonatScreen(inner: PaddingValues, settings: AppSettings) {
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now(zone)) }

    Column(modifier = Modifier.padding(inner).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_left), "Vorheriger Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(painterResource(R.drawable.ic_chevron_right), "Nächster Monat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Tabelle folgt in Task 2.
        Text(
            "…",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
