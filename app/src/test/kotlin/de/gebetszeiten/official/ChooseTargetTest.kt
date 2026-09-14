package de.gebetszeiten.official

import de.gebetszeiten.core.prayertimes.officialtimes.CacheEntry
import de.gebetszeiten.core.prayertimes.officialtimes.CacheHeader
import de.gebetszeiten.core.prayertimes.officialtimes.CacheStore
import de.gebetszeiten.core.prayertimes.officialtimes.RawEntry
import de.gebetszeiten.core.prayertimes.officialtimes.ScheduleText
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import de.gebetszeiten.core.prayertimes.officialtimes.chooseTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Die Zielauswahl als Ganzes: `CacheStore.dueOrder` + [chooseTarget], gegen
 * SIMULIERTE Ausloeser. Genau hier lag der Aushungerungs-Fehler, und genau
 * hier gab es vorher keine Testnaht — die Auswahl steckte in einer
 * `suspend fun` mit `Context`.
 *
 * Die Simulation spiegelt, was `OfficialTimesCache` am Speicher tut
 * (`recordAttempt`: vorhandenen Eintrag am Ort aendern, sonst einen leeren
 * anlegen; `putAll`: Eintrag mit Zeitplan ablegen, Protokoll uebernehmen) —
 * aber als reine Funktion auf `List<RawEntry>`, ohne DataStore und ohne
 * Robolectric.
 */
class ChooseTargetTest {

    private val today = LocalDate.of(2026, 9, 7)
    private val start = 1_786_000_000_000L
    private val fourHoursMs = 4 * 60 * 60 * 1000L
    private val fiveMinMs = 5 * 60 * 1000L

    /** Zehn Favoriten, ein Grad auseinander: jeder ein eigener Ort
     *  (`stampMatches` greift erst unter ~1 km). */
    private val favoriten = (0 until 10).map { 10.0 + it to 10.0 + it }

    /** Der aktive Ort, weit weg von allen Favoriten. */
    private val aktiv = 41.0 to 29.0

    // ---------- Der Fairness-Test ----------

    @Test
    fun `Szenario a - nach 20 Ausloesern war jeder der zehn Favoriten dran, nicht nur der erste`() {
        // Zehn Favoriten ohne Zeiten, Netz dauerhaft weg: JEDER Abruf
        // scheitert. Der aktive Ort ist hier ein eigener, reichlich versorgter
        // Ort — er soll nichts entscheiden, sonst truege Schluessel 3 (aktiv
        // vor Favorit) den Test und die Rotation bliebe ungeprueft.
        var entries = listOf(mitAbdeckung(aktiv, today.plusDays(300)))
        val gewaehlt = mutableListOf<Pair<Double, Double>>()

        for (ausloeser in 0 until 20) {
            val now = start + ausloeser * fourHoursMs
            val ziel = chooseTarget(
                due = CacheStore.dueOrder(entries, favoriten, aktiv, today),
                activeCoords = aktiv,
                force = false,
                today = today,
                nowEpochMs = now,
            )
            assertNotNull("bei Ausloeser $ausloeser braucht ein Ort einen Abruf", ziel)
            gewaehlt += ziel!!
            entries = fehlversuch(entries, ziel, now)
        }

        // Vor dem Fix frass der erste Favorit alle 20 Ausloeser: die
        // 30-Minuten-Sperre ist bei 4 h Abstand laengst abgelaufen, und ohne
        // `hopeless` und Rotation stand er dauerhaft auf Platz 1.
        for ((index, favorit) in favoriten.withIndex()) {
            assertEquals(
                "Favorit $index ($favorit) muss bei 20 Ausloesern zweimal dran gewesen sein",
                2,
                gewaehlt.count { it == favorit },
            )
        }
        assertTrue("der versorgte aktive Ort braucht keinen Abruf", gewaehlt.none { it == aktiv })
    }

    // ---------- Szenario (b) aus der Pruefung ----------

    @Test
    fun `Szenario b - ein kaputter Favorit hungert die neun knapp versorgten nicht aus`() {
        // Ein Favorit, den Diyanet nicht aufloest (jeder Abruf scheitert),
        // neun Favoriten mit 31 Tagen Abdeckung, dazu der aktive Ort mit 31
        // Tagen. Alles unter der 60-Tage-Schwelle, alle brauchen also etwas.
        val kaputt = favoriten[0]
        var entries = (favoriten.drop(1) + aktiv).map { mitAbdeckung(it, today.plusDays(31)) }
        val gewaehlt = mutableListOf<Pair<Double, Double>>()

        for (ausloeser in 0 until 12) {
            val now = start + ausloeser * fourHoursMs
            val ziel = chooseTarget(
                due = CacheStore.dueOrder(entries, favoriten, aktiv, today),
                activeCoords = aktiv,
                force = false,
                today = today,
                nowEpochMs = now,
            ) ?: break
            gewaehlt += ziel
            entries = if (ziel == kaputt) {
                fehlversuch(entries, ziel, now)
            } else {
                erfolg(entries, ziel, now, today.plusDays(400))
            }
        }

        // Der kaputte ist zuerst dran (keine Zeiten, noch kein Fehler), wird
        // dann hoffnungslos und sortiert nach hinten; danach der aktive Ort
        // (Bildschirm vor Favoriten) und die neun Favoriten in
        // Listenreihenfolge. Erst wenn niemand sonst etwas braucht, ist der
        // kaputte wieder dran.
        assertEquals(listOf(kaputt, aktiv) + favoriten.drop(1) + listOf(kaputt), gewaehlt)
        for (i in 1 until gewaehlt.size) {
            assertTrue(
                "der kaputte Favorit darf nicht zweimal in Folge gewaehlt werden (Position $i)",
                !(gewaehlt[i] == kaputt && gewaehlt[i - 1] == kaputt),
            )
        }
    }

    // ---------- Szenario (c) aus der Pruefung ----------

    @Test
    fun `Szenario c - der aktive Ort ohne Zeiten kommt vor einem Favoriten ohne Zeiten`() {
        val favorit = 10.0 to 10.0

        val ziel = chooseTarget(
            due = CacheStore.dueOrder(entries = emptyList(), pinnedCoords = listOf(favorit), activeCoords = aktiv, today = today),
            activeCoords = aktiv,
            force = false,
            today = today,
            nowEpochMs = start,
        )

        // Beide haben gar keine Zeiten und sind gleich dringend. Den
        // Bildschirm sieht der Nutzer aber JETZT an.
        assertEquals(aktiv, ziel)
    }

    // ---------- force ----------

    @Test
    fun `force waehlt den aktiven Ort, obwohl ein Favorit dringender ist`() {
        val favorit = 10.0 to 10.0
        val entries = listOf(mitAbdeckung(aktiv, today.plusDays(300)))
        val due = CacheStore.dueOrder(entries, listOf(favorit), aktiv, today)

        // Ohne force wuerde der Favorit ohne Zeiten gewinnen.
        assertEquals(favorit, chooseTarget(due, aktiv, force = false, today = today, nowEpochMs = start))
        assertEquals(aktiv, chooseTarget(due, aktiv, force = true, today = today, nowEpochMs = start))
    }

    @Test
    fun `force waehlt den aktiven Ort auch mitten in einer Sperrfrist`() {
        // Fehlversuch vor fuenf Minuten: die Sperre laeuft noch.
        val entries = listOf(
            nurVersuch(aktiv, nowEpochMs = start - fiveMinMs, error = "Kein Netz"),
        )
        val due = CacheStore.dueOrder(entries, emptyList(), aktiv, today)

        assertNull("die Sperrfrist muss ohne force greifen", chooseTarget(due, aktiv, force = false, today = today, nowEpochMs = start))
        assertEquals(aktiv, chooseTarget(due, aktiv, force = true, today = today, nowEpochMs = start))
    }

    // ---------- Bremse und frischer Favorit ----------

    @Test
    fun `passiert kein Kandidat die Bremse, wird nichts abgerufen`() {
        val favorit = 10.0 to 10.0
        val entries = listOf(favorit, aktiv).map { mitAbdeckung(it, today.plusDays(300)) }
        val due = CacheStore.dueOrder(entries, listOf(favorit), aktiv, today)

        assertNull(chooseTarget(due, aktiv, force = false, today = today, nowEpochMs = start))
    }

    @Test
    fun `ein frisch angelegter Favorit kommt sofort dran, obwohl ein hoffnungsloser Ort in der Liste steht`() {
        val hoffnungslos = 10.0 to 10.0
        val frisch = 20.0 to 20.0
        // Der hoffnungslose steht ABSICHTLICH vorn in der Favoritenliste.
        val entries = listOf(
            nurVersuch(hoffnungslos, nowEpochMs = start - 10 * fourHoursMs, error = "Kein Netz"),
            mitAbdeckung(aktiv, today.plusDays(300)),
        )

        val ziel = chooseTarget(
            due = CacheStore.dueOrder(entries, listOf(hoffnungslos, frisch), aktiv, today),
            activeCoords = aktiv,
            force = false,
            today = today,
            nowEpochMs = start,
        )

        // Der frische Favorit hat noch keinen Fehler vorzuweisen, ist also
        // nicht hoffnungslos — er darf nicht hinter einem Ort warten, der
        // nachweislich nicht geht.
        assertEquals(frisch, ziel)
    }

    // ---------- Simulation des Speichers ----------

    private fun sixTimes() = SixTimes(
        fajr = LocalTime.of(4, 54),
        sunrise = LocalTime.of(6, 23),
        dhuhr = LocalTime.of(13, 2),
        asr = LocalTime.of(16, 39),
        maghrib = LocalTime.of(19, 31),
        isha = LocalTime.of(20, 53),
    )

    private fun header(
        coords: Pair<Double, Double>,
        updatedEpochMs: Long,
        lastAttemptEpochMs: Long?,
        lastError: String?,
    ) = CacheHeader(
        latitude = coords.first,
        longitude = coords.second,
        locationId = null,
        // firstDate/lastDate leitet CacheStore aus dem Zeitplan ab.
        firstDate = null,
        lastDate = null,
        updatedEpochMs = updatedEpochMs,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastError = lastError,
    )

    /** Eintrag mit Zeitplan bis [bis], ohne Versuchsprotokoll. */
    private fun mitAbdeckung(coords: Pair<Double, Double>, bis: LocalDate): RawEntry {
        val plan = mapOf(today to sixTimes(), bis to sixTimes())
        return RawEntry(
            header(coords, updatedEpochMs = start, lastAttemptEpochMs = null, lastError = null)
                .copy(firstDate = today, lastDate = bis),
            ScheduleText.serialize(plan),
        )
    }

    /** Eintrag OHNE Zeitplan — reines Versuchsprotokoll, wie ihn
     *  `recordAttempt` an einem Ort ohne Erfolg anlegt. */
    private fun nurVersuch(coords: Pair<Double, Double>, nowEpochMs: Long, error: String?) = RawEntry(
        header(coords, updatedEpochMs = nowEpochMs, lastAttemptEpochMs = nowEpochMs, lastError = error),
        "",
    )

    /** Wie `OfficialTimesCache.recordAttempt`: den vorhandenen Eintrag am Ort
     *  aendern (ueber `indexOf`, also der erste Treffer), sonst einen leeren
     *  anlegen. */
    private fun protokolliere(
        entries: List<RawEntry>,
        coords: Pair<Double, Double>,
        nowEpochMs: Long,
        error: String?,
    ): List<RawEntry> {
        val index = CacheStore.indexOf(entries, coords.first, coords.second)
        if (index >= 0) {
            return entries.mapIndexed { i, entry ->
                if (i == index) {
                    entry.copy(header = entry.header.copy(lastAttemptEpochMs = nowEpochMs, lastError = error))
                } else {
                    entry
                }
            }
        }
        return CacheStore.put(
            entries,
            CacheEntry(
                header(coords, updatedEpochMs = nowEpochMs, lastAttemptEpochMs = nowEpochMs, lastError = error),
                emptyMap(),
            ),
            pinnedCoords = favoriten,
        )
    }

    /** Ein gescheiterter Abruf: Versuchsprotokoll aktualisieren, keine Zeiten
     *  hinzufuegen. */
    private fun fehlversuch(entries: List<RawEntry>, coords: Pair<Double, Double>, nowEpochMs: Long) =
        protokolliere(entries, coords, nowEpochMs, "Kein Netz")

    /** Ein gelungener Abruf: `putAll` legt den Eintrag an den Koordinaten des
     *  GEWAEHLTEN Orts ab, danach `recordAttempt(null)`. */
    private fun erfolg(
        entries: List<RawEntry>,
        coords: Pair<Double, Double>,
        nowEpochMs: Long,
        bis: LocalDate,
    ): List<RawEntry> {
        val plan = mapOf(today to sixTimes(), bis to sixTimes())
        val vorhanden = CacheStore.select(entries, coords.first, coords.second)
        val abgelegt = CacheStore.put(
            entries,
            CacheEntry(
                header(
                    coords,
                    updatedEpochMs = nowEpochMs,
                    lastAttemptEpochMs = vorhanden?.header?.lastAttemptEpochMs,
                    lastError = vorhanden?.header?.lastError,
                ),
                plan,
            ),
            pinnedCoords = favoriten,
        )
        return protokolliere(abgelegt, coords, nowEpochMs, null)
    }
}
