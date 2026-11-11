package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.ExhibitionMapArt
import kotlin.random.Random

class GlobeArtsPickerImpl(
    private val random: Random,
) : GlobeArtsPicker {

    // Random order, then keep each art that still fits both rules. Not the biggest possible set:
    // a continent whose arts are all in one city may get fewer pins than its limit.
    override fun pick(arts: List<ExhibitionMapArt>): List<ExhibitionMapArt> {
        val picked = mutableListOf<ExhibitionMapArt>()
        val pickedPerContinent = mutableMapOf<Continent, Int>()
        for (art in arts.shuffled(random)) {
            val continent = art.mapArt.continent
            val pickedInContinent = pickedPerContinent[continent] ?: 0
            if (pickedInContinent >= (GlobeLimits.MAX_ARTS_PER_CONTINENT[continent] ?: 0)) continue
            if (picked.any { it.isTooCloseTo(art) }) continue
            picked += art
            pickedPerContinent[continent] = pickedInContinent + 1
        }
        return picked
    }

    private fun ExhibitionMapArt.isTooCloseTo(other: ExhibitionMapArt): Boolean = distanceKm(
        fromLatitude = mapArt.latitude,
        fromLongitude = mapArt.longitude,
        toLatitude = other.mapArt.latitude,
        toLongitude = other.mapArt.longitude,
    ) < GlobeLimits.MIN_DISTANCE_KM
}
