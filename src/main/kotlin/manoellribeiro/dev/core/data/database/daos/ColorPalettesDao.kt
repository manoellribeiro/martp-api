package manoellribeiro.dev.core.data.database.daos

import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity

interface ColorPalettesDao {
    /** Palettes offered to the apps: available and not deleted, ordered by position. */
    suspend fun getAvailable(): List<ColorPaletteEntity>

    /** Whether a palette ever had this exact name, including ones that aren't available or are deleted. */
    suspend fun existsByName(name: String): Boolean
}
