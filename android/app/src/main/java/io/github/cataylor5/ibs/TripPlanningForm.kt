package io.github.cataylor5.ibs

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.widget.PlaceAutocomplete

@Composable
fun TripPlanningForm(
    viewModel: TripPlanningViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val origin = viewModel.origin
    val destination = viewModel.destination
    val selectedStop = demoTripStops.find { it.id == viewModel.selectedStopId }

    fun handleSearchResult(target: EndpointTarget, result: ActivityResult) {
        val data = result.data
        try {
            when {
                result.resultCode == Activity.RESULT_CANCELED -> viewModel.cancelSearch(target)
                result.resultCode == Activity.RESULT_OK && data != null -> {
                    val prediction = PlaceAutocomplete.getPredictionFromIntent(data)
                    if (prediction == null) {
                        viewModel.searchFailed(target)
                    } else {
                        viewModel.placeSelected(
                            target, prediction.placeId, prediction.getFullText(null).toString(),
                            PlaceAutocomplete.getSessionTokenFromIntent(data)
                        )
                    }
                }
                else -> viewModel.searchFailed(
                    target, data?.let { PlaceAutocomplete.getResultStatusFromIntent(it)?.statusCode }
                )
            }
        } catch (_: Exception) {
            viewModel.searchFailed(target)
        }
    }

    // Separate stable launchers route results correctly even after Activity recreation.
    val originLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        handleSearchResult(EndpointTarget.ORIGIN, it)
    }
    val destinationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        handleSearchResult(EndpointTarget.DESTINATION, it)
    }

    fun search(target: EndpointTarget) {
        focusManager.clearFocus()
        viewModel.beginSearch(target)
        try {
            check(Places.isInitialized())
            val intent = PlaceAutocomplete.IntentBuilder()
                .setInitialQuery(viewModel.state(target).endpoint.displayText)
                .build(context)
            if (target == EndpointTarget.ORIGIN) originLauncher.launch(intent) else destinationLauncher.launch(intent)
        } catch (_: Exception) {
            viewModel.searchFailed(target)
        }
    }

    val gpsPending = origin.operation == EndpointOperation.GPS || origin.operation == EndpointOperation.PERMISSION
    val gpsSelected = origin.endpoint.source == EndpointSource.GPS
    val startMissing = !origin.endpoint.isResolved
    val destinationMissing = !destination.endpoint.isResolved

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Plan your trip", style = MaterialTheme.typography.titleLarge)
            Text("Out of way by:", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { minutes ->
                    FilterChip(
                        selected = viewModel.maxDetourMinutes == minutes,
                        onClick = { viewModel.setDetour(minutes) },
                        label = { Text("$minutes") },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            Text("Extra driving minutes per stop.", style = MaterialTheme.typography.bodySmall)
            Text("Availability:", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewModel.freePublicOnly,
                    onClick = { viewModel.changeFreePublicOnly(true) },
                    label = { Text("Free") },
                    shape = RoundedCornerShape(50)
                )
                FilterChip(
                    selected = !viewModel.freePublicOnly,
                    onClick = { viewModel.changeFreePublicOnly(false) },
                    label = { Text("Any") },
                    shape = RoundedCornerShape(50)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrentLocationButton(viewModel = viewModel, modifier = Modifier.width(56.dp))
                OutlinedTextField(
                    value = when {
                        origin.operation == EndpointOperation.PERMISSION -> "Waiting for location permission…"
                        origin.operation == EndpointOperation.GPS -> "Getting current location…"
                        else -> origin.endpoint.displayText
                    },
                    onValueChange = { viewModel.edit(EndpointTarget.ORIGIN, it) },
                    label = { Text("Starting location") },
                    singleLine = true,
                    readOnly = gpsSelected || gpsPending,
                    isError = origin.isError || (viewModel.attemptedPreview && startMissing),
                    trailingIcon = {
                        if (gpsSelected || gpsPending) {
                            TextButton(onClick = { viewModel.useManualOrigin() }) { Text("Edit") }
                        } else {
                            TextButton(onClick = { search(EndpointTarget.ORIGIN) }) { Text("Search") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            EndpointFeedback(
                state = origin,
                validation = if (viewModel.attemptedPreview && startMissing)
                    "Search and select a starting location, or tap GPS." else null
            )

            OutlinedTextField(
                value = destination.endpoint.displayText,
                onValueChange = { viewModel.edit(EndpointTarget.DESTINATION, it) },
                label = { Text("Destination") },
                placeholder = { Text("Where are you going?") },
                singleLine = true,
                isError = destination.isError || (viewModel.attemptedPreview && destinationMissing),
                trailingIcon = {
                    TextButton(onClick = { search(EndpointTarget.DESTINATION) }) { Text("Search") }
                },
                supportingText = {
                    EndpointFeedback(
                        state = destination,
                        validation = if (viewModel.attemptedPreview && destinationMissing)
                            "Search and select a destination." else null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.preview()
                },
                enabled = !origin.isBusy && !destination.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Preview trip") }
            Text(
                "Both locations must be selected before preview. Restroom stops and detour times are fictional demo data; no route is calculated.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (selectedStop != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Your planned stop", style = MaterialTheme.typography.titleLarge)
                Text(selectedStop.name, style = MaterialTheme.typography.titleMedium)
                Text(selectedStop.accessDescription)
                Text("Demo detour: ${selectedStop.detourMinutes} extra minutes")
                Text("Fictional demo stop. No route has been calculated.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { focusManager.clearFocus(); viewModel.preview() },
                        enabled = viewModel.canPreview
                    ) { Text("Change") }
                    TextButton(onClick = { viewModel.selectStop(null) }) { Text("Remove") }
                }
            }
        }
    }

    if (viewModel.showPreview && viewModel.canPreview) {
        AlertDialog(
            onDismissRequest = { viewModel.closePreview() },
            title = { Text("Your trip preview") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("From: ${origin.endpoint.displayText}")
                    Text("${origin.endpoint.latitude}, ${origin.endpoint.longitude}")
                    Text("To: ${destination.endpoint.displayText}")
                    Text("${destination.endpoint.latitude}, ${destination.endpoint.longitude}")
                    Text("Maximum detour preference: ${viewModel.maxDetourMinutes} extra driving minutes per stop.")
                    Text(if (viewModel.freePublicOnly) "Availability: Free public restrooms."
                        else "Availability: Any, including paid and customer-only restrooms.")
                    Text("Detour time includes returning to the route, but not time spent at the restroom.")
                    Text("Endpoints selected. No route has been calculated. The following stops and times are fictional demo data.")
                    DemoTripStops(
                        maxDetourMinutes = viewModel.maxDetourMinutes,
                        freePublicOnly = viewModel.freePublicOnly,
                        selectedStopId = viewModel.selectedStopId,
                        onStopSelected = viewModel::selectStop
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closePreview() }) { Text("Back to planning") }
            }
        )
    }
}

@Composable
private fun EndpointFeedback(state: EndpointState, validation: String?) {
    val message = state.message ?: validation ?:
        if (!state.endpoint.isResolved) "Type an address, then Search and select a result." else null
    if (message != null) {
        Text(
            message,
            color = if (state.isError || (validation != null && !state.isBusy))
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
