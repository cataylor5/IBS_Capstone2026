package io.github.cataylor5.ibs

internal enum class ReplacementOutcome { ACCEPTED, RESTORED, RESTORE_FAILED }

/** Commit only a successful replacement; explicitly restore the prior waypoints on failure. */
internal class RouteReplacementTransaction<T>(
    private val install: (T, (Boolean) -> Unit) -> Unit
) {
    private var revision = 0L
    fun cancel() { revision++ }
    fun replace(previous: T, proposed: T, completed: (ReplacementOutcome) -> Unit) {
        val ticket = ++revision
        var restoring = false
        install(proposed) { success ->
            if (ticket == revision && !restoring) {
                if (success) { revision++; completed(ReplacementOutcome.ACCEPTED) }
                else {
                    restoring = true
                    install(previous) { restored ->
                        if (ticket == revision) {
                            revision++
                            completed(if (restored) ReplacementOutcome.RESTORED else ReplacementOutcome.RESTORE_FAILED)
                        }
                    }
                }
            }
        }
    }
}
