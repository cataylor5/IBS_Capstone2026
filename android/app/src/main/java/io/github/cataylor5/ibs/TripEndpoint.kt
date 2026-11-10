package io.github.cataylor5.ibs

enum class EndpointTarget { ORIGIN, DESTINATION }
enum class EndpointSource { MANUAL, GPS }
enum class EndpointOperation { NONE, AUTOCOMPLETE, PERMISSION, LOOKUP, GPS }

/** Shared representation for a searched place or a GPS fix. */
data class TripEndpoint(
    val displayText: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeId: String? = null,
    val source: EndpointSource = EndpointSource.MANUAL
) {
    val isResolved: Boolean
        get() = displayText.isNotBlank() &&
            latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude != null && longitude.isFinite() && longitude in -180.0..180.0
}

/** Each new action receives a revision. Only its own completion may change this slot. */
data class EndpointState(
    val endpoint: TripEndpoint = TripEndpoint(),
    val revision: Long = 0,
    val operation: EndpointOperation = EndpointOperation.NONE,
    val message: String? = null,
    val isError: Boolean = false
) {
    val isBusy: Boolean get() = operation != EndpointOperation.NONE

    fun edit(text: String) = EndpointState(
        endpoint = TripEndpoint(displayText = text),
        revision = revision + 1
    )

    fun begin(operation: EndpointOperation, message: String? = null) = copy(
        revision = revision + 1,
        operation = operation,
        message = message,
        isError = false
    )

    fun complete(ticket: Long, endpoint: TripEndpoint): EndpointState =
        if (ticket != revision) this else copy(
            endpoint = endpoint,
            operation = EndpointOperation.NONE,
            message = if (endpoint.source == EndpointSource.GPS) "Current location selected." else "Location selected.",
            isError = false
        )

    fun fail(ticket: Long, message: String): EndpointState =
        if (ticket != revision) this else copy(
            operation = EndpointOperation.NONE,
            message = message,
            isError = true
        )

    fun cancel(ticket: Long): EndpointState =
        if (ticket != revision) this else copy(
            operation = EndpointOperation.NONE,
            message = null,
            isError = false
        )
}

fun canPreviewTrip(origin: EndpointState, destination: EndpointState): Boolean =
    origin.endpoint.isResolved && destination.endpoint.isResolved &&
        !origin.isBusy && !destination.isBusy
