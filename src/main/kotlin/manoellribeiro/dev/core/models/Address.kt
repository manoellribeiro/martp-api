package manoellribeiro.dev.core.models

/** Result of a reverse geocoding lookup. At least one field is present: otherwise AddressNotFoundFailure is thrown. */
data class Address(
    val street: String?,
    val city: String?,
    val country: String?,
)
