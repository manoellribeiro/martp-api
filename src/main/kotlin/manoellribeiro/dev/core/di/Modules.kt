package manoellribeiro.dev.core.di

import manoellribeiro.dev.core.config.AppConfig
import manoellribeiro.dev.core.data.database.MartpDatabase
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.logging.LogbackErrorLogger
import org.koin.dsl.module

/**
 * Koin modules, one per layer. Every class is bound to its interface (`single<Interface> { Impl(...) }`)
 * so each layer only knows the interface of the layer below it and tests can replace it with a mock.
 *
 * Add new modules here as the layers are filled in and register them in `allModules`:
 * - daosModule:         single<MapArtsDao> { MapArtsDaoImpl(get()) }, ...
 * - servicesModule:     single<ImageStorageService> { ... }, single<GeocodingService> { ... }
 * - repositoriesModule: single<MartpRepository> { MartpRepositoryImpl(get(), get(), ...) }
 * - controllersModule:  single<MapArtsController> { MapArtsControllerImpl(get(), get()) }, ...
 */
fun configModule(appConfig: AppConfig) = module {
    single { appConfig }
    single { appConfig.database }
    single { appConfig.storage }
    single { appConfig.geocoding }
    single { appConfig.cors }
    single { appConfig.appCheck }
}

val loggingModule = module {
    single<ErrorLogger> { LogbackErrorLogger() }
}

val databaseModule = module {
    single { MartpDatabase(config = get()) }
}

fun allModules(appConfig: AppConfig) = listOf(
    configModule(appConfig),
    loggingModule,
    databaseModule,
)
