package de.gebetszeiten.core.prayertimes

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Correctness audit: runs the engine across a latitude × date matrix and records
 * crashes, ordering violations, and out-of-range times. The engine is otherwise
 * only calibrated/tested against Nürnberg (49.45°).
 */
class EdgeCaseAuditTest {

    private val hm = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val utc = ZoneId.of("UTC")

    private data class Place(val name: String, val lat: Double, val lng: Double)

    private val places = listOf(
        Place("Equator", 0.0, 0.0),
        Place("Mecca", 21.4225, 39.8262),
        Place("Sydney(S)", -33.8688, 151.2093),
        Place("PuntaArenas(S)", -53.1638, -70.9171),
        Place("Nurnberg", 49.4521, 11.0767),
        Place("Oslo", 59.9139, 10.7522),
        Place("Reykjavik", 64.1466, -21.9426),
        Place("Tromso", 69.6492, 18.9560),
        Place("Svalbard", 78.2232, 15.6267),
    )

    private val dates = listOf(
        LocalDate.of(2026, 3, 20),  // equinox
        LocalDate.of(2026, 6, 21),  // N summer solstice
        LocalDate.of(2026, 12, 21), // N winter solstice
    )

    @Test fun auditMatrix() {
        val findings = StringBuilder()
        for (p in places) for (d in dates) {
            val t = try {
                DiyanetPrayerTimesCalculator.calculate(GeoLocation(p.lat, p.lng), d, utc)
            } catch (e: Throwable) {
                findings.append("\nCRASH  ${p.name.padEnd(14)} $d : ${e.javaClass.simpleName}")
                null
            } ?: continue

            val ordered = t.ordered()
            for (i in 1 until ordered.size) {
                if (!ordered[i].second.isAfter(ordered[i - 1].second)) {
                    findings.append(
                        "\nORDER  ${p.name.padEnd(14)} $d : ${ordered[i - 1].first}=${ordered[i - 1].second.format(hm)}" +
                            " >= ${ordered[i].first}=${ordered[i].second.format(hm)}",
                    )
                }
            }
            t.ordered().forEach { (prayer, time) ->
                val daysOff = kotlin.math.abs(time.toLocalDate().toEpochDay() - d.toEpochDay())
                if (daysOff > 1) findings.append("\nRANGE  ${p.name.padEnd(14)} $d : $prayer=$time (off ${daysOff}d)")
            }
        }
        // Regression guard: no crash, no ordering/range violation anywhere in
        // the matrix. On a regression the message prints the full finding list.
        assertTrue("AUDIT FINDINGS:$findings\n---", findings.isEmpty())
    }
}
