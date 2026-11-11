plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "manoellribeiro.dev"
version = "1.0.0-SNAPSHOT"

application {
    // Our main() wraps EngineMain to log startup failures through Logback
    mainClass = "manoellribeiro.dev.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}

ktor {
    fatJar {
        archiveFileName.set("martp-api.jar")
    }
}

dependencies {
    // Ktor server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.serialization.kotlinx.json)

    // Ktor client (outside APIs, e.g. Nominatim)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Image storage
    implementation(libs.aws.sdk.s3)

    // Continent of an art (Natural Earth borders)
    implementation(libs.jts.core)

    // Dependency injection
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder) // JSON logs in production (logback-cloud.xml)

    // Tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(ktorLibs.server.testHost)
}

tasks.test {
    useJUnitPlatform()
}
