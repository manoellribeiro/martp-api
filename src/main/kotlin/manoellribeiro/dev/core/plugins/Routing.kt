package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

/**
 * Registers the routes of every theme in `routes/`. Each theme exposes a `Route` extension
 * (e.g. `fun Route.mapArtsRoutes()` in routes/mapArts/MapArtsRoutes.kt) that is called here.
 * Every route goes inside `authenticate(APP_CHECK_AUTH)`: the API has no public endpoint.
 */
fun Application.configureRouting() {
    routing {
        authenticate(APP_CHECK_AUTH) {
        }
    }
}
