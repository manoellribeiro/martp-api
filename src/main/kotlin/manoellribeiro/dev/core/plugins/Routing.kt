package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import manoellribeiro.dev.routes.colorPalettes.colorPalettesRoutes
import manoellribeiro.dev.routes.geocoding.geocodingRoutes
import manoellribeiro.dev.routes.mapArts.mapArtsRoutes

/** Every route goes inside `authenticate(APP_CHECK_AUTH)`: the API has no public endpoint. */
fun Application.configureRouting() {
    routing {
        authenticate(APP_CHECK_AUTH) {
            colorPalettesRoutes()
            geocodingRoutes()
            mapArtsRoutes()
        }
    }
}
