package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class TripEndpointTest {
    private val place = TripEndpoint("Selected test place", 36.0, -79.0, "test-place")
    private val resolved = EndpointState(endpoint = place)

    @Test fun editingResolvedFieldInvalidatesCoordinatesAndPlaceId() {
        val edited = resolved.edit("Different address")
        assertEquals("Different address", edited.endpoint.displayText)
        assertNull(edited.endpoint.latitude)
        assertNull(edited.endpoint.longitude)
        assertNull(edited.endpoint.placeId)
        assertFalse(edited.endpoint.isResolved)
        assertTrue(edited.revision > resolved.revision)
    }

    @Test fun resultFromBeforeAnEditCannotOverwriteTheEdit() {
        val pending = resolved.begin(EndpointOperation.LOOKUP)
        val edited = pending.edit("New address")
        assertEquals(edited, edited.complete(pending.revision, place))
        assertEquals(edited, edited.fail(pending.revision, "Old network failure"))
    }

    @Test fun olderSelectionCannotReplaceNewerSelection() {
        val first = resolved.begin(EndpointOperation.LOOKUP)
        val second = first.begin(EndpointOperation.LOOKUP)
        val newerPlace = TripEndpoint("New selection", 37.0, -80.0, "new-id")
        val completed = second.complete(second.revision, newerPlace)
        assertEquals(newerPlace, completed.endpoint)
        assertEquals(completed, completed.complete(first.revision, place))
        assertEquals(completed, completed.fail(first.revision, "Old failure"))
    }

    @Test fun switchingToManualRejectsLateGpsFix() {
        val locating = resolved.begin(EndpointOperation.GPS)
        val manual = locating.edit("")
        val gps = TripEndpoint("Using current location", 35.0, -78.0, source = EndpointSource.GPS)
        assertEquals(manual, manual.complete(locating.revision, gps))
        assertEquals(EndpointSource.MANUAL, manual.endpoint.source)
    }

    @Test fun switchingToGpsRejectsLatePlaceLookup() {
        val lookup = resolved.begin(EndpointOperation.LOOKUP)
        val gpsPending = lookup.begin(EndpointOperation.GPS)
        assertEquals(gpsPending, gpsPending.complete(lookup.revision, place))
    }

    @Test fun cancellingPickerPreservesPreviousValidSelection() {
        val searching = resolved.begin(EndpointOperation.AUTOCOMPLETE)
        val canceled = searching.cancel(searching.revision)
        assertEquals(place, canceled.endpoint)
        assertTrue(canceled.endpoint.isResolved)
        assertFalse(canceled.isBusy)
        assertFalse(canceled.isError)
    }

    @Test fun lateCancellationCannotStopNewerRequest() {
        val old = resolved.begin(EndpointOperation.AUTOCOMPLETE)
        val current = old.begin(EndpointOperation.LOOKUP)
        assertEquals(current, current.cancel(old.revision))
    }

    @Test fun gpsEndpointDoesNotRequirePlaceId() {
        val gps = TripEndpoint("Using current location", 0.0, 0.0, source = EndpointSource.GPS)
        assertTrue(gps.isResolved)
        assertNull(gps.placeId)
    }

    @Test fun previewRequiresTwoResolvedIdleEndpoints() {
        assertTrue(canPreviewTrip(resolved, resolved))
        assertFalse(canPreviewTrip(resolved, EndpointState(TripEndpoint("Typed only"))))
        assertFalse(canPreviewTrip(EndpointState(), resolved))
        assertFalse(canPreviewTrip(resolved.begin(EndpointOperation.GPS), resolved))
        assertFalse(canPreviewTrip(resolved, resolved.begin(EndpointOperation.LOOKUP)))
    }

    @Test fun invalidCoordinatesAreNotAccepted() {
        assertFalse(place.copy(latitude = Double.NaN).isResolved)
        assertFalse(place.copy(longitude = Double.POSITIVE_INFINITY).isResolved)
        assertFalse(place.copy(latitude = 91.0).isResolved)
        assertFalse(place.copy(longitude = -181.0).isResolved)
        assertFalse(place.copy(displayText = " ").isResolved)
    }

    @Test fun failedLookupStopsLoadingAndKeepsPreviousEndpoint() {
        val request = resolved.begin(EndpointOperation.LOOKUP)
        val failed = request.fail(request.revision, "Please retry.")
        assertFalse(failed.isBusy)
        assertTrue(failed.isError)
        assertEquals(place, failed.endpoint)
        assertEquals("Please retry.", failed.message)
    }
}
