package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.time.Instant
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember

@Composable
internal fun RestroomListSection(viewModel: RestroomsViewModel, freePublicOnly: Boolean, access: LocalBackendAccess, tripViewModel: TripPlanningViewModel, tripFlow: Boolean = false) {
    var showFarther by rememberSaveable(tripViewModel.maxDetourMinutes) { mutableStateOf(false) }
    val origin = tripViewModel.origin.endpoint
    val destination = tripViewModel.destination.endpoint
    LaunchedEffect(origin, destination, viewModel.restrooms, viewModel.loading, freePublicOnly) {
        if (!viewModel.loading) viewModel.checkDetours(origin, destination, freePublicOnly)
    }
    val resolved = origin.isResolved && destination.isResolved
    val available = viewModel.restrooms.filter { !freePublicOnly || it.isConfirmedFreePublic }
    val visible = if (tripFlow) matchingBusinesses(available, viewModel.detourSeconds,
        tripViewModel.maxDetourMinutes, freePublicOnly)
    else available.filter {
        showFarther || !resolved || detourMatches(viewModel.detourSeconds[it.id], tripViewModel.maxDetourMinutes)
    }
    Text(if (tripFlow) "Businesses" else "IBS restrooms", style = MaterialTheme.typography.titleLarge)
    Text("Development database records; entries may be test data.", style = MaterialTheme.typography.bodySmall)
    Text("Use this stop compares road driving time with your direct route.", style = MaterialTheme.typography.bodySmall)
    RestroomDetourFeedback(tripViewModel)
    if (resolved) {
        Text("Businesses within ${tripViewModel.maxDetourMinutes} extra driving minutes, sorted by least detour.")
        if (!tripFlow) TextButton(onClick = { showFarther = !showFarther }) {
            Text(if (showFarther) "Show only matching stops" else "Show all distances")
        }
        if (viewModel.checkingDetours) {
            CircularProgressIndicator()
            Text("Checking road detours…")
        }
        if (viewModel.detourErrors.isNotEmpty()) {
            Text("Some detours could not be checked. Retry to check those businesses.")
            TextButton(onClick = { viewModel.retryDetours(origin, destination, freePublicOnly) }) { Text("Retry detour checks") }
        }
    } else Text("Select both trip locations to filter by added driving time.")

    access.message?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = access.openSettings) { Text("App Settings") }
    }
    if (viewModel.loading) {
        CircularProgressIndicator()
        Text(if (viewModel.restrooms.isEmpty()) "Loading restrooms…" else "Refreshing restrooms…")
    }
    viewModel.error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error)
        if (viewModel.restrooms.isNotEmpty()) Text("Previously loaded results are shown below.")
    }
    TextButton(onClick = { access.request(RestroomRequest.REFRESH, null) }, enabled = !viewModel.loading) {
        Text(if (viewModel.error != null || access.message != null) "Retry" else "Refresh restrooms")
    }

    if (freePublicOnly && viewModel.restrooms.any { it.feeRequired == null }) {
        Text(
            "Only confirmed free public restrooms are shown. Fee information is missing for some entries; choose Any to see them.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (viewModel.hasLoaded && !viewModel.loading && !viewModel.checkingDetours && viewModel.error == null && access.message == null) {
        Text("Results: ${visible.size}", style = MaterialTheme.typography.bodyMedium)
        if (visible.isEmpty()) {
            Text(if (freePublicOnly) "No confirmed free public restrooms match."
                else if (resolved) "No matching businesses. Go back to adjust your preferences."
                else "No restrooms are available yet.")
        }
    }
    visible.forEach { restroom ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(restroom.name, style = MaterialTheme.typography.titleMedium)
                if (resolved) {
                    val seconds = viewModel.detourSeconds[restroom.id]
                    Text(if (seconds != null) "Added driving time: ${drivingMinutes(seconds)}" +
                        (if (!detourMatches(seconds, tripViewModel.maxDetourMinutes)) " • Beyond your preference" else "")
                        else if (viewModel.checkingDetours) "Detour not checked yet"
                        else "Detour unavailable")
                }
                Text("${restroom.accessLabel} • ${restroom.feeLabel}")
                restroom.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(ratingText(restroom), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { access.request(RestroomRequest.DETAILS, restroom.id) }) { Text("View details") }
                RestroomRouteAction(restroom, tripViewModel, access)
            }
        }
    }
}

@Composable
internal fun BackendRestroomDetailsDialog(viewModel: RestroomsViewModel, access: LocalBackendAccess, tripViewModel: TripPlanningViewModel, showRouteAction: Boolean = true) {
    if (viewModel.selectedId == null) return
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(viewModel.selectedId) { while (true) { delay(15_000); now = Instant.now() } }
    AlertDialog(
        onDismissRequest = viewModel::closeDetails,
        title = { Text(viewModel.detail?.name ?: "Restroom details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                access.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    if (!viewModel.detailLoading && viewModel.detailError == null) {
                        TextButton(onClick = { access.request(RestroomRequest.DETAILS, viewModel.selectedId) }) { Text("Retry") }
                    }
                }
                if (viewModel.detailLoading) {
                    CircularProgressIndicator()
                    Text("Loading restroom details…")
                }
                viewModel.detailError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { access.request(RestroomRequest.DETAILS, viewModel.selectedId) }) { Text("Retry") }
                }
                RestroomDetourFeedback(tripViewModel)

                viewModel.detail?.let { restroom ->
                    restroom.address?.let { Text(it) }
                    Text(restroom.accessLabel)
                    Text(restroom.privacyLabel)
                    Text(restroom.feeLabel)
                    Text(when (restroom.requiresPurchase) {
                        true -> "Purchase required."
                        false -> "No purchase requirement listed."
                        null -> "Purchase requirement unknown."
                    })
                    Text(when (restroom.requiresCode) {
                        true -> "An access code is required."
                        false -> "No access-code requirement listed."
                        null -> "Access-code requirement unknown."
                    })
                    Text(when (restroom.accessible) {
                        true -> "Marked accessible."
                        false -> "Not marked accessible."
                        null -> "Accessibility unknown."
                    })
                    Text(if (restroom.verified == true) "Marked verified by IBS." else "Not verified by IBS.")
                    Text(ratingText(restroom))
                    Text(businessHoursCountdown(restroom.businessHours, now))
                    if (showRouteAction) RestroomRouteAction(restroom, tripViewModel, access)
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::closeDetails) { Text("Close") } }
    )
}

private fun ratingText(restroom: BackendRestroom): String =
    if (restroom.ratingCount == 0 || restroom.averageRating == null) "No ratings yet"
    else String.format(Locale.US, "%.1f / 5 (%d ratings)", restroom.averageRating, restroom.ratingCount)
