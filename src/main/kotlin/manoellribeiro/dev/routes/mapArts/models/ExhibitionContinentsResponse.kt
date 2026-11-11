package manoellribeiro.dev.routes.mapArts.models

import kotlinx.serialization.Serializable

@Serializable
data class ExhibitionContinentsResponse(
    val continents: List<ExhibitionContinentResponse>,
)

@Serializable
data class ExhibitionContinentResponse(
    /** Same values as the art's continent, OCEAN and UNKNOWN included. */
    val continent: String,
    val total: Long,
)
