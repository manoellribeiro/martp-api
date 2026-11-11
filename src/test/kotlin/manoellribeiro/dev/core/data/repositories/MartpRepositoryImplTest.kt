package manoellribeiro.dev.core.data.repositories

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import manoellribeiro.dev.core.data.database.daos.ColorPalettesDao
import manoellribeiro.dev.core.data.database.daos.MapArtsDao
import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.entities.ContinentTotalEntity
import manoellribeiro.dev.core.data.database.entities.ExhibitionMapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.Region
import manoellribeiro.dev.core.models.failures.AddressNotFoundFailure
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.core.models.failures.ImageStorageErrorFailure
import manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure
import manoellribeiro.dev.core.services.imageStorage.ImageStorageService
import manoellribeiro.dev.core.services.naturalEarth.RegionService
import manoellribeiro.dev.core.services.nominatim.GeocodingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class MartpRepositoryImplTest {

    private lateinit var colorPalettesDao: ColorPalettesDao
    private lateinit var mapArtsDao: MapArtsDao
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var geocodingService: GeocodingService
    private lateinit var regionService: RegionService
    private lateinit var errorLogger: ErrorLogger
    private lateinit var repository: MartpRepository

    private val fakeId = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
    private val fakeImageKey = "$fakeId.png"
    private val fakeImage = byteArrayOf(1, 2, 3)
    private val fakeColors = MapLayersColorsEntity(null, null, null, null, null, null)
    private val fakeRegion = Region(continent = Continent.EUROPE, countryCode = "FR")
    private val fakeNewMapArt = NewMapArtEntity(
        id = fakeId,
        title = "Fake Title",
        latitude = 1.0,
        longitude = 2.0,
        dateInMillis = 1L,
        sketchArtType = null,
        colorPalette = null,
        colorsLayers = fakeColors,
    )

    @BeforeEach
    fun setup() {
        colorPalettesDao = mockk()
        mapArtsDao = mockk()
        imageStorageService = mockk()
        geocodingService = mockk()
        regionService = mockk()
        errorLogger = mockk()
        repository = MartpRepositoryImpl(colorPalettesDao, mapArtsDao, imageStorageService, geocodingService, regionService, errorLogger)
    }

    @Test
    fun `if the dao returns palettes should return them`() = runTest {
        // Given
        val fakePalettes = listOf(ColorPaletteEntity(fakeId, "Fake", listOf("#000000"), 1))
        coEvery { colorPalettesDao.getAvailable() } returns fakePalettes

        // When
        val palettes = repository.getColorPalettes()

        // Then
        assertEquals(fakePalettes, palettes)
    }

    @Test
    fun `if the palettes dao throws should log once and throw DatabaseErrorFailure`() = runTest {
        // Given
        val fakeException = IllegalStateException("fake")
        coEvery { colorPalettesDao.getAvailable() } throws fakeException
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.getColorPalettes() }
        verify(exactly = 1) { errorLogger.logNonFatal(fakeException, "MartpRepository.getColorPalettes") }
    }

    @Test
    fun `if the coroutine is cancelled should rethrow it without logging`() = runTest {
        // Given
        coEvery { colorPalettesDao.getAvailable() } throws CancellationException("fake")

        // When / Then
        assertThrows<CancellationException> { repository.getColorPalettes() }
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if a palette with the name exists should return true`() = runTest {
        // Given
        coEvery { colorPalettesDao.existsByName("Fake Palette") } returns true

        // When
        val exists = repository.colorPaletteExists("Fake Palette")

        // Then
        assertTrue(exists)
        coVerify(exactly = 1) { colorPalettesDao.existsByName("Fake Palette") }
    }

    @Test
    fun `if no palette with the name exists should return false`() = runTest {
        // Given
        coEvery { colorPalettesDao.existsByName("Fake Palette") } returns false

        // When
        val exists = repository.colorPaletteExists("Fake Palette")

        // Then
        assertFalse(exists)
    }

    @Test
    fun `if checking the palette name throws should log once and throw DatabaseErrorFailure`() = runTest {
        // Given
        val fakeException = IllegalStateException("fake")
        coEvery { colorPalettesDao.existsByName("Fake Palette") } throws fakeException
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.colorPaletteExists("Fake Palette") }
        verify(exactly = 1) { errorLogger.logNonFatal(fakeException, "MartpRepository.colorPaletteExists") }
    }

    @Test
    fun `if the palette name check is cancelled should rethrow it without logging`() = runTest {
        // Given
        coEvery { colorPalettesDao.existsByName("Fake Palette") } throws CancellationException("fake")

        // When / Then
        assertThrows<CancellationException> { repository.colorPaletteExists("Fake Palette") }
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if the service returns an address should return it`() = runTest {
        // Given
        val fakeAddress = Address("Fake Street", "Fake City", "Fake Country")
        coEvery { geocodingService.reverseGeocode(1.0, 2.0, "pt-BR") } returns fakeAddress

        // When
        val address = repository.reverseGeocode(1.0, 2.0, "pt-BR")

        // Then
        assertEquals(fakeAddress, address)
    }

    @Test
    fun `if the service throws a Failure should let it through without logging`() = runTest {
        // Given
        coEvery { geocodingService.reverseGeocode(1.0, 2.0, null) } throws AddressNotFoundFailure(null)

        // When / Then
        assertThrows<AddressNotFoundFailure> { repository.reverseGeocode(1.0, 2.0, null) }
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if the id is free should upload the image and then insert`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, fakeRegion, "fake@example.com", "fake") } returns true

        // When
        repository.saveMapArt(fakeNewMapArt, fakeImage, "fake@example.com", "fake")

        // Then
        coVerify(exactly = 1) { imageStorageService.upload(fakeImageKey, fakeImage) }
        coVerify(exactly = 1) { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, fakeRegion, "fake@example.com", "fake") }
        verify(exactly = 1) { regionService.findRegion(1.0, 2.0) }
        coVerify(exactly = 0) { imageStorageService.delete(any()) }
    }

    @Test
    fun `if the region lookup answers UNKNOWN should still upload and insert the art`() = runTest {
        // Given
        val fakeUnknownRegion = Region(continent = Continent.UNKNOWN, countryCode = null)
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeUnknownRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, fakeUnknownRegion, null, null) } returns true

        // When
        repository.saveMapArt(fakeNewMapArt, fakeImage, null, null)

        // Then
        coVerify(exactly = 1) { imageStorageService.upload(fakeImageKey, fakeImage) }
        coVerify(exactly = 1) { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, fakeUnknownRegion, null, null) }
    }

    @Test
    fun `if the id already exists should throw conflict and not upload`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns true

        // When / Then
        assertThrows<MapArtAlreadyExistsFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        verify(exactly = 0) { regionService.findRegion(any(), any()) }
        coVerify(exactly = 0) { imageStorageService.upload(any(), any()) }
        coVerify(exactly = 0) { mapArtsDao.insertWithUser(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `if the exists check fails should log and not upload`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } throws IllegalStateException("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "MartpRepository.saveMapArt") }
        coVerify(exactly = 0) { imageStorageService.upload(any(), any()) }
    }

    @Test
    fun `if the upload fails should not insert`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } throws ImageStorageErrorFailure("fake")

        // When / Then
        assertThrows<ImageStorageErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 0) { mapArtsDao.insertWithUser(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if the insert fails should delete the image and throw DatabaseErrorFailure`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any(), any()) } throws IllegalStateException("fake")
        coEvery { imageStorageService.delete(fakeImageKey) } just runs
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 1) { imageStorageService.delete(fakeImageKey) }
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "MartpRepository.saveMapArt") }
    }

    @Test
    fun `if the insert and the image delete fail should still throw the insert error`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any(), any()) } throws IllegalStateException("fake")
        coEvery { imageStorageService.delete(fakeImageKey) } throws ImageStorageErrorFailure("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
    }

    @Test
    fun `if another request saved the same id during the insert should throw conflict and keep the image`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        every { regionService.findRegion(1.0, 2.0) } returns fakeRegion
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any(), any()) } returns false

        // When / Then
        assertThrows<MapArtAlreadyExistsFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 0) { imageStorageService.delete(any()) }
    }

    @Test
    fun `if the dao returns approved arts should add their public image url`() = runTest {
        // Given
        val fakeMapArt = MapArtEntity(fakeId, "Fake", 1.0, 2.0, 1L, fakeImageKey, Continent.EUROPE, "FR", null, null, fakeColors, null, true)
        coEvery { mapArtsDao.getApprovedForExhibition(null) } returns listOf(ExhibitionMapArtEntity(fakeMapArt, "fake"))
        every { imageStorageService.publicUrl(fakeImageKey) } returns "https://fake.example.com/$fakeImageKey"

        // When
        val arts = repository.getExhibitionMapArts(null)

        // Then
        assertEquals(1, arts.size)
        assertEquals("https://fake.example.com/$fakeImageKey", arts.first().imageUrl)
        assertEquals("fake", arts.first().artistUsername)
        assertEquals(fakeMapArt, arts.first().mapArt)
    }

    @Test
    fun `if a continent is given should pass it to the dao`() = runTest {
        // Given
        coEvery { mapArtsDao.getApprovedForExhibition(Continent.CENTRAL_AMERICA) } returns emptyList()

        // When
        val arts = repository.getExhibitionMapArts(Continent.CENTRAL_AMERICA)

        // Then
        assertTrue(arts.isEmpty())
        coVerify(exactly = 1) { mapArtsDao.getApprovedForExhibition(Continent.CENTRAL_AMERICA) }
        coVerify(exactly = 0) { mapArtsDao.getApprovedForExhibition(null) }
    }

    @Test
    fun `if the exhibition query fails should log and throw DatabaseErrorFailure`() = runTest {
        // Given
        coEvery { mapArtsDao.getApprovedForExhibition(null) } throws IllegalStateException("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.getExhibitionMapArts(null) }
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "MartpRepository.getExhibitionMapArts") }
    }

    @Test
    fun `if the dao returns continent totals should return them as they are`() = runTest {
        // Given
        val fakeTotals = listOf(
            ContinentTotalEntity(continent = Continent.SOUTH_AMERICA, total = 32),
            ContinentTotalEntity(continent = Continent.UNKNOWN, total = 1),
        )
        coEvery { mapArtsDao.getApprovedTotalsByContinent() } returns fakeTotals

        // When
        val totals = repository.getExhibitionContinents()

        // Then
        assertEquals(fakeTotals, totals)
        coVerify(exactly = 1) { mapArtsDao.getApprovedTotalsByContinent() }
    }

    @Test
    fun `if the continent totals query fails should log and throw DatabaseErrorFailure`() = runTest {
        // Given
        coEvery { mapArtsDao.getApprovedTotalsByContinent() } throws IllegalStateException("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.getExhibitionContinents() }
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "MartpRepository.getExhibitionContinents") }
    }
}
