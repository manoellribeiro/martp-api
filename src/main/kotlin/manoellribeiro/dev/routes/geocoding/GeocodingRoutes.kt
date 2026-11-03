package manoellribeiro.dev.routes.geocoding

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.geocodingRoutes() {
    val geocodingController by inject<GeocodingController>()

    get("/geocoding/reverse") {
        val parameters = call.request.queryParameters
        call.respond(
            HttpStatusCode.OK,
            geocodingController.reverseGeocode(
                latitude = parameters["latitude"],
                longitude = parameters["longitude"],
                language = parameters["language"],
            ),
        )
    }
}
