package manoellribeiro.dev.core.data.database.entities

import kotlin.uuid.Uuid

data class UserEntity(
    val id: Uuid,
    val email: String?,
    val username: String?,
)
