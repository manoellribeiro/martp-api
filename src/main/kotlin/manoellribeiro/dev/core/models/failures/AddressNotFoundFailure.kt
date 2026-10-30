package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

/** The coordinates have no street, city or country (e.g. the middle of the ocean). The apps detect this one by name. */
class AddressNotFoundFailure(
    originalExceptionMessage: String?,
    messageToClient: String = "No address was found for these coordinates.",
) : Failure(
    originalExceptionMessage = originalExceptionMessage,
    statusCode = HttpStatusCode.NotFound,
    messageToClient = messageToClient,
)
