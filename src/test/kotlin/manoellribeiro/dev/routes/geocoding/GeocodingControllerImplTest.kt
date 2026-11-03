package manoellribeiro.dev.routes.geocoding

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.failures.AddressNotFoundFailure
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.core.utils.ValidationLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeocodingControllerImplTest {

    private lateinit var repository: MartpRepository
    private lateinit var mapArtValidator: MapArtValidator
    private lateinit var errorLogger: ErrorLogger
    private lateinit var controller: GeocodingController

    private val fakeAddress = Address("Fake Street", null, "Fake Country")

    @BeforeEach
    fun setup() {
        repository = mockk()
        mapArtValidator = mockk()
        errorLogger = mockk()
        controller = GeocodingControllerImpl(repository = repository, mapArtValidator = mapArtValidator, errorLogger = errorLogger)
    }

    @Test
    fun `if the request is valid should return the address keeping missing fields null`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        coEvery { repository.reverseGeocode(48.5, 2.5, "pt-BR") } returns fakeAddress

        // When
        val response = controller.reverseGeocode("48.5", "2.5", "pt-BR")

        // Then
        assertEquals("Fake Street", response.street)
        assertNull(response.city)
        assertEquals("Fake Country", response.country)
    }

    @Test
    fun `if language is missing should pass null and not log`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        coEvery { repository.reverseGeocode(1.0, 2.0, null) } returns fakeAddress

        // When
        controller.reverseGeocode("1", "2", null)

        // Then
        coVerify(exactly = 1) { repository.reverseGeocode(1.0, 2.0, null) }
        verify(exactly = 0) { errorLogger.logClientError(any(), any(), any()) }
    }

    @Test
    fun `if language is invalid should use english and log the value`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        val fakeLanguages = listOf("pt_BR", "", "pt,en", "a".repeat(ValidationLimits.MAX_LANGUAGE_LENGTH + 1))
        coEvery { repository.reverseGeocode(1.0, 2.0, "en") } returns fakeAddress
        every { errorLogger.logClientError(any(), any(), any()) } just runs

        // When
        fakeLanguages.forEach { controller.reverseGeocode("1", "2", it) }

        // Then
        coVerify(exactly = fakeLanguages.size) { repository.reverseGeocode(1.0, 2.0, "en") }
        verify(exactly = 1) { errorLogger.logClientError("InvalidLanguage", match { "pt_BR" in it }, "GeocodingController.reverseGeocode") }
    }

    @Test
    fun `if language is huge should cut it in the log`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        val message = slot<String>()
        coEvery { repository.reverseGeocode(1.0, 2.0, "en") } returns fakeAddress
        every { errorLogger.logClientError(any(), capture(message), any()) } just runs

        // When
        controller.reverseGeocode("1", "2", "a".repeat(ValidationLimits.MAX_LOGGED_VALUE_LENGTH + 500))

        // Then
        assertTrue(message.captured.contains("a".repeat(ValidationLimits.MAX_LOGGED_VALUE_LENGTH)))
        assertTrue(!message.captured.contains("a".repeat(ValidationLimits.MAX_LOGGED_VALUE_LENGTH + 1)))
    }

    @Test
    fun `if language is at the length limit should use it`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        val fakeLanguage = "a".repeat(ValidationLimits.MAX_LANGUAGE_LENGTH)
        coEvery { repository.reverseGeocode(1.0, 2.0, fakeLanguage) } returns fakeAddress

        // When
        controller.reverseGeocode("1", "2", fakeLanguage)

        // Then
        coVerify(exactly = 1) { repository.reverseGeocode(1.0, 2.0, fakeLanguage) }
    }

    @Test
    fun `if a coordinate is missing or not a number should throw ValidationFailure and not call the validator or the repository`() = runTest {
        // Given
        val fakeCoordinates = listOf(null to "2", "1" to null, "abc" to "2", "1" to "abc", "" to "2")

        // When / Then
        fakeCoordinates.forEach { (latitude, longitude) ->
            assertThrows<ValidationFailure> { controller.reverseGeocode(latitude, longitude, null) }
        }
        verify(exactly = 0) { mapArtValidator.validateCoordinates(any(), any()) }
        coVerify(exactly = 0) { repository.reverseGeocode(any(), any(), any()) }
    }

    @Test
    fun `if the validator rejects the coordinates should let the failure through and not call the repository`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(90.1, 2.0) } throws ValidationFailure("fake")

        // When / Then
        assertThrows<ValidationFailure> { controller.reverseGeocode("90.1", "2", null) }
        coVerify(exactly = 0) { repository.reverseGeocode(any(), any(), any()) }
    }

    @Test
    fun `if a coordinate is not a number should keep it for the logs and not send it to the client`() = runTest {
        // When
        val failure = assertThrows<ValidationFailure> { controller.reverseGeocode("abc", "2", null) }

        // Then
        assertEquals("latitude must be a number.", failure.messageToClient)
        assertEquals("latitude must be a number. Rejected value: \"abc\"", failure.message)
    }

    @Test
    fun `if there is no address should let the failure through`() = runTest {
        // Given
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        coEvery { repository.reverseGeocode(0.0, -30.0, null) } throws AddressNotFoundFailure(null)

        // When / Then
        assertThrows<AddressNotFoundFailure> { controller.reverseGeocode("0", "-30", null) }
    }
}
