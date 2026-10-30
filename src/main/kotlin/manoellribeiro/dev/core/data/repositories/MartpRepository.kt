package manoellribeiro.dev.core.data.repositories

import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.ExhibitionMapArt

interface MartpRepository {
    suspend fun getColorPalettes(): List<ColorPaletteEntity>

    suspend fun reverseGeocode(latitude: Double, longitude: Double, language: String?): Address

    /** @throws manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure if another art has this id */
    suspend fun saveMapArt(mapArt: NewMapArtEntity, image: ByteArray, email: String?, username: String?)

    suspend fun getExhibitionMapArts(): List<ExhibitionMapArt>
}
