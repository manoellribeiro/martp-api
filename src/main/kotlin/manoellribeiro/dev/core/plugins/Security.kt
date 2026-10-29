package manoellribeiro.dev.core.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import manoellribeiro.dev.core.config.AppCheckConfig
import manoellribeiro.dev.core.models.ErrorResponse
import org.koin.ktor.ext.inject
import java.net.URI
import java.util.concurrent.TimeUnit

const val APP_CHECK_AUTH = "app-check"
const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/**
 * Only the Martp apps may call the API. Each request carries a Firebase App Check token
 * (Play Integrity on Android, reCAPTCHA on the web) in the X-Firebase-AppCheck header.
 * The token is a JWT signed by Google, checked here without calling Google per request:
 * signature (RS256, keys from the JWKS URL, cached), issuer, audience, expiry and that
 * `sub` is one of our Firebase App IDs.
 * https://firebase.google.com/docs/app-check/custom-resource-backend
 */
fun Application.configureSecurity() {
    val appCheckConfig by inject<AppCheckConfig>()

    val jwkProvider = JwkProviderBuilder(URI(appCheckConfig.jwksUrl).toURL())
        .cached(10, 6, TimeUnit.HOURS) // Google rotates the keys; cache them for up to 6 hours
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt(APP_CHECK_AUTH) {
            authHeader { call ->
                call.request.headers[APP_CHECK_HEADER]?.let { token -> HttpAuthHeader.Single("Bearer", token) }
            }
            verifier(jwkProvider, appCheckConfig.issuer) {
                withAudience(appCheckConfig.audience)
            }
            validate { credential ->
                if (credential.payload.subject in appCheckConfig.allowedAppIds) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(error = "InvalidAppCheckToken", message = "This client is not allowed to call the API."),
                )
            }
        }
    }
}
