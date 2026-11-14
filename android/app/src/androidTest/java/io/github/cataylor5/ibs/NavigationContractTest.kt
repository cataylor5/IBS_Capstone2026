package io.github.cataylor5.ibs

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationContractTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun route(stop: Boolean = true) = DrivingRoute(
        TripEndpoint("Origin", 36.07, -79.79),
        TripEndpoint("Destination", 36.10, -80.24),
        1000, 100.0, "unused", emptyList(),
        if (stop) RouteStop(2, "Test restroom", null, 36.09, -80.10) else null
    )

    @Test fun intentPreservesOrderedEndpointsAndRestroom() {
        val intent = LiveNavigationActivity.intent(context, route(), false)
        val plan = LiveNavigationActivity.decodePlan(intent)!!
        assertEquals("Test restroom", plan.remainingTargets(NavigationProgress()).first().name)
        assertEquals("Destination", plan.destination!!.name)
        assertEquals(36.07, plan.origin.latitude, 0.0)
        assertFalse(intent.getBooleanExtra("demo", true))
    }
    @Test fun directTripDoesNotInventRestroom() {
        val plan = LiveNavigationActivity.decodePlan(LiveNavigationActivity.intent(context, route(false), false))!!
        assertNull(plan.restroom)
        assertEquals(1, plan.remainingTargets(NavigationProgress()).size)
    }
    @Test fun malformedInternalRequestsAreRejected() {
        assertNull(LiveNavigationActivity.decodePlan(Intent()))
        val intent = LiveNavigationActivity.intent(context, route(), false)
        intent.putExtra("restroom", Bundle().apply {
            putString("name", "Invalid")
            putDouble("latitude", Double.NaN)
            putDouble("longitude", 0.0)
        })
        assertNull(LiveNavigationActivity.decodePlan(intent))
    }
    @Test fun mapOnlyIntentExplicitlyAllowsMissingDestination() {
        val map = NavigationPlan(NavigationTarget("User", 36.07, -79.79), null)
        val request = LiveNavigationActivity.intent(context, map, true, false, 10)
        val decoded = LiveNavigationActivity.decodePlan(request)!!
        assertNull(decoded.destination)
        assertTrue(decoded.remainingTargets(NavigationProgress()).isEmpty())
        request.removeExtra("map_only")
        assertNull(LiveNavigationActivity.decodePlan(request))
    }

    @Test fun selectedPreferencesCarryIntoChangeStopForDirectAndMapOnlyTrips() {
        val filters = RestroomPreferences(setOf(RestroomAccess.MEMBERSHIP_REQUIRED), 5, PrivacyPreference.PRIVATE)
        val request = LiveNavigationActivity.intent(context,
            NavigationPlan(NavigationTarget("User", 36.07, -79.79), null), true, false, 30,
            preferences = filters)
        assertEquals(filters, LiveNavigationActivity.decodePreferences(request))
        assertEquals(30, request.getIntExtra("detour_minutes", 0))
    }

    @Test fun businessChosenAsDestinationKeepsRestroomMarkerIdentity() {
        val target = NavigationTarget("Restroom business", 36.09, -80.1)
        val request = LiveNavigationActivity.intent(context,
            NavigationPlan(NavigationTarget("User", 36.07, -79.79), target),
            true, false, 15, selectedId = 5)
        assertTrue(request.getBooleanExtra("final_is_restroom", false))
        val via = LiveNavigationActivity.intent(context,
            NavigationPlan(NavigationTarget("User", 36.07, -79.79), target, target),
            true, false, 15, selectedId = 5)
        assertFalse(via.getBooleanExtra("final_is_restroom", true))
    }

    @Test fun navigationActivityIsPrivate() {
        val info = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, LiveNavigationActivity::class.java), 0)
        assertFalse(info.exported)
    }
}
