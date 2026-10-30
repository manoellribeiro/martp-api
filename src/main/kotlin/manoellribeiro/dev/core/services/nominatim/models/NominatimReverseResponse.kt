package manoellribeiro.dev.core.services.nominatim.models

import kotlinx.serialization.Serializable

/** Nominatim's JSON, only the fields we use. A point without an address answers 200 with an `error` field and no `address`. */
@Serializable
data class NominatimReverseResponse(
    val address: NominatimAddress? = null,
)

@Serializable
data class NominatimAddress(
    val road: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val country: String? = null,
)
