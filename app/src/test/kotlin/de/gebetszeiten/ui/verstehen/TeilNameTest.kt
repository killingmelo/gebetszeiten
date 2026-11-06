package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Test

/** DMG-Namen aller Teile. */
class TeilNameTest {
    @Test
    fun alleTeileHabenDmgName() {
        val erwartet = mapOf(
            Teil.FAJR to "Faǧr", Teil.SHURUQ to "Šurūq", Teil.DUHA to "Ḍuḥā", Teil.ZAWAL to "Zawāl",
            Teil.ZUHR to "Ẓuhr", Teil.ASR to "ʿAṣr", Teil.ISFIRAR to "Vergilben", Teil.MAGHRIB to "Maġrib",
            Teil.AWWABIN to "Auwābīn", Teil.ISHA to "ʿIšāʾ", Teil.TAHAJJUD to "Tahaǧǧud",
        )
        assertEquals(Teil.values().toSet(), erwartet.keys)
        for ((teil, name) in erwartet) assertEquals(name, teilName(teil))
    }
}
