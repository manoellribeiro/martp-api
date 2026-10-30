package manoellribeiro.dev.core.services.imageStorage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.CancellationException
import manoellribeiro.dev.core.config.StorageConfig
import manoellribeiro.dev.core.logging.ErrorLogger
import manoellribeiro.dev.core.models.failures.ImageStorageErrorFailure

class ImageStorageServiceImpl(
    private val s3Client: S3Client,
    private val config: StorageConfig,
    private val errorLogger: ErrorLogger,
) : ImageStorageService {

    override suspend fun upload(key: String, image: ByteArray) = runConvertingErrors("ImageStorageService.upload") {
        s3Client.putObject {
            bucket = config.bucket
            this.key = key
            body = ByteStream.fromBytes(image)
            contentType = PNG_CONTENT_TYPE
        }
    }

    override suspend fun delete(key: String) = runConvertingErrors("ImageStorageService.delete") {
        s3Client.deleteObject {
            bucket = config.bucket
            this.key = key
        }
    }

    override fun publicUrl(key: String): String = "${config.publicBaseUrl}/$key"

    private suspend fun runConvertingErrors(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, operation)
            throw ImageStorageErrorFailure(exception.message)
        }
    }

    private companion object {
        const val PNG_CONTENT_TYPE = "image/png"
    }
}
