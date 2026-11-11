package manoellribeiro.dev.core.data.database.entities

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.SketchArtType
import kotlin.uuid.Uuid

data class MapArtEntity(
    val id: Uuid,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val dateInMillis: Long,
    val imageKey: String,
    val continent: Continent,
    val countryCode: String?,
    val sketchArtType: SketchArtType?,
    val colorPalette: String?,
    val colorsLayers: MapLayersColorsEntity,
    val artistId: Uuid?,
    val isApprovedForTheExhibition: Boolean,
)

/** An art about to be saved: the database decides its artist and approval, the repository its image key and region. */
data class NewMapArtEntity(
    val id: Uuid,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val dateInMillis: Long,
    val sketchArtType: SketchArtType?,
    val colorPalette: String?,
    val colorsLayers: MapLayersColorsEntity,
)

/** An approved art with the only artist data the exhibition may show. The email is never selected for it. */
data class ExhibitionMapArtEntity(
    val mapArt: MapArtEntity,
    val artistUsername: String?,
)

/** The six map_layer_*_color columns, grouped like Room's @Embedded(prefix = "map_layer_") on Android. */
data class MapLayersColorsEntity(
    val roadsColor: String?,
    val buildingStrokeColor: String?,
    val buildingColor: String?,
    val roadsPathColor: String?,
    val waterColor: String?,
    val landOthersColor: String?,
)
