package manoellribeiro.dev.routes.mapArts

import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.models.ExhibitionMapArt
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.GlobeArtsPicker
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.routes.mapArts.models.ExhibitionContinentResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionContinentsResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtsResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionUserResponse
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsRequest
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsResponse
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest

class MapArtsControllerImpl(
    private val repository: MartpRepository,
    private val mapArtValidator: MapArtValidator,
    private val globeArtsPicker: GlobeArtsPicker,
) : MapArtsController {

    override suspend fun saveMapArt(request: SaveMapArtRequest, image: ByteArray) {
        val id = mapArtValidator.parseId(request.id)
        val title = mapArtValidator.parseTitle(request.title)
        mapArtValidator.validateCoordinates(request.latitude, request.longitude)
        mapArtValidator.validateDateInMillis(request.dateInMillis)
        val sketchArtType = mapArtValidator.parseSketchArtType(request.sketchArtType)
        val colorsLayers = parsedColorsLayers(request.colorsLayers ?: MapLayersColorsRequest())
        val colorPalette = mapArtValidator.parseColorPalette(request.colorPalette)
        val email = mapArtValidator.parseEmail(request.email)
        val username = mapArtValidator.parseUsername(request.username)
        mapArtValidator.validatePngImage(image)
        // Last, since it's the only check that reads the database
        validateColorPalette(colorPalette)

        repository.saveMapArt(
            mapArt = NewMapArtEntity(
                id = id,
                title = title,
                latitude = request.latitude,
                longitude = request.longitude,
                dateInMillis = request.dateInMillis,
                sketchArtType = sketchArtType,
                colorPalette = colorPalette,
                colorsLayers = colorsLayers,
            ),
            image = image,
            email = email,
            username = username,
        )
    }

    override suspend fun getExhibitionMapArts(continent: String?): ExhibitionMapArtsResponse = ExhibitionMapArtsResponse(
        mapArts = repository.getExhibitionMapArts(continent = mapArtValidator.parseContinent(continent)).map { it.toResponse() },
    )

    override suspend fun getExhibitionContinents(): ExhibitionContinentsResponse = ExhibitionContinentsResponse(
        continents = repository.getExhibitionContinents().map {
            ExhibitionContinentResponse(continent = it.continent.name, total = it.total)
        },
    )

    override suspend fun getGlobeMapArts(): ExhibitionMapArtsResponse = ExhibitionMapArtsResponse(
        mapArts = globeArtsPicker.pick(repository.getExhibitionMapArts(continent = null)).map { it.toResponse() },
    )

    private fun parsedColorsLayers(layers: MapLayersColorsRequest) = MapLayersColorsEntity(
        roadsColor = mapArtValidator.parseLayerColor("colorsLayers.roadsColor", layers.roadsColor),
        buildingStrokeColor = mapArtValidator.parseLayerColor("colorsLayers.buildingStrokeColor", layers.buildingStrokeColor),
        buildingColor = mapArtValidator.parseLayerColor("colorsLayers.buildingColor", layers.buildingColor),
        roadsPathColor = mapArtValidator.parseLayerColor("colorsLayers.roadsPathColor", layers.roadsPathColor),
        waterColor = mapArtValidator.parseLayerColor("colorsLayers.waterColor", layers.waterColor),
        landOthersColor = mapArtValidator.parseLayerColor("colorsLayers.landOthersColor", layers.landOthersColor),
    )

    private suspend fun validateColorPalette(colorPalette: String?) {
        if (colorPalette == null) return
        if (!repository.colorPaletteExists(colorPalette)) {
            throw ValidationFailure("colorPalette must be the name of an existing palette.", rejectedValue = colorPalette)
        }
    }

    private fun ExhibitionMapArt.toResponse() = ExhibitionMapArtResponse(
        id = mapArt.id.toString(),
        title = mapArt.title.ifEmpty { UNKNOWN_LOCATION_TITLE },
        latitude = mapArt.latitude,
        longitude = mapArt.longitude,
        dateInMillis = mapArt.dateInMillis,
        continent = mapArt.continent.name,
        countryCode = mapArt.countryCode,
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
