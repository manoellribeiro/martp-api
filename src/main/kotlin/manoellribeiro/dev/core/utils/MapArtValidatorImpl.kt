package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.ValidationFailure
import java.nio.ByteBuffer
import kotlin.uuid.Uuid

class MapArtValidatorImpl : MapArtValidator {

    override fun parseId(id: String): Uuid =
        Uuid.parseHexDashOrNull(id.trim()) ?: throw ValidationFailure("id must be a valid UUID.", rejectedValue = id)

    override fun parseEmail(email: String?): String? =
        parseOptionalText(field = "email", value = email, maxLength = ValidationLimits.MAX_EMAIL_LENGTH)

    override fun parseUsername(username: String?): String? =
        parseOptionalText(field = "username", value = username, maxLength = ValidationLimits.MAX_USERNAME_LENGTH)

    override fun parseTitle(title: String): String =
        trimWithinLength(field = "title", value = title, maxLength = ValidationLimits.MAX_TITLE_LENGTH)

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
        val name = sketchArtType.trimmedOrNull() ?: return null
        return SketchArtType.entries.firstOrNull { it.name == name }
            ?: throw ValidationFailure("sketchArtType must be one of ${SketchArtType.entries.joinToString()}.", rejectedValue = sketchArtType)
    }

    override fun parseColorPalette(colorPalette: String?): String? = colorPalette.trimmedOrNull()

    override fun parseLayerColor(field: String, color: String?): String? {
        val trimmed = color.trimmedOrNull() ?: return null
        if (!HEX_COLOR_FORMAT.matches(trimmed)) {
            throw ValidationFailure("$field must be an uppercase #RRGGBB color.", rejectedValue = color)
        }
        return trimmed
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

    override fun parseContinent(continent: String?): Continent? {
        val name = continent.trimmedOrNull() ?: return null
        return Continent.entries.firstOrNull { it.name == name }
            ?: throw ValidationFailure("continent must be one of ${Continent.entries.joinToString()}.", rejectedValue = continent)
    }

    private fun parseOptionalText(field: String, value: String?, maxLength: Int): String? =
        value?.let { trimWithinLength(field, it, maxLength) }?.ifEmpty { null }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.ifEmpty { null }

    // Only the outer whitespace goes: "Manoel Ribeiro" keeps its inner space
    private fun trimWithinLength(field: String, value: String, maxLength: Int): String {
        val trimmed = value.trim()
        if (trimmed.length > maxLength) throw ValidationFailure("$field must have at most $maxLength characters.", rejectedValue = value)
        return trimmed
    }

    private fun ByteArray.hasPngHeader(): Boolean =
        size >= PNG_MIN_HEADER_SIZE &&
            PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] } &&
            PNG_HEADER_CHUNK_TYPE.indices.all { this[PNG_CHUNK_TYPE_OFFSET + it] == PNG_HEADER_CHUNK_TYPE[it] }

    private companion object {
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
