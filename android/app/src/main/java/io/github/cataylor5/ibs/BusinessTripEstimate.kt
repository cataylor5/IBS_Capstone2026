package io.github.cataylor5.ibs

internal data class BusinessTripEstimate(val travelSeconds: Double?, val preferenceSeconds: Double?)

internal fun businessEndpoint(row: BackendRestroom): TripEndpoint {
    require(row.hasCoordinates)
    return TripEndpoint(row.name, row.latitude, row.longitude)
}

/** Shared home/dropdown calculation: arrival time and the preference metric stay distinct. */
internal suspend fun estimateBusinessTrip(
    origin: TripEndpoint, destination: TripEndpoint?, row: BackendRestroom, freeOnly: Boolean,
    repository: RouteRepository = RouteRepository()
): BusinessTripEstimate {
    require(origin.isResolved && row.canUseForRoute(freeOnly))
    return if (destination == null) {
        val route = repository.compute(origin, businessEndpoint(row))
        BusinessTripEstimate(route.durationSeconds, route.durationSeconds)
    } else {
        require(destination.isResolved)
        val route = repository.compute(origin, destination, row.id, freePublicOnly = freeOnly,
            includeStopTravelTime = true)
        // Older running backends (or incomplete leg responses) may omit first-leg time.
        // Ask the same routing provider for arrival time; never substitute the detour.
        val arrival = route.stopTravelSeconds ?: try {
            repository.compute(origin, businessEndpoint(row)).durationSeconds
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        BusinessTripEstimate(arrival, route.extraDrivingSeconds)
    }
}
