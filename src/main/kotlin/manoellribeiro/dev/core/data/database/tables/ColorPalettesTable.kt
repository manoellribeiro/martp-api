package manoellribeiro.dev.core.data.database.tables

import org.jetbrains.exposed.v1.core.Table

object ColorPalettesTable : Table("color_palettes") {
    val id = uuid("id")
    val name = text("name")
    val colors = array<String>("colors")
    val position = integer("position")
    val isDeleted = bool("is_deleted")

    override val primaryKey = PrimaryKey(id)
}
