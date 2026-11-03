package manoellribeiro.dev.routes.colorPalettes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.colorPalettesRoutes() {
    val colorPalettesController by inject<ColorPalettesController>()

    get("/color-palettes") {
        call.respond(HttpStatusCode.OK, colorPalettesController.getColorPalettes())
    }
}
