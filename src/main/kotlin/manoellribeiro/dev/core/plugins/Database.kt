package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import manoellribeiro.dev.core.data.database.MartpDatabase
import org.koin.ktor.ext.inject

fun Application.configureDatabase() {
    val martpDatabase by inject<MartpDatabase>()
    martpDatabase.connect()
    monitor.subscribe(ApplicationStopped) { martpDatabase.close() }
}
