package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.officialtimes.FetchResult
import de.gebetszeiten.core.prayertimes.officialtimes.OfficialTimesFetcher
import de.gebetszeiten.core.prayertimes.officialtimes.SixTimes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.UnknownHostException
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DataTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 25)
    private val nbgLat = 49.45
    private val nbgLng = 11.08

    private fun six(fajr: String) = SixTimes(
        LocalTime.parse(fajr), LocalTime.parse("07:30"), LocalTime.parse("12:30"),
        LocalTime.parse("15:00"), LocalTime.parse("17:30"), LocalTime.parse("19:00"),
    )

    private fun bundled() = BundledTables { path -> DataTest::class.java.classLoader.getResourceAsStream(path) }

    private fun cache() = DesktopCache(tmp.root.toPath().resolve("cache.txt"))

    private val nuernberg = DesktopSettings(placeName = "Nürnberg", latitude = nbgLat, longitude = nbgLng)

    @Test
    fun settingsRoundTrip() {
        val store = SettingsStore(tmp.root.toPath().resolve("sub"))
        val s = DesktopSettings(
            placeName = "Nürnberg – Mitte",
            latitude = 49.4521,
            longitude = 11.0767,
            diyanetId = 9541,
            reminders = setOf(Prayer.FAJR, Prayer.ISHA),
            leadMinutes = 10,
            karahaWarning = true,
            showKaraha = false,
            ezanFile = "C:\\Ezan\\ezan.mp3",
            fajrEzanFile = "C:\\Ezan\\fajr.mp3",
            ezanVolume = 35,
            taskbarField = false,
            autostart = false,
        )
        store.save(s)
        assertEquals(s, store.load())
    }

    @Test
    fun settingsDefaultsWhenNoFile() {
        assertEquals(DesktopSettings(), SettingsStore(tmp.root.toPath()).load())
    }

    @Test
    fun brokenSettingsFileGivesDefaults() {
        val dir = tmp.root.toPath()
        Files.writeString(
            dir.resolve("settings.properties"),
            "latitude=abc\nlongitude=11.0\nleadMinutes=-4\nezanVolume=900\nshowKaraha=vielleicht\n" +
                "reminders=FAJR,QUATSCH\nautostart=\n",
        )
        val s = SettingsStore(dir).load()
        val d = DesktopSettings()
        assertNull(s.latitude)
        assertNull(s.longitude)
        assertEquals(d.leadMinutes, s.leadMinutes)
        assertEquals(d.ezanVolume, s.ezanVolume)
        assertEquals(d.showKaraha, s.showKaraha)
        assertEquals(setOf(Prayer.FAJR), s.reminders)
        assertEquals(d.autostart, s.autostart)
    }

    @Test
    fun cachePutThenGet() {
        val c = cache()
        c.putAll(mapOf(day to six("05:10")), nbgLat, nbgLng, 9541, null)
        assertEquals(six("05:10"), c.get(day, nbgLat, nbgLng))
        assertEquals(9541, c.cachedLocationId(nbgLat, nbgLng))
        assertNull(c.get(day.plusDays(1), nbgLat, nbgLng))
        // Eine neue Instanz auf derselben Datei sieht denselben Stand.
        assertEquals(six("05:10"), cache().get(day, nbgLat, nbgLng))
    }

    @Test
    fun recordAttemptWithoutScheduleKeepsNoTimes() {
        val c = cache()
        c.recordAttempt("Kein Netz", 1_000L, nbgLat, nbgLng)
        assertNull(c.get(day, nbgLat, nbgLng))
        val due = c.dueOrder(nbgLat to nbgLng, day).single()
        assertEquals("Kein Netz", due.lastError)
        assertNull(due.coveredUntil)
    }

    @Test
    fun cacheBeatsBundle() {
        val c = cache()
        c.putAll(mapOf(day to six("01:00")), nbgLat, nbgLng, 9541, null)
        val daily = TimesSource(c, bundled()).daily(nuernberg, day, zone)
        assertNotNull(daily)
        assertEquals(LocalTime.parse("01:00"), daily!!.fajr.toLocalTime())
    }

    @Test
    fun bundleUsedWhenCacheEmpty() {
        val daily = TimesSource(cache(), bundled()).daily(nuernberg, day, zone)
        assertNotNull(daily)
        assertEquals(day, daily!!.date)
    }

    @Test
    fun noPlaceGivesNull() {
        assertNull(TimesSource(cache(), bundled()).daily(DesktopSettings(), day, zone))
    }

    @Test
    fun farAwayPlaceWithoutCacheGivesNull() {
        val tokyo = DesktopSettings(placeName = "Tokio", latitude = 35.68, longitude = 139.69)
        assertNull(TimesSource(cache(), bundled()).daily(tokyo, day, zone))
    }

    @Test
    fun missingBundleResourceGivesNull() {
        val none = BundledTables { null }
        assertNull(none.get(nbgLat, nbgLng, day))
    }

    @Test
    fun refresherFailureRecordsError() = runBlocking {
        val c = cache()
        val failing = object : OfficialTimesFetcher {
            override suspend fun fetch(lat: Double, lng: Double, city: String, preferredLocationId: Int?): FetchResult =
                throw UnknownHostException("diyanet")
        }
        Refresher(c, failing) { 1_000_000L }.refresh(nuernberg, force = true)
        assertNull(c.get(day, nbgLat, nbgLng))
        assertEquals("Kein Netz", c.dueOrder(nbgLat to nbgLng, day).single().lastError)
    }

    @Test
    fun refresherSuccessStoresTimesAndClearsError() = runBlocking {
        val c = cache()
        c.recordAttempt("alt", 1L, nbgLat, nbgLng)
        val ok = object : OfficialTimesFetcher {
            override suspend fun fetch(lat: Double, lng: Double, city: String, preferredLocationId: Int?) =
                FetchResult(mapOf(day to six("05:10")), locationId = 9541)
        }
        Refresher(c, ok) { 2_000_000L }.refresh(nuernberg, force = true)
        assertEquals(six("05:10"), c.get(day, nbgLat, nbgLng))
        assertNull(c.dueOrder(nbgLat to nbgLng, day).single().lastError)
    }

    @Test
    fun refresherEmptyResultRecordsFallbackText() = runBlocking {
        val c = cache()
        val empty = object : OfficialTimesFetcher {
            override suspend fun fetch(lat: Double, lng: Double, city: String, preferredLocationId: Int?) =
                FetchResult(emptyMap(), locationId = null)
        }
        Refresher(c, empty) { 1L }.refresh(nuernberg, force = true)
        assertTrue(c.dueOrder(nbgLat to nbgLng, day).single().lastError!!.startsWith("Keine amtlichen Zeiten"))
    }
}
