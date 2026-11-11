package manoellribeiro.dev.core.services.naturalEarth

import io.mockk.mockk
import io.mockk.verify
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class NaturalEarthCountriesLoaderTest {

    private lateinit var errorLogger: ErrorLogger

    @BeforeEach
    fun setup() {
        errorLogger = mockk()
    }

    @Test
    fun `if the bundled borders are used should find the region of known places without logging`() {
        // Given
        val service = RegionServiceImpl(countries = { loadNaturalEarthCountries(errorLogger) }, errorLogger = errorLogger)
        val fakePlaces = listOf(
            Triple("Paris", 48.8566 to 2.3522, Region(Continent.EUROPE, "FR")),
            Triple("Moscow", 55.7558 to 37.6173, Region(Continent.EUROPE, "RU")),
            Triple("Vladivostok (whole country decides)", 43.1155 to 131.8855, Region(Continent.EUROPE, "RU")),
            Triple("Istanbul (whole country decides)", 41.0082 to 28.9784, Region(Continent.ASIA, "TR")),
            Triple("Havana", 23.1136 to -82.3666, Region(Continent.CENTRAL_AMERICA, "CU")),
            Triple("Panama City", 8.9824 to -79.5199, Region(Continent.CENTRAL_AMERICA, "PA")),
            Triple("Guatemala City", 14.6349 to -90.5069, Region(Continent.CENTRAL_AMERICA, "GT")),
            Triple("Mexico City", 19.4326 to -99.1332, Region(Continent.NORTH_AMERICA, "MX")),
            Triple("New York", 40.7128 to -74.0060, Region(Continent.NORTH_AMERICA, "US")),
            Triple("Sao Paulo", -23.5505 to -46.6333, Region(Continent.SOUTH_AMERICA, "BR")),
            Triple("Copacabana beach", -22.9711 to -43.1822, Region(Continent.SOUTH_AMERICA, "BR")),
            Triple("Tokyo", 35.6762 to 139.6503, Region(Continent.ASIA, "JP")),
            Triple("Sydney", -33.8688 to 151.2093, Region(Continent.OCEANIA, "AU")),
            Triple("Nairobi", -1.2921 to 36.8219, Region(Continent.AFRICA, "KE")),
            Triple("Port Louis (seven seas, UN Africa)", -20.1609 to 57.5012, Region(Continent.AFRICA, "MU")),
            Triple("Male (seven seas, UN Asia)", 4.1755 to 73.5093, Region(Continent.ASIA, "MV")),
            Triple("Antarctica", -80.0 to 0.0, Region(Continent.ANTARCTICA, "AQ")),
            Triple("Mid Atlantic", 30.0 to -40.0, Region(Continent.OCEAN, null)),
        )

        // When
        val results = fakePlaces.map { (name, coordinates, _) -> name to service.findRegion(coordinates.first, coordinates.second) }

        // Then
        assertAll(fakePlaces.zip(results).map { (place, result) -> Executable { assertEquals(place.third, result.second, place.first) } })
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if Natural Earth uses a continent name this code doesn't know should return null`() {
        // When / Then
        assertNull(continentOf("Atlantis", "Fake", "Fake", null))
        assertNull(continentOf("Seven seas (open ocean)", "Fake", "Fake", null))
    }

    @Test
    fun `if the country is in North America should split Central America and the Caribbean without Mexico`() {
        // When / Then
        assertEquals(Continent.CENTRAL_AMERICA, continentOf("North America", "Caribbean", "Americas", "JM"))
        assertEquals(Continent.CENTRAL_AMERICA, continentOf("North America", "Central America", "Americas", "CR"))
        assertEquals(Continent.NORTH_AMERICA, continentOf("North America", "Central America", "Americas", "MX"))
        assertEquals(Continent.NORTH_AMERICA, continentOf("North America", "Northern America", "Americas", "CA"))
    }
}
