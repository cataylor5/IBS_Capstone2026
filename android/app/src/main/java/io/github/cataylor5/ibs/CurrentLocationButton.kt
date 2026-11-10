package io.github.cataylor5.ibs

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
fun CurrentLocationButton(
    viewModel: TripPlanningViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun granted() = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun needsRationale() = activity != null && permissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Recheck the actual grants; do not cache permission state in the ViewModel.
        val allowed = granted()
        val stillPending = viewModel.origin.operation == EndpointOperation.PERMISSION
        viewModel.gpsPermissionResult(allowed)
        if (stillPending && !allowed && !needsRationale()) showSettings = true
    }

    val busy = viewModel.origin.operation == EndpointOperation.GPS ||
        viewModel.origin.operation == EndpointOperation.PERMISSION
    Button(
        onClick = {
            viewModel.beginGpsPermission()
            when {
                granted() -> viewModel.gpsPermissionResult(true)
                needsRationale() -> showRationale = true
                else -> permissionLauncher.launch(permissions)
            }
        },
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        modifier = modifier.semantics { contentDescription = "Use current location" }
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("GPS")
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                viewModel.cancelGpsPermission()
            },
            title = { Text("Use your location") },
            text = { Text("IBS uses your location once to select the trip's starting point. You can search for a starting location instead.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(permissions)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    viewModel.cancelGpsPermission()
                }) { Text("Cancel") }
            }
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Location permission is off") },
            text = { Text("Allow location in app settings, then return and tap GPS. Manual starting-location search also works without location permission.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    context.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ))
                }) { Text("Open app settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Use manual search") }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
