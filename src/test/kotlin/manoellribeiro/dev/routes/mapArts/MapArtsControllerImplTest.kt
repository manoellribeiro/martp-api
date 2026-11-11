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
import manoellribeiro.dev.core.data.database.entities.ContinentTotalEntity
import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.data.repositories.MartpRepository
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.ExhibitionMapArt
import manoellribeiro.dev.core.models.SketchArtType
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure
import manoellribeiro.dev.core.models.failures.ValidationFailure
import manoellribeiro.dev.core.utils.GlobeArtsPicker
import manoellribeiro.dev.core.utils.MapArtValidator
import manoellribeiro.dev.routes.mapArts.models.ExhibitionContinentResponse
import manoellribeiro.dev.routes.mapArts.models.MapLayersColorsRequest
import manoellribeiro.dev.routes.mapArts.models.SaveMapArtRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class MapArtsControllerImplTest {

    private lateinit var repository: MartpRepository
    private lateinit var mapArtValidator: MapArtValidator
    private lateinit var globeArtsPicker: GlobeArtsPicker
    private lateinit var controller: MapArtsController

    private val fakeId = Uuid.parse("00000000-0000-0000-0000-00000000fa4e")
    private val fakeImage = byteArrayOf(1, 2, 3)
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
        globeArtsPicker = mockk()
        controller = MapArtsControllerImpl(repository = repository, mapArtValidator = mapArtValidator, globeArtsPicker = globeArtsPicker)
    }

    private fun givenEveryValidationPasses() {
        every { mapArtValidator.parseId(any()) } returns fakeId
        every { mapArtValidator.parseTitle(any()) } answers { firstArg() }
        every { mapArtValidator.validateCoordinates(any(), any()) } just runs
        every { mapArtValidator.validateDateInMillis(any()) } just runs
        every { mapArtValidator.parseSketchArtType(any()) } returns SketchArtType.COLORFUL_MAP
        every { mapArtValidator.parseLayerColor(any(), any()) } answers { secondArg() }
        every { mapArtValidator.parseColorPalette(any()) } answers { firstArg() }
        every { mapArtValidator.parseEmail(any()) } answers { firstArg() }
        every { mapArtValidator.parseUsername(any()) } answers { firstArg() }
        every { mapArtValidator.validatePngImage(any()) } just runs
    }

    @Test
    fun `if the request is valid should save the art with its image and user`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
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
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
        coEvery { repository.saveMapArt(any(), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest, fakeImage)

        // Then
        verify(exactly = 1) { mapArtValidator.parseId(fakeId.toString()) }
        verify(exactly = 1) { mapArtValidator.parseTitle("Fake Title") }
        verify(exactly = 1) { mapArtValidator.validateCoordinates(10.5, 20.5) }
        verify(exactly = 1) { mapArtValidator.validateDateInMillis(1_000L) }
        verify(exactly = 1) { mapArtValidator.parseSketchArtType("COLORFUL_MAP") }
        verify(exactly = 1) { mapArtValidator.parseLayerColor("colorsLayers.roadsColor", "#AAAAAA") }
        verify(exactly = 1) { mapArtValidator.parseLayerColor("colorsLayers.waterColor", "#BBBBBB") }
        verify(exactly = 1) { mapArtValidator.parseLayerColor("colorsLayers.buildingColor", null) }
        verify(exactly = 6) { mapArtValidator.parseLayerColor(any(), any()) }
        verify(exactly = 1) { mapArtValidator.parseEmail("fake.user@example.com") }
        verify(exactly = 1) { mapArtValidator.parseUsername("fake-user") }
        verify(exactly = 1) { mapArtValidator.parseColorPalette("Fake Palette") }
        verify(exactly = 1) { mapArtValidator.validatePngImage(fakeImage) }
    }

    @Test
    fun `if optional fields are missing should save them as null without checking the palette`() = runTest {
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
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
        coVerify(exactly = 1) { repository.saveMapArt(any(), fakeImage, null, null) }
    }

    @Test
    fun `if the title is empty should save it empty`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest.copy(title = ""), fakeImage)

        // Then
        assertEquals("", mapArtSlot.captured.title)
    }

    @Test
    fun `if the validator cleans the text fields should save the cleaned values`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseTitle(" Fake Title ") } returns "Fake Title"
        every { mapArtValidator.parseEmail("") } returns null
        every { mapArtValidator.parseUsername(" fake-user ") } returns "fake-user"
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), fakeImage, null, "fake-user") } just runs
        val request = fakeRequest.copy(title = " Fake Title ", email = "", username = " fake-user ")

        // When
        controller.saveMapArt(request, fakeImage)

        // Then
        assertEquals("Fake Title", mapArtSlot.captured.title)
        coVerify(exactly = 1) { repository.saveMapArt(any(), fakeImage, null, "fake-user") }
    }

    @Test
    fun `if the validator cleans the palette and layer colors should check and save the cleaned values`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseColorPalette(" Fake Palette ") } returns "Fake Palette"
        every { mapArtValidator.parseLayerColor("colorsLayers.roadsColor", " #AAAAAA ") } returns "#AAAAAA"
        every { mapArtValidator.parseLayerColor("colorsLayers.waterColor", "") } returns null
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), any(), any(), any()) } just runs
        val request = fakeRequest.copy(
            colorPalette = " Fake Palette ",
            colorsLayers = MapLayersColorsRequest(roadsColor = " #AAAAAA ", waterColor = ""),
        )

        // When
        controller.saveMapArt(request, fakeImage)

        // Then
        assertEquals("Fake Palette", mapArtSlot.captured.colorPalette)
        assertEquals(MapLayersColorsEntity("#AAAAAA", null, null, null, null, null), mapArtSlot.captured.colorsLayers)
        coVerify(exactly = 1) { repository.colorPaletteExists("Fake Palette") }
    }

    @Test
    fun `if the palette is blank should save it as null without checking it`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseColorPalette("  ") } returns null
        val mapArtSlot = slot<NewMapArtEntity>()
        coEvery { repository.saveMapArt(capture(mapArtSlot), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest.copy(colorPalette = "  "), fakeImage)

        // Then
        assertNull(mapArtSlot.captured.colorPalette)
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
    }

    @Test
    fun `if the color palette does not exist should throw ValidationFailure and not save`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("Fake Missing Palette") } returns false

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest.copy(colorPalette = "Fake Missing Palette"), fakeImage)
        }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the palette exists should check it by name without reading the offered palettes`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
        coEvery { repository.saveMapArt(any(), any(), any(), any()) } just runs

        // When
        controller.saveMapArt(fakeRequest, fakeImage)

        // Then
        coVerify(exactly = 1) { repository.colorPaletteExists("Fake Palette") }
        coVerify(exactly = 0) { repository.getColorPalettes() }
    }

    @Test
    fun `if the palette name differs only in case should throw ValidationFailure`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("fake palette") } returns false

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest.copy(colorPalette = "fake palette"), fakeImage)
        }
        coVerify(exactly = 1) { repository.colorPaletteExists("fake palette") }
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
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if a layer color is invalid should throw and not touch the repository`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseLayerColor("colorsLayers.waterColor", "#BBBBBB") } throws ValidationFailure("fake failure")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the email is invalid should throw and not touch the repository`() = runTest {
        // Given
        givenEveryValidationPasses()
        every { mapArtValidator.parseEmail("fake.user@example.com") } throws ValidationFailure("fake failure")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.saveMapArt(fakeRequest, fakeImage)
        }
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
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
        coVerify(exactly = 0) { repository.colorPaletteExists(any()) }
        coVerify(exactly = 0) { repository.saveMapArt(any(), any(), any(), any()) }
    }

    @Test
    fun `if the id already exists should let MapArtAlreadyExistsFailure through`() = runTest {
        // Given
        givenEveryValidationPasses()
        coEvery { repository.colorPaletteExists("Fake Palette") } returns true
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
        coEvery { repository.colorPaletteExists(any()) } throws DatabaseErrorFailure("fake message")

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
        continent = Continent.CENTRAL_AMERICA,
        countryCode = "CU",
        sketchArtType = SketchArtType.POINTILLISM,
        colorPalette = "Fake Palette",
        colorsLayers = MapLayersColorsEntity("#AAAAAA", null, "#CCCCCC", null, null, "#FFFFFF"),
        artistId = Uuid.random(),
        isApprovedForTheExhibition = true,
    )

    @Test
    fun `if there are approved arts should map every field to the response`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt, artistUsername = "fake-user", imageUrl = "https://fake.example.com/fake-key.png"),
        )

        // When
        val response = controller.getExhibitionMapArts(null)

        // Then
        val art = response.mapArts.single()
        assertEquals(fakeId.toString(), art.id)
        assertEquals("Fake Title", art.title)
        assertEquals(10.5, art.latitude)
        assertEquals(20.5, art.longitude)
        assertEquals(1_000L, art.dateInMillis)
        assertEquals("CENTRAL_AMERICA", art.continent)
        assertEquals("CU", art.countryCode)
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
        coVerify(exactly = 1) { repository.getExhibitionMapArts(null) }
    }

    @Test
    fun `if the title is empty should return Unknown Location`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(title = ""), artistUsername = "fake-user", imageUrl = "fake-url"),
        )

        // When
        val response = controller.getExhibitionMapArts(null)

        // Then
        assertEquals("Unknown Location", response.mapArts.single().title)
    }

    @Test
    fun `if there is no username should return user null`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(artistId = null), artistUsername = null, imageUrl = "fake-url"),
        )

        // When
        val response = controller.getExhibitionMapArts(null)

        // Then
        assertNull(response.mapArts.single().user)
    }

    @Test
    fun `if sketch type and palette are missing should return them null`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(sketchArtType = null, colorPalette = null), artistUsername = null, imageUrl = "fake-url"),
        )

        // When
        val art = controller.getExhibitionMapArts(null).mapArts.single()

        // Then
        assertNull(art.sketchArtType)
        assertNull(art.colorPalette)
    }

    @Test
    fun `if there are several arts should keep the repository order`() = runTest {
        // Given
        val fakeSecondId = Uuid.random()
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt, artistUsername = null, imageUrl = "fake-url-1"),
            ExhibitionMapArt(mapArt = fakeMapArt.copy(id = fakeSecondId), artistUsername = null, imageUrl = "fake-url-2"),
        )

        // When
        val response = controller.getExhibitionMapArts(null)

        // Then
        assertEquals(listOf(fakeId.toString(), fakeSecondId.toString()), response.mapArts.map { it.id })
    }

    @Test
    fun `if there are no approved arts should return an empty list`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns emptyList()

        // When
        val response = controller.getExhibitionMapArts(null)

        // Then
        assertEquals(emptyList<Any>(), response.mapArts)
    }

    @Test
    fun `if the repository fails should let the failure through`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } throws DatabaseErrorFailure("fake message")

        // When / Then
        assertThrows<DatabaseErrorFailure> {
            controller.getExhibitionMapArts(null)
        }
    }

    @Test
    fun `if the art is in the ocean should return OCEAN and countryCode null`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(null) } returns null
        coEvery { repository.getExhibitionMapArts(null) } returns listOf(
            ExhibitionMapArt(mapArt = fakeMapArt.copy(continent = Continent.OCEAN, countryCode = null), artistUsername = null, imageUrl = "fake-url"),
        )

        // When
        val art = controller.getExhibitionMapArts(null).mapArts.single()

        // Then
        assertEquals("OCEAN", art.continent)
        assertNull(art.countryCode)
    }

    @Test
    fun `if a continent filter is given should pass the parsed continent to the repository`() = runTest {
        // Given
        every { mapArtValidator.parseContinent(" EUROPE ") } returns Continent.EUROPE
        coEvery { repository.getExhibitionMapArts(Continent.EUROPE) } returns emptyList()

        // When
        controller.getExhibitionMapArts(" EUROPE ")

        // Then
        coVerify(exactly = 1) { repository.getExhibitionMapArts(Continent.EUROPE) }
    }

    @Test
    fun `if the continent filter is invalid should throw ValidationFailure and not call the repository`() = runTest {
        // Given
        every { mapArtValidator.parseContinent("fake") } throws ValidationFailure("fake message")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.getExhibitionMapArts("fake")
        }
        coVerify(exactly = 0) { repository.getExhibitionMapArts(any()) }
    }

    @Test
    fun `if the repository returns continent totals should map them keeping the order`() = runTest {
        // Given
        coEvery { repository.getExhibitionContinents() } returns listOf(
            ContinentTotalEntity(continent = Continent.SOUTH_AMERICA, total = 32),
            ContinentTotalEntity(continent = Continent.EUROPE, total = 10),
            ContinentTotalEntity(continent = Continent.OCEAN, total = 1),
        )

        // When
        val response = controller.getExhibitionContinents()

        // Then
        assertEquals(
            listOf(
                ExhibitionContinentResponse(continent = "SOUTH_AMERICA", total = 32),
                ExhibitionContinentResponse(continent = "EUROPE", total = 10),
                ExhibitionContinentResponse(continent = "OCEAN", total = 1),
            ),
            response.continents,
        )
        coVerify(exactly = 1) { repository.getExhibitionContinents() }
    }

    @Test
    fun `if no continent has approved arts should return an empty list`() = runTest {
        // Given
        coEvery { repository.getExhibitionContinents() } returns emptyList()

        // When
        val response = controller.getExhibitionContinents()

        // Then
        assertTrue(response.continents.isEmpty())
    }

    @Test
    fun `if the continent totals fail should let the failure through`() = runTest {
        // Given
        coEvery { repository.getExhibitionContinents() } throws DatabaseErrorFailure("fake message")

        // When / Then
        assertThrows<DatabaseErrorFailure> {
            controller.getExhibitionContinents()
        }
    }

    @Test
    fun `if the picker selects arts should return only those, in its order`() = runTest {
        // Given
        val fakeFirst = ExhibitionMapArt(mapArt = fakeMapArt, artistUsername = null, imageUrl = "fake-url-1")
        val fakeSecond = ExhibitionMapArt(mapArt = fakeMapArt.copy(id = Uuid.random()), artistUsername = null, imageUrl = "fake-url-2")
        val fakeThird = ExhibitionMapArt(mapArt = fakeMapArt.copy(id = Uuid.random()), artistUsername = null, imageUrl = "fake-url-3")
        val fakeAll = listOf(fakeFirst, fakeSecond, fakeThird)
        coEvery { repository.getExhibitionMapArts(null) } returns fakeAll
        every { globeArtsPicker.pick(fakeAll) } returns listOf(fakeThird, fakeFirst)

        // When
        val response = controller.getGlobeMapArts()

        // Then
        assertEquals(listOf("fake-url-3", "fake-url-1"), response.mapArts.map { it.imageUrl })
        coVerify(exactly = 1) { repository.getExhibitionMapArts(null) }
        verify(exactly = 1) { globeArtsPicker.pick(fakeAll) }
    }

    @Test
    fun `if the globe query fails should let the failure through and not call the picker`() = runTest {
        // Given
        coEvery { repository.getExhibitionMapArts(null) } throws DatabaseErrorFailure("fake message")

        // When / Then
        assertThrows<DatabaseErrorFailure> {
            controller.getGlobeMapArts()
        }
        verify(exactly = 0) { globeArtsPicker.pick(any()) }
    }
}
