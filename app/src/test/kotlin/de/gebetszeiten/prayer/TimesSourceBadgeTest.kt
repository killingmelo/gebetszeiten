package de.gebetszeiten.prayer

import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class TimesSourceBadgeTest {

    private val sakarya = DiyanetPlace(9807, "SAKARYA", "SAKARYA", "TR", 40.78056, 30.40333)

    @Test fun `amtliche Quelle schlaegt die Berechnung, auch bei eingeschaltetem Notausgang`() {
        // Bis Aufgabe 15 hiess dieser Test "eigene Berechnung schlaegt alles"
        // und erwartete Calculated - das war die alte Bedeutung ("immer
        // rechnen"). Amtliche Zeiten gewinnen jetzt immer, wenn es sie gibt.
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", sakarya, 2.1, calculationFillsGaps = true),
        )
    }

    @Test fun `gebuendelte Tabelle hat Vorrang vor dem Index`() {
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", sakarya, 2.1, calculationFillsGaps = false),
        )
    }

    @Test fun `ohne gebuendelten Treffer greift der Index`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge(null, sakarya, 2.1, calculationFillsGaps = false),
        )
    }

    @Test fun `Index-Treffer schlaegt die Berechnung auch bei eingeschaltetem Notausgang (kein Bundle)`() {
        // Fix-Runde 1: eine Mutation, die `calculationFillsGaps -> Calculated`
        // vom letzten in den mittleren Rang schiebt (zwischen bundledName und
        // officialPlace), ueberlebte bis hierhin ALLE zehn Tests - keiner
        // pruefte "kein Bundle-Treffer, aber ein Index-Treffer, UND der
        // Notausgang an". Genau das ist der Fall eines Auslands-Orts wie
        // Serdivan (kein DE-Bundle, Index-Treffer Sakarya): ohne diesen Test
        // haette die Mutation dort "Berechnet" statt "Amtlich · Sakarya"
        // gezeigt, obwohl eine amtliche Quelle vorlag.
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 2),
            timesSourceBadge(null, sakarya, 2.1, calculationFillsGaps = true),
        )
    }

    @Test fun `Distanz wird kaufmaennisch gerundet`() {
        assertEquals(
            TimesSourceBadge.Official("Sakarya", 8),
            timesSourceBadge(null, sakarya, 7.6, calculationFillsGaps = false),
        )
    }

    @Test fun `gebuendelte Tabelle greift ohne Index-Treffer`() {
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", null, null, calculationFillsGaps = false),
        )
    }

    @Test fun `Index ohne Distanz verdraengt einen Bundle-Treffer nicht`() {
        assertEquals(
            TimesSourceBadge.Bundled("Nürnberg"),
            timesSourceBadge("Nürnberg", sakarya, null, calculationFillsGaps = false),
        )
    }

    @Test fun `ohne Quelle und mit eingeschaltetem Notausgang greift die Berechnung`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, null, null, calculationFillsGaps = true),
        )
    }

    @Test fun `ohne Quelle und ausgeschaltetem Notausgang gibt es gar keine Zeiten`() {
        // Aufgabe 15: vorher fiel dieser Fall auf Calculated zurueck - der
        // Notausgang war ja immer "an" im Sinne von "die Berechnung ist die
        // einzige Alternative". Jetzt ist er eine eigene Einstellung, und
        // ausgeschaltet heisst ausgeschaltet.
        assertEquals(
            TimesSourceBadge.None,
            timesSourceBadge(null, null, null, calculationFillsGaps = false),
        )
    }

    @Test fun `Index ohne Distanz ist ein Datenfehler und faellt auf Berechnung, wenn der Notausgang an ist`() {
        assertEquals(
            TimesSourceBadge.Calculated,
            timesSourceBadge(null, sakarya, null, calculationFillsGaps = true),
        )
    }

    @Test fun `Index ohne Distanz ist ein Datenfehler und ergibt None, wenn der Notausgang aus ist`() {
        assertEquals(
            TimesSourceBadge.None,
            timesSourceBadge(null, sakarya, null, calculationFillsGaps = false),
        )
    }
}
