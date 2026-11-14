package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class HomeAndChangeStopLimitsTest {
    private fun row(id: Long, business: Long = id) = BackendRestroom(
        id, "Business $id", null, 36.0, -80.0, RestroomAccess.PUBLIC,
        false, false, false, true, false, 4.8, 3, business,
        restroomType = "SINGLE_STALL"
    )
    private val prefs = RestroomPreferences(setOf(RestroomAccess.PUBLIC), 4, PrivacyPreference.PRIVATE)

    @Test fun blankDestinationHomeUsesDriveTimeAtAllSixUnroundedBoundaries() {
        val rows = (1L..4L).map { row(it) }
        for (limit in listOf(5, 10, 15, 20, 25, 30)) {
            val travel = mapOf(1L to limit * 60.0, 2L to limit * 60.0 + 0.01, 3L to Double.NaN)
            val matches = homeStops(rows, limit, rows.associate { it.id to 0.0 }, travel, false, prefs)
            assertEquals(listOf(1L), matches.map { it.restroom.id })
            assertNull(matches.single().offRouteLabel)
        }
    }

    @Test fun destinationHomeFiltersAddedTimeButRanksByTimeToReachBusiness() {
        val rows = (1L..3L).map { row(it) }
        val detours = mapOf(1L to 180.0, 2L to 301.0, 3L to 240.0)
        val travel = mapOf(1L to 1200.0, 2L to 60.0, 3L to 600.0)
        val matches = homeStops(rows, 5, detours, travel, true, prefs)
        assertEquals(listOf(3L, 1L), matches.map { it.restroom.id })
        assertEquals("4 minutes off route", matches.first().offRouteLabel)
        assertTrue(matches.first().details.startsWith("10 min away"))
    }

    @Test fun changingHomeLimitUpdatesMatchesAndStillAppliesOtherPreferences() {
        val good = row(1)
        val rows = listOf(good, row(2).copy(restroomType = "MULTI_STALL"),
            row(3).copy(access = RestroomAccess.CUSTOMER_ONLY),
            row(4).copy(averageRating = 3.0, exactAverageRating = java.math.BigDecimal("3.0")))
        val times = rows.associate { it.id to 21 * 60.0 }
        assertTrue(homeStops(rows, 20, times, times, false, prefs).isEmpty())
        assertEquals(listOf(1L), homeStops(rows, 25, times, times, false, prefs).map { it.restroom.id })
        assertEquals(listOf(1L), homeStops(rows, 30, times, times, true, prefs).map { it.restroom.id })
    }

    @Test fun changeStopHasNoTimeCapAndSearchRunsBeforeTenResultLimit() {
        val rows = (1L..17L).map { row(it, if (it == 2L) 1 else it) } +
            row(18).copy(restroomType = "MULTI_STALL") +
            row(19).copy(access = RestroomAccess.CUSTOMER_ONLY)
        val travel = rows.associate { it.id to it.id * 60.0 }
        val detours = rows.associate { it.id to 3600.0 }
        for (hasDestination in listOf(false, true)) {
            fun suggestions(query: String) = replacementStops(rows, 1, false, null, detours, travel, query,
                enforceTimeLimit = false, preferences = prefs, showDetour = hasDestination, resultLimit = 10)
            val initial = suggestions("")
            assertEquals((3L..12L).toList(), initial.map { it.restroom.id })
            assertTrue(initial.last().details.startsWith("12 min away"))
            assertEquals(listOf(17L), suggestions(" BUSINESS 17 ").map { it.restroom.id })
            assertFalse(initial.any { it.restroom.id in listOf(1L, 2L, 18L, 19L) })
        }
    }

    @Test fun tenResultLimitCountsUniqueBusinessesAndPutsUnavailableTimesLast() {
        val rows = (1L..24L).map { row(it, (it + 1) / 2) }
        val travel = rows.associate { it.id to it.id * 10.0 }
        val results = replacementStops(rows, null, false, null, emptyMap(), travel, "",
            enforceTimeLimit = false, resultLimit = 10)
        assertEquals((1L..10L).toList(), results.map { it.restroom.businessId })
        val unknown = replacementStops(listOf(row(1), row(2)), null, false, null, emptyMap(),
            mapOf(2L to 720.0), "", enforceTimeLimit = false, resultLimit = 10)
        assertEquals(listOf(2L, 1L), unknown.map { it.restroom.id })
        assertNull(unknown.last().travelSeconds)
    }
}
