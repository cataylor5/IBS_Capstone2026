package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class ReplacementStopTest {
    private val position = NavigationTarget("Current", 0.0, 0.0)
    private fun row(id: Long, lng: Double, business: Long = id, fee: Boolean? = false,
        access: RestroomAccess = RestroomAccess.PUBLIC) =
        BackendRestroom(id, if (id == 3L) "Sheetz" else "Business $id", null, 0.0, lng,
            access, fee, false, false, true, false, null, 0, business)
    private val rows = listOf(row(1, 0.0, 100), row(2, 0.001, 100), row(3, 0.02),
        row(4, 0.01), row(5, 0.001, fee = null), row(6, 0.001, access = RestroomAccess.EMPLOYEE_ONLY))
    private val detours = rows.associate { it.id to 60.0 }
    private val travel = mapOf(3L to 420.0, 4L to 120.0)

    @Test fun excludesCurrentBusinessAndSortsByArrivalTimeWithUnknownLast() {
        val result = replacementStops(rows, 1, false, 5, detours, travel, "")
        assertEquals(listOf(4L, 3L, 5L), result.map { it.restroom.id })
        assertTrue(result[0].details.startsWith("2 min away"))
        assertTrue(result[1].details.startsWith("7 min away"))
        assertTrue(result[2].details.startsWith("Travel time unavailable"))
        assertTrue(result[0].details.contains("No ratings yet"))
    }
    @Test fun searchReusesEstimatesAndDetourIsSeparateFromArrivalTime() {
        assertEquals(listOf(3L), replacementStops(rows, 1, true, 5, detours, travel, " SHEE ").map { it.restroom.id })
        assertEquals(420.0, replacementStops(rows, 1, true, 5, detours, travel, "SHEETZ")[0].travelSeconds!!, 0.0)
        assertTrue(replacementStops(rows, 1, false, 5, mapOf(3L to 300.01), travel, "Sheetz").isEmpty())
        assertEquals(1, replacementStops(rows, 1, false, 5, mapOf(3L to 300.0), travel, "Sheetz").size)
        assertTrue(replacementStops(rows, 1, true, 5, emptyMap(), travel, "").isEmpty())
    }
    @Test fun estimatesFromMovingOriginReorderResultsAndInvalidTimesAreUnavailable() {
        val result = replacementStops(rows, 1, true, 5, detours, mapOf(3L to 30.0, 4L to Double.NaN), "")
        assertEquals(listOf(3L, 4L), result.map { it.restroom.id })
        assertNull(result[1].travelSeconds)
        assertTrue(result[0].details.startsWith("1 min away"))
    }
    @Test fun blankDestinationShowsNearbyBusinessesBeyondTheDetourLimit() {
        val times = mapOf(3L to 1620.0, 4L to 960.0)
        val result = replacementStops(rows, 1, true, 5, times, times, "",
            enforceTimeLimit = false)
        assertEquals(listOf(4L, 3L), result.map { it.restroom.id })
        assertTrue(result[0].details.startsWith("16 min away"))
        assertTrue(result[1].details.startsWith("27 min away"))
        // A final destination restores the user's detour filter.
        assertTrue(replacementStops(rows, 1, true, 15, times, times, "",
            enforceTimeLimit = true).isEmpty())
    }

    @Test fun blankDestinationKeepsUnknownTravelTimesLastAndSearchUsesCachedTimes() {
        val result = replacementStops(rows, 1, false, 5, emptyMap(),
            mapOf(3L to 1620.0, 4L to 960.0), "", enforceTimeLimit = false)
        assertEquals(listOf(4L, 3L, 5L), result.map { it.restroom.id })
        assertNull(result.last().travelSeconds)
        assertEquals(listOf(3L), replacementStops(rows, 1, true, 5, emptyMap(),
            mapOf(3L to 1620.0), "sheetz", enforceTimeLimit = false).map { it.restroom.id })
    }

    @Test fun showsBothDrivingTimesAndSortsByArrivalInsteadOfDetour() {
        val result = replacementStops(rows, 1, true, 15,
            mapOf(3L to 60.0, 4L to 300.0), mapOf(3L to 1200.0, 4L to 960.0), "")
        assertEquals(listOf(4L, 3L), result.map { it.restroom.id })
        assertTrue(result[0].details.startsWith("16 min away"))
        assertEquals("5 minutes off route", result[0].offRouteLabel)
        assertEquals("1 minute off route", result[1].offRouteLabel)
        val nearby = replacementStops(rows, 1, true, 5, emptyMap(), mapOf(3L to 1200.0), "",
            enforceTimeLimit = false)
        assertTrue(nearby.all { it.offRouteLabel == null })
        assertNull(ReplacementStop(rows[0], 60.0, Double.NaN).offRouteLabel)
    }

    @Test fun movementThresholdDistanceStillHandlesDateline() {
        assertEquals(111194.9, straightLineMeters(position, NavigationTarget("East", 0.0, 1.0)), 1.0)
        assertEquals(22238.98, straightLineMeters(NavigationTarget("A", 0.0, 179.9),
            NavigationTarget("B", 0.0, -179.9)), 1.0)
    }
    @Test fun ratingsUseDatabaseAverageOnlyWhenThereAreRatings() {
        val rated = row(7, 0.1).copy(averageRating = 4.25, ratingCount = 4)
        assertTrue(ReplacementStop(rated, 420.0).details.contains("4.3/5"))
        assertTrue(ReplacementStop(rated.copy(ratingCount = 0), null).details.endsWith("No ratings yet"))
    }
}
