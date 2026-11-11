package manoellribeiro.dev

import io.ktor.server.application.Application
import manoellribeiro.dev.core.plugins.configureCors
import manoellribeiro.dev.core.plugins.configureDatabase
import manoellribeiro.dev.core.plugins.configureDependencyInjection
import manoellribeiro.dev.core.plugins.configureMonitoring
import manoellribeiro.dev.core.plugins.configureRateLimiting
import manoellribeiro.dev.core.plugins.configureRouting
import manoellribeiro.dev.core.plugins.configureSecurity
import manoellribeiro.dev.core.plugins.configureSerialization
import manoellribeiro.dev.core.plugins.configureStatusPages
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        io.ktor.server.netty.EngineMain.main(args)
    } catch (throwable: Throwable) {
        // Startup failures (database unreachable, a broken migration, bad config) happen before Koin
        // and the ErrorLogger exist: log through SLF4J so production keeps them as one JSON entry.
        LoggerFactory.getLogger("Application").error("The API failed to start", throwable)
        exitProcess(1)
    }
}

/**
 * Single Ktor module, referenced in application.yaml. The order matters: Koin first (every other
 * plugin resolves its dependencies from it), then the database (runs the migrations before the
 * server answers any call), then the HTTP plugins and the routes.
 */
fun Application.module() {
    configureDependencyInjection()
    configureDatabase()
    configureSerialization()
    configureMonitoring()
    configureCors()
    configureStatusPages()
    configureRateLimiting()
    configureSecurity()
    configureRouting()
}
