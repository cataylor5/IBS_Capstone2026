package io.github.cataylor5.ibs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripPlanningViewModelTest {
    private fun onMain(test: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(test)

    private fun create(saved: SavedStateHandle) = TripPlanningViewModel(
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
        saved
    )

    private fun selectedEndpoints() = SavedStateHandle(mapOf(
        "origin_text" to "Using current location",
        "origin_lat" to 36.0,
        "origin_lng" to -79.0,
        "origin_source" to "GPS",
        "destination_text" to "Test destination",
        "destination_lat" to 37.0,
        "destination_lng" to -80.0,
        "destination_place" to "test-id",
        "detour" to 15,
        "free" to true,
        "stop" to "demo-library"
    ))

    @Test fun letsGoRequiresDestinationAndDenialNeverAdvances() = onMain {
        val missing = create(SavedStateHandle())
        assertFalse(missing.prepareGo())
        val vm = create(selectedEndpoints())
        assertTrue(vm.prepareGo())
        vm.beginGpsPermission()
        vm.gpsPermissionResult(false)
        assertFalse(vm.businessesReady)
        assertFalse(vm.origin.isBusy)
        assertTrue(vm.origin.isError)
        assertTrue(vm.origin.message!!.contains("denied"))
    }

    @Test fun destinationEditsCancelPendingLetsGo() = onMain {
        val vm = create(selectedEndpoints())
        vm.prepareGo()
        vm.beginGpsPermission()
        vm.edit(EndpointTarget.DESTINATION, "Changed")
        vm.gpsPermissionResult(true)
        assertFalse(vm.businessesReady)
        assertFalse(vm.origin.isBusy)
        assertFalse(vm.origin.endpoint.isResolved)
    }

    @Test fun restoresCoordinatesPreferencesAndStop() = onMain {
        val vm = create(selectedEndpoints())
        assertTrue(vm.canPreview)
        assertNull(vm.origin.endpoint.placeId)
        assertEquals(EndpointSource.GPS, vm.origin.endpoint.source)
        assertEquals("test-id", vm.destination.endpoint.placeId)
        assertEquals(15, vm.maxDetourMinutes)
        assertTrue(vm.freePublicOnly)
        assertEquals("demo-library", vm.selectedStopId)
    }

    @Test fun processRestorationNeverRestoresAnOrphanedSpinner() = onMain {
        val saved = selectedEndpoints()
        saved["origin_operation"] = EndpointOperation.GPS.name
        saved["destination_operation"] = EndpointOperation.LOOKUP.name
        val vm = create(saved)
        assertFalse(vm.origin.isBusy)
        assertFalse(vm.destination.isBusy)
        assertTrue(vm.origin.message!!.contains("interrupted"))
        assertTrue(vm.destination.message!!.contains("interrupted"))
    }

    @Test fun cancelingPickerPreservesSelectionAndStop() = onMain {
        val vm = create(selectedEndpoints())
        val previous = vm.destination.endpoint
        vm.beginSearch(EndpointTarget.DESTINATION)
        assertFalse(vm.canPreview)
        vm.cancelSearch(EndpointTarget.DESTINATION)
        assertEquals(previous, vm.destination.endpoint)
        assertEquals("demo-library", vm.selectedStopId)
        assertTrue(vm.canPreview)
    }

    @Test fun editsInvalidatePersistedCoordinatesAndRejectLatePickerResult() = onMain {
        val saved = selectedEndpoints()
        val vm = create(saved)
        vm.beginSearch(EndpointTarget.DESTINATION)
        vm.edit(EndpointTarget.DESTINATION, "Changed address")
        // There is no active picker ticket, so this must not start an API call.
        vm.placeSelected(EndpointTarget.DESTINATION, "old-id", "Old selection", null)
        assertEquals("Changed address", vm.destination.endpoint.displayText)
        assertFalse(vm.canPreview)
        assertNull(vm.destination.endpoint.latitude)
        assertNull(vm.selectedStopId)
        assertFalse(create(saved).destination.endpoint.isResolved)
    }

    @Test fun manualSwitchRejectsLatePermissionCallback() = onMain {
        val vm = create(selectedEndpoints())
        vm.beginGpsPermission()
        vm.useManualOrigin()
        vm.gpsPermissionResult(true)
        assertEquals(EndpointSource.MANUAL, vm.origin.endpoint.source)
        assertFalse(vm.origin.isBusy)
        assertFalse(vm.origin.endpoint.isResolved)
    }

    @Test fun allSixTimeOptionsPersistAndInvalidSelectionsAreIgnored() = onMain {
        val saved = selectedEndpoints()
        val vm = create(saved)
        for (minutes in listOf(5, 10, 15, 20, 25, 30)) {
            vm.setDetour(minutes)
            assertEquals(minutes, vm.maxDetourMinutes)
            assertEquals(minutes, create(saved).maxDetourMinutes)
        }
        vm.setDetour(7)
        assertEquals(30, vm.maxDetourMinutes)
    }

    @Test fun changedPreferencesPersistAndResetStop() = onMain {
        val saved = selectedEndpoints()
        val vm = create(saved)
        vm.setDetour(5)
        vm.changeFreePublicOnly(false)
        val restored = create(saved)
        assertEquals(5, restored.maxDetourMinutes)
        assertFalse(restored.freePublicOnly)
        assertNull(restored.selectedStopId)
    }

    @Test fun restoresStopIdentityWithoutGeometryAndCanRemoveIt() = onMain {
        val saved = selectedEndpoints()
        saved["route_requested"] = true
        saved["route_stop_id"] = 12L
        saved["route_stop_name"] = "Test restroom"
        val vm = create(saved)
        assertEquals("Test restroom", vm.routeStopName)
        assertNull(vm.routeState.route)
        assertFalse(vm.routeState.loading)
        vm.removeRestroomStop()
        assertNull(vm.routeStopName)
        assertNull(saved.get<Long>("route_stop_id"))
        assertNull(create(saved).routeStopName)
    }

    @Test fun editingEndpointDiscardsRestoredRestroomSelection() = onMain {
        val saved = selectedEndpoints()
        saved["route_stop_id"] = 12L
        saved["route_stop_name"] = "Test restroom"
        val vm = create(saved)
        vm.edit(EndpointTarget.DESTINATION, "New destination")
        assertNull(vm.routeStopName)
        assertNull(saved.get<Long>("route_stop_id"))
        assertNull(vm.routeState.route)
    }
}
