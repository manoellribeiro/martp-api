package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

class DatabaseErrorFailure(
    originalExceptionMessage: String?,
    messageToClient: String = "Something went wrong. Please try again later.",
) : Failure(
    originalExceptionMessage = originalExceptionMessage,
    statusCode = HttpStatusCode.InternalServerError,
    messageToClient = messageToClient,
)
