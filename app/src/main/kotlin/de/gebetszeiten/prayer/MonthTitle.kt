package de.gebetszeiten.prayer

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Deutscher Monatsname + Jahr, z. B. "Juni 2026". */
fun monthTitle(ym: YearMonth): String =
    "${ym.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${ym.year}"
