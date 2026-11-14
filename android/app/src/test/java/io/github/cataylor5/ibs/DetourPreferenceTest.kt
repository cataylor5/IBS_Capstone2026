package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class DetourPreferenceTest {
    @Test fun filtersAtExactUnroundedBoundary() {
        for (minutes in listOf(5, 10, 15, 20, 25, 30)) {
            assertTrue(detourMatches(minutes * 60.0, minutes))
            assertFalse(detourMatches(minutes * 60.0 + 0.01, minutes))
            assertTrue(detourMatches(0.0, minutes))
        }
    }
    @Test fun unknownAndInvalidEstimatesNeverCountAsMatches() {
        for (seconds in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0))
            assertFalse(detourMatches(seconds, 10))
    }
}
