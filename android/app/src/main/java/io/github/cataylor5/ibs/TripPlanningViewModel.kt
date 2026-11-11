package io.github.cataylor5.ibs

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class TripPlanningViewModel(
    application: Application,
    private val saved: SavedStateHandle
) : AndroidViewModel(application) {
    private val service = EndpointLocationService(application)
    private val jobs = mutableMapOf<EndpointTarget, Job>()
    private val routeRepository = RouteRepository()
    private var routeJob: Job? = null
    var routeState by mutableStateOf(RouteState(error =
        if (saved.get<Boolean>("route_requested") == true)
            "Tap Start now to recalculate your route." else null))
        private set

    fun clearRoute() {
        // Invalidate first, so cancellation or a late response cannot restore the old route.
        routeState = routeState.clear()
        routeJob?.cancel()
        routeJob = null
        saved["route_requested"] = false
        saved.remove<Long>("route_stop_id")
        saved.remove<String>("route_stop_name")
    }

    private fun interruptRouteRequest() {
        if (routeState.loading) clearRoute()
    }

    val routeStopName: String? get() = routeState.route?.stop?.name ?: saved.get<String>("route_stop_name")

    fun startRoute() = calculateRoute(routeState.route?.stop?.id ?: saved.get<Long>("route_stop_id"))

    fun routeViaRestroom(id: Long) {
        if (id <= 0 || routeState.route?.stop?.id == id) return
        calculateRoute(id)
    }

    fun removeRestroomStop() {
        val restored = routeState.withoutStop()
        if (restored == routeState && saved.get<Long>("route_stop_id") == null) return
        routeState = if (restored == routeState) routeState.clear() else restored
        saved.remove<Long>("route_stop_id")
        saved.remove<String>("route_stop_name")
        routeJob?.cancel()
        routeJob = null
        saved["route_requested"] = true
    }

    private fun calculateRoute(restroomId: Long?) {
        if (!canPreview || routeState.loading) {
            if (!canPreview) preview()
            return
        }
        val from = origin.endpoint
        val to = destination.endpoint
        val allowance = maxDetourMinutes
        val freeOnly = freePublicOnly
        routeState = routeState.begin(stopId = restroomId, keepRoute = restroomId != null)
        val ticket = routeState.revision
        saved["route_requested"] = true
        routeJob = viewModelScope.launch {
            try {
                val result = withTimeout(35_000) { routeRepository.compute(from, to, restroomId, allowance, freeOnly) }
                if (routeState.revision == ticket) {
                    routeState = routeState.complete(ticket, result)
                    if (result.stop != null) {
                        saved["route_stop_id"] = result.stop.id
                        saved["route_stop_name"] = result.stop.name
                    } else {
                        saved.remove<Long>("route_stop_id")
                        saved.remove<String>("route_stop_name")
                    }
                    closePreview()
                }
            } catch (_: TimeoutCancellationException) {
                routeState = routeState.fail(ticket, "Route calculation timed out. Please try again.")
            } catch (cancelled: CancellationException) {
                routeState = routeState.fail(ticket, "Route calculation was interrupted. Tap Start now to retry.")
                throw cancelled
            } catch (error: Exception) {
                routeState = routeState.fail(ticket, routeErrorMessage(error))
            }
        }
    }

    var origin by mutableStateOf(restore(EndpointTarget.ORIGIN))
        private set
    var destination by mutableStateOf(restore(EndpointTarget.DESTINATION))
        private set
    var maxDetourMinutes by mutableStateOf(saved.get<Int>("detour") ?: 10)
        private set
    var freePublicOnly by mutableStateOf(saved.get<Boolean>("free") ?: false)
        private set
    var attemptedPreview by mutableStateOf(saved.get<Boolean>("attempted") ?: false)
        private set
    var showPreview by mutableStateOf(saved.get<Boolean>("preview") ?: false)
        private set
    var selectedStopId by mutableStateOf(saved.get<String>("stop"))
        private set

    val canPreview: Boolean get() = canPreviewTrip(origin, destination)

    private fun prefix(target: EndpointTarget) = target.name.lowercase()

    private fun restore(target: EndpointTarget): EndpointState {
        val key = prefix(target)
        val wasBusy = saved.get<String>(key + "_operation")?.let { it != EndpointOperation.NONE.name } ?: false
        return EndpointState(
            endpoint = TripEndpoint(
                saved.get<String>(key + "_text") ?: "",
                saved.get<Double>(key + "_lat"),
                saved.get<Double>(key + "_lng"),
                saved.get<String>(key + "_place"),
                if (saved.get<String>(key + "_source") == EndpointSource.GPS.name) EndpointSource.GPS else EndpointSource.MANUAL
            ),
            revision = saved.get<Long>(key + "_revision") ?: 0,
            message = if (wasBusy) "Selection was interrupted. Search again or tap GPS." else null
        )
        // After process death, never restore a spinner without its request.
        // Pending Activity results still carry the saved revision below.
    }

    fun state(target: EndpointTarget) = if (target == EndpointTarget.ORIGIN) origin else destination

    private fun update(target: EndpointTarget, next: EndpointState) {
        if (target == EndpointTarget.ORIGIN) origin = next else destination = next
        val key = prefix(target)
        saved[key + "_text"] = next.endpoint.displayText
        saved[key + "_lat"] = next.endpoint.latitude
        saved[key + "_lng"] = next.endpoint.longitude
        saved[key + "_place"] = next.endpoint.placeId
        saved[key + "_source"] = next.endpoint.source.name
        saved[key + "_revision"] = next.revision
        saved[key + "_operation"] = next.operation.name
    }

    private fun cancelWork(target: EndpointTarget) {
        jobs.remove(target)?.cancel()
        saved.remove<Long>(prefix(target) + "_search")
        if (target == EndpointTarget.ORIGIN) saved.remove<Long>("gps_permission")
    }

    private fun tripChanged() {
        clearRoute()
        closePreview()
        selectStop(null)
        attemptedPreview = false
        saved["attempted"] = false
    }

    fun edit(target: EndpointTarget, text: String) {
        cancelWork(target)
        update(target, state(target).edit(text))
        tripChanged()
    }

    fun useManualOrigin() = edit(EndpointTarget.ORIGIN, "")

    fun setDetour(minutes: Int) {
        if (minutes !in listOf(5, 10, 15) || minutes == maxDetourMinutes) return
        maxDetourMinutes = minutes
        saved["detour"] = minutes
        tripChanged()
    }

    fun changeFreePublicOnly(value: Boolean) {
        if (freePublicOnly == value) return
        freePublicOnly = value
        saved["free"] = value
        tripChanged()
    }

    fun selectStop(id: String?) {
        selectedStopId = id
        saved["stop"] = id
    }

    fun preview() {
        attemptedPreview = true
        saved["attempted"] = true
        showPreview = canPreview
        saved["preview"] = showPreview
    }

    fun closePreview() {
        showPreview = false
        saved["preview"] = false
    }

    fun beginSearch(target: EndpointTarget) {
        interruptRouteRequest()
        cancelWork(target)
        closePreview()
        val next = state(target).begin(EndpointOperation.AUTOCOMPLETE)
        update(target, next)
        saved[prefix(target) + "_search"] = next.revision
    }

    fun cancelSearch(target: EndpointTarget) {
        val ticket = saved.remove<Long>(prefix(target) + "_search") ?: return
        update(target, state(target).cancel(ticket))
        // Keep the previous endpoint and stop when the picker is canceled.
    }

    fun searchFailed(target: EndpointTarget, statusCode: Int? = null) {
        val ticket = saved.remove<Long>(prefix(target) + "_search") ?: return
        update(target, state(target).fail(ticket, placesFailureMessage(statusCode)))
    }

    fun placeSelected(target: EndpointTarget, id: String, text: String, session: AutocompleteSessionToken?) {
        val ticket = saved.remove<Long>(prefix(target) + "_search") ?: return
        if (state(target).revision != ticket) return
        update(target, state(target).copy(
            endpoint = TripEndpoint(displayText = text),
            operation = EndpointOperation.LOOKUP,
            message = "Resolving location…",
            isError = false
        ))
        tripChanged()
        jobs[target] = viewModelScope.launch {
            try {
                val endpoint = withTimeout(20_000) { service.resolvePlace(id, text, session) }
                if (endpoint.isResolved) {
                    update(target, state(target).complete(ticket, endpoint))
                } else {
                    update(target, state(target).fail(ticket, "This place has no usable coordinates. Choose another result."))
                }
            } catch (_: TimeoutCancellationException) {
                update(target, state(target).fail(ticket, "Location lookup timed out. Check your connection and search again."))
            } catch (cancelled: CancellationException) {
                // Canceled work must neither overwrite a newer selection nor leave a spinner.
                update(target, state(target).fail(ticket, "Location lookup was interrupted. Search again."))
                throw cancelled
            } catch (error: Exception) {
                update(target, state(target).fail(ticket, placesFailureMessage(error)))
            }
        }
    }

    fun beginGpsPermission() {
        interruptRouteRequest()
        val target = EndpointTarget.ORIGIN
        cancelWork(target)
        closePreview()
        val next = origin.begin(EndpointOperation.PERMISSION, "Waiting for location permission…")
        update(target, next)
        saved["gps_permission"] = next.revision
    }

    fun gpsPermissionResult(allowed: Boolean) {
        val ticket = saved.remove<Long>("gps_permission") ?: return
        if (origin.revision != ticket) return
        if (!allowed) {
            update(EndpointTarget.ORIGIN, origin.fail(ticket,
                "Location access wasn't granted. Allow it in app settings, or search for a starting location."))
            return
        }
        update(EndpointTarget.ORIGIN, origin.copy(
            operation = EndpointOperation.GPS, message = "Getting your location…", isError = false
        ))
        jobs[EndpointTarget.ORIGIN] = viewModelScope.launch {
            try {
                val endpoint = withTimeout(20_000) { service.currentLocation() }
                if (endpoint != null && endpoint.isResolved) {
                    if (origin.revision == ticket) tripChanged()
                    update(EndpointTarget.ORIGIN, origin.complete(ticket, endpoint))
                } else {
                    update(EndpointTarget.ORIGIN, origin.fail(ticket,
                        "No location received. Turn on device location and try GPS again, or search manually."))
                }
            } catch (_: SecurityException) {
                update(EndpointTarget.ORIGIN, origin.fail(ticket,
                    "Location permission changed. Tap GPS to allow access, or use manual search."))
            } catch (_: TimeoutCancellationException) {
                update(EndpointTarget.ORIGIN, origin.fail(ticket,
                    "Location request timed out. Check device location and tap GPS again."))
            } catch (cancelled: CancellationException) {
                update(EndpointTarget.ORIGIN, origin.fail(ticket, "Location request was interrupted. Tap GPS again."))
                throw cancelled
            } catch (_: Exception) {
                update(EndpointTarget.ORIGIN, origin.fail(ticket,
                    "Couldn't get your location. Check device location and try again."))
            }
        }
    }

    fun cancelGpsPermission() {
        val ticket = saved.remove<Long>("gps_permission") ?: return
        update(EndpointTarget.ORIGIN, origin.cancel(ticket))
    }
}
