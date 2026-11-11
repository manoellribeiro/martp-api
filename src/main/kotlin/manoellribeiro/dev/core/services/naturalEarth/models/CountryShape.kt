package manoellribeiro.dev.core.services.naturalEarth.models

import manoellribeiro.dev.core.models.Region
import org.locationtech.jts.geom.Geometry

/** One Natural Earth country: its borders in [longitude, latitude] degrees, and the region it belongs to. */
data class CountryShape(
    val geometry: Geometry,
    val region: Region,
)
