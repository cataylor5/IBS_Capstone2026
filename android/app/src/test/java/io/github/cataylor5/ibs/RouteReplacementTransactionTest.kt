package io.github.cataylor5.ibs

import org.junit.Assert.*
import org.junit.Test

class RouteReplacementTransactionTest {
    @Test fun doesNotCommitUntilSdkAccepts() {
        var callback: ((Boolean) -> Unit)? = null
        var outcome: ReplacementOutcome? = null
        val transaction = RouteReplacementTransaction<String> { _, done -> callback = done }
        transaction.replace("old", "new") { outcome = it }
        assertNull(outcome)
        callback!!(true)
        assertEquals(ReplacementOutcome.ACCEPTED, outcome)
    }
    @Test fun failedReplacementInstallsPreviousRouteBeforeReportingRestored() {
        val installed = mutableListOf<String>()
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var outcome: ReplacementOutcome? = null
        val transaction = RouteReplacementTransaction<String> { route, done -> installed += route; callbacks += done }
        transaction.replace("old", "new") { outcome = it }
        callbacks[0](false)
        assertEquals(listOf("new", "old"), installed)
        assertNull(outcome)
        callbacks[0](true) // A late/duplicate proposal result cannot commit during restoration.
        assertNull(outcome)
        callbacks[1](true)
        assertEquals(ReplacementOutcome.RESTORED, outcome)
    }
    @Test fun restorationFailureIsExplicit() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var outcome: ReplacementOutcome? = null
        val transaction = RouteReplacementTransaction<String> { _, done -> callbacks += done }
        transaction.replace("old", "new") { outcome = it }
        callbacks[0](false); callbacks[1](false)
        assertEquals(ReplacementOutcome.RESTORE_FAILED, outcome)
    }
    @Test fun closingOrNewerSelectionRejectsStaleCompletion() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val outcomes = mutableListOf<ReplacementOutcome>()
        val transaction = RouteReplacementTransaction<String> { _, done -> callbacks += done }
        transaction.replace("old", "first") { outcomes += it }
        transaction.cancel()
        callbacks[0](true)
        assertTrue(outcomes.isEmpty())
        transaction.replace("old", "second") { outcomes += it }
        callbacks[0](false)
        assertEquals(2, callbacks.size)
        callbacks[1](true)
        assertEquals(listOf(ReplacementOutcome.ACCEPTED), outcomes)
    }
}
