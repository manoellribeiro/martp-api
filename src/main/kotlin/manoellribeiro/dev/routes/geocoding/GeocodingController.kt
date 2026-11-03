package manoellribeiro.dev.routes.geocoding

import manoellribeiro.dev.routes.geocoding.models.AddressResponse

interface GeocodingController {
    suspend fun reverseGeocode(latitude: String?, longitude: String?, language: String?): AddressResponse
}
