package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.Continent

/** Rules for the exhibition globe. */
object GlobeLimits {
    /** Two pins are never closer than this (any continents). */
    const val MIN_DISTANCE_KM = 300.0

    /** Most pins per continent, roughly by size. UNKNOWN stays off the globe until fixed by hand. */
    val MAX_ARTS_PER_CONTINENT: Map<Continent, Int> = mapOf(
        Continent.ASIA to 8,
        Continent.AFRICA to 7,
        Continent.NORTH_AMERICA to 6,
        Continent.SOUTH_AMERICA to 5,
        Continent.EUROPE to 5,
        Continent.OCEANIA to 4,
        Continent.CENTRAL_AMERICA to 2,
        Continent.ANTARCTICA to 2,
        Continent.OCEAN to 2,
        Continent.UNKNOWN to 0,
    )
}
