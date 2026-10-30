package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.ValidationFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class MapArtValidatorTest {

    private fun fakePng(width: Int = 100, height: Int = 100, size: Int = 24): ByteArray {
        val buffer = ByteBuffer.allocate(size)
        buffer.put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        buffer.putInt(13)
        buffer.put("IHDR".toByteArray())
        buffer.putInt(width)
        buffer.putInt(height)
        return buffer.array()
    }

    @Test
    fun `if id is a valid UUID should return it`() {
        // Given
        val fakeId = "123e4567-e89b-12d3-a456-426614174000"

        // When
        val id = MapArtValidator.parseId(fakeId)

        // Then
        assertEquals(fakeId, id.toString())
    }

    @Test
    fun `if id is not a UUID should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { MapArtValidator.parseId("fake-id") }
    }

    @Test
    fun `if email and username are null should accept them`() {
        // When / Then
        assertDoesNotThrow {
            MapArtValidator.validateEmail(null)
            MapArtValidator.validateUsername(null)
        }
    }

    @Test
    fun `if email has a valid format should accept it`() {
        // When / Then
        assertDoesNotThrow { MapArtValidator.validateEmail("fake.user@example.com") }
    }

    @Test
    fun `if email is invalid should throw ValidationFailure`() {
        // Given
        val fakeEmails = listOf("", "  ", " fake@example.com", "fake@example.com ", "fake", "fake@example", "a@b@c.com", "fake user@example.com")

        // When / Then
        fakeEmails.forEach { assertThrows<ValidationFailure> { MapArtValidator.validateEmail(it) } }
    }

    @Test
    fun `if email is at the length limit should accept it and above it should throw ValidationFailure`() {
        // Given
        val fakeDomain = "@example.com"
        val fakeLocalPart = "a".repeat(ValidationLimits.MAX_EMAIL_LENGTH - fakeDomain.length)

        // When / Then
        assertDoesNotThrow { MapArtValidator.validateEmail(fakeLocalPart + fakeDomain) }
        assertThrows<ValidationFailure> { MapArtValidator.validateEmail("a$fakeLocalPart$fakeDomain") }
    }

    @Test
    fun `if username is invalid should throw ValidationFailure`() {
        // Given
        val fakeUsernames = listOf("", "  ", " fake", "fake ", "a".repeat(ValidationLimits.MAX_USERNAME_LENGTH + 1))

        // When / Then
        fakeUsernames.forEach { assertThrows<ValidationFailure> { MapArtValidator.validateUsername(it) } }
    }

    @Test
    fun `if username is at the length limit should accept it`() {
        // When / Then
        assertDoesNotThrow { MapArtValidator.validateUsername("a".repeat(ValidationLimits.MAX_USERNAME_LENGTH)) }
    }

    @Test
    fun `if title is empty or normal or at the limit should accept it`() {
        // Given
        val fakeTitles = listOf("", "Fake Street, Fake City", "a".repeat(ValidationLimits.MAX_TITLE_LENGTH))

        // When / Then
        fakeTitles.forEach { assertDoesNotThrow { MapArtValidator.validateTitle(it) } }
    }

    @Test
    fun `if title is only whitespace or has outer whitespace or is too long should throw ValidationFailure`() {
        // Given
        val fakeTitles = listOf("  ", " fake", "fake ", "a".repeat(ValidationLimits.MAX_TITLE_LENGTH + 1))

        // When / Then
        fakeTitles.forEach { assertThrows<ValidationFailure> { MapArtValidator.validateTitle(it) } }
    }

    @Test
    fun `if coordinates are at the boundaries should accept them`() {
        // When / Then
        assertDoesNotThrow {
            MapArtValidator.validateCoordinates(-90.0, -180.0)
            MapArtValidator.validateCoordinates(90.0, 180.0)
        }
    }

    @Test
    fun `if coordinates are out of range or NaN should throw ValidationFailure`() {
        // Given
        val fakeCoordinates = listOf(-90.1 to 0.0, 90.1 to 0.0, 0.0 to -180.1, 0.0 to 180.1, Double.NaN to 0.0, 0.0 to Double.NaN)

        // When / Then
        fakeCoordinates.forEach { (latitude, longitude) ->
            assertThrows<ValidationFailure> { MapArtValidator.validateCoordinates(latitude, longitude) }
        }
    }

    @Test
    fun `if dateInMillis is not positive should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { MapArtValidator.validateDateInMillis(0) }
        assertThrows<ValidationFailure> { MapArtValidator.validateDateInMillis(-1) }
        assertDoesNotThrow { MapArtValidator.validateDateInMillis(1) }
    }

    @Test
    fun `if sketchArtType is null should return null`() {
        // When / Then
        assertNull(MapArtValidator.parseSketchArtType(null))
    }

    @Test
    fun `if sketchArtType is a known name should return it`() {
        // When / Then
        SketchArtType.entries.forEach { assertEquals(it, MapArtValidator.parseSketchArtType(it.name)) }
    }

    @Test
    fun `if sketchArtType is unknown or in another case should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { MapArtValidator.parseSketchArtType("FAKE") }
        assertThrows<ValidationFailure> { MapArtValidator.parseSketchArtType("default") }
    }

    @Test
    fun `if layer color is null or uppercase hex should accept it`() {
        // When / Then
        assertDoesNotThrow {
            MapArtValidator.validateLayerColor("roadsColor", null)
            MapArtValidator.validateLayerColor("roadsColor", "#A1B2C3")
        }
    }

    @Test
    fun `if layer color is invalid should throw ValidationFailure without the value`() {
        // Given
        val fakeColors = listOf("#a1b2c3", "A1B2C3", "#A1B2C", "#A1B2C3FF", "")

        // When / Then
        fakeColors.forEach { fakeColor ->
            val failure = assertThrows<ValidationFailure> { MapArtValidator.validateLayerColor("roadsColor", fakeColor) }
            assertEquals("roadsColor must be an uppercase #RRGGBB color.", failure.messageToClient)
        }
    }

    @Test
    fun `if email is invalid should keep it for the logs and not send it to the client`() {
        // When
        val failure = assertThrows<ValidationFailure> { MapArtValidator.validateEmail("fake.private.value") }

        // Then
        assertFalse(failure.messageToClient.contains("fake.private.value"))
        assertEquals("email has an invalid format. Rejected value: \"fake.private.value\"", failure.message)
    }

    @Test
    fun `if a rejected value is huge should cut it in the logs`() {
        // Given
        val fakeTitle = "a".repeat(ValidationLimits.MAX_TITLE_LENGTH + 500)

        // When
        val failure = assertThrows<ValidationFailure> { MapArtValidator.validateTitle(fakeTitle) }

        // Then
        val expectedValue = "a".repeat(ValidationLimits.MAX_LOGGED_VALUE_LENGTH)
        assertEquals("title must have at most ${ValidationLimits.MAX_TITLE_LENGTH} characters. Rejected value: \"$expectedValue\"", failure.message)
    }

    @Test
    fun `if image is rejected should not log its bytes`() {
        // When
        val failure = assertThrows<ValidationFailure> { MapArtValidator.validatePngImage(ByteArray(0)) }

        // Then
        assertEquals("image must be a PNG.", failure.message)
    }

    @Test
    fun `if image is a PNG within the limits should accept it`() {
        // When / Then
        assertDoesNotThrow {
            MapArtValidator.validatePngImage(fakePng(width = 1, height = 1))
            MapArtValidator.validatePngImage(fakePng(width = 1400, height = 1400))
        }
    }

    @Test
    fun `if image is too big in pixels or has no size should throw ValidationFailure`() {
        // Given
        val fakeSizes = listOf(1401 to 100, 100 to 1401, 0 to 100, 100 to 0, -1 to 100)

        // When / Then
        fakeSizes.forEach { (width, height) ->
            assertThrows<ValidationFailure> { MapArtValidator.validatePngImage(fakePng(width, height)) }
        }
    }

    @Test
    fun `if image file is at the size limit should accept it and above it should throw ValidationFailure`() {
        // Given
        val fakeLimit = ValidationLimits.MAX_IMAGE_SIZE_BYTES

        // When / Then
        assertDoesNotThrow { MapArtValidator.validatePngImage(fakePng(size = fakeLimit)) }
        assertThrows<ValidationFailure> { MapArtValidator.validatePngImage(fakePng(size = fakeLimit + 1)) }
    }

    @Test
    fun `if image is not a PNG or is truncated should throw ValidationFailure`() {
        // Given
        val fakeWrongSignature = fakePng().also { it[0] = 0x00 }
        val fakeWrongChunk = fakePng().also { it[12] = 0x00 }
        val fakeImages = listOf(ByteArray(0), fakePng().copyOf(23), fakeWrongSignature, fakeWrongChunk)

        // When / Then
        fakeImages.forEach { assertThrows<ValidationFailure> { MapArtValidator.validatePngImage(it) } }
    }
}
