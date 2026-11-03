package manoellribeiro.dev.routes.mapArts.models

import kotlinx.serialization.Serializable

/** The `art` part of `POST /map-arts`. The image comes in its own `image` part. */
@Serializable
data class SaveMapArtRequest(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val dateInMillis: Long,
    val sketchArtType: String? = null,
    val colorPalette: String? = null,
    val colorsLayers: MapLayersColorsRequest? = null,
    val email: String? = null,
    val username: String? = null,
)

@Serializable
data class MapLayersColorsRequest(
    val roadsColor: String? = null,
    val buildingStrokeColor: String? = null,
    val buildingColor: String? = null,
    val roadsPathColor: String? = null,
    val waterColor: String? = null,
    val landOthersColor: String? = null,
)
