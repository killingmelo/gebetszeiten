package de.gebetszeiten.wear

import android.content.Context
import de.gebetszeiten.core.prayertimes.DailyPrayerTimes
import de.gebetszeiten.core.prayertimes.DiyanetPrayerTimesCalculator
import de.gebetszeiten.core.prayertimes.GeoLocation
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.DaySource
import de.gebetszeiten.core.prayertimes.officialtimes.daySourceOrder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reine Verschmelzung von (ggf. fehlenden) heutigen/morgigen Zeiten zur
 * Liste der naechsten [count] Gebete nach [now] — herausgezogen aus
 * [WearPrayer.upcoming], damit die Leerfall-Faelle (ein oder beide Tage
 * `null`) OHNE Robolectric testbar sind (Praezedenz: [SyncDecision] im
 * selben Modul, ebenfalls eine reine Verschmelzungs-/Auswahlfunktion neben
 * einem Context-verdrahteten Aufrufer). Sunrise ist nur das Ende von Fajrs
 * Fenster, kein eigenes Gebet, und wird deshalb ausgefiltert.
 */
internal fun mergeUpcoming(
    today: DailyPrayerTimes?,
    tomorrow: DailyPrayerTimes?,
    now: ZonedDateTime,
    count: Int,
): List<Pair<Prayer, ZonedDateTime>> =
    ((today?.ordered() ?: emptyList()) + (tomorrow?.ordered() ?: emptyList()))
        .filter { it.first != Prayer.SUNRISE && it.second.isAfter(now) }
        .take(count)

/**
 * Shared prayer-time helpers for the watch. Prioritätskette wie am Phone,
 * ueber dieselbe [daySourceOrder] (Task 16, nicht mehr eine eigene Kette):
 * amtliche Zeiten (WearOfficialCache) → gebündelte Diyanet-Tabellen
 * (nearest ≤ 25 km) → Berechnung, und zwar NUR, wenn der Nutzer sie als
 * Notausgang eingeschaltet hat ([WearSettings.calculationFillsGaps]) —
 * sonst `null`, dieselbe Regel wie `PrayerProvider.daily` am Telefon: die
 * Uhr zeigt keine Zeit, fuer die sie nicht buergt.
 *
 * `useOnline` in [daySourceOrder] ist hier immer `true`: die Uhr hat keinen
 * eigenen "Online"-Schalter, [WearOfficialCache] wird in BEIDEN Flavors
 * abgefragt (der Sync vom Handy ist rein empfangend und laeuft auch im
 * offline-Flavor). Im ONLINE-Flavor traegt WearOfficialCache seit Aufgabe 6
 * zusaetzlich, was [refreshWearOfficial] selbst abgerufen hat — der Sync
 * bleibt zwar weiterhin empfangend, ist aber nicht mehr der einzige Weg, wie
 * amtliche Zeiten in den Cache kommen.
 */
object WearPrayer {

    suspend fun today(context: Context, location: GeoLocation, zone: ZoneId): DailyPrayerTimes? =
        daily(context, location, ZonedDateTime.now(zone).toLocalDate(), zone)

    /** Next prayer strictly after [now], rolling into tomorrow if needed, oder
     *  `null`, wenn unter den aktuellen Einstellungen fuer weder heute noch
     *  morgen amtliche Zeiten (oder der Notausgang) vorliegen. Sunrise is
     *  just the end of Fajr's window, not a prayer → skipped. */
    suspend fun next(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
    ): Pair<Prayer, ZonedDateTime>? = upcoming(context, location, zone, now, count = 1).firstOrNull()

    /** The next [count] prayers after [now], across the day boundary, skipping
     *  sunrise. Used to build a self-switching tile timeline for the whole day.
     *  Liefert eine leere Liste (statt zu werfen), wenn fuer heute UND morgen
     *  keine Zeiten vorliegen — der Leerfall, den alle drei Oberflaechen
     *  (App, Kachel, Komplikation) selbst behandeln. */
    suspend fun upcoming(
        context: Context,
        location: GeoLocation,
        zone: ZoneId,
        now: ZonedDateTime,
        count: Int,
    ): List<Pair<Prayer, ZonedDateTime>> {
        val today = daily(context, location, now.toLocalDate(), zone)
        val tomorrow = daily(context, location, now.toLocalDate().plusDays(1), zone)
        return mergeUpcoming(today, tomorrow, now, count)
    }

    /** Sync-Cache → amtliche Tabelle → Berechnung (nur als Notausgang), oder
     *  `null` — dieselbe [daySourceOrder] wie `PrayerProvider.daily` am
     *  Phone, statt einer eigenen, ungetesteten Kette. */
    private suspend fun daily(
        context: Context,
        location: GeoLocation,
        date: LocalDate,
        zone: ZoneId,
    ): DailyPrayerTimes? {
        val calculationFillsGaps = WearSettings.calculationFillsGaps(context)
        for (source in daySourceOrder(useOnline = true, calculationFillsGaps = calculationFillsGaps)) {
            when (source) {
                DaySource.ONLINE_CACHE ->
                    WearOfficialCache.get(context, date, location.latitude, location.longitude)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.BUNDLED_TABLE ->
                    WearOfficialSource.get(context, location.latitude, location.longitude, date)
                        ?.let { return it.toDaily(date, zone) }
                DaySource.CALCULATION ->
                    return DiyanetPrayerTimesCalculator.calculate(location, date, zone)
            }
        }
        return null
    }
}
