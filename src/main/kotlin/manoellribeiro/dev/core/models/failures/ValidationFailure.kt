package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode
import manoellribeiro.dev.core.utils.ValidationLimits

/**
 * A request field breaks a validation rule. The client's message names the field and the rule.
 * [rejectedValue] only reaches the logs, to understand what users are trying to send.
 */
class ValidationFailure(
    messageToClient: String,
    rejectedValue: Any? = null,
) : Failure(
    originalExceptionMessage = if (rejectedValue == null) {
        messageToClient
    } else {
        "$messageToClient Rejected value: \"${rejectedValue.toString().take(ValidationLimits.MAX_LOGGED_VALUE_LENGTH)}\""
    },
    statusCode = HttpStatusCode.BadRequest,
    messageToClient = messageToClient,
)
