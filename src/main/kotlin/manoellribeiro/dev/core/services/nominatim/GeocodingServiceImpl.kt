package manoellribeiro.dev.core.services.nominatim

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.failures.AddressNotFoundFailure
import manoellribeiro.dev.core.models.failures.NominatimApiErrorFailure
import manoellribeiro.dev.core.services.nominatim.models.NominatimAddress
import manoellribeiro.dev.core.services.nominatim.models.NominatimReverseResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class GeocodingServiceImpl(
    private val httpClient: HttpClient,
    private val errorLogger: ErrorLogger,
) : GeocodingService {

    private val mutex = Mutex()
    private var lastRequestMark = TimeSource.Monotonic.markNow() - MIN_INTERVAL

    override suspend fun reverseGeocode(latitude: Double, longitude: Double, language: String?): Address {
        val response = fetch(latitude, longitude, language)
        return response.address?.toAddress() ?: throw AddressNotFoundFailure(originalExceptionMessage = null)
    }

    private suspend fun fetch(latitude: Double, longitude: Double, language: String?): NominatimReverseResponse {
        try {
            return throttled {
                httpClient.get("reverse") {
                    parameter("format", "jsonv2")
                    parameter("layer", "address")
                    parameter("lat", latitude)
                    parameter("lon", longitude)
                    parameter("accept-language", language)
                }.body()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, "GeocodingService.reverseGeocode(lat=$latitude, lon=$longitude)")
            throw NominatimApiErrorFailure(originalExceptionMessage = exception.message)
        }
    }

    private suspend fun <T> throttled(block: suspend () -> T): T = mutex.withLock {
        val elapsed = lastRequestMark.elapsedNow()
        if (elapsed < MIN_INTERVAL) delay(MIN_INTERVAL - elapsed)
        try {
            block()
        } finally {
            lastRequestMark = TimeSource.Monotonic.markNow()
        }
    }

    /** Null when all three are missing. */
    private fun NominatimAddress.toAddress(): Address? {
        val street = road.takeUnless { it.isNullOrBlank() }
        val city = listOf(city, town, village).firstOrNull { !it.isNullOrBlank() }
        val country = country.takeUnless { it.isNullOrBlank() }
        if (street == null && city == null && country == null) return null
        return Address(street = street, city = city, country = country)
    }

    private companion object {
        val MIN_INTERVAL = 1.seconds
    }
}
