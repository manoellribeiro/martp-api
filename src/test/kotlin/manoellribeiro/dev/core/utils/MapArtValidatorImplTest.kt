package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.ValidationFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class MapArtValidatorImplTest {

    private lateinit var validator: MapArtValidator

    @BeforeEach
    fun setup() {
        validator = MapArtValidatorImpl()
    }

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
        val id = validator.parseId(fakeId)

        // Then
        assertEquals(fakeId, id.toString())
    }

    @Test
    fun `if id is not a UUID should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { validator.parseId("fake-id") }
    }

    @Test
    fun `if email and username are null should return null`() {
        // When / Then
        assertNull(validator.parseEmail(null))
        assertNull(validator.parseUsername(null))
    }

    @Test
    fun `if email or username is empty or only whitespace should return null`() {
        // Given
        val fakeValues = listOf("", "  ", "\t\n")

        // When / Then
        fakeValues.forEach {
            assertNull(validator.parseEmail(it))
            assertNull(validator.parseUsername(it))
        }
    }

    @Test
    fun `if email has any format should return it`() {
        // Given
        val fakeEmails = listOf("fake.user@example.com", "fake", "fake@example", "a@b@c.com", "fake user@example.com")

        // When / Then
        fakeEmails.forEach { assertEquals(it, validator.parseEmail(it)) }
    }

    @Test
    fun `if email or username has outer whitespace should return it trimmed`() {
        // When / Then
        assertEquals("fake@example.com", validator.parseEmail("  fake@example.com \n"))
        assertEquals("Fake User", validator.parseUsername(" Fake User  "))
    }

    @Test
    fun `if email is at the length limit after trimming should accept it and above it should throw ValidationFailure`() {
        // Given
        val fakeEmail = "a".repeat(ValidationLimits.MAX_EMAIL_LENGTH)

        // When / Then
        assertEquals(fakeEmail, validator.parseEmail("  $fakeEmail  "))
        assertThrows<ValidationFailure> { validator.parseEmail("a$fakeEmail") }
    }

    @Test
    fun `if username is at the length limit after trimming should accept it and above it should throw ValidationFailure`() {
        // Given
        val fakeUsername = "a".repeat(ValidationLimits.MAX_USERNAME_LENGTH)

        // When / Then
        assertEquals(fakeUsername, validator.parseUsername("  $fakeUsername  "))
        assertThrows<ValidationFailure> { validator.parseUsername("a$fakeUsername") }
    }

    @Test
    fun `if title is empty or normal or at the limit should return it`() {
        // Given
        val fakeTitles = listOf("", "Fake Street, Fake City", "a".repeat(ValidationLimits.MAX_TITLE_LENGTH))

        // When / Then
        fakeTitles.forEach { assertEquals(it, validator.parseTitle(it)) }
    }

    @Test
    fun `if title has outer whitespace should return it trimmed keeping the inner spaces`() {
        // When / Then
        assertEquals("Manoel Ribeiro", validator.parseTitle("  Manoel Ribeiro \t"))
        assertEquals("", validator.parseTitle("   "))
        assertEquals("a".repeat(ValidationLimits.MAX_TITLE_LENGTH), validator.parseTitle(" " + "a".repeat(ValidationLimits.MAX_TITLE_LENGTH) + " "))
    }

    @Test
    fun `if title is too long after trimming should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { validator.parseTitle("a".repeat(ValidationLimits.MAX_TITLE_LENGTH + 1)) }
    }

    @Test
    fun `if coordinates are at the boundaries should accept them`() {
        // When / Then
        assertDoesNotThrow {
            validator.validateCoordinates(-90.0, -180.0)
            validator.validateCoordinates(90.0, 180.0)
        }
    }

    @Test
    fun `if coordinates are out of range or NaN should throw ValidationFailure`() {
        // Given
        val fakeCoordinates = listOf(-90.1 to 0.0, 90.1 to 0.0, 0.0 to -180.1, 0.0 to 180.1, Double.NaN to 0.0, 0.0 to Double.NaN)

        // When / Then
        fakeCoordinates.forEach { (latitude, longitude) ->
            assertThrows<ValidationFailure> { validator.validateCoordinates(latitude, longitude) }
        }
    }

    @Test
    fun `if dateInMillis is not positive should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { validator.validateDateInMillis(0) }
        assertThrows<ValidationFailure> { validator.validateDateInMillis(-1) }
        assertDoesNotThrow { validator.validateDateInMillis(1) }
    }

    @Test
    fun `if sketchArtType is null should return null`() {
        // When / Then
        assertNull(validator.parseSketchArtType(null))
    }

    @Test
    fun `if sketchArtType is a known name should return it`() {
        // When / Then
        SketchArtType.entries.forEach { assertEquals(it, validator.parseSketchArtType(it.name)) }
    }

    @Test
    fun `if sketchArtType is unknown or in another case should throw ValidationFailure`() {
        // When / Then
        assertThrows<ValidationFailure> { validator.parseSketchArtType("FAKE") }
        assertThrows<ValidationFailure> { validator.parseSketchArtType("default") }
    }

    @Test
    fun `if sketchArtType has outer whitespace should trim it and if blank should return null`() {
        // When / Then
        assertEquals(SketchArtType.COLORFUL_MAP, validator.parseSketchArtType("  COLORFUL_MAP "))
        assertNull(validator.parseSketchArtType(""))
        assertNull(validator.parseSketchArtType("   "))
    }

    @Test
    fun `if id has outer whitespace should parse it`() {
        // Given
        val fakeId = "123e4567-e89b-12d3-a456-426614174000"

        // When / Then
        assertEquals(fakeId, validator.parseId("  $fakeId \n").toString())
    }

    @Test
    fun `if color palette has outer whitespace should trim it and if blank should return null`() {
        // When / Then
        assertEquals("Fake Palette", validator.parseColorPalette(" Fake Palette  "))
        assertNull(validator.parseColorPalette(null))
        assertNull(validator.parseColorPalette(""))
        assertNull(validator.parseColorPalette("  "))
    }

    @Test
    fun `if layer color is uppercase hex should return it trimmed`() {
        // When / Then
        assertEquals("#A1B2C3", validator.parseLayerColor("roadsColor", "#A1B2C3"))
        assertEquals("#A1B2C3", validator.parseLayerColor("roadsColor", "  #A1B2C3 "))
    }

    @Test
    fun `if layer color is null or blank should return null`() {
        // When / Then
        assertNull(validator.parseLayerColor("roadsColor", null))
        assertNull(validator.parseLayerColor("roadsColor", ""))
        assertNull(validator.parseLayerColor("roadsColor", "   "))
    }

    @Test
    fun `if layer color is invalid should throw ValidationFailure without the value`() {
        // Given
        val fakeColors = listOf("#a1b2c3", "A1B2C3", "#A1B2C", "#A1B2C3FF", "#A1 B2C3")

        // When / Then
        fakeColors.forEach { fakeColor ->
            val failure = assertThrows<ValidationFailure> { validator.parseLayerColor("roadsColor", fakeColor) }
            assertEquals("roadsColor must be an uppercase #RRGGBB color.", failure.messageToClient)
        }
    }

    @Test
    fun `if a username is rejected should keep it for the logs and not send it to the client`() {
        // Given
        val fakeUsername = "fake.private.value".repeat(10)

        // When
        val failure = assertThrows<ValidationFailure> { validator.parseUsername(fakeUsername) }

        // Then
        assertFalse(failure.messageToClient.contains("fake.private.value"))
        assertEquals("username must have at most ${ValidationLimits.MAX_USERNAME_LENGTH} characters. Rejected value: \"$fakeUsername\"", failure.message)
    }

    @Test
    fun `if a rejected value is huge should cut it in the logs`() {
        // Given
        val fakeTitle = "a".repeat(ValidationLimits.MAX_TITLE_LENGTH + 500)

        // When
        val failure = assertThrows<ValidationFailure> { validator.parseTitle(fakeTitle) }

        // Then
        val expectedValue = "a".repeat(ValidationLimits.MAX_LOGGED_VALUE_LENGTH)
        assertEquals("title must have at most ${ValidationLimits.MAX_TITLE_LENGTH} characters. Rejected value: \"$expectedValue\"", failure.message)
    }

    @Test
    fun `if image is rejected should not log its bytes`() {
        // When
        val failure = assertThrows<ValidationFailure> { validator.validatePngImage(ByteArray(0)) }

        // Then
        assertEquals("image must be a PNG.", failure.message)
    }

    @Test
    fun `if image is a PNG within the limits should accept it`() {
        // When / Then
        assertDoesNotThrow {
            validator.validatePngImage(fakePng(width = 1, height = 1))
            validator.validatePngImage(fakePng(width = 1400, height = 1400))
        }
    }

    @Test
    fun `if image is too big in pixels or has no size should throw ValidationFailure`() {
        // Given
        val fakeSizes = listOf(1401 to 100, 100 to 1401, 0 to 100, 100 to 0, -1 to 100)

        // When / Then
        fakeSizes.forEach { (width, height) ->
            assertThrows<ValidationFailure> { validator.validatePngImage(fakePng(width, height)) }
        }
    }

    @Test
    fun `if image file is at the size limit should accept it and above it should throw ValidationFailure`() {
        // Given
        val fakeLimit = ValidationLimits.MAX_IMAGE_SIZE_BYTES

        // When / Then
        assertDoesNotThrow { validator.validatePngImage(fakePng(size = fakeLimit)) }
        assertThrows<ValidationFailure> { validator.validatePngImage(fakePng(size = fakeLimit + 1)) }
    }

    @Test
    fun `if image is not a PNG or is truncated should throw ValidationFailure`() {
        // Given
        val fakeWrongSignature = fakePng().also { it[0] = 0x00 }
        val fakeWrongChunk = fakePng().also { it[12] = 0x00 }
        val fakeImages = listOf(ByteArray(0), fakePng().copyOf(23), fakeWrongSignature, fakeWrongChunk)

        // When / Then
        fakeImages.forEach { assertThrows<ValidationFailure> { validator.validatePngImage(it) } }
    }

    @Test
    fun `if continent is null or blank should return null`() {
        // When / Then
        assertNull(validator.parseContinent(null))
        assertNull(validator.parseContinent("   "))
    }

    @Test
    fun `if continent is a known name should return it trimmed`() {
        // When / Then
        Continent.entries.forEach { assertEquals(it, validator.parseContinent(" ${it.name} ")) }
    }

    @Test
    fun `if continent is unknown or in another case should throw ValidationFailure`() {
        // Given
        val fakeValues = listOf("FAKE", "europe", "Central America")

        // When / Then
        fakeValues.forEach { assertThrows<ValidationFailure> { validator.parseContinent(it) } }
    }
}
