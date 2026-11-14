package io.github.cataylor5.ibs

/** Only validated endpoints enter navigation. The preview origin is used by demo mode only. */
data class NavigationTarget(val name: String, val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = name.isNotBlank() && name.length <= 1000 &&
        latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0
}

data class NavigationPlan(
    val origin: NavigationTarget,
    val destination: NavigationTarget?,
    val restroom: NavigationTarget? = null
) {
    val valid: Boolean get() = origin.valid && destination?.valid != false && (restroom?.valid != false) && (destination != null || restroom == null)
    fun remainingTargets(progress: NavigationProgress): List<NavigationTarget> =
        if (progress.arrived) emptyList() else
            listOfNotNull(restroom.takeUnless { progress.stopVisited }, destination)

    fun withBusiness(position: NavigationTarget, business: BackendRestroom): NavigationPlan {
        require(position.valid && business.hasCoordinates)
        val target = NavigationTarget(business.name, business.latitude!!, business.longitude!!)
        return if (destination == null) NavigationPlan(position, target)
            else copy(origin = position, restroom = target)
    }

    companion object {
        fun fromEndpoints(origin: TripEndpoint, destination: TripEndpoint?): NavigationPlan {
            require(origin.isResolved && (destination == null || destination.isResolved))
            return NavigationPlan(NavigationTarget(origin.displayText, origin.latitude!!, origin.longitude!!),
                destination?.let { NavigationTarget(it.displayText, it.latitude!!, it.longitude!!) })
        }

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
