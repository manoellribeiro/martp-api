package manoellribeiro.dev.core.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/** Also used to read JSON that doesn't come as the body, like the `art` part of a multipart upload. */
val martpJson = Json {
    // Older or newer app versions may send fields this API does not know yet.
    ignoreUnknownKeys = true
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(martpJson)
    }
}
