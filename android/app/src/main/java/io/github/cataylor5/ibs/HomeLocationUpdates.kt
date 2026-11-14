package io.github.cataylor5.ibs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.*

/** Home-only foreground movement updates; never requests permission or runs during navigation. */
@Composable
internal fun HomeLocationUpdates(trip: TripPlanningViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled = trip.origin.endpoint.isResolved && !trip.origin.isBusy && !trip.origin.isError
    DisposableEffect(lifecycle, enabled) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        var started = false
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                result.lastLocation?.let { trip.updateHomeLocation(it.latitude, it.longitude) }
            }
        }
        fun stop() { client.removeLocationUpdates(callback); started = false }
        fun start() {
            if (started || !enabled || ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            try {
                client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000)
                    .setMinUpdateDistanceMeters(100f).setMaxUpdateAgeMillis(0).build(),
                    callback, Looper.getMainLooper()).addOnFailureListener { started = false }
                started = true
            } catch (_: SecurityException) { started = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
}
