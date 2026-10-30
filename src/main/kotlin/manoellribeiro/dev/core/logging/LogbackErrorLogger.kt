package manoellribeiro.dev.core.logging

import org.slf4j.LoggerFactory

/**
 * Writes errors to the application log (Logback). Temporary implementation until a monitoring
 * tool is chosen: add a new ErrorLogger for it and change the binding in core/di/Modules.kt.
 */
class LogbackErrorLogger : ErrorLogger {

    private val logger = LoggerFactory.getLogger("ErrorLogger")

    override fun logNonFatal(throwable: Throwable, operation: String) {
        logger.error("Non-fatal in $operation", throwable)
    }

    override fun logClientError(errorCode: String, message: String, operation: String) {
        logger.warn("Client error in $operation: $errorCode - $message")
    }
}
