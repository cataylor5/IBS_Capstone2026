package io.github.cataylor5.ibs

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class BusinessEstimateObservationTest {
    private fun model() = RestroomsViewModel(SavedStateHandle(), object : RestroomRepository {
        override suspend fun list() = emptyList<BackendRestroom>()
        override suspend fun details(id: Long): BackendRestroom = error("Unused")
    })

    @Test fun selectingDestinationNotifiesTheHomeEvenBeforeAnyEstimatesArrive() {
        val vm = model()
        val origin = TripEndpoint("Current location", 36.07, -79.79)
        val destination = TripEndpoint("Winston-Salem", 36.10, -80.24)
        vm.checkDetours(origin, TripEndpoint(""), false)
        Snapshot.sendApplyNotifications()
        var invalidated = false
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { invalidated = true }) {
                assertFalse(vm.estimatesMatch(origin, destination, false))
            }
            vm.checkDetours(origin, destination, false)
            Snapshot.sendApplyNotifications()
            assertTrue("Destination selection must invalidate Compose's hidden-results decision", invalidated)
            assertTrue(vm.estimatesMatch(origin, destination, false))
            assertFalse(vm.estimatesMatch(origin, TripEndpoint(""), false))
        } finally { observer.stop() }
    }

    @Test fun clearingDestinationAndChangingFreePreferenceInvalidateOldResults() {
        val vm = model()
        val origin = TripEndpoint("Current location", 36.07, -79.79)
        val destination = TripEndpoint("Winston-Salem", 36.10, -80.24)
        vm.checkDetours(origin, destination, false)
        vm.checkDetours(origin, TripEndpoint(""), true)
        assertFalse(vm.estimatesMatch(origin, destination, false))
        assertTrue(vm.estimatesMatch(origin, TripEndpoint(""), true))
    }
}
