package manoellribeiro.dev.core.utils

/** Requests allowed per client IP. */
object RateLimits {
    /** Every route together. */
    const val REQUESTS_PER_MINUTE = 120

    /** POST /map-arts: each upload can put 8 MB in the bucket. */
    const val MAP_ART_UPLOADS_PER_HOUR = 30

    /** GET /geocoding/reverse: Nominatim allows 1 request per second for the whole API. */
    const val GEOCODING_REQUESTS_PER_MINUTE = 30
}
