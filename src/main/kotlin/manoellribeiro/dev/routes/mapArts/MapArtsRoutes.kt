package manoellribeiro.dev.routes.mapArts

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.plugins.MAP_ART_UPLOADS_RATE_LIMIT
import manoellribeiro.dev.core.plugins.martpJson
import manoellribeiro.dev.core.utils.ValidationLimits
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest
import org.koin.ktor.ext.inject

fun Route.mapArtsRoutes() {
    val mapArtsController by inject<MapArtsController>()

    route("/map-arts") {
        rateLimit(MAP_ART_UPLOADS_RATE_LIMIT) {
            post {
                val upload = call.receiveMapArtUpload()
                mapArtsController.saveMapArt(upload.request, upload.image)
                call.respond(HttpStatusCode.Created)
            }
        }
        get("/exhibition") {
            val continent = call.request.queryParameters["continent"]
            call.respond(HttpStatusCode.OK, mapArtsController.getExhibitionMapArts(continent))
        }
        get("/exhibition/continents") {
            call.respond(HttpStatusCode.OK, mapArtsController.getExhibitionContinents())
        }
        get("/exhibition/globe") {
            call.respond(HttpStatusCode.OK, mapArtsController.getGlobeMapArts())
        }
    }
}

private class MapArtUpload(val request: SaveMapArtRequest, val image: ByteArray)

/** Reads the `art` JSON part and the `image` file part. Other parts are ignored. */
private suspend fun ApplicationCall.receiveMapArtUpload(): MapArtUpload {
    // Ktor fails the whole call (500) when a part passes formFieldLimit, so a declared size over the limit is rejected first.
    // Only a chunked body without Content-Length can still reach that 500.
    val contentLength = request.contentLength()
    if (contentLength != null && contentLength > ValidationLimits.MAX_MAP_ART_UPLOAD_BYTES) {
        throw ValidationFailure("The body must have at most ${ValidationLimits.MAX_MAP_ART_UPLOAD_BYTES} bytes.", rejectedValue = contentLength)
    }
    var art: String? = null
    var image: ByteArray? = null
    receiveMultipart(formFieldLimit = ValidationLimits.MAX_MAP_ART_UPLOAD_BYTES).forEachPart { part ->
        when {
            part.name == ART_PART && part is PartData.FormItem -> art = part.value
            // The web app may send the JSON as a Blob, which arrives as a file part
            part.name == ART_PART && part is PartData.FileItem -> art = part.provider().readBuffer().readByteArray().decodeToString()
            part.name == IMAGE_PART && part is PartData.FileItem -> image = part.provider().readBuffer().readByteArray()
        }
        part.release()
    }
    return MapArtUpload(
        request = decodeArt(art ?: throw ValidationFailure("The \"$ART_PART\" part is required.")),
        image = image ?: throw ValidationFailure("The \"$IMAGE_PART\" file part is required."),
    )
}

private fun decodeArt(art: String): SaveMapArtRequest = try {
    martpJson.decodeFromString<SaveMapArtRequest>(art)
} catch (exception: IllegalArgumentException) {
    // kotlinx's SerializationException is an IllegalArgumentException. Same answer as a malformed JSON body.
    throw BadRequestException("Invalid art part", exception)
}

private const val ART_PART = "art"
private const val IMAGE_PART = "image"
