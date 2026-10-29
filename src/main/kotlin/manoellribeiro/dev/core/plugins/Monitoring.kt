package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

/** Logs method, path and status of every call. Never log request bodies: they carry emails. */
fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
    }
}
