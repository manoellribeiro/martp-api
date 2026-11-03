package manoellribeiro.dev.routes.mapArts

import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.models.ExhibitionMapArt
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtsResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionUserResponse
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsRequest
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsResponse
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest

class MapArtsControllerImpl(
    private val repository: MartpRepository,
    private val mapArtValidator: MapArtValidator,
) : MapArtsController {

    override suspend fun saveMapArt(request: SaveMapArtRequest, image: ByteArray) {
        val id = mapArtValidator.parseId(request.id)
        mapArtValidator.validateTitle(request.title)
        mapArtValidator.validateCoordinates(request.latitude, request.longitude)
        mapArtValidator.validateDateInMillis(request.dateInMillis)
        val sketchArtType = mapArtValidator.parseSketchArtType(request.sketchArtType)
        val colorsLayers = validatedColorsLayers(request.colorsLayers ?: MapLayersColorsRequest())
        mapArtValidator.validateEmail(request.email)
        mapArtValidator.validateUsername(request.username)
        mapArtValidator.validatePngImage(image)
        // Last, since it's the only check that reads the database
        validateColorPalette(request.colorPalette)

        repository.saveMapArt(
            mapArt = NewMapArtEntity(
                id = id,
                title = request.title,
                latitude = request.latitude,
                longitude = request.longitude,
                dateInMillis = request.dateInMillis,
                sketchArtType = sketchArtType,
                colorPalette = request.colorPalette,
                colorsLayers = colorsLayers,
            ),
            image = image,
            email = request.email,
            username = request.username,
        )
    }

    override suspend fun getExhibitionMapArts(): ExhibitionMapArtsResponse = ExhibitionMapArtsResponse(
        mapArts = repository.getExhibitionMapArts().map { it.toResponse() },
    )

    private fun validatedColorsLayers(layers: MapLayersColorsRequest): MapLayersColorsEntity {
        mapArtValidator.validateLayerColor("colorsLayers.roadsColor", layers.roadsColor)
        mapArtValidator.validateLayerColor("colorsLayers.buildingStrokeColor", layers.buildingStrokeColor)
        mapArtValidator.validateLayerColor("colorsLayers.buildingColor", layers.buildingColor)
        mapArtValidator.validateLayerColor("colorsLayers.roadsPathColor", layers.roadsPathColor)
        mapArtValidator.validateLayerColor("colorsLayers.waterColor", layers.waterColor)
        mapArtValidator.validateLayerColor("colorsLayers.landOthersColor", layers.landOthersColor)
        return MapLayersColorsEntity(
            roadsColor = layers.roadsColor,
            buildingStrokeColor = layers.buildingStrokeColor,
            buildingColor = layers.buildingColor,
            roadsPathColor = layers.roadsPathColor,
            waterColor = layers.waterColor,
            landOthersColor = layers.landOthersColor,
        )
    }

    private suspend fun validateColorPalette(colorPalette: String?) {
        if (colorPalette == null) return
        if (repository.getColorPalettes().none { it.name == colorPalette }) {
            throw ValidationFailure("colorPalette must be the name of an existing palette.", rejectedValue = colorPalette)
        }
    }

    private fun ExhibitionMapArt.toResponse() = ExhibitionMapArtResponse(
        id = mapArt.id.toString(),
        title = mapArt.title.ifEmpty { UNKNOWN_LOCATION_TITLE },
        latitude = mapArt.latitude,
        longitude = mapArt.longitude,
        dateInMillis = mapArt.dateInMillis,
        imageUrl = imageUrl,
        sketchArtType = mapArt.sketchArtType?.name,
        colorPalette = mapArt.colorPalette,
        colorsLayers = MapLayersColorsResponse(
            roadsColor = mapArt.colorsLayers.roadsColor,
            buildingStrokeColor = mapArt.colorsLayers.buildingStrokeColor,
            buildingColor = mapArt.colorsLayers.buildingColor,
            roadsPathColor = mapArt.colorsLayers.roadsPathColor,
            waterColor = mapArt.colorsLayers.waterColor,
            landOthersColor = mapArt.colorsLayers.landOthersColor,
        ),
        user = artistUsername?.let { ExhibitionUserResponse(username = it) },
    )

    private companion object {
        const val UNKNOWN_LOCATION_TITLE = "Unknown Location"
    }
}
