package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.core.prayertimes.officialtimes.SourceId
import de.gebetszeiten.core.prayertimes.officialtimes.SourceResult
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import de.gebetszeiten.net.fetchErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId

/**
 * Frischt den Cache fuer den gewaehlten Ort auf - wie
 * `PrayerProvider.refreshOfficial` der Handy-App, aber fuer genau einen Ort.
 * [force] (Knopf "Jetzt aktualisieren") ruft immer ab; sonst entscheidet
 * `chooseTarget`, ob der Ort faellig ist. Jeder Abruf endet mit einem
 * protokollierten Versuch: Erfolg (`null`) oder der Fehlertext.
 */
class Refresher(
    private val cache: DesktopCache,
    private val fetcher: OfficialTimesFetcher,
    private val clock: () -> Long,
) {

    suspend fun refresh(s: DesktopSettings, force: Boolean) {
        val active = s.coords ?: return
        val now = clock()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val (lat, lng) = chooseTarget(
            due = if (force) emptyList() else cache.dueOrder(active, today),
            activeCoords = active,
            force = force,
            today = today,
            nowEpochMs = now,
        ) ?: return

        try {
            withTimeout(TIMEOUT_MS) {
                val result = fetcher.fetch(lat, lng, s.placeName.orEmpty(), cache.cachedLocationId(lat, lng))
                if (result.schedule.isEmpty()) {
                    cache.recordAttempt(emptyResultError(fetchErrorSummary(result.candidates)), now, lat, lng)
                } else {
                    cache.putAll(result.schedule, lat, lng, result.locationId, result.verification)
                    // Ein gelungener Abruf ist kein Fehler, auch wenn eine
                    // der Quellen gescheitert ist.
                    cache.recordAttempt(null, now, lat, lng)
                }
            }
        } catch (e: TimeoutCancellationException) {
            cache.recordAttempt("Zeitüberschreitung beim Abruf", now, lat, lng)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cache.recordAttempt(fetchErrorText(e), now, lat, lng)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 25_000L
    }
}

/** Was schiefging, Quelle fuer Quelle ("Direktabruf: HTTP 503 · Proxy: ...").
 *  Gleicher Text wie `fetchErrorSummary` der Handy-App; `null`, wenn keine
 *  Quelle scheiterte. */
internal fun fetchErrorSummary(candidates: List<SourceResult>): String? =
    candidates.filter { it.schedule.isEmpty() && it.error != null }
        .sortedBy { it.source.ordinal }
        .joinToString(" · ") { "${sourceLabel(it.source)}: ${it.error}" }
        .takeIf { it.isNotEmpty() }

internal fun emptyResultError(errorSummary: String?): String =
    errorSummary ?: "Keine amtlichen Zeiten erhalten (Standort oder Netz)"

private fun sourceLabel(source: SourceId): String = when (source) {
    SourceId.DIRECT -> "Direktabruf"
    SourceId.PROXY_ABDUS -> "Proxy"
    SourceId.EZANVAKTI -> "ezanvakti"
}
