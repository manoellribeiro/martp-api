package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

/**
 * Logs status, method, path (no query string) and client IP of every call. The IP is the one the
 * rate limits use (see RateLimiting.kt). Never log request bodies: they carry emails.
 */
fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            "${call.response.status()}: ${call.request.httpMethod.value} ${call.request.path()} from ${call.request.origin.remoteAddress}"
        }
    }
}
