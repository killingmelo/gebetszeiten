package de.gebetszeiten.ui.verstehen

import org.junit.Assert.assertEquals
import org.junit.Test

/** Namen aller Teile: DMG, außer „Zenit“ und „Vor Sonnenuntergang“ (06.11.2026). */
class TeilNameTest {
    @Test
    fun alleTeileHabenDmgName() {
        val erwartet = mapOf(
            Teil.FAJR to "Faǧr", Teil.SHURUQ to "Šurūq", Teil.DUHA to "Ḍuḥā", Teil.ZAWAL to "Zenit",
            Teil.ZUHR to "Ẓuhr", Teil.ASR to "ʿAṣr", Teil.ISFIRAR to "Vor Sonnenuntergang", Teil.MAGHRIB to "Maġrib",
            Teil.AWWABIN to "Auwābīn", Teil.ISHA to "ʿIšāʾ", Teil.TAHAJJUD to "Tahaǧǧud",
        )
        assertEquals(Teil.values().toSet(), erwartet.keys)
        for ((teil, name) in erwartet) assertEquals(name, teilName(teil))
    }
}
