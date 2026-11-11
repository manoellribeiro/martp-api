package manoellribeiro.dev.core.data.database.entities

import manoellribeiro.dev.core.models.Continent

data class ContinentTotalEntity(
    val continent: Continent,
    val total: Long,
)
