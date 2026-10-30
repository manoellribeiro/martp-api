package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

class ImageStorageErrorFailure(
    originalExceptionMessage: String?,
    messageToClient: String = "The image could not be stored right now. Please try again later.",
) : Failure(
    originalExceptionMessage = originalExceptionMessage,
    statusCode = HttpStatusCode.BadGateway,
    messageToClient = messageToClient,
)
