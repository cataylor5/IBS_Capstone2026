package io.github.cataylor5.ibs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal enum class RestroomRequest { LOAD, REFRESH, DETAILS, START_ROUTE, ROUTE_VIA_STOP }
internal data class LocalBackendAccess(
    val request: (RestroomRequest, Long?) -> Unit,
    val message: String?
)

/** Android 17 gates direct local-network traffic, including the debug emulator API. */
@Composable
internal fun rememberLocalBackendAccess(
    viewModel: RestroomsViewModel,
    onStartRoute: () -> Unit = {},
    onRouteViaStop: (Long) -> Unit = {}
): LocalBackendAccess {
    val currentStartRoute by rememberUpdatedState(onStartRoute)
    val currentRouteViaStop by rememberUpdatedState(onRouteViaStop)
    val context = LocalContext.current
    val activity = context.hostActivity()
    val permission = "android.permission.ACCESS_LOCAL_NETWORK"
    val permissionRequired = BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var rationale by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }

    fun granted() = !permissionRequired ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun needsRationale() = activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    fun clearPending() {
        pendingAction = null
        pendingId = null
    }
    fun executePending() {
        val action = pendingAction ?: return
        val id = pendingId
        clearPending()
        message = null
        when (RestroomRequest.valueOf(action)) {
            RestroomRequest.LOAD -> viewModel.ensureLoaded()
            RestroomRequest.REFRESH -> viewModel.refresh()
            RestroomRequest.DETAILS -> id?.let(viewModel::showDetails)
            RestroomRequest.START_ROUTE -> currentStartRoute()
            RestroomRequest.ROUTE_VIA_STOP -> id?.let(currentRouteViaStop)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (granted()) {
            executePending()
        } else {
            clearPending()
            message = "Local network access is off. Allow Nearby devices access to connect to the development API, then retry."
            if (!needsRationale()) settings = true
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false; clearPending() },
            title = { Text("Connect to the development API") },
            text = { Text("This debug build needs local network access to load IBS restroom data and calculate routes through your development computer.") },
            confirmButton = {
                TextButton(onClick = { rationale = false; launcher.launch(permission) }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { rationale = false; clearPending() }) { Text("Cancel") }
            }
        )
    }
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Local network access is off") },
            text = { Text("Allow Nearby devices in app settings, return to IBS, and tap Retry.") },
            confirmButton = {
                TextButton(onClick = {
                    settings = false
                    context.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ))
                }) { Text("Open app settings") }
            },
            dismissButton = {
                TextButton(onClick = { settings = false }) { Text("Cancel") }
            }
        )
    }
    return LocalBackendAccess(
        request = { action, id ->
            // A recreated composition must not launch a second permission prompt.
            if (pendingAction == null) {
                pendingAction = action.name
                pendingId = id
                when {
                    granted() -> executePending()
                    needsRationale() -> rationale = true
                    else -> launcher.launch(permission)
                }
            }
        },
        message = message
    )
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
