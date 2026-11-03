package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.ValidationFailure
import java.nio.ByteBuffer
import kotlin.uuid.Uuid

class MapArtValidatorImpl : MapArtValidator {

    override fun parseId(id: String): Uuid =
        Uuid.parseHexDashOrNull(id) ?: throw ValidationFailure("id must be a valid UUID.", rejectedValue = id)

    override fun validateEmail(email: String?) {
        if (email == null) return
        validateOptionalText(field = "email", value = email, maxLength = ValidationLimits.MAX_EMAIL_LENGTH)
        if (!EMAIL_FORMAT.matches(email)) throw ValidationFailure("email has an invalid format.", rejectedValue = email)
    }

    override fun validateUsername(username: String?) {
        if (username == null) return
        validateOptionalText(field = "username", value = username, maxLength = ValidationLimits.MAX_USERNAME_LENGTH)
    }

    override fun validateTitle(title: String) {
        if (title != title.trim()) throw ValidationFailure("title must not have leading or trailing whitespace.", rejectedValue = title)
        if (title.length > ValidationLimits.MAX_TITLE_LENGTH) {
            throw ValidationFailure("title must have at most ${ValidationLimits.MAX_TITLE_LENGTH} characters.", rejectedValue = title)
        }
    }

    override fun validateCoordinates(latitude: Double, longitude: Double) {
        if (latitude !in ValidationLimits.MIN_LATITUDE..ValidationLimits.MAX_LATITUDE) {
            throw ValidationFailure("latitude must be between ${ValidationLimits.MIN_LATITUDE} and ${ValidationLimits.MAX_LATITUDE}.", rejectedValue = latitude)
        }
        if (longitude !in ValidationLimits.MIN_LONGITUDE..ValidationLimits.MAX_LONGITUDE) {
            throw ValidationFailure("longitude must be between ${ValidationLimits.MIN_LONGITUDE} and ${ValidationLimits.MAX_LONGITUDE}.", rejectedValue = longitude)
        }
    }

    override fun validateDateInMillis(dateInMillis: Long) {
        if (dateInMillis <= 0) throw ValidationFailure("dateInMillis must be greater than 0.", rejectedValue = dateInMillis)
    }

    override fun parseSketchArtType(sketchArtType: String?): SketchArtType? {
        if (sketchArtType == null) return null
        return SketchArtType.entries.firstOrNull { it.name == sketchArtType }
            ?: throw ValidationFailure("sketchArtType must be one of ${SketchArtType.entries.joinToString()}.", rejectedValue = sketchArtType)
    }

    override fun validateLayerColor(field: String, color: String?) {
        if (color != null && !HEX_COLOR_FORMAT.matches(color)) {
            throw ValidationFailure("$field must be an uppercase #RRGGBB color.", rejectedValue = color)
        }
    }

    override fun validatePngImage(image: ByteArray) {
        if (image.size > ValidationLimits.MAX_IMAGE_SIZE_BYTES) {
            throw ValidationFailure("image must have at most ${ValidationLimits.MAX_IMAGE_SIZE_BYTES} bytes.")
        }
        if (!image.hasPngHeader()) throw ValidationFailure("image must be a PNG.")
        val header = ByteBuffer.wrap(image)
        val maxSide = ValidationLimits.MAX_IMAGE_SIDE_PX
        // getInt reads a size above 2^31 as negative, which this range also rejects
        if (header.getInt(PNG_WIDTH_OFFSET) !in 1..maxSide || header.getInt(PNG_HEIGHT_OFFSET) !in 1..maxSide) {
            throw ValidationFailure("image must be at most $maxSide x $maxSide pixels.")
        }
    }

    private fun validateOptionalText(field: String, value: String, maxLength: Int) {
        if (value.isBlank()) throw ValidationFailure("$field must not be blank.", rejectedValue = value)
        if (value != value.trim()) throw ValidationFailure("$field must not have leading or trailing whitespace.", rejectedValue = value)
        if (value.length > maxLength) throw ValidationFailure("$field must have at most $maxLength characters.", rejectedValue = value)
    }

    private fun ByteArray.hasPngHeader(): Boolean =
        size >= PNG_MIN_HEADER_SIZE &&
            PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] } &&
            PNG_HEADER_CHUNK_TYPE.indices.all { this[PNG_CHUNK_TYPE_OFFSET + it] == PNG_HEADER_CHUNK_TYPE[it] }

    private companion object {
        val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val HEX_COLOR_FORMAT = Regex("^#[0-9A-F]{6}$")
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val PNG_HEADER_CHUNK_TYPE = "IHDR".toByteArray(Charsets.US_ASCII)

        // A PNG starts with the signature (8 bytes) and the IHDR chunk: length (4), type (4), width (4), height (4)
        const val PNG_CHUNK_TYPE_OFFSET = 12
        const val PNG_WIDTH_OFFSET = 16
        const val PNG_HEIGHT_OFFSET = 20
        const val PNG_MIN_HEADER_SIZE = 24
    }
}
