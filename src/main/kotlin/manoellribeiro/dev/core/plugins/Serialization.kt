package manoellribeiro.dev.core.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                // Older or newer app versions may send fields this API does not know yet.
                ignoreUnknownKeys = true
            }
        )
    }
}
