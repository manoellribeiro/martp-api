package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.SketchArtType
import kotlin.uuid.Uuid

/**
 * The rules for saving an art. Every text field is trimmed first (outer whitespace only), and an optional one that is
 * blank becomes `null`. Nothing else is fixed: a value that still breaks a rule is rejected. Rejected values go to the
 * logs, never to the client.
 */
interface MapArtValidator {
    fun parseId(id: String): Uuid

    /** Trimmed, `null` when nothing is left. Only the length is checked: any text is accepted as an email. */
    fun parseEmail(email: String?): String?

    /** Trimmed, `null` when nothing is left. */
    fun parseUsername(username: String?): String?

    /** Trimmed. May end up empty: the title comes from the address, and some coordinates don't have one. */
    fun parseTitle(title: String): String

    fun validateCoordinates(latitude: Double, longitude: Double)

    fun validateDateInMillis(dateInMillis: Long)

    fun parseSketchArtType(sketchArtType: String?): SketchArtType?

    /** Trimmed, `null` when nothing is left. Whether the palette exists is checked by the caller (it reads the database). */
    fun parseColorPalette(colorPalette: String?): String?

    /** Trimmed, `null` when nothing is left. */
    fun parseLayerColor(field: String, color: String?): String?

    /** Reads only the header, so the image is never decoded. */
    fun validatePngImage(image: ByteArray)

    /** The exhibition filter. Trimmed, `null` when nothing is left (no filter). Exact name, case included. */
    fun parseContinent(continent: String?): Continent?
}
