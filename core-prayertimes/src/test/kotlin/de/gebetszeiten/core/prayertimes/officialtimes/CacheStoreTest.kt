package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CacheStoreTest {

    // Nuernberg
    private val lat = 49.4521
    private val lng = 11.0767

    private fun sixTimes(hour: Int) = SixTimes(
        fajr = java.time.LocalTime.of(hour % 5, 30),
        sunrise = java.time.LocalTime.of((hour + 1) % 5, 30),
        dhuhr = java.time.LocalTime.of(13, 0),
        asr = java.time.LocalTime.of(17, 0),
        maghrib = java.time.LocalTime.of(21, 0),
        isha = java.time.LocalTime.of(22, 0),
    )

    private fun schedule(from: LocalDate, days: Int): Map<LocalDate, SixTimes> =
        (0 until days).associate { from.plusDays(it.toLong()) to sixTimes(it) }

    private fun header(
        lat: Double = this.lat,
        lng: Double = this.lng,
        locationId: Int? = 42,
        firstDate: LocalDate? = null,
        lastDate: LocalDate? = null,
        updatedEpochMs: Long = 1_000L,
        lastAttemptEpochMs: Long? = 1_000L,
        lastError: String? = null,
    ) = CacheHeader(
        latitude = lat,
        longitude = lng,
        locationId = locationId,
        firstDate = firstDate,
        lastDate = lastDate,
        updatedEpochMs = updatedEpochMs,
        lastAttemptEpochMs = lastAttemptEpochMs,
        lastError = lastError,
    )

    @Test
    fun `Rundreise - serialize dann split ergibt Kopf und Rumpf unveraendert`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 5)
        val entry = CacheEntry(header(), plan)

        val text = CacheStore.serialize(listOf(entry))
        val split = CacheStore.split(text)

        assertEquals(1, split.size)
        val raw = split[0]
        assertEquals(lat, raw.header.latitude, 0.0)
        assertEquals(lng, raw.header.longitude, 0.0)
        assertEquals(42, raw.header.locationId)
        assertEquals(plan, ScheduleText.parse(raw.body))
    }

    @Test
    fun `mehrere Eintraege hintereinander werden korrekt getrennt`() {
        val plan1 = schedule(LocalDate.of(2026, 9, 6), 3)
        val plan2 = schedule(LocalDate.of(2026, 1, 1), 4)
        val entries = listOf(
            CacheEntry(header(lat = 49.0, lng = 11.0), plan1),
            CacheEntry(header(lat = 41.0, lng = 29.0), plan2),
        )

        val text = CacheStore.serialize(entries)
        val split = CacheStore.split(text)

        assertEquals(2, split.size)
        assertEquals(49.0, split[0].header.latitude, 0.0)
        assertEquals(plan1, ScheduleText.parse(split[0].body))
        assertEquals(41.0, split[1].header.latitude, 0.0)
        assertEquals(plan2, ScheduleText.parse(split[1].body))
    }

    @Test
    fun `firstDate und lastDate werden aus dem Zeitplan abgeleitet`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 5)
        // Aufrufer gibt absichtlich falsche Werte mit.
        val wrongHeader = header(
            firstDate = LocalDate.of(1999, 1, 1),
            lastDate = LocalDate.of(1999, 12, 31),
        )
        val entry = CacheEntry(wrongHeader, plan)

        val text = CacheStore.serialize(listOf(entry))
        val split = CacheStore.split(text)

        assertEquals(LocalDate.of(2026, 9, 6), split[0].header.firstDate)
        assertEquals(LocalDate.of(2026, 9, 10), split[0].header.lastDate)
    }

    @Test
    fun `split ist tolerant - kaputte Kopfzeile mittendrin verwirft nur ihren Eintrag`() {
        val plan1 = schedule(LocalDate.of(2026, 9, 6), 2)
        val plan3 = schedule(LocalDate.of(2026, 1, 1), 2)
        val goodText1 = CacheStore.serialize(listOf(CacheEntry(header(lat = 49.0, lng = 11.0), plan1)))
        val goodText3 = CacheStore.serialize(listOf(CacheEntry(header(lat = 41.0, lng = 29.0), plan3)))
        val text = "$goodText1\n#kaputt-keine-zahlen-hier\n2026-05-05 04:00 05:00 13:00 17:00 21:00 22:00\n$goodText3"

        val split = CacheStore.split(text)

        assertEquals(2, split.size)
        assertEquals(49.0, split[0].header.latitude, 0.0)
        assertEquals(41.0, split[1].header.latitude, 0.0)
        assertEquals(plan1, ScheduleText.parse(split[0].body))
        assertEquals(plan3, ScheduleText.parse(split[1].body))
    }

    @Test
    fun `unbekanntes Extra-Feld in der Kopfzeile wird trotzdem gelesen`() {
        val line = "#49.4521|11.0767|42|2026-09-06|2026-09-06|1000|-|-|EIN-UNBEKANNTES-FELD"
        val split = CacheStore.split(line)

        assertEquals(1, split.size)
        assertEquals(49.4521, split[0].header.latitude, 0.0)
        assertEquals(42, split[0].header.locationId)
    }

    @Test
    fun `fehlende hintere Felder ergeben Standardwerte statt Verwerfen`() {
        // Nur bis updatedEpochMs, lastAttemptEpochMs und lastError fehlen komplett.
        val line = "#49.4521|11.0767|42|2026-09-06|2026-09-06|1000"
        val split = CacheStore.split(line)

        assertEquals(1, split.size)
        val h = split[0].header
        assertEquals(1000L, h.updatedEpochMs)
        assertNull(h.lastAttemptEpochMs)
        assertNull(h.lastError)
    }

    @Test
    fun `lastError mit Trennzeichen und Zeilenumbruch bleibt bei Rundreise heil`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 2)
        val errorWithPipeAndNewline = "Fehler: Netz|nicht erreichbar\nTimeout"
        val entry = CacheEntry(header(lastError = errorWithPipeAndNewline), plan)

        val text = CacheStore.serialize(listOf(entry))
        val split = CacheStore.split(text)

        assertEquals(1, split.size)
        // Trennzeichen und Zeilenumbruch sind durch Leerzeichen ersetzt worden;
        // der Verlust ist bei unseren eigenen Fehlerliteralen hinnehmbar.
        assertEquals("Fehler: Netz nicht erreichbar Timeout", split[0].header.lastError)
        // Und die Zeile bleibt eine einzige Kopfzeile (kein eingebetteter Zeilenumbruch).
        assertEquals(1, text.lineSequence().count { it.startsWith("#") })
    }

    @Test
    fun `select findet Treffer bei 0,5 km, nichts bei 5 km, nichts bei leerer Liste`() {
        val entry = RawEntry(header(), "")
        assertEquals(entry, CacheStore.select(listOf(entry), lat = 49.4566, lng = 11.0767)) // ~500 m
        assertNull(CacheStore.select(listOf(entry), lat = 49.4521, lng = 11.13)) // ~5 km (Laenge)
        assertNull(CacheStore.select(emptyList(), lat = lat, lng = lng))
    }

    @Test
    fun `put ersetzt statt anzuhaengen wenn Koordinaten innerhalb 1 km liegen`() {
        val oldPlan = schedule(LocalDate.of(2026, 1, 1), 2)
        val newPlan = schedule(LocalDate.of(2026, 9, 6), 3)
        val existing = listOf(RawEntry(header(updatedEpochMs = 500L), ScheduleText.serialize(oldPlan)))

        val result = CacheStore.put(
            entries = existing,
            added = CacheEntry(header(lat = 49.4566, lng = 11.0767, updatedEpochMs = 2000L), newPlan),
            pinnedCoords = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals(2000L, result[0].header.updatedEpochMs)
        assertEquals(newPlan, ScheduleText.parse(result[0].body))
    }

    @Test
    fun `put verdraengt nie einen angehefteten Eintrag`() {
        val pinnedLat = 49.4521
        val pinnedLng = 11.0767
        val pinnedEntry = RawEntry(
            header(lat = pinnedLat, lng = pinnedLng, updatedEpochMs = 1L),
            ScheduleText.serialize(schedule(LocalDate.of(2026, 1, 1), 1)),
        )

        var entries = listOf(pinnedEntry)
        // Zehn nicht angeheftete Orte kommen dazu, weit ueber maxUnpinned hinaus.
        for (i in 0 until 10) {
            entries = CacheStore.put(
                entries = entries,
                added = CacheEntry(
                    header(lat = 10.0 + i, lng = 10.0 + i, updatedEpochMs = (100 + i).toLong()),
                    schedule(LocalDate.of(2026, 1, 1), 1),
                ),
                pinnedCoords = listOf(pinnedLat to pinnedLng),
                maxUnpinned = 5,
            )
        }

        assertTrue(entries.any { stampMatches(it.header.latitude, it.header.longitude, pinnedLat, pinnedLng) })
    }

    @Test
    fun `put verdraengt den aeltesten nicht angehefteten bei Ueberschreitung von maxUnpinned`() {
        // Absichtlich ABSTEIGEND nach Alter: der aelteste Eintrag steht
        // HINTEN. Kaeme die Liste schon in aufsteigender Altersreihenfolge,
        // truebe die stabile Sortierung das Ergebnis — der Test bliebe dann
        // gruen, auch wenn jemand den Sortierschluessel `updatedEpochMs`
        // ersatzlos streicht.
        val entries = (0 until 5).map { i ->
            rawWithPlan(lat = 10.0 + i, lng = 10.0 + i, updatedEpochMs = (500 - 100 * i).toLong())
        }

        val result = CacheStore.put(
            entries = entries,
            added = CacheEntry(
                header(lat = 20.0, lng = 20.0, updatedEpochMs = 600L),
                schedule(LocalDate.of(2026, 1, 1), 1),
            ),
            pinnedCoords = emptyList(),
            maxUnpinned = 5,
        )

        assertEquals(5, result.size)
        // Der aelteste (updatedEpochMs=100, ganz hinten) muss weg sein.
        assertTrue(result.none { it.header.updatedEpochMs == 100L })
        assertTrue(result.any { it.header.updatedEpochMs == 600L })
    }

    /** Eintrag mit echtem Zeitplan — `lastDate` steht im Kopf, wie ihn
     *  `serialize`/`split` hinterlassen. */
    private fun rawWithPlan(lat: Double, lng: Double, updatedEpochMs: Long): RawEntry {
        val plan = schedule(LocalDate.of(2026, 1, 1), 1)
        return RawEntry(
            header(
                lat = lat,
                lng = lng,
                updatedEpochMs = updatedEpochMs,
                firstDate = plan.keys.minOrNull(),
                lastDate = plan.keys.maxOrNull(),
            ),
            ScheduleText.serialize(plan),
        )
    }

    /** Eintrag, der NUR einen Fehlversuch protokolliert: leerer Rumpf,
     *  `lastDate == null`. */
    private fun rawEmpty(lat: Double, lng: Double, updatedEpochMs: Long) = RawEntry(
        header(lat = lat, lng = lng, locationId = null, updatedEpochMs = updatedEpochMs, lastError = "Kein Netz"),
        "",
    )

    @Test
    fun `put haelt den Fehlversuch am sechsten Ort fest, ohne einen Zeitplan zu opfern`() {
        // Fuenf Orte erfolgreich abgerufen, aufsteigend aktualisiert.
        var entries = emptyList<RawEntry>()
        for (i in 0 until 5) {
            entries = CacheStore.put(
                entries = entries,
                added = CacheEntry(
                    header(lat = 10.0 + i, lng = 10.0 + i, updatedEpochMs = (100 + i).toLong()),
                    schedule(LocalDate.of(2026, 1, 1), 1),
                ),
                pinnedCoords = emptyList(),
                maxUnpinned = 5,
            )
        }

        // Wechsel zum sechsten Ort, Abruf scheitert: ein Eintrag ohne Zeitplan.
        val result = CacheStore.put(
            entries = entries,
            added = CacheEntry(
                header(lat = 20.0, lng = 20.0, locationId = null, updatedEpochMs = 999L, lastError = "Kein Netz"),
                emptyMap(),
            ),
            pinnedCoords = emptyList(),
            maxUnpinned = 5,
        )

        // maxUnpinned zaehlt nur Zeitplaene, der leere Eintrag kommt dazu.
        assertEquals(6, result.size)
        // Alle fuenf Jahresplaene ueberleben — auch der aelteste.
        for (i in 0 until 5) {
            assertTrue(
                "Zeitplan fuer Ort $i wurde verdraengt",
                result.any { stampMatches(it.header.latitude, it.header.longitude, 10.0 + i, 10.0 + i) },
            )
        }
        // Und der Fehlversuch ist protokolliert — sonst zeigte die
        // Statuszeile an diesem Ort "noch kein Versuch", obwohl der Abruf
        // gerade eben gescheitert ist.
        val empty = result.single { stampMatches(it.header.latitude, it.header.longitude, 20.0, 20.0) }
        assertEquals("Kein Netz", empty.header.lastError)
        assertNull(empty.header.lastDate)
    }

    @Test
    fun `put behaelt von mehreren leeren Eintraegen nur den juengsten`() {
        // Zwei Fehlversuche an zwei verschiedenen unbekannten Orten liegen
        // schon vor, dazu ein Zeitplan.
        val entries = listOf(
            // Der JUENGERE leere Eintrag steht vorn, der aeltere dahinter:
            // so faellt der Test um, sobald jemand den Sortierschluessel
            // `updatedEpochMs` streicht (stabile Sortierung wuerde sonst
            // schon die Eingabereihenfolge richtig raten).
            rawEmpty(lat = 20.0, lng = 20.0, updatedEpochMs = 200L),
            rawEmpty(lat = 21.0, lng = 21.0, updatedEpochMs = 100L),
            // Der aelteste Eintrag ueberhaupt — aber er traegt einen Zeitplan.
            rawWithPlan(lat = 22.0, lng = 22.0, updatedEpochMs = 50L),
        )

        val result = CacheStore.put(
            entries = entries,
            added = CacheEntry(
                header(lat = 23.0, lng = 23.0, updatedEpochMs = 300L),
                schedule(LocalDate.of(2026, 1, 1), 1),
            ),
            pinnedCoords = emptyList(),
            // Reichlich Platz fuer Zeitplaene: die leeren Eintraege werden
            // trotzdem begrenzt, sie haben ihre eigene Grenze.
            maxUnpinned = 5,
        )

        // Zwei Zeitplaene plus genau ein leerer Eintrag.
        assertEquals(3, result.size)
        // Der aeltere leere faellt ...
        assertTrue(
            "der aeltere leere Eintrag haette fallen muessen",
            result.none { stampMatches(it.header.latitude, it.header.longitude, 21.0, 21.0) },
        )
        // ... der juengere leere bleibt ...
        assertTrue(
            "der juengere leere Eintrag wurde verdraengt",
            result.any { stampMatches(it.header.latitude, it.header.longitude, 20.0, 20.0) },
        )
        // ... und die Zeitplaene sind unberuehrt, auch der aelteste Eintrag
        // der ganzen Liste.
        assertTrue(
            "der Zeitplan wurde verdraengt, obwohl er gar nicht mit leeren Eintraegen konkurriert",
            result.any { stampMatches(it.header.latitude, it.header.longitude, 22.0, 22.0) },
        )
        assertTrue(result.any { stampMatches(it.header.latitude, it.header.longitude, 23.0, 23.0) })
    }

    @Test
    fun `indexOf trifft denselben Eintrag wie select - ein Fehlversuch legt keinen zweiten an`() {
        val entries = listOf(
            rawWithPlan(lat = 41.0, lng = 29.0, updatedEpochMs = 100L),
            rawWithPlan(lat = lat, lng = lng, updatedEpochMs = 200L),
        )

        // ~500 m entfernt: derselbe Ort. `recordAttempt` findet den
        // bestehenden Eintrag darueber und aktualisiert nur dessen Kopf,
        // statt einen zweiten Eintrag fuer denselben Ort anzulegen.
        val index = CacheStore.indexOf(entries, lat = 49.4566, lng = 11.0767)
        assertEquals(1, index)
        assertEquals(CacheStore.select(entries, lat = 49.4566, lng = 11.0767), entries[index])

        // ~5 km entfernt: kein Treffer, und zwar derselbe Nicht-Treffer wie
        // bei `select` — beide gehen ueber dieselbe Ortsidentitaet.
        assertEquals(-1, CacheStore.indexOf(entries, lat = lat, lng = 11.13))
        assertNull(CacheStore.select(entries, lat = lat, lng = 11.13))
        assertEquals(-1, CacheStore.indexOf(emptyList(), lat = lat, lng = lng))
    }

    @Test
    fun `serializeRaw - Rundreise ueber split laesst Kopf und Rumpf unveraendert`() {
        val entries = listOf(
            rawWithPlan(lat = 49.0, lng = 11.0, updatedEpochMs = 1_000L),
            // Eintrag mit leerem Rumpf (nur Versuchsprotokoll) mittendrin —
            // die Kopfzeile darf nicht mit der des Nachbarn verschmelzen.
            rawEmpty(lat = 41.0, lng = 29.0, updatedEpochMs = 2_000L),
            rawWithPlan(lat = 37.0, lng = 15.0, updatedEpochMs = 3_000L),
        )

        val split = CacheStore.split(CacheStore.serializeRaw(entries))

        assertEquals(entries, split)
    }

    @Test
    fun `migrateLegacy - alles vorhanden`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 2)
        val result = CacheStore.migrateLegacy(
            schedule = ScheduleText.serialize(plan),
            lat = lat,
            lng = lng,
            locationId = 7,
            lastAttemptEpochMs = 500L,
            lastError = "Fehler",
            nowEpochMs = 9999L,
        )

        assertEquals(1, result.size)
        val e = result[0]
        assertEquals(plan, e.schedule)
        assertEquals(lat, e.header.latitude, 0.0)
        assertEquals(7, e.header.locationId)
        assertEquals(500L, e.header.lastAttemptEpochMs)
        assertEquals("Fehler", e.header.lastError)
    }

    @Test
    fun `migrateLegacy - Zeitplan ohne Versuchsdaten`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 2)
        val result = CacheStore.migrateLegacy(
            schedule = ScheduleText.serialize(plan),
            lat = lat,
            lng = lng,
            locationId = null,
            lastAttemptEpochMs = null,
            lastError = null,
            nowEpochMs = 9999L,
        )

        assertEquals(1, result.size)
        assertEquals(plan, result[0].schedule)
        assertNull(result[0].header.lastAttemptEpochMs)
        assertNull(result[0].header.lastError)
    }

    @Test
    fun `migrateLegacy - Versuchsdaten ohne Zeitplan`() {
        val result = CacheStore.migrateLegacy(
            schedule = null,
            lat = lat,
            lng = lng,
            locationId = null,
            lastAttemptEpochMs = 500L,
            lastError = "Fehler",
            nowEpochMs = 9999L,
        )

        assertEquals(1, result.size)
        assertTrue(result[0].schedule.isEmpty())
        assertEquals(500L, result[0].header.lastAttemptEpochMs)
        assertEquals("Fehler", result[0].header.lastError)
    }

    @Test
    fun `migrateLegacy - gar nichts ergibt leere Liste`() {
        val result = CacheStore.migrateLegacy(
            schedule = null,
            lat = lat,
            lng = lng,
            locationId = null,
            lastAttemptEpochMs = null,
            lastError = null,
            nowEpochMs = 9999L,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `migrateLegacy ohne Koordinaten ergibt leere Liste`() {
        val plan = schedule(LocalDate.of(2026, 9, 6), 2)
        val result = CacheStore.migrateLegacy(
            schedule = ScheduleText.serialize(plan),
            lat = null,
            lng = null,
            locationId = null,
            lastAttemptEpochMs = null,
            lastError = null,
            nowEpochMs = 9999L,
        )

        assertTrue(result.isEmpty())
    }

    // ---------- Verdraengung mit echten Favoriten (Task 5) ----------

    @Test
    fun `put haelt alle zehn Favoriten, auch wenn zehn fremde Orte dazukommen`() {
        val pinnedCoords = (0 until 10).map { (10.0 + it) to (10.0 + it) }
        var entries = emptyList<RawEntry>()
        for ((i, p) in pinnedCoords.withIndex()) {
            entries = CacheStore.put(
                entries = entries,
                added = CacheEntry(
                    header(lat = p.first, lng = p.second, updatedEpochMs = (100 + i).toLong()),
                    schedule(LocalDate.of(2026, 1, 1), 1),
                ),
                pinnedCoords = pinnedCoords,
                maxUnpinned = 5,
            )
        }
        // Zehn fremde Orte, doppelt so viele wie maxUnpinned erlaubt.
        for (i in 0 until 10) {
            entries = CacheStore.put(
                entries = entries,
                added = CacheEntry(
                    header(lat = 40.0 + i, lng = 40.0 + i, updatedEpochMs = (1_000 + i).toLong()),
                    schedule(LocalDate.of(2026, 1, 1), 1),
                ),
                pinnedCoords = pinnedCoords,
                maxUnpinned = 5,
            )
        }

        for (p in pinnedCoords) {
            assertTrue(
                "Favorit bei " + p.first + " wurde verdraengt",
                entries.any { stampMatches(it.header.latitude, it.header.longitude, p.first, p.second) },
            )
        }
        // Zehn Favoriten plus die fuenf erlaubten fremden Orte.
        assertEquals(15, entries.size)
    }

    @Test
    fun `put verdraengt einen Favoriten nicht, obwohl sein Zeitstempel der aelteste von allen ist`() {
        val favorit = 49.0 to 11.0
        var entries = listOf(rawWithPlan(lat = favorit.first, lng = favorit.second, updatedEpochMs = 1L))
        for (i in 0 until 6) {
            entries = CacheStore.put(
                entries = entries,
                added = CacheEntry(
                    header(lat = 20.0 + i, lng = 20.0 + i, updatedEpochMs = (500 + i).toLong()),
                    schedule(LocalDate.of(2026, 1, 1), 1),
                ),
                pinnedCoords = listOf(favorit),
                maxUnpinned = 5,
            )
        }

        val favEntry = entries.single {
            stampMatches(it.header.latitude, it.header.longitude, favorit.first, favorit.second)
        }
        assertEquals(1L, favEntry.header.updatedEpochMs)
        // Favorit plus die fuenf erlaubten fremden Orte.
        assertEquals(6, entries.size)
    }

    @Test
    fun `put aktualisiert einen angehefteten Ort statt ihn zu duplizieren`() {
        val favorit = lat to lng
        val neuerPlan = schedule(LocalDate.of(2026, 9, 6), 3)

        val result = CacheStore.put(
            entries = listOf(rawWithPlan(lat = lat, lng = lng, updatedEpochMs = 100L)),
            // ~500 m daneben: derselbe Ort.
            added = CacheEntry(header(lat = 49.4566, lng = lng, updatedEpochMs = 2_000L), neuerPlan),
            pinnedCoords = listOf(favorit),
            maxUnpinned = 5,
        )

        assertEquals(1, result.size)
        assertEquals(2_000L, result[0].header.updatedEpochMs)
        assertEquals(neuerPlan, ScheduleText.parse(result[0].body))
    }

    @Test
    fun `put verdraengt niemals den gerade hinzugefuegten Eintrag`() {
        // Ein JUENGERER leerer Eintrag liegt schon vor, der neue ist aelter
        // (rueckwaerts gestellte Uhr, oder ein migrierter Eintrag mit
        // spaeterem Stempel). Der neue darf trotzdem nicht selbst
        // rausfallen — sonst verschweigt die Statuszeile den Fehler, den
        // `recordAttempt` gerade eben protokolliert hat.
        val result = CacheStore.put(
            entries = listOf(rawEmpty(lat = 20.0, lng = 20.0, updatedEpochMs = 500L)),
            added = CacheEntry(
                header(lat = 30.0, lng = 30.0, locationId = null, updatedEpochMs = 100L, lastError = "Kein Netz"),
                emptyMap(),
            ),
            pinnedCoords = emptyList(),
            maxUnpinned = 5,
        )

        assertEquals(1, result.size)
        assertTrue(
            "der gerade hinzugefuegte Eintrag wurde verdraengt",
            result.any { stampMatches(it.header.latitude, it.header.longitude, 30.0, 30.0) },
        )
    }

    // ---------- dueOrder (Task 5) ----------

    private val today = LocalDate.of(2026, 9, 7)

    /** Eintrag, dessen Abdeckung bis [lastDate] reicht. Ein einziger Tag im
     *  Rumpf genuegt — `dueOrder` liest nur den Kopf. */
    private fun rawCovering(
        lat: Double,
        lng: Double,
        lastDate: LocalDate,
        updatedEpochMs: Long = 1_000L,
    ) = RawEntry(
        header(lat = lat, lng = lng, firstDate = lastDate, lastDate = lastDate, updatedEpochMs = updatedEpochMs),
        ScheduleText.serialize(mapOf(lastDate to sixTimes(0))),
    )

    @Test
    fun `dueOrder - ein Favorit ohne Eintrag steht vor einem mit 300 Tagen Abdeckung`() {
        val versorgt = 20.0 to 20.0
        val ohneZeiten = 10.0 to 10.0
        val entries = listOf(rawCovering(versorgt.first, versorgt.second, today.plusDays(300)))

        // Der versorgte Favorit steht ABSICHTLICH vorn in pinnedCoords.
        val order = CacheStore.dueOrder(entries, listOf(versorgt, ohneZeiten), activeCoords = null, today = today)

        assertEquals(listOf(10.0, 20.0), order.map { it.latitude })
        assertNull(order[0].entry)
        assertTrue(order[0].pinned)
    }

    @Test
    fun `dueOrder - abgelaufene Abdeckung vor knapper, knappe vor reichlicher`() {
        val abgelaufen = 10.0 to 10.0
        val knapp = 20.0 to 20.0
        val reichlich = 30.0 to 30.0
        val entries = listOf(
            rawCovering(reichlich.first, reichlich.second, today.plusDays(300)),
            rawCovering(knapp.first, knapp.second, today.plusDays(3)),
            rawCovering(abgelaufen.first, abgelaufen.second, today.minusDays(5)),
        )

        // pinnedCoords absichtlich genau umgekehrt zum erwarteten Ergebnis.
        val order = CacheStore.dueOrder(
            entries,
            listOf(reichlich, knapp, abgelaufen),
            activeCoords = null,
            today = today,
        )

        assertEquals(listOf(10.0, 20.0, 30.0), order.map { it.latitude })
    }

    @Test
    fun `dueOrder - bei gleicher Restabdeckung steht der aktive Ort vor dem Favoriten`() {
        val favorit = 10.0 to 10.0
        val aktiv = 20.0 to 20.0
        val entries = listOf(
            rawCovering(favorit.first, favorit.second, today.plusDays(30)),
            rawCovering(aktiv.first, aktiv.second, today.plusDays(30)),
        )

        val order = CacheStore.dueOrder(entries, listOf(favorit), activeCoords = aktiv, today = today)

        assertEquals(listOf(20.0, 10.0), order.map { it.latitude })
        // Der aktive Ort ist hier KEIN Favorit.
        assertFalse(order[0].pinned)
        assertTrue(order[1].pinned)
    }

    @Test
    fun `dueOrder - bei Gleichstand gilt die Reihenfolge von pinnedCoords`() {
        val a = 30.0 to 30.0
        val b = 10.0 to 10.0
        val c = 20.0 to 20.0
        val entries = listOf(a, b, c).map { rawCovering(it.first, it.second, today.plusDays(30)) }

        val order = CacheStore.dueOrder(entries, listOf(a, b, c), activeCoords = null, today = today)

        assertEquals(listOf(30.0, 10.0, 20.0), order.map { it.latitude })
    }

    @Test
    fun `dueOrder - der aktive Ort, der zugleich Favorit ist, erscheint einmal und angeheftet`() {
        val favorit = lat to lng
        val aktivGleicherOrt = 49.4566 to lng // ~500 m daneben
        val entries = listOf(rawCovering(lat, lng, today.plusDays(10)))

        val order = CacheStore.dueOrder(entries, listOf(favorit), activeCoords = aktivGleicherOrt, today = today)

        assertEquals(1, order.size)
        assertTrue(order[0].pinned)
        // Beim Verschmelzen gewinnen die AKTIVEN Koordinaten — der Ort, an dem
        // der Nutzer gerade ist. Seinen Eintrag findet er trotzdem.
        assertEquals(49.4566, order[0].latitude, 0.0)
        assertEquals(today.plusDays(10), order[0].entry?.header?.lastDate)
    }

    @Test
    fun `dueOrder - ein nicht angehefteter Cache-Eintrag erscheint nicht`() {
        val favorit = 10.0 to 10.0
        val fremd = 41.0 to 29.0
        val entries = listOf(
            // Waere mit Abstand das dringendste, ist aber weder Favorit noch
            // aktiver Ort: ein zufaellig besuchter Ort, an dem der Nutzer
            // nicht ist.
            rawCovering(fremd.first, fremd.second, today.minusDays(100)),
            rawCovering(favorit.first, favorit.second, today.plusDays(300)),
        )

        val order = CacheStore.dueOrder(entries, listOf(favorit), activeCoords = null, today = today)

        assertEquals(1, order.size)
        assertEquals(10.0, order[0].latitude, 0.0)
    }

    @Test
    fun `dueOrder - ohne aktiven Ort kommen nur die Favoriten`() {
        val order = CacheStore.dueOrder(
            entries = listOf(rawCovering(41.0, 29.0, today)),
            pinnedCoords = listOf(10.0 to 10.0, 20.0 to 20.0),
            activeCoords = null,
            today = today,
        )

        assertEquals(listOf(10.0, 20.0), order.map { it.latitude })
    }

    @Test
    fun `dueOrder - leere Eingaben ergeben eine leere Liste`() {
        assertTrue(
            CacheStore.dueOrder(emptyList(), emptyList(), activeCoords = null, today = today).isEmpty(),
        )
    }

    @Test
    fun `dueOrder - ein Eintrag mit leerem Zeitplan zaehlt als keine Zeiten`() {
        val nurVersuch = 10.0 to 10.0
        val versorgt = 20.0 to 20.0
        val entries = listOf(
            rawCovering(versorgt.first, versorgt.second, today.plusDays(300)),
            rawEmpty(lat = nurVersuch.first, lng = nurVersuch.second, updatedEpochMs = 900L),
        )

        val order = CacheStore.dueOrder(entries, listOf(versorgt, nurVersuch), activeCoords = null, today = today)

        assertEquals(listOf(10.0, 20.0), order.map { it.latitude })
        // Der Eintrag EXISTIERT (Fehlerprotokoll), er traegt nur keine Zeiten.
        assertEquals("Kein Netz", order[0].entry?.header?.lastError)
    }

    @Test
    fun `dueOrder - ein Favorit findet seinen Eintrag 500 m daneben`() {
        val favorit = lat to lng
        val entries = listOf(rawCovering(49.4566, lng, today.plusDays(42)))

        val order = CacheStore.dueOrder(entries, listOf(favorit), activeCoords = null, today = today)

        assertEquals(1, order.size)
        assertEquals(today.plusDays(42), order[0].entry?.header?.lastDate)
    }
}
