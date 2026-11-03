package manoellribeiro.dev.routes.mapArts.models

import kotlinx.serialization.Serializable

@Serializable
data class ExhibitionMapArtsResponse(
    val mapArts: List<ExhibitionMapArtResponse>,
)

@Serializable
data class ExhibitionMapArtResponse(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val dateInMillis: Long,
    val imageUrl: String,
    val sketchArtType: String?,
    val colorPalette: String?,
    val colorsLayers: MapLayersColorsResponse,
    /** null when the art is anonymous, or its user is deleted or has no username. */
    val user: ExhibitionUserResponse?,
)

@Serializable
data class MapLayersColorsResponse(
    val roadsColor: String?,
    val buildingStrokeColor: String?,
    val buildingColor: String?,
    val roadsPathColor: String?,
    val waterColor: String?,
    val landOthersColor: String?,
)

/** Only the username: the email never leaves the API. */
@Serializable
data class ExhibitionUserResponse(
    val username: String,
)
