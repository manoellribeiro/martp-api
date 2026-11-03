package manoellribeiro.dev.routes.colorPalettes

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.routes.colorPalettes.models.ColorPaletteResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class ColorPalettesControllerImplTest {

    private lateinit var repository: MartpRepository
    private lateinit var controller: ColorPalettesController

    @BeforeEach
    fun setup() {
        repository = mockk()
        controller = ColorPalettesControllerImpl(repository = repository)
    }

    @Test
    fun `if the repository returns palettes should keep their order and send only name and colors`() = runTest {
        // Given
        val fakeColors = listOf("#000000", "#111111", "#222222", "#333333", "#444444", "#555555")
        coEvery { repository.getColorPalettes() } returns listOf(
            ColorPaletteEntity(Uuid.random(), "Fake First", fakeColors, 1),
            ColorPaletteEntity(Uuid.random(), "Fake Second", fakeColors, 2),
        )

        // When
        val response = controller.getColorPalettes()

        // Then
        assertEquals(
            listOf(ColorPaletteResponse("Fake First", fakeColors), ColorPaletteResponse("Fake Second", fakeColors)),
            response.palettes,
        )
    }

    @Test
    fun `if there are no palettes should return an empty list`() = runTest {
        // Given
        coEvery { repository.getColorPalettes() } returns emptyList()

        // When
        val response = controller.getColorPalettes()

        // Then
        assertTrue(response.palettes.isEmpty())
    }

    @Test
    fun `if the repository throws a Failure should let it through`() = runTest {
        // Given
        coEvery { repository.getColorPalettes() } throws DatabaseErrorFailure("fake")

        // When / Then
        assertThrows<DatabaseErrorFailure> { controller.getColorPalettes() }
    }
}
