package manoellribeiro.dev.core.data.database.daos

import manoellribeiro.dev.core.data.database.entities.ContinentTotalEntity
import manoellribeiro.dev.core.data.database.entities.ExhibitionMapArtEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import kotlin.uuid.Uuid

interface MapArtsDao {
    /** Also true for a deleted art: its id stays taken, and so does its image key. */
    suspend fun existsById(id: Uuid): Boolean

    /**
     * Saves the art and its user (matched by the pair, or created) in one transaction.
     * Returns false, saving nothing, if another art already has this id.
     */
    suspend fun insertWithUser(mapArt: NewMapArtEntity, imageKey: String, region: Region, email: String?, username: String?): Boolean

    /** [continent] null = every continent. */
    suspend fun getApprovedForExhibition(continent: Continent?): List<ExhibitionMapArtEntity>

    /** Approved arts that aren't deleted, per continent. Continents without arts are left out. Biggest total first, then by name. */
    suspend fun getApprovedTotalsByContinent(): List<ContinentTotalEntity>
}
