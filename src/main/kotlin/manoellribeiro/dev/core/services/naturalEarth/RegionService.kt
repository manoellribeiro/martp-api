package manoellribeiro.dev.core.services.naturalEarth

import manoellribeiro.dev.core.models.Region

interface RegionService {
    /**
     * Never throws: saving an art must not depend on this. A point away from every country is OCEAN.
     * Any failure is logged and answered as UNKNOWN, to be fixed by hand.
     */
    fun findRegion(latitude: Double, longitude: Double): Region
}
