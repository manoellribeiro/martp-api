package manoellribeiro.dev.routes.geocoding

import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.core.utils.ValidationLimits
import manoellribeiro.dev.routes.geocoding.models.AddressResponse

class GeocodingControllerImpl(
    private val repository: MartpRepository,
    private val mapArtValidator: MapArtValidator,
    private val errorLogger: ErrorLogger,
) : GeocodingController {

    override suspend fun reverseGeocode(latitude: String?, longitude: String?, language: String?): AddressResponse {
        val parsedLatitude = parseCoordinate("latitude", latitude)
        val parsedLongitude = parseCoordinate("longitude", longitude)
        mapArtValidator.validateCoordinates(parsedLatitude, parsedLongitude)

        val address = repository.reverseGeocode(parsedLatitude, parsedLongitude, languageOrFallback(language))
        return AddressResponse(street = address.street, city = address.city, country = address.country)
    }

    private fun parseCoordinate(field: String, value: String?): Double {
        if (value == null) throw ValidationFailure("$field is required.")
        return value.toDoubleOrNull() ?: throw ValidationFailure("$field must be a number.", rejectedValue = value)
    }

    /** An invalid language never blocks the user: the lookup goes on in English and the value is logged. */
    private fun languageOrFallback(language: String?): String? {
        if (language == null) return null
        if (language.length <= ValidationLimits.MAX_LANGUAGE_LENGTH && LANGUAGE_FORMAT.matches(language)) return language
        errorLogger.logClientError(
            errorCode = "InvalidLanguage",
            message = "Using $FALLBACK_LANGUAGE instead. Rejected value: \"${language.take(ValidationLimits.MAX_LOGGED_VALUE_LENGTH)}\"",
            operation = "GeocodingController.reverseGeocode",
        )
        return FALLBACK_LANGUAGE
    }

    private companion object {
        val LANGUAGE_FORMAT = Regex("^[A-Za-z0-9-]+$")
        const val FALLBACK_LANGUAGE = "en"
    }
}
