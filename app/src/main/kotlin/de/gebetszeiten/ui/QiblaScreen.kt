package de.gebetszeiten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.prayer.QiblaMath
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun QiblaScreen(inner: PaddingValues, settings: AppSettings) {
    val bearing = QiblaMath.bearing(settings.latitude, settings.longitude)
    val distance = QiblaMath.distanceKm(settings.latitude, settings.longitude)
    val cardinal = QiblaMath.cardinal(bearing)
    val km = String.format(Locale.GERMAN, "%,d", distance.roundToInt())

    Column(
        modifier = Modifier.padding(inner).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${bearing.roundToInt()}° · $cardinal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Kaaba · $km km",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
