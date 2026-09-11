package de.gebetszeiten.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Das Lesen von `official/coverage.tsv` — der reine Teil davon (Task 19).
 *
 * Das Oeffnen des Assets braucht einen `Context` und ist hier nicht
 * pruefbar; die Zerlegung des Inhalts ist es. Genau dort steckt das
 * Versprechen „fehlend oder unlesbar ergibt null, keinen Absturz": eine
 * fehlende Warnung ist besser als eine App, die nicht startet.
 */
class CoverageAssetTest {

    @Test fun `liest den letzten abgedeckten Tag, nicht den ersten`() {
        assertEquals(
            LocalDate.of(2026, 12, 31),
            parseCoverageEnd(sequenceOf("2026-01-01\t2026-12-31")),
        )
    }

    @Test fun `die echte ausgelieferte Datei laesst sich lesen`() {
        // Gegenprobe gegen das Format, das die Pipeline wirklich schreibt —
        // ein Parser, der nur meine erfundene Zeile kann, ist wertlos.
        //
        // Der Pfad kommt aus der Konstanten, die auch die App oeffnet
        // (`shared-assets` ist der Asset-Wurzelordner, siehe
        // `assets.srcDir` in app/build.gradle.kts). Abgeschrieben waere er
        // eine zweite Schreibweise: ein Tippfehler in der Konstanten liesse
        // diesen Test gruen und die App die Datei nicht finden.
        val real = File("../shared-assets/${BundledOfficialSource.COVERAGE_ASSET}")
        assertTrue("$real fehlt", real.isFile)
        val ende = real.useLines { parseCoverageEnd(it) }
        assertEquals(LocalDate.parse(real.readLines().first().trim().split('\t')[1]), ende)
    }

    @Test fun `leere Datei ergibt null`() {
        assertNull(parseCoverageEnd(emptySequence()))
        assertNull(parseCoverageEnd(sequenceOf("", "   ")))
    }

    @Test fun `eine fuehrende Leerzeile wird uebersprungen, nicht gelesen`() {
        // Ohne diesen Fall bestuende „leere Datei ergibt null" aus dem
        // falschen Grund: `firstOrNull { it.isNotBlank() }` liesse sich zu
        // `firstOrNull()` verkuerzen, ohne dass ein Test faellt. Hier faellt
        // er — eine vorangestellte Leerzeile darf die Reserve nicht
        // verschwinden lassen.
        assertEquals(
            LocalDate.of(2026, 12, 31),
            parseCoverageEnd(sequenceOf("", "2026-01-01\t2026-12-31")),
        )
    }

    @Test fun `fehlende zweite Spalte ergibt null`() {
        assertNull(parseCoverageEnd(sequenceOf("2026-01-01")))
    }

    @Test fun `unparsbares Datum ergibt null`() {
        assertNull(parseCoverageEnd(sequenceOf("2026-01-01\tirgendwas")))
        assertNull(parseCoverageEnd(sequenceOf("2026-01-01\t2026-13-40")))
    }
}
