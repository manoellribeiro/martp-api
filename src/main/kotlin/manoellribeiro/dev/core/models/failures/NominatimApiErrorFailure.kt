package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

class NominatimApiErrorFailure(
    originalExceptionMessage: String?,
    messageToClient: String = "The address could not be fetched right now. Please try again later.",
) : Failure(
    originalExceptionMessage = originalExceptionMessage,
    statusCode = HttpStatusCode.BadGateway,
    messageToClient = messageToClient,
)
