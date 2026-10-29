package manoellribeiro.dev.core.models.failures

import io.ktor.http.HttpStatusCode

/**
 * Failure is an exception with a meaningful name, the HTTP status it answers with and a message
 * that is safe to send to the client. Controllers, repositories and services only throw Failures;
 * the StatusPages plugin turns them into responses. `originalExceptionMessage` is for logs only.
 */
abstract class Failure(
    originalExceptionMessage: String?,
    val statusCode: HttpStatusCode,
    val messageToClient: String,
) : Exception(originalExceptionMessage) {

    /** Sent as `error` in the response body, so clients can tell failures apart. */
    val errorCode: String
        get() = this::class.simpleName ?: "Failure"
}
