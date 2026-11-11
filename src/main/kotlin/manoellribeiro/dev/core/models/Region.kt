package manoellribeiro.dev.core.models

/** The continent and country of a point. [countryCode] is ISO 3166-1 alpha-2, null in the ocean. */
data class Region(
    val continent: Continent,
    val countryCode: String?,
)
