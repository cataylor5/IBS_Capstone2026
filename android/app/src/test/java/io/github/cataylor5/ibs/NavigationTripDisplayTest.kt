package io.github.cataylor5.ibs

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NavigationTripDisplayTest {
    private val now = Instant.parse("2026-11-13T01:46:00Z")
    private val zone = ZoneId.of("America/New_York")
    private val origin = NavigationTarget("Origin", 36.0, -80.0)
    private val stop = NavigationTarget("Restroom", 36.1, -80.1)
    private val final = NavigationTarget("Final", 36.2, -80.2)

    @Test fun businessOnlyTripUsesRestroomHeaderWithoutChangingRouteEndpoint() {
        val plan = NavigationPlan(origin, stop)
        assertEquals(NavigationHeader(null, "Restroom"), navigationHeader(plan, true))
        assertEquals(listOf(stop), plan.remainingTargets(NavigationProgress()))
    }

    @Test fun absentHeaderSectionsStayAbsentForMapOnlyAndDirectTrips() {
        assertEquals(NavigationHeader(null, null), navigationHeader(NavigationPlan(origin, null), false))
        assertEquals(NavigationHeader("Final", null), navigationHeader(NavigationPlan(origin, final), false))
    }

    @Test fun midwayStopAndFinalDestinationKeepSeparateHeaders() {
        assertEquals(NavigationHeader("Final", "Restroom"),
            navigationHeader(NavigationPlan(origin, final, stop), false))
        val replacement = NavigationTarget("Replacement", 36.15, -80.15)
        assertEquals(NavigationHeader(null, "Replacement"),
            navigationHeader(NavigationPlan(origin, stop, replacement), true))
    }

    @Test fun nativeStyleEtaKeepsDurationAboveDistanceAndArrivalWithDeviceClock() {
        val result = tripEta(900, 16093, now, zone, Locale.US)!!
        assertEquals("15 min", result.duration)
        assertEquals("10 mi • 9:01 PM", result.distanceAndArrival)
        assertEquals("10 mi • 21:01", tripEta(900, 16093, now, zone, Locale.US, true)!!.distanceAndArrival)
    }
    @Test fun nextStopEstimatesAndFinalEstimatesAreNotSummed() {
        // The SDK currentTimeAndDistance switches after the explicit continuation.
        val toStop = tripEta(900, 16093, now, zone, Locale.US)!!
        val toFinal = tripEta(2100, 32187, now, zone, Locale.US)!!
        assertEquals("15 min", toStop.duration)
        assertEquals("35 min", toFinal.duration)
        assertEquals("20 mi • 9:21 PM", toFinal.distanceAndArrival)
    }
    @Test fun etaHandlesArrivalHoursMetricAndInvalidValues() {
        assertEquals("0 min", tripEta(0, 0, now, zone, Locale.US)!!.duration)
        assertEquals("1 hr 1 min", tripEta(3601, 1500, now, zone, Locale.US)!!.duration)
        assertTrue(tripEta(60, 1500, now, zone, Locale.FRANCE)!!.distanceAndArrival.startsWith("1,5 km"))
        assertNull(tripEta(-1, 100, now))
        assertNull(tripEta(60, -1, now))
    }
    @Test fun allRemainingLegsAndLongRoutesNeedExplicitBounds() {
        assertTrue(needsFullRouteBounds(2, 600))
        assertTrue(needsFullRouteBounds(1, 2701))
        assertFalse(needsFullRouteBounds(1, 2700))
        assertTrue(needsFullRouteBounds(1, null))
        val plan = NavigationPlan(origin, final, stop)
        assertEquals(listOf(stop, final), plan.remainingTargets(NavigationProgress()))
        assertEquals(listOf(final), plan.remainingTargets(NavigationProgress(stopVisited = true)))
    }
    @Test fun restroomPinsAreGeographicUniqueAndKeepBusinessDestinationsBranded() {
        val pins = navigationMapPins(NavigationPlan(origin, final, stop), false)
        assertEquals(listOf(stop, final), pins.map { it.target })
        assertTrue(pins.first().restroom)
        assertFalse(pins.last().restroom)
        assertTrue(navigationMapPins(NavigationPlan(origin, stop), true).single().restroom)
        val coincident = navigationMapPins(NavigationPlan(origin, stop, stop), false)
        assertEquals(1, coincident.size)
        assertTrue(coincident.single().restroom)
    }
}
