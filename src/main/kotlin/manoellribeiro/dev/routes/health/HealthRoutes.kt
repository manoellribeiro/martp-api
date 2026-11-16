package manoellribeiro.dev.routes.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val HEALTH_PATH = "/health"

/**
 * The only public route: Cloud Scheduler calls it every 5 minutes so Cloud Run keeps the instance warm.
 * It touches neither the database nor any data, so it wakes nothing else (Neon keeps sleeping).
 */
fun Route.healthRoutes() {
    get(HEALTH_PATH) {
        call.respond(HttpStatusCode.NoContent)
    }
}
