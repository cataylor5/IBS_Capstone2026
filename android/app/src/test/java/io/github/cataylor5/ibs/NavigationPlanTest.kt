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
    @Test fun rejectsInvalidCoordinatesAndNames() {
        assertFalse(plan.copy(destination = destination.copy(latitude = Double.NaN)).valid)
        assertFalse(plan.copy(restroom = restroom.copy(longitude = 181.0)).valid)
        assertFalse(plan.copy(destination = destination.copy(name = "")).valid)
        assertFalse(plan.copy(origin = origin.copy(latitude = 91.0)).valid)
        assertTrue(plan.valid)
    }
}
