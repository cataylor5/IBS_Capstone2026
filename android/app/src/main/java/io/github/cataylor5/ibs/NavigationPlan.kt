package io.github.cataylor5.ibs

/** Only validated endpoints enter navigation. The preview origin is used by demo mode only. */
data class NavigationTarget(val name: String, val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = name.isNotBlank() && name.length <= 1000 &&
        latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0
}

data class NavigationPlan(
    val origin: NavigationTarget,
    val destination: NavigationTarget,
    val restroom: NavigationTarget? = null
) {
    val valid: Boolean get() = origin.valid && destination.valid && (restroom?.valid != false)
    fun remainingTargets(progress: NavigationProgress): List<NavigationTarget> =
        if (progress.arrived) emptyList() else
            listOfNotNull(restroom.takeUnless { progress.stopVisited }, destination)

    companion object {
        fun from(route: DrivingRoute): NavigationPlan {
            require(route.origin.isResolved && route.destination.isResolved)
            return NavigationPlan(
                NavigationTarget(route.origin.displayText, route.origin.latitude!!, route.origin.longitude!!),
                NavigationTarget(route.destination.displayText, route.destination.latitude!!, route.destination.longitude!!),
                route.stop?.let { NavigationTarget(it.name, it.latitude, it.longitude) }
            ).also { require(it.valid) }
        }
    }
}

/** Saved across recreation; a restroom visit requires an explicit continuation. */
data class NavigationProgress(
    val stopVisited: Boolean = false,
    val atRestroom: Boolean = false,
    val arrived: Boolean = false
) {
    fun arrive(final: Boolean) = if (final) copy(arrived = true, atRestroom = false)
        else copy(atRestroom = true)
    fun continueTrip() = if (atRestroom && !arrived)
        copy(stopVisited = true, atRestroom = false) else this
}
