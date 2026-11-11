package manoellribeiro.dev.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import manoellribeiro.dev.core.utils.RateLimits
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val MAP_ART_UPLOADS_RATE_LIMIT = RateLimitName("mapArtUploads")
val GEOCODING_RATE_LIMIT = RateLimitName("geocoding")

/** Per-IP limits. Over a limit, Ktor answers 429 with Retry-After (body from StatusPages). */
fun Application.configureRateLimiting() {
    // The host's proxy appends the client's IP as the last X-Forwarded-For value.
    // Earlier values come from the client and can be faked, so only the last one is used.
    install(XForwardedHeaders) {
        useLastProxy()
    }
    install(RateLimit) {
        global {
            rateLimiter(limit = RateLimits.REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
        register(MAP_ART_UPLOADS_RATE_LIMIT) {
            rateLimiter(limit = RateLimits.MAP_ART_UPLOADS_PER_HOUR, refillPeriod = 1.hours)
            requestKey { call -> call.clientIp() }
        }
        register(GEOCODING_RATE_LIMIT) {
            rateLimiter(limit = RateLimits.GEOCODING_REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
    }
}

private fun ApplicationCall.clientIp(): String = request.origin.remoteAddress
