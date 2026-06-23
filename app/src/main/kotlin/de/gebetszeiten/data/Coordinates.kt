package de.gebetszeiten.data

/**
 * Validierung der manuell eingegebenen Koordinaten. Verhindert, dass ein
 * Tippfehler oder vertauschte Breite/Länge stillschweigend in die
 * Zeitberechnung läuft (Breite −90..90, Länge −180..180).
 */
object Coordinates {

    /** True, wenn der Text KEINE gültige Breite ist (unparsbar oder außerhalb −90..90). */
    fun latError(text: String): Boolean = !inRange(text, -90.0, 90.0)

    /** True, wenn der Text KEINE gültige Länge ist (unparsbar oder außerhalb −180..180). */
    fun lngError(text: String): Boolean = !inRange(text, -180.0, 180.0)

    /** True, wenn Breite und Länge beide gültig sind. */
    fun bothValid(lat: String, lng: String): Boolean = !latError(lat) && !lngError(lng)

    private fun inRange(text: String, min: Double, max: Double): Boolean {
        val d = text.trim().toDoubleOrNull() ?: return false
        return d in min..max
    }
}
