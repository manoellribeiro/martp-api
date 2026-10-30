package manoellribeiro.dev.core.utils

object ValidationLimits {
    const val MAX_EMAIL_LENGTH = 254
    const val MAX_USERNAME_LENGTH = 100
    const val MAX_TITLE_LENGTH = 300

    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0

    /** Mapbox's 1280 px limit plus the apps' 30 px frame and 30 px padding on each side. */
    const val MAX_IMAGE_SIDE_PX = 1400

    /** A 1400 x 1400 RGBA image is 7.84 MB uncompressed, so no real art reaches this. */
    const val MAX_IMAGE_SIZE_BYTES = 8 * 1024 * 1024

    /** Rejected values are cut to this in the logs, so huge inputs can't flood them. */
    const val MAX_LOGGED_VALUE_LENGTH = 300
}
