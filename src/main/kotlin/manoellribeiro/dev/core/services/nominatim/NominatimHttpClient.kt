package manoellribeiro.dev.core.services.nominatim

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import manoellribeiro.dev.core.config.GeocodingConfig

fun createNominatimHttpClient(config: GeocodingConfig): HttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
    }
    defaultRequest {
        url(config.baseUrl + "/")
        header(HttpHeaders.UserAgent, config.userAgent)
    }
}
