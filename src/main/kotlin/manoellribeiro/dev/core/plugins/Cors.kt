package manoellribeiro.dev.core.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import manoellribeiro.dev.core.config.CorsConfig
import org.koin.ktor.ext.inject

/** Only the web app needs CORS (the Android app is not a browser). Origins come from `cors.allowedOrigins`. */
fun Application.configureCors() {
    val corsConfig by inject<CorsConfig>()
    install(CORS) {
        corsConfig.allowedOrigins.forEach { origin ->
            val parts = origin.split("://", limit = 2)
            require(parts.size == 2) { "Invalid CORS origin '$origin', expected scheme://host[:port]" }
            allowHost(host = parts[1], schemes = listOf(parts[0]))
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(APP_CHECK_HEADER)
    }
}
