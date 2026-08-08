package de.gebetszeiten.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoGeocoderTest {

    // Gekürzte echte Antwort von geocoding-api.open-meteo.com (name=esenköy&language=de).
    private val body = """
        {"results":[
          {"id":747342,"name":"Esenköy","latitude":40.61695,"longitude":28.95713,
           "country_code":"tr","country":"Türkei","admin1":"Yalova"},
          {"id":316406,"name":"Esenköy","latitude":37.80976,"longitude":28.34974,
           "country_code":"tr","country":"Türkei","admin1":"Aydın"},
          {"id":1,"name":"","latitude":0.0,"longitude":0.0,"country_code":"xx"}
        ],"generationtime_ms":0.7}
    """.trimIndent()

    @Test
    fun parsesResultsWithRegionAndSkipsEmptyNames() {
        val cities = OpenMeteoGeocoder.parseGeocodingResponse(body)
        assertEquals(2, cities.size)
        val esenkoy = cities.first()
        assertEquals("Esenköy", esenkoy.name)
        assertEquals("TR", esenkoy.country)
        assertEquals("Yalova", esenkoy.region)
        assertEquals(40.61695, esenkoy.latitude, 1e-5)
        assertEquals(28.95713, esenkoy.longitude, 1e-5)
    }

    @Test
    fun emptyOrBrokenBodyYieldsEmptyList() {
        assertTrue(OpenMeteoGeocoder.parseGeocodingResponse("{}").isEmpty())
        assertTrue(OpenMeteoGeocoder.parseGeocodingResponse("""{"results":[]}""").isEmpty())
    }
}
