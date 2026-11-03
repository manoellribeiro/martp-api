package manoellribeiro.dev.routes.mapArts

import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtsResponse
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest

interface MapArtsController {
    suspend fun saveMapArt(request: SaveMapArtRequest, image: ByteArray)

    suspend fun getExhibitionMapArts(): ExhibitionMapArtsResponse
}
