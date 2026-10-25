package de.gebetszeiten.net

import java.net.HttpURLConnection
import java.net.URL

/**
 * Schlanker GET mit Timeouts — gemeinsame Basis fuer Direkt- und Proxy-Abruf
 * UND, seit Fix-Runde 1 zu Aufgabe 2, fuer [de.gebetszeiten.places.OpenMeteoGeocoder]
 * (App-seitig, online-Flavor): der haengt ueber `onlineImplementation` ohnehin
 * an diesem Modul, eine zweite Kopie waere nur auseinandergelaufen (Beleg:
 * die beiden Fassungen unterschieden sich bereits im User-Agent). Deshalb
 * NICHT mehr `internal`, und [userAgent] ist deshalb parametrisiert statt
 * fest verdrahtet.
 */
fun httpGet(
    urlString: String,
    accept: String = "application/json",
    readTimeoutMs: Int = 10_000,
    userAgent: String = "GebetszeitenApp (amtliche Zeiten, ~1 Abruf/Jahr)",
): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = readTimeoutMs
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", userAgent)
    }
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
