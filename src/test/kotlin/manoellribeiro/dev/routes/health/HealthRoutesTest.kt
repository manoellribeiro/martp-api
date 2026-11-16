package manoellribeiro.dev.routes.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.response.respond
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import manoellribeiro.dev.core.plugins.APP_CHECK_AUTH
import manoellribeiro.dev.core.plugins.configureRateLimiting
import manoellribeiro.dev.core.plugins.configureRouting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.Koin

class HealthRoutesTest {

    // The real routing, with an App Check stand-in that rejects every call (like a request without a token)
    private fun applicationRejectingEveryToken(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(Koin)
            configureRateLimiting()
            install(Authentication) {
                provider(APP_CHECK_AUTH) {
                    authenticate { context ->
                        context.challenge("fake-challenge", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                            call.respond(HttpStatusCode.Unauthorized)
                            challenge.complete()
                        }
                    }
                }
            }
            configureRouting()
        }
        block()
    }

    @Test
    fun `if health is called without an App Check token should answer 204 with an empty body`() =
        applicationRejectingEveryToken {
            // When
            val response = client.get(HEALTH_PATH)

            // Then
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }

    @Test
    fun `if a protected route is called without an App Check token should still answer 401`() =
        applicationRejectingEveryToken {
            // When
            val response = client.get("/color-palettes")

            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
