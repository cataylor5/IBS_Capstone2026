package io.github.cataylor5.ibs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier,
    primaryGo: Boolean = false
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun granted() = (if (primaryGo) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else permissions).any {
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
        viewModel.gpsPermissionResult(allowed)
    }

    val busy = viewModel.origin.operation == EndpointOperation.GPS ||
        viewModel.origin.operation == EndpointOperation.PERMISSION
    Button(
        onClick = {
            if (primaryGo && !viewModel.prepareGo()) return@Button
            viewModel.beginGpsPermission()
            when {
                granted() -> viewModel.gpsPermissionResult(true)
                permissionAsked && !needsRationale() -> viewModel.gpsPermissionResult(false)
                else -> {
                    permissionAsked = true
                    permissionLauncher.launch(permissions)
                }
            }
        },
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        modifier = modifier.semantics { contentDescription = if (primaryGo) "Let’s Go" else "Use current location" }
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(if (primaryGo) "Let’s Go" else "GPS")
        }
    }

}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
