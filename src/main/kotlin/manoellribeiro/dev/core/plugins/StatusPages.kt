package manoellribeiro.dev.core.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.ErrorResponse
import manoellribeiro.dev.core.models.failures.Failure
import manoellribeiro.dev.core.models.failures.UnknownErrorFailure
import org.koin.ktor.ext.inject

/**
 * The only place that turns errors into HTTP responses, so routes and controllers never catch
 * exceptions just to answer them. Everything that ends in a 5xx goes to the ErrorLogger.
 */
fun Application.configureStatusPages() {
    val errorLogger by inject<ErrorLogger>()

    install(StatusPages) {
        exception<Failure> { call, failure ->
            if (failure.statusCode.value < 500) {
                errorLogger.logClientError(failure.errorCode, failure.message ?: failure.messageToClient, call.operationName())
            }
            call.respond(failure.statusCode, ErrorResponse(failure.errorCode, failure.messageToClient))
        }

        // Thrown by Ktor when the body cannot be read (malformed JSON, missing required field, ...)
        exception<BadRequestException> { call, _ ->
            val message = "The request body or parameters are invalid."
            errorLogger.logClientError("BadRequest", message, call.operationName())
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "BadRequest", message = message))
        }

        exception<Throwable> { call, cause ->
            errorLogger.logNonFatal(cause, "StatusPages.unhandled")
            val failure = UnknownErrorFailure(originalExceptionMessage = cause.message)
            call.respond(failure.statusCode, ErrorResponse(failure.errorCode, failure.messageToClient))
        }

        status(HttpStatusCode.NotFound) { call, status ->
            val message = "This endpoint does not exist."
            errorLogger.logClientError("NotFound", message, call.operationName())
            call.respond(status, ErrorResponse(error = "NotFound", message = message))
        }
    }
}

// With the query string: for a lookup, that is the point that failed.
internal fun ApplicationCall.operationName() = "${request.httpMethod.value} ${request.uri}"
