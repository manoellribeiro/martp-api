package manoellribeiro.dev.core.services.naturalEarth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import manoellribeiro.dev.core.services.naturalEarth.models.CountryShape
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon

/**
 * Reads the Natural Earth 1:50m countries bundled in the resources (public domain, trimmed to the fields used here).
 * A country that can't be read is skipped and logged. A missing file throws, and [RegionServiceImpl] handles that.
 */
fun loadNaturalEarthCountries(errorLogger: ErrorLogger): List<CountryShape> {
    val text = CountryShape::class.java.getResourceAsStream(COUNTRIES_RESOURCE)?.use { it.readBytes().decodeToString() }
        ?: error("$COUNTRIES_RESOURCE is missing from the resources")
    return Json.parseToJsonElement(text).jsonObject.getValue("features").jsonArray.mapNotNull { feature ->
        try {
            feature.jsonObject.toCountryShape(errorLogger)
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, "NaturalEarthCountriesLoader.loadNaturalEarthCountries")
            null
        }
    }
}

/** Natural Earth's continent, plus the Central America split: Caribbean + UN Central America without Mexico. null = unknown name. */
internal fun continentOf(continent: String, subregion: String, unRegion: String, countryCode: String?): Continent? =
    when (continent) {
        "Africa" -> Continent.AFRICA
        "Antarctica" -> Continent.ANTARCTICA
        "Asia" -> Continent.ASIA
        "Europe" -> Continent.EUROPE
        "Oceania" -> Continent.OCEANIA
        "South America" -> Continent.SOUTH_AMERICA
        "North America" ->
            if (subregion == "Caribbean" || (subregion == "Central America" && countryCode != MEXICO)) {
                Continent.CENTRAL_AMERICA
            } else {
                Continent.NORTH_AMERICA
            }
        // Remote islands (Mauritius, Maldives, Saint Helena, ...) go by their UN region
        "Seven seas (open ocean)" -> when (unRegion) {
            "Africa" -> Continent.AFRICA
            "Asia" -> Continent.ASIA
            "Americas" -> Continent.SOUTH_AMERICA
            else -> null
        }
        else -> null
    }

private val geometryFactory = GeometryFactory()

private fun JsonObject.toCountryShape(errorLogger: ErrorLogger): CountryShape {
    val properties = getValue("properties").jsonObject
    fun property(name: String) = properties.getValue(name).jsonPrimitive.content
    // Natural Earth uses -99 when a place has no ISO code
    val countryCode = property("ISO_A2_EH").takeIf { COUNTRY_CODE_FORMAT.matches(it) }
    val continent = continentOf(property("CONTINENT"), property("SUBREGION"), property("REGION_UN"), countryCode)
        ?: Continent.UNKNOWN.also {
            errorLogger.logNonFatal(
                IllegalStateException("Unknown Natural Earth continent for $countryCode: ${property("CONTINENT")} / ${property("REGION_UN")}"),
                "NaturalEarthCountriesLoader.loadNaturalEarthCountries",
            )
        }
    return CountryShape(
        geometry = getValue("geometry").jsonObject.toGeometry(),
        region = Region(continent = continent, countryCode = countryCode),
    )
}

private fun JsonObject.toGeometry(): Geometry {
    val coordinates = getValue("coordinates").jsonArray
    return when (val type = getValue("type").jsonPrimitive.content) {
        "Polygon" -> coordinates.toPolygon()
        "MultiPolygon" -> geometryFactory.createMultiPolygon(coordinates.map { it.jsonArray.toPolygon() }.toTypedArray())
        else -> error("Unexpected Natural Earth geometry type: $type")
    }
}

// The first ring is the border, the others are holes (e.g. Lesotho inside South Africa)
private fun JsonArray.toPolygon(): Polygon {
    val rings = map { ring -> geometryFactory.createLinearRing(ring.jsonArray.map { it.jsonArray.toCoordinate() }.toTypedArray()) }
    return geometryFactory.createPolygon(rings.first(), rings.drop(1).toTypedArray())
}

// GeoJSON order is [longitude, latitude]
private fun JsonArray.toCoordinate() = Coordinate(this[0].jsonPrimitive.double, this[1].jsonPrimitive.double)

private const val COUNTRIES_RESOURCE = "/naturalEarth/countries.geojson"
private const val MEXICO = "MX"
private val COUNTRY_CODE_FORMAT = Regex("^[A-Z]{2}$")
