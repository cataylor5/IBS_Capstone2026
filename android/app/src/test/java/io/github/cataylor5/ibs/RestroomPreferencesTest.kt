package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class RestroomPreferencesTest {
    private fun row(id: Long = 1, access: RestroomAccess = RestroomAccess.PUBLIC,
        rating: Double? = 4.5, type: String? = "SINGLE_STALL") =
        BackendRestroom(id, "Business", "Address", 36.0, -80.0, access, false, false,
            false, true, false, rating, if (rating == null) 0 else 2, businessId = 8, restroomType = type)

    @Test fun exactUnroundedThresholdsAreUsedForEveryRatingChoice() {
        for ((stars, minimum) in mapOf(5 to 4.6, 4 to 4.0, 3 to 3.0, 2 to 2.0, 1 to 1.0)) {
            val filter = RestroomPreferences(minimumStars = stars)
            assertTrue(filter.matches(row(rating = minimum)))
            assertFalse(filter.matches(row(rating = minimum - 0.00001)))
            assertFalse(filter.matches(row(rating = null)))
        }
        assertFalse(RestroomPreferences(minimumStars = 5).matches(row(rating = 4.599999)))
        assertTrue(RestroomPreferences().matches(row(rating = null)))
        assertFalse(RestroomPreferences(minimumStars = 4).matches(row().copy(ratingCount = 0)))
    }

    @Test fun accessSelectionsUseStoredValuesAndAllowMembership() {
        for (access in listOf(RestroomAccess.PUBLIC, RestroomAccess.CUSTOMER_ONLY, RestroomAccess.MEMBERSHIP_REQUIRED)) {
            assertTrue(RestroomPreferences(setOf(access)).matches(row(access = access)))
            assertFalse(RestroomPreferences(setOf(access)).matches(row(access =
                if (access == RestroomAccess.PUBLIC) RestroomAccess.CUSTOMER_ONLY else RestroomAccess.PUBLIC)))
        }
        assertTrue(RestroomPreferences(setOf(RestroomAccess.PUBLIC, RestroomAccess.CUSTOMER_ONLY))
            .matches(row(access = RestroomAccess.CUSTOMER_ONLY)))
        assertFalse(RestroomPreferences().matches(row(access = RestroomAccess.EMPLOYEE_ONLY)))
        assertFalse(RestroomPreferences().matches(row(access = RestroomAccess.UNKNOWN)))
    }

    @Test fun privacyMatchesSingleAndFamilyVersusMultipleStalls() {
        val private = RestroomPreferences(privacy = PrivacyPreference.PRIVATE)
        assertTrue(private.matches(row(type = "SINGLE_STALL")))
        assertTrue(private.matches(row(type = "FAMILY")))
        assertFalse(private.matches(row(type = "MULTI_STALL")))
        assertFalse(private.matches(row(type = null)))
        val stalls = RestroomPreferences(privacy = PrivacyPreference.STALLS)
        assertTrue(stalls.matches(row(type = "MULTI_STALL")))
        assertFalse(stalls.matches(row(type = "FAMILY")))
    }

    @Test fun filtersCannotCombineAttributesFromDifferentRestroomsAtSameBusiness() {
        val rows = listOf(row(1, rating = 4.9, type = "MULTI_STALL"),
            row(2, access = RestroomAccess.CUSTOMER_ONLY, rating = 3.5, type = "FAMILY"))
        val prefs = RestroomPreferences(setOf(RestroomAccess.PUBLIC), 4, PrivacyPreference.PRIVATE)
        assertTrue(replacementStops(rows, null, false, 15, mapOf(1L to 60.0, 2L to 60.0),
            mapOf(1L to 100.0, 2L to 90.0), "", preferences = prefs).isEmpty())
        val withMatch = rows + row(3, rating = 4.8, type = "FAMILY")
        assertEquals(listOf(3L), replacementStops(withMatch, null, false, 15,
            mapOf(1L to 60.0, 2L to 60.0, 3L to 60.0), mapOf(3L to 150.0), "", preferences = prefs)
            .map { it.restroom.id })
    }

    @Test fun blankDestinationShowsFartherBusinessesInDrivingOrderWhileKeepingOtherFilters() {
        val rows = listOf(
            row(1, rating = 4.8, type = "FAMILY").copy(businessId = 1),
            row(2, rating = 4.8).copy(businessId = 2),
            row(3, access = RestroomAccess.CUSTOMER_ONLY, rating = 4.8).copy(businessId = 3),
            row(4, rating = 3.5).copy(businessId = 4),
            row(5, rating = 4.8, type = "MULTI_STALL").copy(businessId = 5))
        val travel = mapOf(1L to 1620.0, 2L to 960.0, 3L to 1380.0, 4L to 1020.0, 5L to 1200.0)
        val prefs = RestroomPreferences(setOf(RestroomAccess.PUBLIC), 4, PrivacyPreference.PRIVATE)
        for (minutes in listOf(5, 10, 15)) {
            val result = replacementStops(rows, null, false, minutes, emptyMap(), travel, "",
                enforceTimeLimit = false, preferences = prefs, showDetour = false)
            assertEquals(listOf(2L, 1L), result.map { it.restroom.id })
            assertTrue(result[0].details.startsWith("16 min away"))
            assertTrue(result[1].details.startsWith("27 min away"))
            assertTrue(result.all { it.offRouteLabel == null })
        }
        // Once a destination exists, the detour limit still applies independently.
        assertEquals(listOf(2L), replacementStops(rows, null, false, 5,
            mapOf(1L to 360.0, 2L to 180.0), travel, "", preferences = prefs)
            .map { it.restroom.id })
    }
}
