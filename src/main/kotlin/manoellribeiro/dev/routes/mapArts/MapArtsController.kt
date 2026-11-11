package manoellribeiro.dev.routes.mapArts

import manoellribeiro.dev.routes.mapArts.models.ExhibitionContinentsResponse
import manoellribeiro.dev.routes.mapArts.models.ExhibitionMapArtsResponse
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest

interface MapArtsController {
    suspend fun saveMapArt(request: SaveMapArtRequest, image: ByteArray)

    /** [continent] is the raw query parameter: null or blank = every continent. */
    suspend fun getExhibitionMapArts(continent: String?): ExhibitionMapArtsResponse

    suspend fun getExhibitionContinents(): ExhibitionContinentsResponse

    /** A random selection of approved arts for the globe. See [manoellribeiro.dev.core.utils.GlobeArtsPicker]. */
    suspend fun getGlobeMapArts(): ExhibitionMapArtsResponse
}
