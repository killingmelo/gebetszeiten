package de.gebetszeiten.prayer

import androidx.annotation.StringRes
import de.gebetszeiten.R

/** Footer-Label: amtliche vs. berechnete Quelle des angezeigten Tages. */
@StringRes
fun dataCreditRes(official: Boolean): Int =
    if (official) R.string.data_credit_official else R.string.data_credit_calculated
