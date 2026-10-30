package manoellribeiro.dev.core.services.imageStorage

interface ImageStorageService {
    /** Stores a PNG under this key. An object that already has this key is overwritten. */
    suspend fun upload(key: String, image: ByteArray)

    suspend fun delete(key: String)

    fun publicUrl(key: String): String
}
