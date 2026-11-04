package de.gebetszeiten.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** „Jetzt abrufen“ meldet sein Ergebnis und zeigt, dass es arbeitet. */
class FetchOutcomeTest {

    @Test fun `mit Zeiten heisst geladen, mit Ortsname`() {
        assertEquals(FetchOutcome.Loaded("İstanbul"), fetchOutcome(true, "İstanbul", lastError = "alt"))
    }

    @Test fun `ohne Zeiten heisst fehlgeschlagen, mit Grund falls bekannt`() {
        assertEquals(FetchOutcome.Failed("Zeitüberschreitung beim Abruf"), fetchOutcome(false, "X", "Zeitüberschreitung beim Abruf"))
        assertEquals(FetchOutcome.Failed(null), fetchOutcome(false, "X", null))
        assertEquals(FetchOutcome.Failed(null), fetchOutcome(false, "X", "   "))
    }

    private fun quelle(pfad: String) = File(pfad).readText()

    @Test fun `Abruf ist verdrahtet - Laden, Ergebnis, sofortiges Neulesen`() {
        val vm = quelle("src/main/kotlin/de/gebetszeiten/ui/PrayerViewModel.kt")
        assertTrue(vm.contains("_refreshing.value = true"))
        assertTrue(vm.contains("_fetchOutcomes.emit(outcomeFor(value))"))
        val main = quelle("src/main/kotlin/de/gebetszeiten/ui/MainActivity.kt")
        assertTrue(main.contains("viewModel.fetchOutcomes.collect"))
        assertTrue(main.contains("refreshing = refreshing"))
        val heute = quelle("src/main/kotlin/de/gebetszeiten/ui/HeuteScreen.kt")
        assertTrue(heute.contains("produceState<DayInfo?>(null, settings, tick, selectedDate, refreshTick)"))
        assertTrue(heute.contains("enabled = !refreshing"))
        assertTrue(heute.contains("R.string.fetch_running"))
    }
}
