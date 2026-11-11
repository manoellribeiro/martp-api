package manoellribeiro.dev.core.di

import manoellribeiro.dev.core.config.AppConfig
import manoellribeiro.dev.core.data.database.MartpDatabase
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.logging.LogbackErrorLogger
import manoellribeiro.dev.core.data.database.daos.ColorPalettesDao
import manoellribeiro.dev.core.data.database.daos.ColorPalettesDaoImpl
import manoellribeiro.dev.core.data.database.daos.MapArtsDao
import manoellribeiro.dev.core.data.database.daos.MapArtsDaoImpl
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.data.repositories.MartpRepositoryImpl
import manoellribeiro.dev.core.services.imageStorage.ImageStorageService
import manoellribeiro.dev.core.services.imageStorage.ImageStorageServiceImpl
import manoellribeiro.dev.core.services.imageStorage.createS3Client
import manoellribeiro.dev.core.services.naturalEarth.RegionService
import manoellribeiro.dev.core.services.naturalEarth.RegionServiceImpl
import manoellribeiro.dev.core.services.naturalEarth.loadNaturalEarthCountries
import manoellribeiro.dev.core.services.nominatim.GeocodingService
import manoellribeiro.dev.core.services.nominatim.GeocodingServiceImpl
import manoellribeiro.dev.core.services.nominatim.createNominatimHttpClient
import manoellribeiro.dev.core.utils.GlobeArtsPicker
import manoellribeiro.dev.core.utils.GlobeArtsPickerImpl
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.core.utils.MapArtValidatorImpl
import manoellribeiro.dev.routes.colorPalettes.ColorPalettesController
import manoellribeiro.dev.routes.colorPalettes.ColorPalettesControllerImpl
import manoellribeiro.dev.routes.geocoding.GeocodingController
import manoellribeiro.dev.routes.geocoding.GeocodingControllerImpl
import manoellribeiro.dev.routes.mapArts.MapArtsController
import manoellribeiro.dev.routes.mapArts.MapArtsControllerImpl
import org.koin.dsl.onClose
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.random.Random

/**
 * Koin modules, one per layer. Every class is bound to its interface (`single<Interface> { Impl(...) }`)
 * so each layer only knows the interface of the layer below it and tests can replace it with a mock.
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

val daosModule = module {
    single<ColorPalettesDao> { ColorPalettesDaoImpl(martpDatabase = get()) }
    single<MapArtsDao> { MapArtsDaoImpl(martpDatabase = get()) }
}

// The clients are closed when Koin stops, which happens when the application stops
val servicesModule = module {
    single { createS3Client(config = get()) } onClose { it?.close() }
    single(named(NOMINATIM_HTTP_CLIENT)) { createNominatimHttpClient(config = get()) } onClose { it?.close() }
    single<ImageStorageService> { ImageStorageServiceImpl(s3Client = get(), config = get(), errorLogger = get()) }
    single<GeocodingService> { GeocodingServiceImpl(httpClient = get(named(NOMINATIM_HTTP_CLIENT)), errorLogger = get()) }
    // Reads the borders at startup (about a second), not on the first save. A failure never stops the API from starting.
    single<RegionService>(createdAtStart = true) {
        val errorLogger = get<ErrorLogger>()
        RegionServiceImpl(countries = { loadNaturalEarthCountries(errorLogger) }, errorLogger = errorLogger)
    }
}

val repositoriesModule = module {
    single<MartpRepository> {
        MartpRepositoryImpl(
            colorPalettesDao = get(),
            mapArtsDao = get(),
            imageStorageService = get(),
            geocodingService = get(),
            regionService = get(),
            errorLogger = get(),
        )
    }
}

val utilsModule = module {
    single<MapArtValidator> { MapArtValidatorImpl() }
    single<GlobeArtsPicker> { GlobeArtsPickerImpl(random = Random.Default) }
}

val controllersModule = module {
    single<ColorPalettesController> { ColorPalettesControllerImpl(repository = get()) }
    single<GeocodingController> { GeocodingControllerImpl(repository = get(), mapArtValidator = get(), errorLogger = get()) }
    single<MapArtsController> { MapArtsControllerImpl(repository = get(), mapArtValidator = get(), globeArtsPicker = get()) }
}

fun allModules(appConfig: AppConfig) = listOf(
    configModule(appConfig),
    loggingModule,
    databaseModule,
    daosModule,
    servicesModule,
    repositoriesModule,
    utilsModule,
    controllersModule,
)

private const val NOMINATIM_HTTP_CLIENT = "nominatim"
