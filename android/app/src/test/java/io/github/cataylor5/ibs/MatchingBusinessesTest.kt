package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class MatchingBusinessesTest {
    private fun stop(id: Long, business: Long = id, fee: Boolean? = false,
        access: RestroomAccess = RestroomAccess.PUBLIC) =
        BackendRestroom(id, "Business $business", "Address", 36.0, -80.0, access,
            fee, false, false, true, false, null, 0, business)

    @Test fun sortsEligibleMatchingBusinessesAndDeduplicates() {
        val entries = listOf(stop(1, 1), stop(2, 1), stop(3), stop(4), stop(5, access = RestroomAccess.EMPLOYEE_ONLY))
        val result = matchingBusinesses(entries, mapOf(1L to 300.0, 2L to 100.0,
            3L to 200.0, 4L to 600.01, 5L to 1.0), 10, false)
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test fun freeExcludesUnknownFeesAndEmptyMatchesAreExplicit() {
        assertTrue(matchingBusinesses(listOf(stop(1, fee = null)), mapOf(1L to 10.0), 5, true).isEmpty())
        assertTrue(matchingBusinesses(listOf(stop(1)), mapOf(1L to 301.0), 5, false).isEmpty())
        assertTrue(matchingBusinesses(listOf(stop(1)), emptyMap(), 15, false).isEmpty())
    }
}
