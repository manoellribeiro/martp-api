package manoellribeiro.dev.core.data.repositories

import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.entities.ContinentTotalEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.ExhibitionMapArt

interface MartpRepository {
    /** Palettes the apps can use now: available and not deleted. */
    suspend fun getColorPalettes(): List<ColorPaletteEntity>

    /** Whether a saved art may use this palette name: any palette that ever existed. */
    suspend fun colorPaletteExists(name: String): Boolean

    suspend fun reverseGeocode(latitude: Double, longitude: Double, language: String?): Address

    /** @throws manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure if another art has this id */
    suspend fun saveMapArt(mapArt: NewMapArtEntity, image: ByteArray, email: String?, username: String?)

    /** [continent] null = every continent. */
    suspend fun getExhibitionMapArts(continent: Continent?): List<ExhibitionMapArt>

    /** Continents with approved arts, and how many each has. */
    suspend fun getExhibitionContinents(): List<ContinentTotalEntity>
}
