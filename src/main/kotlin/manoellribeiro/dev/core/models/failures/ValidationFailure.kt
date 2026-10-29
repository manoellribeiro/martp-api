package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

/** A request field breaks a validation rule. The message names the field and the rule. */
class ValidationFailure(
    messageToClient: String,
) : Failure(
    originalExceptionMessage = messageToClient,
    statusCode = HttpStatusCode.BadRequest,
    messageToClient = messageToClient,
)
