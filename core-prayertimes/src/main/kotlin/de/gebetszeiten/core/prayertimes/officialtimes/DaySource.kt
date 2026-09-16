package de.gebetszeiten.core.prayertimes.officialtimes

/** Woher die Zeiten eines Tages stammen duerfen. */
enum class DaySource { ONLINE_CACHE, BUNDLED_TABLE, CALCULATION }

/**
 * Welche Quellen fuer einen Tag befragt werden duerfen, in welcher
 * Reihenfolge. Amtliches steht immer vorn; die Berechnung steht ueberhaupt
 * nur in der Liste, wenn der Nutzer sie als Notausgang eingeschaltet hat.
 *
 * Bis 14.09.2026 stand diese Regel im Rumpf von `PrayerProvider.daily` und
 * war damit ungetestet - und sie war falsch: die Berechnung sprang ein, ohne
 * gefragt worden zu sein.
 */
fun daySourceOrder(useOnline: Boolean, calculationFillsGaps: Boolean): List<DaySource> =
    buildList {
        if (useOnline) add(DaySource.ONLINE_CACHE)
        add(DaySource.BUNDLED_TABLE)
        if (calculationFillsGaps) add(DaySource.CALCULATION)
    }
