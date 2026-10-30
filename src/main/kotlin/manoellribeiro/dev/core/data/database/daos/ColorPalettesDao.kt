package manoellribeiro.dev.core.data.database.daos

import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity

interface ColorPalettesDao {
    suspend fun getAll(): List<ColorPaletteEntity>
}
