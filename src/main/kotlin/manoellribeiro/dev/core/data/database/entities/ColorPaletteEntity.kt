package manoellribeiro.dev.core.data.database.entities

import kotlin.uuid.Uuid

data class ColorPaletteEntity(
    val id: Uuid,
    val name: String,
    val colors: List<String>,
    val position: Int,
)
