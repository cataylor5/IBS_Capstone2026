package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class NavigationPlanTest {
    private val origin = NavigationTarget("Start", 36.07, -79.79)
    private val destination = NavigationTarget("Finish", 36.10, -80.24)
    private val restroom = NavigationTarget("Test restroom", 36.09, -80.10)
    private val plan = NavigationPlan(origin, destination, restroom)

    @Test fun restroomPrecedesDestination() {
        assertEquals(listOf(restroom, destination), plan.remainingTargets(NavigationProgress()))
    }
    @Test fun arrivalWaitsForExplicitContinuation() {
        val state = NavigationProgress().arrive(final = false)
        assertTrue(state.atRestroom)
        assertFalse(state.stopVisited)
        val continued = state.continueTrip()
        assertFalse(continued.atRestroom)
        assertTrue(continued.stopVisited)
        assertEquals(listOf(destination), plan.remainingTargets(continued))
    }
    @Test fun finalArrivalCannotContinue() {
        val arrived = NavigationProgress(stopVisited = true).arrive(final = true)
        assertTrue(arrived.arrived)
        assertEquals(emptyList<NavigationTarget>(), plan.remainingTargets(arrived))
        assertEquals(arrived, arrived.continueTrip())
    }
    @Test fun continuingBeforeArrivalDoesNotSkipRestroom() {
        val state = NavigationProgress()
        assertEquals(state, state.continueTrip())
    }
    @Test fun directNavigationHasOnlyDestination() {
        assertEquals(listOf(destination), plan.copy(restroom = null).remainingTargets(NavigationProgress()))
    }
    private val business = BackendRestroom(12, "Restroom business", "Address", 36.09, -80.10,
        RestroomAccess.PUBLIC, false, false, false, true, false, 4.5, 2)

    @Test fun blankLetsGoOpensMapWithoutGuidanceTargets() {
        val map = NavigationPlan.fromEndpoints(TripEndpoint("Current position", 36.07, -79.79), null)
        assertTrue(map.valid)
        assertNull(map.destination)
        assertTrue(map.remainingTargets(NavigationProgress()).isEmpty())
        assertFalse(map.copy(restroom = restroom).valid)
    }

    @Test fun destinationLetsGoHasOnlyTheFinalDestination() {
        val direct = NavigationPlan.fromEndpoints(TripEndpoint("Current position", 36.07, -79.79),
            TripEndpoint(destination.name, destination.latitude, destination.longitude))
        assertEquals(listOf(destination), direct.remainingTargets(NavigationProgress()))
    }

    @Test fun businessFromMapBecomesDestinationAtCurrentPosition() {
        val moving = origin.copy(latitude = 36.08)
        val selected = NavigationPlan(origin, null).withBusiness(moving, business)
        assertEquals(moving, selected.origin)
        assertEquals(business.name, selected.destination!!.name)
        assertNull(selected.restroom)
        assertEquals(1, selected.remainingTargets(NavigationProgress()).size)
    }

    @Test fun goHereAndReplacementPreserveFinalDestination() {
        val moving = origin.copy(latitude = 36.08)
        val selected = plan.withBusiness(moving, business)
        assertEquals(moving, selected.origin)
        assertEquals(destination, selected.destination)
        assertEquals(business.name, selected.restroom!!.name)
        assertEquals(listOf(selected.restroom, destination), selected.remainingTargets(NavigationProgress()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unresolvedOriginCannotStartMapOrGuidance() {
        NavigationPlan.fromEndpoints(TripEndpoint("Unresolved"), null)
    }

    @Test fun rejectsInvalidCoordinatesAndNames() {
        assertFalse(plan.copy(destination = destination.copy(latitude = Double.NaN)).valid)
        assertFalse(plan.copy(restroom = restroom.copy(longitude = 181.0)).valid)
        assertFalse(plan.copy(destination = destination.copy(name = "")).valid)
        assertFalse(plan.copy(origin = origin.copy(latitude = 91.0)).valid)
        assertTrue(plan.valid)
    }
}
