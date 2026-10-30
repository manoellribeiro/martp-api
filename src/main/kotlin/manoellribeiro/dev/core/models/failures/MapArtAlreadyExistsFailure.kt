package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

/** The id sent by the app is already used by another art. */
class MapArtAlreadyExistsFailure(
    originalExceptionMessage: String?,
    messageToClient: String = "A map art with this id already exists.",
) : Failure(
    originalExceptionMessage = originalExceptionMessage,
    statusCode = HttpStatusCode.Conflict,
    messageToClient = messageToClient,
)
