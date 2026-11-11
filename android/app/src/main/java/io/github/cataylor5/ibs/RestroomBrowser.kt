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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun RestroomListSection(viewModel: RestroomsViewModel, freePublicOnly: Boolean, access: LocalBackendAccess, tripViewModel: TripPlanningViewModel) {
    val visible = viewModel.restrooms.filter { !freePublicOnly || it.isConfirmedFreePublic }
    Text("IBS restrooms", style = MaterialTheme.typography.titleLarge)
    Text("Development database records; entries may be test data.", style = MaterialTheme.typography.bodySmall)
    Text("Use this stop compares road driving time with your direct route.", style = MaterialTheme.typography.bodySmall)
    RestroomDetourFeedback(tripViewModel)

    access.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    if (viewModel.hasLoaded && !viewModel.loading && viewModel.error == null && access.message == null) {
        Text("Results: ${visible.size}", style = MaterialTheme.typography.bodyMedium)
        if (visible.isEmpty()) {
            Text(if (freePublicOnly) "No confirmed free public restrooms match."
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
internal fun BackendRestroomDetailsDialog(viewModel: RestroomsViewModel, access: LocalBackendAccess, tripViewModel: TripPlanningViewModel) {
    if (viewModel.selectedId == null) return
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
                    RestroomRouteAction(restroom, tripViewModel, access)
                    Text("Development database record; verify restroom access before relying on it.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::closeDetails) { Text("Close") } }
    )
}

private fun ratingText(restroom: BackendRestroom): String =
    if (restroom.ratingCount == 0 || restroom.averageRating == null) "No ratings yet"
    else String.format(Locale.US, "%.1f / 5 (%d ratings)", restroom.averageRating, restroom.ratingCount)
