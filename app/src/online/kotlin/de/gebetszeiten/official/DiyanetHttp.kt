package de.gebetszeiten.official

import java.net.HttpURLConnection
import java.net.URL

/** Schlanker GET mit Timeouts — gemeinsame Basis für Direkt- und Proxy-Abruf. */
internal fun httpGet(
    urlString: String,
    accept: String = "application/json",
    readTimeoutMs: Int = 10_000,
): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = readTimeoutMs
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "GebetszeitenApp (amtliche Zeiten, ~1 Abruf/Jahr)")
    }
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
