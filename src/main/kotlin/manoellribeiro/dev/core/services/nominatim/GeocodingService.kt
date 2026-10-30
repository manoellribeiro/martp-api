package manoellribeiro.dev.core.services.nominatim

import manoellribeiro.dev.core.models.Address

interface GeocodingService {
    /** @throws manoellribeiro.dev.core.models.failures.AddressNotFoundFailure if the point has no street, city or country */
    suspend fun reverseGeocode(latitude: Double, longitude: Double, language: String?): Address
}
