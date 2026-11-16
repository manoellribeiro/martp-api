package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import manoellribeiro.dev.routes.colorPalettes.colorPalettesRoutes
import manoellribeiro.dev.routes.geocoding.geocodingRoutes
import manoellribeiro.dev.routes.health.healthRoutes
import manoellribeiro.dev.routes.mapArts.mapArtsRoutes

/**
 * Every route goes inside `authenticate(APP_CHECK_AUTH)`, except `/health` (the keep-warm ping),
 * which returns nothing and touches no data.
 */
fun Application.configureRouting() {
    routing {
        healthRoutes()
        authenticate(APP_CHECK_AUTH) {
            colorPalettesRoutes()
            geocodingRoutes()
            mapArtsRoutes()
        }
    }
}
