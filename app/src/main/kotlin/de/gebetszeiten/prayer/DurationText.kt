package de.gebetszeiten.prayer

/** "3 Std 26 Min" / "55 Min" — kompakte Dauer in Stunden/Minuten. */
fun durationLabel(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h Std $m Min"
        h > 0 -> "$h Std"
        else -> "$m Min"
    }
}
