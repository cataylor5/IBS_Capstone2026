package io.github.cataylor5.ibs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class LocationDisabledException : Exception()

/** Uses application context only; requests outlive Activity recreation, not the ViewModel. */
internal class EndpointLocationService(private val context: Context) {
    suspend fun predictions(query: String, session: AutocompleteSessionToken): List<DestinationPrediction> {
        check(Places.isInitialized())
        val cancellation = CancellationTokenSource()
        val request = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
            .setQuery(query).setSessionToken(session).setCancellationToken(cancellation.token).build()
        return Places.createClient(context).findAutocompletePredictions(request)
            .awaitResult(cancellation).autocompletePredictions.map {
                DestinationPrediction(it.placeId, it.getFullText(null).toString())
            }
    }

    suspend fun resolvePlace(id: String, text: String, session: AutocompleteSessionToken?): TripEndpoint {
        check(Places.isInitialized()) { "Places is not initialized" }
        val cancellation = CancellationTokenSource()
        val request = FetchPlaceRequest.builder(id, listOf(Place.Field.ID, Place.Field.LOCATION))
            .setSessionToken(session)
            .setCancellationToken(cancellation.token)
            .build()
        val place = Places.createClient(context).fetchPlace(request).awaitResult(cancellation).place
        val location = place.location
        return TripEndpoint(text, location?.latitude, location?.longitude, id)
    }

    suspend fun currentLocation(): TripEndpoint? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) throw SecurityException()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)) throw LocationDisabledException()
        val cancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(15_000)
            .build()
        val location = LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(request, cancellation.token).awaitResult(cancellation) ?: return null
        return TripEndpoint(
            "Using current location", location.latitude, location.longitude,
            source = EndpointSource.GPS
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(cancellation: CancellationTokenSource): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellation.cancel() }
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

/** Deliberately exclude exception payloads: they can contain keys, queries or locations. */
internal fun placesFailureMessage(statusCode: Int?): String = when (statusCode) {
    7 -> "Network error (Places status 7). Check your connection and search again."
    15 -> "Places request timed out (status 15). Please search again."
    9010, 9011, 9012 -> "Places access denied (status $statusCode). Check billing, API enablement and key restrictions."
    null -> "Places search is unavailable. Check your connection and API configuration, then search again."
    else -> "Places search failed (status $statusCode). Please search again; check API configuration if it persists."
}

internal fun placesFailureMessage(error: Exception): String =
    placesFailureMessage((error as? ApiException)?.statusCode)
