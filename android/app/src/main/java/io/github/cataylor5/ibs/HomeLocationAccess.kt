package io.github.cataylor5.ibs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
internal fun rememberHomeLocationRequest(trip: TripPlanningViewModel): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun granted() = ContextCompat.checkSelfPermission(context, permissions[0]) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        trip.gpsPermissionResult(granted())
    }
    return {
        if (!trip.origin.isBusy) {
            trip.beginGpsPermission()
            val activity = context.homeActivity()
            when {
                granted() -> trip.gpsPermissionResult(true)
                asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[0]) ->
                    trip.gpsPermissionResult(false)
                else -> { asked = true; launcher.launch(permissions) }
            }
        }
    }
}

private tailrec fun android.content.Context.homeActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.homeActivity()
    else -> null
}
