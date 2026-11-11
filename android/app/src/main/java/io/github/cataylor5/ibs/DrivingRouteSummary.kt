package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ceil

internal fun drivingMinutes(seconds: Double): String = when {
    seconds == 0.0 -> "0 min"
    seconds < 60 -> "Less than 1 min"
    else -> "${ceil(seconds / 60).toLong()} min"
}

@Composable
fun DrivingRouteSummary(route: DrivingRoute, onClear: () -> Unit, onRemoveStop: () -> Unit) {
    val context = LocalContext.current
    var confirmNavigation by remember { mutableStateOf(false) }
    if (confirmNavigation) {
        AlertDialog(
            onDismissRequest = { confirmNavigation = false },
            title = { Text("Start live navigation?") },
            text = { Text("Guidance starts from your device’s current location, even when the preview uses a manual origin. Your selected restroom stays on the trip. Google recalculates the route; the preview’s detour limit is not enforced during live rerouting. Guidance pauses when this screen is in the background.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNavigation = false
                    context.startActivity(LiveNavigationActivity.intent(context, route, demo = false))
                }) { Text("Start live guidance") }
            },
            dismissButton = { TextButton(onClick = { confirmNavigation = false }) { Text("Cancel") } }
        )
    }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Driving route ready", style = MaterialTheme.typography.titleLarge)
        Text("From: ${route.origin.displayText}")
        route.stop?.let { Text("Via restroom: ${it.name}", style = MaterialTheme.typography.titleMedium) }
        Text("To: ${route.destination.displayText}")
        Text(String.format(Locale.US, "%.1f mi • %s", route.distanceMeters / 1609.344, drivingMinutes(route.durationSeconds)),
            style = MaterialTheme.typography.titleMedium)
        if (route.stop != null && route.baseline != null && route.extraDrivingSeconds != null) {
            Text("Added driving time: ${drivingMinutes(route.extraDrivingSeconds)} (limit: ${route.detourLimitMinutes} min)")
            Text("Direct route: ${drivingMinutes(route.baseline.durationSeconds)}. Restroom visit time is not included.",
                style = MaterialTheme.typography.bodySmall)
            Text("Development database stop; verify restroom access before relying on it.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRemoveStop) { Text("Remove restroom stop") }
        } else {
            Text("Use Find a Restroom to choose a database stop and compare its driving detour.",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Google Routes estimate; traffic is not included. Fictional demo stops are separate.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { confirmNavigation = true }) { Text("Navigate") }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = {
                context.startActivity(LiveNavigationActivity.intent(context, route, demo = true))
            }) { Text("Demo navigation (simulated)") }
        }
        TextButton(onClick = onClear) { Text("Clear route") }
    }
}
