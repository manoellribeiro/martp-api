package manoellribeiro.dev.core.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.utils.RateLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class RateLimitingTest {

    private lateinit var errorLogger: ErrorLogger

    @BeforeEach
    fun setup() {
        errorLogger = mockk()
    }

    private fun rateLimitedApplication(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(Koin) { modules(module { single<ErrorLogger> { errorLogger } }) }
            install(ContentNegotiation) { json() }
            configureStatusPages()
            configureRateLimiting()
            // Like the real app: every route inside authenticate (here a provider that accepts everyone)
            install(Authentication) {
                provider("fake-auth") { authenticate { context -> context.principal(UserIdPrincipal("fake-app")) } }
            }
            routing {
                authenticate("fake-auth") {
                    get("/fake-read") { call.respond(HttpStatusCode.OK) }
                    rateLimit(MAP_ART_UPLOADS_RATE_LIMIT) {
                        post("/fake-upload") { call.respond(HttpStatusCode.Created) }
                    }
                    rateLimit(GEOCODING_RATE_LIMIT) {
                        get("/fake-geocoding") { call.respond(HttpStatusCode.OK) }
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.upload(ip: String): HttpResponse =
        client.post("/fake-upload") { header("X-Forwarded-For", ip) }

    @Test
    fun `if an ip passes the upload limit should answer 429 and let another ip through`() = rateLimitedApplication {
        // Given
        every { errorLogger.logClientError(any(), any(), any()) } just runs
        repeat(RateLimits.MAP_ART_UPLOADS_PER_HOUR) {
            assertEquals(HttpStatusCode.Created, upload("203.0.113.1").status)
        }

        // When
        val overLimit = upload("203.0.113.1")
        val otherIp = upload("203.0.113.2")

        // Then
        assertEquals(HttpStatusCode.TooManyRequests, overLimit.status)
        assertEquals(HttpStatusCode.Created, otherIp.status)
    }

    @Test
    fun `if an ip passes the global limit should answer 429`() = rateLimitedApplication {
        // Given
        every { errorLogger.logClientError(any(), any(), any()) } just runs
        repeat(RateLimits.REQUESTS_PER_MINUTE) {
            assertEquals(HttpStatusCode.OK, client.get("/fake-read") { header("X-Forwarded-For", "203.0.113.3") }.status)
        }

        // When
        val response = client.get("/fake-read") { header("X-Forwarded-For", "203.0.113.3") }

        // Then
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `if an ip passes the geocoding limit should answer 429`() = rateLimitedApplication {
        // Given
        every { errorLogger.logClientError(any(), any(), any()) } just runs
        repeat(RateLimits.GEOCODING_REQUESTS_PER_MINUTE) {
            assertEquals(HttpStatusCode.OK, client.get("/fake-geocoding") { header("X-Forwarded-For", "203.0.113.4") }.status)
        }

        // When
        val response = client.get("/fake-geocoding") { header("X-Forwarded-For", "203.0.113.4") }

        // Then
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `if the client fakes the first forwarded ip should still be limited by the last one`() = rateLimitedApplication {
        // Given
        every { errorLogger.logClientError(any(), any(), any()) } just runs
        repeat(RateLimits.MAP_ART_UPLOADS_PER_HOUR) { index ->
            assertEquals(HttpStatusCode.Created, upload("10.0.0.$index, 203.0.113.5").status)
        }

        // When
        val response = upload("10.0.0.99, 203.0.113.5")

        // Then
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `if the limit is passed should answer TooManyRequests with Retry-After and log it`() = rateLimitedApplication {
        // Given
        every { errorLogger.logClientError(any(), any(), any()) } just runs
        repeat(RateLimits.MAP_ART_UPLOADS_PER_HOUR) { upload("203.0.113.6") }

        // When
        val response = upload("203.0.113.6")

        // Then
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"error\":\"TooManyRequests\""))
        verify(exactly = 1) { errorLogger.logClientError("TooManyRequests", any(), "POST /fake-upload") }
    }
}
