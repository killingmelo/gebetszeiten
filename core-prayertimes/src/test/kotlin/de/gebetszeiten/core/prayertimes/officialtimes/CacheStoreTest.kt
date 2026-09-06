package de.gebetszeiten.core.prayertimes.officialtimes

import org.junit.Assert.assertEquals
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
}
