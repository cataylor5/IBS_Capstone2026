package io.github.cataylor5.ibs

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun RestroomRouteAction(
    restroom: BackendRestroom,
    viewModel: TripPlanningViewModel,
    access: LocalBackendAccess
) {
    val onRoute = viewModel.routeState.route?.stop?.id == restroom.id
    val calculating = viewModel.routeState.loading && viewModel.routeState.requestedStopId == restroom.id
    TextButton(
        onClick = { access.request(RestroomRequest.ROUTE_VIA_STOP, restroom.id) },
        enabled = viewModel.canPreview && restroom.canUseForRoute(viewModel.freePublicOnly) &&
            !viewModel.routeState.loading && !onRoute
    ) {
        Text(when {
            onRoute -> "On your route"
            calculating -> "Calculating detour…"
            else -> "Use this stop"
        })
    }
    val explanation = when {
        !viewModel.canPreview -> "Select both trip locations before calculating a restroom detour."
        !restroom.hasCoordinates -> "This restroom has no usable location."
        !restroom.canUseForRoute(viewModel.freePublicOnly) ->
            "This restroom doesn't meet your access and fee preference."
        else -> null
    }
    explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun RestroomDetourFeedback(viewModel: TripPlanningViewModel) {
    if (viewModel.routeState.loading && viewModel.routeState.requestedStopId != null) {
        CircularProgressIndicator()
        Text("Comparing direct driving time with the restroom detour…")
    }
    viewModel.routeState.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        if (viewModel.routeState.route != null) {
            Text("Your existing route is unchanged.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
