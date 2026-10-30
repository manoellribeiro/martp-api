package manoellribeiro.dev.core.logging

/**
 * Records exceptions that are handled by the API (e.g. transformed into a Failure or answered with
 * a 5xx), so they are not lost even though the client only receives an error response.
 * Never put user data (emails, usernames, coordinates) in the operation name.
 */
interface ErrorLogger {
    fun logNonFatal(throwable: Throwable, operation: String)

    /** A request the API refused on purpose (4xx), to observe how often and why it happens. */
    fun logClientError(errorCode: String, message: String, operation: String)
}
