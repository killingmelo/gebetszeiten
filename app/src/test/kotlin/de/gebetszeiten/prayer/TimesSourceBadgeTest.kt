package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class TimesSourceBadgeTest {

    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)

    @Test fun `eigene Berechnung schlaegt alles`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge("Nürnberg", sakarya, 2.1, useCalculated = true),
        )
    }

    @Test fun `Index hat Vorrang vor der gebuendelten Tabelle`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge("Nürnberg", sakarya, 2.1, useCalculated = false),
        )
    }

    @Test fun `Index liefert Standortname und gerundete Distanz`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge(null, sakarya, 2.1, useCalculated = false),
        )
    }

    @Test fun `Distanz wird kaufmaennisch gerundet`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 8),
            timesSourceBadge(null, sakarya, 7.6, useCalculated = false),
        )
    }

    @Test fun `gebuendelte Tabelle greift ohne Index-Treffer`() {
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", null, null, useCalculated = false),
        )
    }

    @Test fun `ohne Quelle bleibt Berechnung`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, null, null, useCalculated = false),
        )
    }

    @Test fun `Index ohne Distanz ist ein Datenfehler und faellt auf Berechnung`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, sakarya, null, useCalculated = false),
        )
    }
}
