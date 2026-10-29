package manoellribeiro.dev.core.config

import io.ktor.server.config.ApplicationConfig

/**
 * Typed copy of application.yaml. Read once at startup and provided through Koin, so no class
 * reads `environment.config` directly and tests can build the configs by hand.
 */
data class AppConfig(
    val database: DatabaseConfig,
    val storage: StorageConfig,
    val geocoding: GeocodingConfig,
    val cors: CorsConfig,
    val appCheck: AppCheckConfig,
)

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
)

data class StorageConfig(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val forcePathStyle: Boolean,
    val publicBaseUrl: String,
)

data class GeocodingConfig(
    val baseUrl: String,
    val userAgent: String,
)

data class CorsConfig(
    val allowedOrigins: List<String>,
)

data class AppCheckConfig(
    val projectNumber: String,
    val allowedAppIds: Set<String>,
    val jwksUrl: String,
) {
    val issuer: String get() = "https://firebaseappcheck.googleapis.com/$projectNumber"
    val audience: String get() = "projects/$projectNumber"
}

fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig(
    database = DatabaseConfig(
        url = string("database.url"),
        user = string("database.user"),
        password = string("database.password"),
        maxPoolSize = string("database.maxPoolSize").toInt(),
    ),
    storage = StorageConfig(
        endpoint = string("storage.endpoint"),
        region = string("storage.region"),
        bucket = string("storage.bucket"),
        accessKey = string("storage.accessKey"),
        secretKey = string("storage.secretKey"),
        forcePathStyle = string("storage.forcePathStyle").toBooleanStrict(),
        publicBaseUrl = string("storage.publicBaseUrl").trimEnd('/'),
    ),
    geocoding = GeocodingConfig(
        baseUrl = string("geocoding.baseUrl").trimEnd('/'),
        userAgent = string("geocoding.userAgent"),
    ),
    cors = CorsConfig(
        allowedOrigins = string("cors.allowedOrigins")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
    ),
    appCheck = AppCheckConfig(
        projectNumber = string("appCheck.projectNumber").also {
            require(it.isNotBlank()) { "FIREBASE_PROJECT_NUMBER is required" }
        },
        allowedAppIds = string("appCheck.allowedAppIds")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .also { require(it.isNotEmpty()) { "FIREBASE_ALLOWED_APP_IDS is required" } },
        jwksUrl = string("appCheck.jwksUrl"),
    ),
)

private fun ApplicationConfig.string(path: String): String = property(path).getString()
