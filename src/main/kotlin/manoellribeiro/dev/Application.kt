package manoellribeiro.dev

import io.ktor.server.application.Application
import manoellribeiro.dev.core.plugins.configureCors
import manoellribeiro.dev.core.plugins.configureDatabase
import manoellribeiro.dev.core.plugins.configureDependencyInjection
import manoellribeiro.dev.core.plugins.configureMonitoring
import manoellribeiro.dev.core.plugins.configureRouting
import manoellribeiro.dev.core.plugins.configureSecurity
import manoellribeiro.dev.core.plugins.configureSerialization
import manoellribeiro.dev.core.plugins.configureStatusPages

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
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
    configureSecurity()
    configureRouting()
}
