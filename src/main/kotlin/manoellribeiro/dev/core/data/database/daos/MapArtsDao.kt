package manoellribeiro.dev.core.data.database.daos

import manoellribeiro.dev.core.data.database.entities.ExhibitionMapArtEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import kotlin.uuid.Uuid

interface MapArtsDao {
    /** Also true for a deleted art: its id stays taken, and so does its image key. */
    suspend fun existsById(id: Uuid): Boolean

    /**
     * Saves the art and its user (matched by the pair, or created) in one transaction.
     * Returns false, saving nothing, if another art already has this id.
     */
    suspend fun insertWithUser(mapArt: NewMapArtEntity, email: String?, username: String?): Boolean

    suspend fun getApprovedForExhibition(): List<ExhibitionMapArtEntity>
}
