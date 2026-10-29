package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import manoellribeiro.dev.core.config.toAppConfig
import manoellribeiro.dev.core.di.allModules
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencyInjection() {
    val appConfig = environment.config.toAppConfig()
    install(Koin) {
        slf4jLogger()
        modules(allModules(appConfig))
    }
}
