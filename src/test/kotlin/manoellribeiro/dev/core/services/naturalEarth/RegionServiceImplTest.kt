package manoellribeiro.dev.core.services.naturalEarth

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import manoellribeiro.dev.core.services.naturalEarth.models.CountryShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory

class RegionServiceImplTest {

    private lateinit var errorLogger: ErrorLogger

    private val geometryFactory = GeometryFactory()
    private val fakeOceanRegion = Region(continent = Continent.OCEAN, countryCode = null)
    private val fakeUnknownRegion = Region(continent = Continent.UNKNOWN, countryCode = null)
    private val fakeEuropeRegion = Region(continent = Continent.EUROPE, countryCode = "AA")
    private val fakeAfricaRegion = Region(continent = Continent.AFRICA, countryCode = "BB")

    // A 10 x 10 degree square with a 2 x 2 hole in the middle
    private val fakeSquareCountry = CountryShape(
        geometry = geometryFactory.createPolygon(
            geometryFactory.createLinearRing(ring(0.0, 0.0, 10.0, 10.0)),
            arrayOf(geometryFactory.createLinearRing(ring(4.0, 4.0, 6.0, 6.0))),
        ),
        region = fakeEuropeRegion,
    )
    private val fakeNeighbourCountry = CountryShape(
        geometry = geometryFactory.createPolygon(ring(10.5, 0.0, 20.0, 10.0)),
        region = fakeAfricaRegion,
    )

    @BeforeEach
    fun setup() {
        errorLogger = mockk()
    }

    private fun ring(minX: Double, minY: Double, maxX: Double, maxY: Double) = arrayOf(
        Coordinate(minX, minY), Coordinate(maxX, minY), Coordinate(maxX, maxY), Coordinate(minX, maxY), Coordinate(minX, minY),
    )

    private fun serviceWith(vararg countries: CountryShape) =
        RegionServiceImpl(countries = { countries.toList() }, errorLogger = errorLogger)

    @Test
    fun `if the point is inside a country should return its region`() {
        // Given
        val service = serviceWith(fakeSquareCountry)

        // When
        val region = service.findRegion(latitude = 2.0, longitude = 2.0)

        // Then
        assertEquals(fakeEuropeRegion, region)
    }

    @Test
    fun `if the point is on the border should count it as inside`() {
        // Given
        val service = serviceWith(fakeSquareCountry)

        // When
        val region = service.findRegion(latitude = 0.0, longitude = 5.0)

        // Then
        assertEquals(fakeEuropeRegion, region)
    }

    @Test
    fun `if the point is in a hole far from the border should return OCEAN`() {
        // Given: the hole is 2 x 2 degrees, its center is about 110 km from the country
        val service = serviceWith(fakeSquareCountry.copy(geometry = geometryFactory.createPolygon(
            geometryFactory.createLinearRing(ring(0.0, 0.0, 10.0, 10.0)),
            arrayOf(geometryFactory.createLinearRing(ring(2.0, 2.0, 8.0, 8.0))),
        )))

        // When
        val region = service.findRegion(latitude = 5.0, longitude = 5.0)

        // Then
        assertEquals(fakeOceanRegion, region)
    }

    @Test
    fun `if the point is in the sea within 50 km of a country should return that country`() {
        // Given: 0.3 degree west of the square is about 33 km at this latitude
        val service = serviceWith(fakeSquareCountry)

        // When
        val region = service.findRegion(latitude = 5.0, longitude = -0.3)

        // Then
        assertEquals(fakeEuropeRegion, region)
    }

    @Test
    fun `if two countries are near should return the closest one`() {
        // Given: 10.4 is about 44 km from the square and 11 km from the neighbour
        val service = serviceWith(fakeSquareCountry, fakeNeighbourCountry)

        // When
        val region = service.findRegion(latitude = 5.0, longitude = 10.4)

        // Then
        assertEquals(fakeAfricaRegion, region)
    }

    @Test
    fun `if the point is more than 50 km from every country should return OCEAN`() {
        // Given: 1 degree west of the square is about 111 km
        val service = serviceWith(fakeSquareCountry)

        // When
        val region = service.findRegion(latitude = 5.0, longitude = -1.0)

        // Then
        assertEquals(fakeOceanRegion, region)
    }

    @Test
    fun `if the point is at a pole should not fail`() {
        // Given
        val service = serviceWith(fakeSquareCountry)

        // When
        val region = service.findRegion(latitude = 90.0, longitude = 0.0)

        // Then
        assertEquals(fakeOceanRegion, region)
    }

    @Test
    fun `if the borders fail to load should log once and answer UNKNOWN for every point`() {
        // Given
        every { errorLogger.logNonFatal(any(), any()) } just runs
        val service = RegionServiceImpl(countries = { throw IllegalStateException("fake") }, errorLogger = errorLogger)

        // When
        val first = service.findRegion(latitude = 2.0, longitude = 2.0)
        val second = service.findRegion(latitude = 50.0, longitude = 50.0)

        // Then
        assertEquals(fakeUnknownRegion, first)
        assertEquals(fakeUnknownRegion, second)
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "RegionService.loadCountries") }
    }

    @Test
    fun `if the lookup throws should log and answer UNKNOWN`() {
        // Given: a geometry whose methods aren't mocked throws when the lookup reads it
        val fakeBrokenGeometry = mockk<Geometry>()
        every { fakeBrokenGeometry.envelopeInternal } returns Envelope(0.0, 10.0, 0.0, 10.0)
        every { errorLogger.logNonFatal(any(), any()) } just runs
        val service = serviceWith(CountryShape(geometry = fakeBrokenGeometry, region = fakeEuropeRegion))

        // When
        val region = service.findRegion(latitude = 2.0, longitude = 2.0)

        // Then
        assertEquals(fakeUnknownRegion, region)
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "RegionService.findRegion") }
    }
}
