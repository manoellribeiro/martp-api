package manoellribeiro.dev.core.services.naturalEarth

import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import manoellribeiro.dev.core.services.naturalEarth.models.CountryShape
import manoellribeiro.dev.core.utils.distanceKm
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Location
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.operation.distance.DistanceOp
import kotlin.math.cos

/** Looks the point up in memory: no outside call. The index is built once at startup and only read afterwards. */
class RegionServiceImpl(
    countries: () -> List<CountryShape>,
    private val errorLogger: ErrorLogger,
) : RegionService {

    private val geometryFactory = GeometryFactory()

    // null when the borders couldn't be read: the API still starts and every art is saved as UNKNOWN
    private val index: STRtree? = try {
        STRtree().apply {
            countries().forEach { insert(it.geometry.envelopeInternal, it) }
            build()
        }
    } catch (exception: Exception) {
        errorLogger.logNonFatal(exception, "RegionService.loadCountries")
        null
    }

    override fun findRegion(latitude: Double, longitude: Double): Region {
        val index = index ?: return UNKNOWN_REGION
        return try {
            val point = Coordinate(longitude, latitude)
            val country = index.countryContaining(point) ?: index.nearestCountry(point)
            country?.region ?: OCEAN_REGION
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, "RegionService.findRegion")
            UNKNOWN_REGION
        }
    }

    // A point on a border counts as inside
    private fun STRtree.countryContaining(point: Coordinate): CountryShape? =
        candidates(Envelope(point)).firstOrNull { SimplePointInAreaLocator.locate(point, it.geometry) != Location.EXTERIOR }

    // Natural Earth's simplified coasts can leave a beach or a pier slightly in the sea
    private fun STRtree.nearestCountry(point: Coordinate): CountryShape? {
        val latitudeDegrees = COAST_TOLERANCE_KM / KM_PER_DEGREE
        val longitudeDegrees = latitudeDegrees / cos(Math.toRadians(point.y)).coerceAtLeast(MIN_COSINE)
        val searchArea = Envelope(point).apply { expandBy(longitudeDegrees, latitudeDegrees) }
        val pointGeometry = geometryFactory.createPoint(point)
        return candidates(searchArea)
            .map { country ->
                val nearest = DistanceOp.nearestPoints(country.geometry, pointGeometry)[0]
                country to distanceKm(fromLatitude = point.y, fromLongitude = point.x, toLatitude = nearest.y, toLongitude = nearest.x)
            }
            .filter { (_, distance) -> distance <= COAST_TOLERANCE_KM }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun STRtree.candidates(area: Envelope): List<CountryShape> = query(area).filterIsInstance<CountryShape>()

    private companion object {
        val OCEAN_REGION = Region(continent = Continent.OCEAN, countryCode = null)
        val UNKNOWN_REGION = Region(continent = Continent.UNKNOWN, countryCode = null)
        const val COAST_TOLERANCE_KM = 50.0
        const val KM_PER_DEGREE = 111.32
        const val MIN_COSINE = 0.01 // near the poles a degree of longitude is almost 0 km
    }
}
