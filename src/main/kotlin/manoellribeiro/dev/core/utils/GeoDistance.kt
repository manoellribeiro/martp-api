package manoellribeiro.dev.core.utils

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance in km (haversine). */
fun distanceKm(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
    val latitudeDelta = Math.toRadians(toLatitude - fromLatitude)
    val longitudeDelta = Math.toRadians(toLongitude - fromLongitude)
    val h = sin(latitudeDelta / 2).pow(2) +
        cos(Math.toRadians(fromLatitude)) * cos(Math.toRadians(toLatitude)) * sin(longitudeDelta / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
}

private const val EARTH_RADIUS_KM = 6371.0
