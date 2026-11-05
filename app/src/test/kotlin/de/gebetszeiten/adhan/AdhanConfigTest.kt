package de.gebetszeiten.adhan

import de.gebetszeiten.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhanConfigTest {

    private val an = AdhanConfig(enabled = true)

    @Test
    fun `ab Werk aus`() {
        assertFalse(AdhanConfig().enabled)
        assertFalse(AppSettings.DEFAULT.adhan.enabled)
        assertNull(adhanPlan(AdhanConfig(), "DHUHR"))
    }

    @Test
    fun `Shuruq hat keinen Adhan`() {
        assertNull(adhanPlan(an.copy(prayers = AdhanConfig.ALL_PRAYERS + "SUNRISE"), "SUNRISE"))
    }

    @Test
    fun `nur gewaehlte Gebete`() {
        val nurMaghrib = an.copy(prayers = setOf("MAGHRIB"))
        assertNull(adhanPlan(nurMaghrib, "ASR"))
        assertEquals(listOf(AdhanSource.Bundled), adhanPlan(nurMaghrib, "MAGHRIB")?.tracks)
    }

    @Test
    fun `mitgelieferte Aufnahme ist Standard`() {
        AdhanConfig.ALL_PRAYERS.forEach {
            assertEquals(listOf(AdhanSource.Bundled), adhanPlan(an, it)?.tracks)
        }
    }

    @Test
    fun `eigene Aufnahme ohne Datei spielt nichts statt still die falsche`() {
        assertNull(adhanPlan(an.copy(voice = AdhanConfig.VOICE_CUSTOM), "ISHA"))
        assertNull(adhanPlan(an.copy(voice = AdhanConfig.VOICE_CUSTOM, customUri = " "), "ISHA"))
        assertEquals(
            listOf(AdhanSource.Custom("content://a")),
            adhanPlan(an.copy(voice = AdhanConfig.VOICE_CUSTOM, customUri = "content://a"), "ISHA")?.tracks,
        )
    }

    @Test
    fun `eigener Fajr-Adhan nur zu Fajr`() {
        val config = an.copy(fajrUri = "content://fajr")
        assertEquals(listOf(AdhanSource.Custom("content://fajr")), adhanPlan(config, "FAJR")?.tracks)
        assertEquals(listOf(AdhanSource.Bundled), adhanPlan(config, "DHUHR")?.tracks)
    }

    @Test
    fun `Fajr-Datei gilt auch, wenn sonst eine eigene Aufnahme fehlt`() {
        val config = an.copy(voice = AdhanConfig.VOICE_CUSTOM, fajrUri = "content://fajr")
        assertEquals(listOf(AdhanSource.Custom("content://fajr")), adhanPlan(config, "FAJR")?.tracks)
        assertNull(adhanPlan(config, "ASR"))
    }

    @Test
    fun `Dua folgt nur mit Schalter und Datei`() {
        assertEquals(1, adhanPlan(an.copy(duaEnabled = true), "ASR")?.tracks?.size)
        assertEquals(1, adhanPlan(an.copy(duaUri = "content://dua"), "ASR")?.tracks?.size)
        assertEquals(
            listOf(AdhanSource.Bundled, AdhanSource.Custom("content://dua")),
            adhanPlan(an.copy(duaEnabled = true, duaUri = "content://dua"), "ASR")?.tracks,
        )
    }

    @Test
    fun `Kanal und Lautstaerke werden geklemmt`() {
        assertEquals(AdhanConfig.STREAM_ALARM, normalizedStream(null))
        assertEquals(AdhanConfig.STREAM_ALARM, normalizedStream("RING"))
        assertEquals(AdhanConfig.STREAM_NOTIFICATION, normalizedStream("NOTIFICATION"))
        assertEquals(100, normalizedVolume(null))
        assertEquals(10, normalizedVolume(0))
        assertEquals(100, normalizedVolume(250))
        val plan = adhanPlan(an.copy(volumePercent = 40, stream = AdhanConfig.STREAM_NOTIFICATION), "ASR")!!
        assertEquals(0.4f, plan.volume, 0.0001f)
        assertEquals(AdhanConfig.STREAM_NOTIFICATION, plan.stream)
    }

    @Test
    fun `verspaeteter Wecker ruft keinen Adhan`() {
        val start = 1_000_000_000L
        assertTrue(adhanDue(start, start))
        assertTrue(adhanDue(start, start - 30_000))
        assertTrue(adhanDue(start, start + ADHAN_LATE_LIMIT_MILLIS))
        assertFalse(adhanDue(start, start + ADHAN_LATE_LIMIT_MILLIS + 1))
        assertFalse(adhanDue(start, start + 30 * 60_000L))
    }

    @Test
    fun `Nicht stoeren`() {
        // Kein Filter: immer.
        assertTrue(adhanAllowedUnderDnd(AdhanConfig.STREAM_NOTIFICATION, interruptionFilterAll = true, alarmsAllowed = false))
        // Filter aktiv: Benachrichtigungs-Kanal schweigt immer.
        assertFalse(adhanAllowedUnderDnd(AdhanConfig.STREAM_NOTIFICATION, interruptionFilterAll = false, alarmsAllowed = true))
        // Wecker-Kanal folgt der Wecker-Ausnahme.
        assertTrue(adhanAllowedUnderDnd(AdhanConfig.STREAM_ALARM, interruptionFilterAll = false, alarmsAllowed = true))
        assertFalse(adhanAllowedUnderDnd(AdhanConfig.STREAM_ALARM, interruptionFilterAll = false, alarmsAllowed = false))
    }

    @Test
    fun `Meldung bleibt still, solange der Adhan laeuft`() {
        assertEquals(AppSettings.STYLE_SILENT, entryStyleWithAdhan(AppSettings.STYLE_SOUND, adhanPlays = true))
        assertEquals(AppSettings.STYLE_SOUND, entryStyleWithAdhan(AppSettings.STYLE_SOUND, adhanPlays = false))
        assertEquals(AppSettings.STYLE_VIBRATE, entryStyleWithAdhan(AppSettings.STYLE_VIBRATE, adhanPlays = false))
    }
}
