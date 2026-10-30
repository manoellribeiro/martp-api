package manoellribeiro.dev.core.data.database.daos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import manoellribeiro.dev.core.data.database.MartpDatabase
import manoellribeiro.dev.core.data.database.entities.ExhibitionMapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.data.database.tables.MapArtsTable
import manoellribeiro.dev.core.data.database.tables.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotDistinctFrom
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import kotlin.uuid.Uuid

class MapArtsDaoImpl(
    private val martpDatabase: MartpDatabase,
) : MapArtsDao {

    override suspend fun existsById(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        suspendTransaction(db = martpDatabase.database) {
            MapArtsTable.select(MapArtsTable.id)
                .where { MapArtsTable.id eq id }
                .any()
        }
    }

    override suspend fun insertWithUser(
        mapArt: NewMapArtEntity,
        email: String?,
        username: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        // READ COMMITTED: with the pool's REPEATABLE READ, a user created by a concurrent upload
        // would make insertIgnore fail with a serialization error instead of doing nothing.
        suspendTransaction(db = martpDatabase.database, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val artistId = if (email == null && username == null) null else findOrCreateUserId(email, username)
            val insertedCount = MapArtsTable.insertIgnore {
                it[id] = mapArt.id
                it[title] = mapArt.title
                it[description] = mapArt.description
                it[latitude] = mapArt.latitude
                it[longitude] = mapArt.longitude
                it[dateInMillis] = mapArt.dateInMillis
                it[imageKey] = mapArt.imageKey
                it[sketchArtType] = mapArt.sketchArtType
                it[colorPalette] = mapArt.colorPalette
                it[mapLayerRoadsColor] = mapArt.colorsLayers.roadsColor
                it[mapLayerBuildingStrokeColor] = mapArt.colorsLayers.buildingStrokeColor
                it[mapLayerBuildingColor] = mapArt.colorsLayers.buildingColor
                it[mapLayerRoadsPathColor] = mapArt.colorsLayers.roadsPathColor
                it[mapLayerWaterColor] = mapArt.colorsLayers.waterColor
                it[mapLayerLandOthersColor] = mapArt.colorsLayers.landOthersColor
                it[this.artistId] = artistId
            }.insertedCount
            if (insertedCount == 0) rollback() // don't keep a user created only for this art
            insertedCount > 0
        }
    }

    override suspend fun getApprovedForExhibition(): List<ExhibitionMapArtEntity> = withContext(Dispatchers.IO) {
        suspendTransaction(db = martpDatabase.database) {
            MapArtsTable
                .join(
                    otherTable = UsersTable,
                    joinType = JoinType.LEFT,
                    onColumn = MapArtsTable.artistId,
                    otherColumn = UsersTable.id,
                    additionalConstraint = { UsersTable.isDeleted eq false },
                )
                .select(MapArtsTable.columns + UsersTable.username)
                .where { (MapArtsTable.isApprovedForTheExhibition eq true) and (MapArtsTable.isDeleted eq false) }
                .orderBy(MapArtsTable.dateInMillis, SortOrder.DESC)
                .map { ExhibitionMapArtEntity(mapArt = it.toMapArtEntity(), artistUsername = it[UsersTable.username]) }
        }
    }

    private fun Transaction.findOrCreateUserId(email: String?, username: String?): Uuid {
        UsersTable.insertIgnore {
            it[UsersTable.email] = email
            it[UsersTable.username] = username
        }
        return UsersTable.select(UsersTable.id)
            .where {
                (UsersTable.email isNotDistinctFrom QueryParameter(email, UsersTable.email.columnType)) and
                    (UsersTable.username isNotDistinctFrom QueryParameter(username, UsersTable.username.columnType)) and
                    (UsersTable.isDeleted eq false)
            }
            .single()[UsersTable.id]
    }
}

private fun ResultRow.toMapArtEntity() = MapArtEntity(
    id = this[MapArtsTable.id],
    title = this[MapArtsTable.title],
    description = this[MapArtsTable.description],
    latitude = this[MapArtsTable.latitude],
    longitude = this[MapArtsTable.longitude],
    dateInMillis = this[MapArtsTable.dateInMillis],
    imageKey = this[MapArtsTable.imageKey],
    sketchArtType = this[MapArtsTable.sketchArtType],
    colorPalette = this[MapArtsTable.colorPalette],
    colorsLayers = MapLayersColorsEntity(
        roadsColor = this[MapArtsTable.mapLayerRoadsColor],
        buildingStrokeColor = this[MapArtsTable.mapLayerBuildingStrokeColor],
        buildingColor = this[MapArtsTable.mapLayerBuildingColor],
        roadsPathColor = this[MapArtsTable.mapLayerRoadsPathColor],
        waterColor = this[MapArtsTable.mapLayerWaterColor],
        landOthersColor = this[MapArtsTable.mapLayerLandOthersColor],
    ),
    artistId = this[MapArtsTable.artistId],
    isApprovedForTheExhibition = this[MapArtsTable.isApprovedForTheExhibition],
)
