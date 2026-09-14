package de.gebetszeiten.wear

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCacheMigrationTest {

    private val einTag = "2026-09-12\t04:53\t06:39\t13:18\t16:50\t19:47\t21:18\n"

    @Test fun `alter Ein-Ort-Stand bekommt eine Kopfzeile`() {
        val neu = migrateLegacySchedule(einTag, 49.4521, 11.0767)
        assertNotNull(neu)
        assertTrue("Kopfzeile fehlt: $neu", neu!!.startsWith("#49.4521|11.0767|"))
        assertTrue("Die Tageszeile ist verloren gegangen", neu.contains(einTag.trim()))
    }

    @Test fun `ohne Stempel gibt es nichts zu uebernehmen`() {
        assertNull(migrateLegacySchedule(einTag, null, null))
    }

    @Test fun `leerer Altstand ergibt null`() {
        assertNull(migrateLegacySchedule(null, 49.4521, 11.0767))
    }
}
