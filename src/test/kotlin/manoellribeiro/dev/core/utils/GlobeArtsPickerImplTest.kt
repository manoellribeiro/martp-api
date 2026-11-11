package manoellribeiro.dev.core.utils

import manoellribeiro.dev.core.data.database.entities.MapArtEntity
import manoellribeiro.dev.core.data.database.entities.MapLayersColorsEntity
import manoellribeiro.dev.core.models.Continent
import manoellribeiro.dev.core.models.ExhibitionMapArt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.uuid.Uuid

class GlobeArtsPickerImplTest {

    // About 1 degree of latitude in km
    private val kmPerDegree = 111.195

    private fun fakeArt(continent: Continent, latitude: Double, longitude: Double) = ExhibitionMapArt(
        mapArt = MapArtEntity(
            id = Uuid.random(),
            title = "Fake Title",
            latitude = latitude,
            longitude = longitude,
            dateInMillis = 1_000L,
            imageKey = "fake-key.png",
            continent = continent,
            countryCode = null,
            sketchArtType = null,
            colorPalette = null,
            colorsLayers = MapLayersColorsEntity(null, null, null, null, null, null),
            artistId = null,
            isApprovedForTheExhibition = true,
        ),
        artistUsername = null,
        imageUrl = "fake-url",
    )

    /** Arts on the equator, 10 degrees (about 1100 km) apart, so distance never limits them. */
    private fun fakeFarApartArts(continent: Continent, count: Int) =
        (0 until count).map { fakeArt(continent, latitude = 0.0, longitude = -170.0 + it * 10.0) }

    @Test
    fun `if every continent has many arts far apart should keep each continent limit`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(1))
        val fakeArts = Continent.entries.flatMapIndexed { index, continent ->
            // each continent on its own latitude band, 10 degrees apart
            (0 until 20).map { fakeArt(continent, latitude = -80.0 + index * 16.0, longitude = -170.0 + it * 17.0) }
        }

        // When
        val picked = picker.pick(fakeArts)

        // Then
        Continent.entries.forEach { continent ->
            assertEquals(GlobeLimits.MAX_ARTS_PER_CONTINENT.getValue(continent), picked.count { it.mapArt.continent == continent }, continent.name)
        }
    }

    @Test
    fun `if arts are packed together should never keep two closer than the minimum distance`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(7))
        val fakeArts = (0 until 200).map {
            fakeArt(if (it % 2 == 0) Continent.EUROPE else Continent.AFRICA, latitude = 30.0 + (it % 20) * 0.5, longitude = (it / 20) * 0.7)
        }

        // When
        val picked = picker.pick(fakeArts)

        // Then
        assertTrue(picked.isNotEmpty())
        picked.forEachIndexed { index, art ->
            picked.drop(index + 1).forEach { other ->
                val distance = distanceKm(art.mapArt.latitude, art.mapArt.longitude, other.mapArt.latitude, other.mapArt.longitude)
                assertTrue(distance >= GlobeLimits.MIN_DISTANCE_KM, "$distance km")
            }
        }
    }

    @Test
    fun `if two arts of different continents are closer than the minimum should keep only one`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(3))
        val fakeArts = listOf(fakeArt(Continent.EUROPE, 36.0, -5.6), fakeArt(Continent.AFRICA, 35.8, -5.8))

        // When
        val picked = picker.pick(fakeArts)

        // Then
        assertEquals(1, picked.size)
    }

    @Test
    fun `if two arts are just over the minimum distance apart should keep both`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(3))
        val degrees = (GlobeLimits.MIN_DISTANCE_KM + 1) / kmPerDegree
        val fakeArts = listOf(fakeArt(Continent.EUROPE, 0.0, 0.0), fakeArt(Continent.EUROPE, degrees, 0.0))

        // When
        val picked = picker.pick(fakeArts)

        // Then
        assertEquals(2, picked.size)
    }

    @Test
    fun `if two arts are just under the minimum distance apart should keep one`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(3))
        val degrees = (GlobeLimits.MIN_DISTANCE_KM - 1) / kmPerDegree
        val fakeArts = listOf(fakeArt(Continent.EUROPE, 0.0, 0.0), fakeArt(Continent.EUROPE, degrees, 0.0))

        // When
        val picked = picker.pick(fakeArts)

        // Then
        assertEquals(1, picked.size)
    }

    @Test
    fun `if arts are UNKNOWN should leave them out`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(5))

        // When
        val picked = picker.pick(fakeFarApartArts(Continent.UNKNOWN, 10))

        // Then
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `if there are many OCEAN arts should keep at most 2`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(5))

        // When
        val picked = picker.pick(fakeFarApartArts(Continent.OCEAN, 10))

        // Then
        assertEquals(2, picked.size)
    }

    @Test
    fun `if there are few arts far apart should keep all of them`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(5))
        val fakeArts = fakeFarApartArts(Continent.ASIA, 3) + fakeArt(Continent.EUROPE, 48.8, 2.3)

        // When
        val picked = picker.pick(fakeArts)

        // Then
        assertEquals(fakeArts.toSet(), picked.toSet())
    }

    @Test
    fun `if there are no arts should return an empty list`() {
        // Given
        val picker = GlobeArtsPickerImpl(random = Random(5))

        // When
        val picked = picker.pick(emptyList())

        // Then
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `if called with different random seeds should pick different arts`() {
        // Given
        val fakeArts = fakeFarApartArts(Continent.SOUTH_AMERICA, 30)

        // When
        val first = GlobeArtsPickerImpl(random = Random(1)).pick(fakeArts).toSet()
        val second = GlobeArtsPickerImpl(random = Random(2)).pick(fakeArts).toSet()

        // Then
        assertEquals(5, first.size)
        assertEquals(5, second.size)
        assertNotEquals(first, second)
    }

    @Test
    fun `if a continent is added should also get a globe limit`() {
        // Given / When
        val missing = Continent.entries - GlobeLimits.MAX_ARTS_PER_CONTINENT.keys

        // Then
        assertTrue(missing.isEmpty(), "missing: $missing")
    }

    @Test
    fun `if two points are one degree of latitude apart should be about 111 km`() {
        // Given / When
        val distance = distanceKm(fromLatitude = 0.0, fromLongitude = 0.0, toLatitude = 1.0, toLongitude = 0.0)

        // Then
        assertEquals(kmPerDegree, distance, 0.01)
    }
}
