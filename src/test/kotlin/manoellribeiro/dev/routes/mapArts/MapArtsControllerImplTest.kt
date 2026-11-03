package manoellribeiro.dev.routes.mapArts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.models.ExhibitionMapArt
import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsRequest
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class MapArtsControllerImplTest {

    private lateinit var repository: MartpRepository
    private lateinit var mapArtValidator: MapArtValidator
    private lateinit var controller: MapArtsController

    private val fakeId = Uuid.parse("00000000-0000-0000-0000-00000000fa4e")
    private val fakeImage = byteArrayOf(1, 2, 3)
    private val fakePalette = ColorPaletteEntity(Uuid.random(), "Fake Palette", List(6) { "#000000" }, 0)
    private val fakeRequest = SaveMapArtRequest(
        id = fakeId.toString(),
        title = "Fake Title",
        latitude = 10.5,
        longitude = 20.5,
        dateInMillis = 1_000L,
        sketchArtType = "COLORFUL_MAP",
        colorPalette = "Fake Palette",
        colorsLayers = MapLayersColorsRequest(roadsColor = "#AAAAAA", waterColor = "#BBBBBB"),
        email = "fake.user@example.com",
        username = "fake-user",
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        mapArtValidator = mockk()
        controller = MapArtsControllerImpl(repository = repository, mapArtValidator = mapArtValidator)
    }

    private fun givenEveryValidationPasses() {
        every { mapArtValidator.parseId(any()) } returns fakeId
        every { mapArtValidator.validateTitle(any()) } just runs
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        every { mapArtValidator.validateDateInMillis(any()) } just runs
        every { mapArtValidator.parseSketchArtType(any()) } returns SketchArtType.COLORFUL_MAP
        every { mapArtValidator.validateLayerColor(any(), any()) } just runs
        every { mapArtValidator.validateEmail(any()) } just runs
        every { mapArtValidator.validateUsername(any()) } just runs
        every { mapArtValidator.validatePngImage(any()) } just runs
    }

    @Test
    fun `if the request is valid should save the art with its image and user`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), fakeImage, "fake.user@example.com", "fake-user") } just runs

        // When
        controller.saveMapArt(fakeRequest, fakeImage)

        // Then
        val expected = NewMapArtEntity(
            id = fakeId,
            title = "Fake Title",
            latitude = 10.5,
            longitude = 20.5,
            dateInMillis = 1_000L,
            sketchArtType = SketchArtType.COLORFUL_MAP,
            colorPalette = "Fake Palette",
            colorsLayers = MapLayersColorsEntity("#AAAAAA", null, null, null, "#BBBBBB", null),
        )
        assertEquals(expected, mapArtSlot.captured)
        coVerify(exactly = 1) { repository.saveMapArt(any(), fakeImage, "fake.user@example.com", "fake-user") }
    }

    @Test
    fun `if every field passes should call each validation with the request values`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)
        coEvery { repository.saveMapArt(any(), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest, fakeImage)

        // Then
        verify(exactly = 1) { mapArtValidator.parseId(fakeId.toString()) }
        verify(exactly = 1) { mapArtValidator.validateTitle("Fake Title") }
        verify(exactly = 1) { mapArtValidator.validateCoordinates(10.5, 20.5) }
        verify(exactly = 1) { mapArtValidator.validateDateInMillis(1_000L) }
        verify(exactly = 1) { mapArtValidator.parseSketchArtType("COLORFUL_MAP") }
        verify(exactly = 1) { mapArtValidator.validateLayerColor("colorsLayers.roadsColor", "#AAAAAA") }
        verify(exactly = 1) { mapArtValidator.validateLayerColor("colorsLayers.waterColor", "#BBBBBB") }
        verify(exactly = 1) { mapArtValidator.validateLayerColor("colorsLayers.buildingColor", null) }
        verify(exactly = 6) { mapArtValidator.validateLayerColor(any(), any()) }
        verify(exactly = 1) { mapArtValidator.validateEmail("fake.user@example.com") }
        verify(exactly = 1) { mapArtValidator.validateUsername("fake-user") }
        verify(exactly = 1) { mapArtValidator.validatePngImage(fakeImage) }
    }

    @Test
    fun `if optional fields are missing should save them as null without reading the palettes`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseSketchArtType(null) } returns null
        val request = fakeRequest.copy(sketchArtType = null, colorPalette = null, colorsLayers = null, email = null, username = null)
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), fakeImage, null, null) } just runs

        // When
        controller.saveMapArt(request, fakeImage)

        // Then
        assertNull(mapArtSlot.captured.sketchArtType)
        assertNull(mapArtSlot.captured.colorPalette)
        assertEquals(MapLayersColorsEntity(null, null, null, null, null, null), mapArtSlot.captured.colorsLayers)
        coVerify(exactly = 0) { repository.getColorPalettes() }
        coVerify(exactly = 1) { repository.saveMapArt(any(), fakeImage, null, null) }
    }

    @Test
    fun `if the title is empty should save it empty`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest.copy(title = ""), fakeImage)

        // Then
        assertEquals("", mapArtSlot.captured.title)
    }

    @Test
    fun `if the color palette does not exist should throw ValidationFailure and not save`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest.copy(colorPalette = "Fake Missing Palette"), fakeImage)
        }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the palette list is empty should throw ValidationFailure and not save`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns emptyList()

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the palette name differs only in case should throw ValidationFailure`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest.copy(colorPalette = "fake palette"), fakeImage)
        }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the id is invalid should throw the validator failure and not touch the repository`() = runTest {
        // Given
        val failure = ValidationFailure("fake failure")
        every { mapArtValidator.parseId("fake-id") } throws failure

        // When / Then
        val thrown = assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest.copy(id = "fake-id"), fakeImage)
        }
        assertSame(failure, thrown)
        coVerify(exactly = 0) { repository.getColorPalettes() }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if a layer color is invalid should throw and not touch the repository`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.validateLayerColor("colorsLayers.waterColor", "#BBBBBB") } throws ValidationFailure("fake failure")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.getColorPalettes() }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the email is invalid should throw and not touch the repository`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.validateEmail("fake.user@example.com") } throws ValidationFailure("fake failure")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.getColorPalettes() }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the image is invalid should throw and not touch the repository`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.validatePngImage(fakeImage) } throws ValidationFailure("fake failure")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.getColorPalettes() }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the id already exists should let MapArtAlreadyExistsFailure through`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } returns listOf(fakePalette)
        coEvery { repository.saveMapArt(any(), any(), any(), any()) } throws MapArtAlreadyExistsFailure("fake message")

        // When / Then
        assertThrows<MapArtAlreadyExistsFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
    }

    @Test
    fun `if reading the palettes fails should let the failure through and not save`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.getColorPalettes() } throws DatabaseErrorFailure("fake message")

        // When / Then
        assertThrows<DatabaseErrorFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    private val fakeMapArt = MapArtEntity(
        id = fakeId,
        title = "Fake Title",
        latitude = 10.5,
        longitude = 20.5,
        dateInMillis = 1_000L,
        imageKey = "fake-key.png",
        sketchArtType = SketchArtType.POINTILLISM,
        colorPalette = "Fake Palette",
        colorsLayers = MapLayersColorsEntity("#AAAAAA", null, "#CCCCCC", null, null, "#FFFFFF"),
        artistId = Uuid.random(),
        isApprovedForTheExhibition = true,
    )

    @Test
    fun `if there are approved arts should map every field to the response`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt, artistUsername = "fake-user", imageUrl = "https://fake.example.com/fake-key.png"),
        )

        // When
        val response = controller.getExhibitionMapArts()

        // Then
        val art = response.mapArts.single()
        assertEquals(fakeId.toString(), art.id)
        assertEquals("Fake Title", art.title)
        assertEquals(10.5, art.latitude)
        assertEquals(20.5, art.longitude)
        assertEquals(1_000L, art.dateInMillis)
        assertEquals("https://fake.example.com/fake-key.png", art.imageUrl)
        assertEquals("POINTILLISM", art.sketchArtType)
        assertEquals("Fake Palette", art.colorPalette)
        assertEquals("#AAAAAA", art.colorsLayers.roadsColor)
        assertNull(art.colorsLayers.buildingStrokeColor)
        assertEquals("#CCCCCC", art.colorsLayers.buildingColor)
        assertNull(art.colorsLayers.roadsPathColor)
        assertNull(art.colorsLayers.waterColor)
        assertEquals("#FFFFFF", art.colorsLayers.landOthersColor)
        assertEquals("fake-user", art.user?.username)
        coVerify(exactly = 1) { repository.getExhibitionMapArts() }
    }

    @Test
    fun `if the title is empty should return Unknown Location`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(title = ""), artistUsername = "fake-user", imageUrl = "fake-url"),
        )

        // When
        val response = controller.getExhibitionMapArts()

        // Then
        assertEquals("Unknown Location", response.mapArts.single().title)
    }

    @Test
    fun `if there is no username should return user null`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(artistId = null), artistUsername = null, imageUrl = "fake-url"),
        )

        // When
        val response = controller.getExhibitionMapArts()

        // Then
        assertNull(response.mapArts.single().user)
    }

    @Test
    fun `if sketch type and palette are missing should return them null`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(sketchArtType = null, colorPalette = null), artistUsername = null, imageUrl = "fake-url"),
        )

        // When
        val art = controller.getExhibitionMapArts().mapArts.single()

        // Then
        assertNull(art.sketchArtType)
        assertNull(art.colorPalette)
    }

    @Test
    fun `if there are several arts should keep the repository order`() = runTest {
        // Given
        val fakeSecondId = Uuid.random()
        coEvery { repository.getExhibitionMapArts() } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt, artistUsername = null, imageUrl = "fake-url-1"),
            ExhibitionMapArt(mapArt = fakeMapArt.copy(id = fakeSecondId), artistUsername = null, imageUrl = "fake-url-2"),
        )

        // When
        val response = controller.getExhibitionMapArts()

        // Then
        assertEquals(listOf(fakeId.toString(), fakeSecondId.toString()), response.mapArts.map { it.id })
    }

    @Test
    fun `if there are no approved arts should return an empty list`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } returns emptyList()

        // When
        val response = controller.getExhibitionMapArts()

        // Then
        assertEquals(emptyList<Any>(), response.mapArts)
    }

    @Test
    fun `if the repository fails should let the failure through`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts() } throws DatabaseErrorFailure("fake message")

        // When / Then
        assertThrows<DatabaseErrorFailure> {
            controller.getExhibitionMapArts()
        }
    }
}
