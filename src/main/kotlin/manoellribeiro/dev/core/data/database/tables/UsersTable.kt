package manoellribeiro.dev.core.data.database.tables

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = uuid("id")
    val email = text("email").nullable()
    val username = text("username").nullable()
    val isDeleted = bool("is_deleted")

    override val primaryKey = PrimaryKey(id)
}
