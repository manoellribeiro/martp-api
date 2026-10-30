package manoellribeiro.dev.core.data.database.daos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import manoellribeiro.dev.core.data.database.MartpDatabase
import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.tables.ColorPalettesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

interface ColorPalettesDao {
    suspend fun getAll(): List<ColorPaletteEntity>
}

class ColorPalettesDaoImpl(
    private val martpDatabase: MartpDatabase,
) : ColorPalettesDao {

    override suspend fun getAll(): List<ColorPaletteEntity> = withContext(Dispatchers.IO) {
        suspendTransaction(db = martpDatabase.database) {
            ColorPalettesTable.selectAll()
                .where { ColorPalettesTable.isDeleted eq false }
                .orderBy(ColorPalettesTable.position, SortOrder.ASC)
                .map { it.toColorPaletteEntity() }
        }
    }
}

private fun ResultRow.toColorPaletteEntity() = ColorPaletteEntity(
    id = this[ColorPalettesTable.id],
    name = this[ColorPalettesTable.name],
    colors = this[ColorPalettesTable.colors],
    position = this[ColorPalettesTable.position],
)
