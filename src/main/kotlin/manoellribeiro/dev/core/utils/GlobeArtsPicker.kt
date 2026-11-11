package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.models.ExhibitionMapArt

interface GlobeArtsPicker {
    /**
     * A random selection of [arts] for the globe, different on each call: at most
     * [GlobeLimits.MAX_ARTS_PER_CONTINENT] per continent, and no two closer than [GlobeLimits.MIN_DISTANCE_KM].
     */
    fun pick(arts: List<ExhibitionMapArt>): List<ExhibitionMapArt>
}
