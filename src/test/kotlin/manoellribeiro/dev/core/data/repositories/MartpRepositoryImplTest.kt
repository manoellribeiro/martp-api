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
import manoellribeiro.dev.core.data.database.entities.ExhibitionMapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.failures.AddressNotFoundFailure
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.core.models.failures.ImageStorageErrorFailure
import manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure
import manoellribeiro.dev.core.services.imageStorage.ImageStorageService
import manoellribeiro.dev.core.services.nominatim.GeocodingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class MartpRepositoryImplTest {

    private lateinit var colorPalettesDao: ColorPalettesDao
    private lateinit var mapArtsDao: MapArtsDao
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var geocodingService: GeocodingService
    private lateinit var errorLogger: ErrorLogger
    private lateinit var repository: MartpRepository

    private val fakeId = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
    private val fakeImageKey = "$fakeId.png"
    private val fakeImage = byteArrayOf(1, 2, 3)
    private val fakeColors = MapLayersColorsEntity(null, null, null, null, null, null)
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
        errorLogger = mockk()
        repository = MartpRepositoryImpl(colorPalettesDao, mapArtsDao, imageStorageService, geocodingService, errorLogger)
    }

    @Test
    fun `if the dao returns palettes should return them`() = runTest {
        // Given
        val fakePalettes = listOf(ColorPaletteEntity(fakeId, "Fake", listOf("#000000"), 1))
        coEvery { colorPalettesDao.getAll() } returns fakePalettes

        // When
        val palettes = repository.getColorPalettes()

        // Then
        assertEquals(fakePalettes, palettes)
    }

    @Test
    fun `if the palettes dao throws should log once and throw DatabaseErrorFailure`() = runTest {
        // Given
        val fakeException = IllegalStateException("fake")
        coEvery { colorPalettesDao.getAll() } throws fakeException
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.getColorPalettes() }
        verify(exactly = 1) { errorLogger.logNonFatal(fakeException, "MartpRepository.getColorPalettes") }
    }

    @Test
    fun `if the coroutine is cancelled should rethrow it without logging`() = runTest {
        // Given
        coEvery { colorPalettesDao.getAll() } throws CancellationException("fake")

        // When / Then
        assertThrows<CancellationException> { repository.getColorPalettes() }
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
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, "fake@example.com", "fake") } returns true

        // When
        repository.saveMapArt(fakeNewMapArt, fakeImage, "fake@example.com", "fake")

        // Then
        coVerify(exactly = 1) { imageStorageService.upload(fakeImageKey, fakeImage) }
        coVerify(exactly = 1) { mapArtsDao.insertWithUser(fakeNewMapArt, fakeImageKey, "fake@example.com", "fake") }
        coVerify(exactly = 0) { imageStorageService.delete(any()) }
    }

    @Test
    fun `if the id already exists should throw conflict and not upload`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns true

        // When / Then
        assertThrows<MapArtAlreadyExistsFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 0) { imageStorageService.upload(any(), any()) }
        coVerify(exactly = 0) { mapArtsDao.insertWithUser(any(), any(), any(), any()) }
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
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } throws ImageStorageErrorFailure("fake")

        // When / Then
        assertThrows<ImageStorageErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 0) { mapArtsDao.insertWithUser(any(), any(), any(), any()) }
        verify(exactly = 0) { errorLogger.logNonFatal(any(), any()) }
    }

    @Test
    fun `if the insert fails should delete the image and throw DatabaseErrorFailure`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any()) } throws IllegalStateException("fake")
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
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any()) } throws IllegalStateException("fake")
        coEvery { imageStorageService.delete(fakeImageKey) } throws ImageStorageErrorFailure("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
    }

    @Test
    fun `if another request saved the same id during the insert should throw conflict and keep the image`() = runTest {
        // Given
        coEvery { mapArtsDao.existsById(fakeId) } returns false
        coEvery { imageStorageService.upload(fakeImageKey, fakeImage) } just runs
        coEvery { mapArtsDao.insertWithUser(any(), any(), any(), any()) } returns false

        // When / Then
        assertThrows<MapArtAlreadyExistsFailure> { repository.saveMapArt(fakeNewMapArt, fakeImage, null, null) }
        coVerify(exactly = 0) { imageStorageService.delete(any()) }
    }

    @Test
    fun `if the dao returns approved arts should add their public image url`() = runTest {
        // Given
        val fakeMapArt = MapArtEntity(fakeId, "Fake", 1.0, 2.0, 1L, fakeImageKey, null, null, fakeColors, null, true)
        coEvery { mapArtsDao.getApprovedForExhibition() } returns listOf(ExhibitionMapArtEntity(fakeMapArt, "fake"))
        every { imageStorageService.publicUrl(fakeImageKey) } returns "https://fake.example.com/$fakeImageKey"

        // When
        val arts = repository.getExhibitionMapArts()

        // Then
        assertEquals(1, arts.size)
        assertEquals("https://fake.example.com/$fakeImageKey", arts.first().imageUrl)
        assertEquals("fake", arts.first().artistUsername)
        assertEquals(fakeMapArt, arts.first().mapArt)
    }

    @Test
    fun `if the exhibition query fails should log and throw DatabaseErrorFailure`() = runTest {
        // Given
        coEvery { mapArtsDao.getApprovedForExhibition() } throws IllegalStateException("fake")
        every { errorLogger.logNonFatal(any(), any()) } just runs

        // When / Then
        assertThrows<DatabaseErrorFailure> { repository.getExhibitionMapArts() }
        verify(exactly = 1) { errorLogger.logNonFatal(any(), "MartpRepository.getExhibitionMapArts") }
    }
}
