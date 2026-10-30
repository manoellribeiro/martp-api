package manoellribeiro.dev.core.models

import manoellribeiro.dev.core.data.database.entities.MapArtEntity

data class ExhibitionMapArt(
    val mapArt: MapArtEntity,
    val artistUsername: String?,
    val imageUrl: String,
)
