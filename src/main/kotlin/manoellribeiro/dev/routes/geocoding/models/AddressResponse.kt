package manoellribeiro.dev.routes.geocoding.models

import kotlinx.serialization.Serializable

@Serializable
data class AddressResponse(
    val street: String?,
    val city: String?,
    val country: String?,
)
