package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.SketchArtType
import kotlin.uuid.Uuid

/** The rules for saving an art. Input is rejected, never fixed. Rejected values go to the logs, never to the client. */
interface MapArtValidator {
    fun parseId(id: String): Uuid

    fun validateEmail(email: String?)

    fun validateUsername(username: String?)

    /** May be empty: the title comes from the address, and some coordinates don't have one. */
    fun validateTitle(title: String)

    fun validateCoordinates(latitude: Double, longitude: Double)

    fun validateDateInMillis(dateInMillis: Long)

    fun parseSketchArtType(sketchArtType: String?): SketchArtType?

    fun validateLayerColor(field: String, color: String?)

    /** Reads only the header, so the image is never decoded. */
    fun validatePngImage(image: ByteArray)
}
