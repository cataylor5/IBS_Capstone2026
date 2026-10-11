package io.github.cataylor5.ibs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect

@Composable
fun CurrentLocationButton(
    onLocationSelected: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onStatusChanged: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    // This gives us access to Android services and settings.

    val locationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    // This is the service we ask for a location reading.

    val cancellation = remember { CancellationTokenSource() }
    // This lets us cancel a request when this component leaves the screen.

    var isLoading by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("Use your location as the trip's starting point.")
    }
    var permissionDenied by remember { mutableStateOf(false) }
    // These values control the button and its feedback.


    LaunchedEffect(status, isLoading) {
        onStatusChanged(status, isLoading)
    }
// Let the trip form display feedback below the starting-location field.
    DisposableEffect(Unit) {
        onDispose {
            cancellation.cancel()
        }
    }
    // Stop an outstanding request when this component is removed.

    fun fetchLocation() {
        val hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Check the current permission before each request.

        if (!hasFinePermission && !hasCoarsePermission) {
            isLoading = false
            permissionDenied = true
            status = "Location permission is off."
            return
        }

        permissionDenied = false
        isLoading = true
        status = "Getting your location…"

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(15_000)
            .build()
        // Ask for a fresh reading, with a request duration of 15 seconds.

        try {
            locationClient.getCurrentLocation(
                request,
                cancellation.token
            ).addOnSuccessListener { location ->
                if (!cancellation.token.isCancellationRequested) {
                    isLoading = false

                    if (location != null) {
                        onLocationSelected(
                            location.latitude,
                            location.longitude
                        )
                        // Pass the coordinates back to the home screen.

                        status = if (hasFinePermission) {
                            "Current location selected."
                        } else {
                            "Approximate location selected."
                        }
                    } else {
                        status =
                            "No location received. Check that device location is on, then try again."
                    }
                }
            }.addOnFailureListener {
                if (!cancellation.token.isCancellationRequested) {
                    isLoading = false
                    status =
                        "Couldn't get your location. Check device location settings and try again."
                }
            }
        } catch (error: SecurityException) {
            isLoading = false
            permissionDenied = true
            status = "Location permission changed. Please allow access again."
        }
        // Handle a permission change that occurs during the request.
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allowed =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (allowed) {
            fetchLocation()
        } else {
            isLoading = false
            permissionDenied = true
            status =
                "Location access wasn't granted. You can enable it in app settings."
        }
    }
    // Continue after Android returns the user's permission choice.

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                val hasPermission =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    fetchLocation()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = !isLoading,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (compact) {
                    if (isLoading) "…" else "GPS"
                } else {
                    if (isLoading) "Finding location…" else "Use my location"
                }
            )
// Compact mode keeps the button small enough to sit beside a field.
        }

        if (!compact) {
            Text(status)
        }

        if (permissionDenied && !compact) {
            TextButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            ) {
                Text("Open app settings")
            }
            // This gives the user a way to change a denied permission.
        }
    }
}