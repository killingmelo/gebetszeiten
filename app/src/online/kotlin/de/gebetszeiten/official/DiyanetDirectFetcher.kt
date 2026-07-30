package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Lädt die amtliche Jahresseite direkt von namazvakitleri.diyanet.gov.tr
 * (~390 KB, deckt das ganze Jahr ab — daher großzügiger readTimeout).
 * Wirft bei Netz-/Formatfehlern; der Composite fällt dann auf den Proxy.
 */
class DiyanetDirectFetcher {
    suspend fun fetchYear(locationId: Int): Map<LocalDate, SixTimes> =
        withContext(Dispatchers.IO) {
            DiyanetYearPageParser.parse(
                httpGet(
                    "https://namazvakitleri.diyanet.gov.tr/tr-TR/$locationId",
                    accept = "text/html",
                    readTimeoutMs = 20_000,
                ),
            )
        }
}
