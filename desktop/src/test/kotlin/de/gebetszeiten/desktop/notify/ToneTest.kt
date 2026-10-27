package de.gebetszeiten.desktop.notify

import de.gebetszeiten.desktop.data.ToneChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneTest {

    private fun peak(s: ShortArray) = s.maxOf { abs(it.toInt()) }

    @Test
    fun builtInTonesAreShortAndAudible() {
        for (c in listOf(ToneChoice.SOFT, ToneChoice.BELL, ToneChoice.DOUBLE)) {
            val pcm = synthesize(c, 100)
            val seconds = pcm.size / 44_100.0
            assertTrue("$c dauert $seconds s", seconds in 0.5..12.0)
            assertTrue("$c zu leise", peak(pcm) > 5_000)
            // Kein Knacken am Anfang, ausgeklungen am Ende.
            assertTrue(abs(pcm[0].toInt()) < 200)
            assertTrue(abs(pcm.last().toInt()) < 400)
        }
    }

    @Test
    fun volumeScalesAndZeroIsSilent() {
        assertTrue(peak(synthesize(ToneChoice.BELL, 30)) < peak(synthesize(ToneChoice.BELL, 90)))
        assertEquals(0, peak(synthesize(ToneChoice.BELL, 0)))
    }

    @Test
    fun offAndFileProduceNoPcm() {
        assertEquals(0, synthesize(ToneChoice.OFF, 80).size)
        assertEquals(0, synthesize(ToneChoice.FILE, 80).size)
    }
}
