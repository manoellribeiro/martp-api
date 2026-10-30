package manoellribeiro.dev.core.data.repositories

import kotlinx.coroutines.CancellationException
import manoellribeiro.dev.core.data.database.daos.ColorPalettesDao
import manoellribeiro.dev.core.data.database.daos.MapArtsDao
import manoellribeiro.dev.core.data.database.entities.ColorPaletteEntity
import manoellribeiro.dev.core.data.database.entities.NewMapArtEntity
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.Address
import manoellribeiro.dev.core.models.ExhibitionMapArt
import manoellribeiro.dev.core.models.failures.DatabaseErrorFailure
import manoellribeiro.dev.core.models.failures.Failure
import manoellribeiro.dev.core.models.failures.MapArtAlreadyExistsFailure
import manoellribeiro.dev.core.services.imageStorage.ImageStorageService
import manoellribeiro.dev.core.services.nominatim.GeocodingService

class MartpRepositoryImpl(
    private val colorPalettesDao: ColorPalettesDao,
    private val mapArtsDao: MapArtsDao,
    private val imageStorageService: ImageStorageService,
    private val geocodingService: GeocodingService,
    private val errorLogger: ErrorLogger,
) : MartpRepository {

    override suspend fun getColorPalettes(): List<ColorPaletteEntity> =
        databaseCall("MartpRepository.getColorPalettes") { colorPalettesDao.getAll() }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double, language: String?): Address =
        geocodingService.reverseGeocode(latitude, longitude, language)

    override suspend fun saveMapArt(mapArt: NewMapArtEntity, image: ByteArray, email: String?, username: String?) {
        val operation = "MartpRepository.saveMapArt"
        // Before the upload: an existing key would have its image overwritten
        if (databaseCall(operation) { mapArtsDao.existsById(mapArt.id) }) {
            throw MapArtAlreadyExistsFailure(originalExceptionMessage = null)
        }

        val imageKey = "${mapArt.id}.png"
        imageStorageService.upload(imageKey, image)

        val inserted = try {
            databaseCall(operation) { mapArtsDao.insertWithUser(mapArt, imageKey, email, username) }
        } catch (failure: DatabaseErrorFailure) {
            deleteImageAfterFailedInsert(imageKey)
            throw failure
        }
        // Another request just saved this id, so the image belongs to its art: keep it
        if (!inserted) throw MapArtAlreadyExistsFailure(originalExceptionMessage = null)
    }

    override suspend fun getExhibitionMapArts(): List<ExhibitionMapArt> =
        databaseCall("MartpRepository.getExhibitionMapArts") { mapArtsDao.getApprovedForExhibition() }
            .map {
                ExhibitionMapArt(
                    mapArt = it.mapArt,
                    artistUsername = it.artistUsername,
                    imageUrl = imageStorageService.publicUrl(it.mapArt.imageKey),
                )
            }

    private suspend fun deleteImageAfterFailedInsert(imageKey: String) {
        try {
            imageStorageService.delete(imageKey)
        } catch (_: Failure) {
            // Already logged by the service. The client must get the insert's error, not this one.
        }
    }

    private suspend fun <T> databaseCall(operation: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, operation)
            throw DatabaseErrorFailure(exception.message)
        }
    }
}
