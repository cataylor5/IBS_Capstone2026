package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class DestinationPredictionsTest {
    private val row = DestinationPrediction("test-place", "Winston-Salem, NC")

    @Test fun newerQueryRejectsOlderPredictionsAndErrors() {
        val first = DestinationPredictions().begin("Win")
        val second = first.begin("Greensboro")
        assertEquals(second, second.complete(first.revision, listOf(row)))
        assertEquals(second, second.fail(first.revision, "Old network error"))
        assertTrue(second.loading)
        assertTrue(second.rows.isEmpty())
    }

    @Test fun clearingOrSelectingCancelsPendingResults() {
        val pending = DestinationPredictions().begin("Winston")
        val cleared = pending.begin("")
        assertFalse(cleared.loading)
        assertTrue(cleared.rows.isEmpty())
        assertEquals(cleared, cleared.complete(pending.revision, listOf(row)))
    }

    @Test fun latestResponseStopsLoadingAndCarriesThePredictionId() {
        val pending = DestinationPredictions().begin("Winston")
        val complete = pending.complete(pending.revision, listOf(row))
        assertFalse(complete.loading)
        assertEquals("test-place", complete.rows.single().id)
        assertNull(complete.error)
    }

    @Test fun retryClearsErrorAndInvalidatesPreviousResponse() {
        val first = DestinationPredictions().begin("Winston")
        val failure = first.fail(first.revision, "Network unavailable")
        assertFalse(failure.loading)
        val retry = failure.begin("Winston")
        assertTrue(retry.loading)
        assertNull(retry.error)
        assertEquals(retry, retry.complete(first.revision, listOf(row)))
    }

    @Test fun emptyOrSingleCharacterQueryDoesNotStartNetworkLoading() {
        for (query in listOf("", " ", "W", " W ")) {
            assertFalse(DestinationPredictions().begin(query).loading)
        }
    }
}
