package io.github.cataylor5.ibs

import androidx.compose.foundation.BorderStroke
import io.github.cataylor5.ibs.ui.theme.IbsBrand
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel

/** Always above the field; never moves below it into the keyboard or primary button. */
internal class AboveDestinationField(private val gap: Int = 4) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0))
    }
}

@Composable
internal fun DestinationSearchField(trip: TripPlanningViewModel,
    suggestions: DestinationSuggestionsViewModel = viewModel()) {
    val endpoint = trip.destination
    val state = suggestions.state
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(1) }
    var availableHeight by remember { mutableIntStateOf(1) }
    val safeTop = WindowInsets.statusBars.getTop(density)
    LaunchedEffect(endpoint.endpoint.displayText, endpoint.endpoint.isResolved, endpoint.isBusy) {
        if (endpoint.endpoint.isResolved || endpoint.isBusy) suggestions.clear()
        else suggestions.search(endpoint.endpoint.displayText)
    }
    Box {
        OutlinedTextField(
            value = endpoint.endpoint.displayText,
            onValueChange = {
                dismissed = false
                trip.edit(EndpointTarget.DESTINATION, it)
                suggestions.search(it)
            },
            label = { Text("Destination (optional)") },
            singleLine = true,
            trailingIcon = {
                if (endpoint.endpoint.displayText.isNotEmpty()) TextButton(onClick = {
                    trip.edit(EndpointTarget.DESTINATION, "")
                    suggestions.clear()
                    dismissed = false
                }) { Text("Clear") }
            },
            isError = endpoint.isError,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) dismissed = false }
                .onGloballyPositioned {
                    width = it.size.width
                    availableHeight = (it.boundsInWindow().top.toInt() - safeTop - with(density) { 8.dp.roundToPx() }).coerceAtLeast(1)
                }
        )
        if (focused && !dismissed && !endpoint.endpoint.isResolved && !endpoint.isBusy &&
            endpoint.endpoint.displayText.trim().length >= 2 && state.query == endpoint.endpoint.displayText) {
            Popup(popupPositionProvider = remember { AboveDestinationField() },
                onDismissRequest = { dismissed = true },
                properties = PopupProperties(focusable = false)) {
                Surface(tonalElevation = 0.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(with(density) { width.toDp() })
                        .heightIn(max = minOf(280.dp, with(density) { availableHeight.toDp() }))) {
                    Column {
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            if (state.loading) item {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Finding destinations…", Modifier.padding(12.dp))
                            }
                            state.error?.let { error -> item {
                                Text(error, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { suggestions.search(state.query, retry = true) }) { Text("Retry") }
                            } }
                            if (!state.loading && state.error == null && state.rows.isEmpty()) item {
                                Text("No matching destinations.", Modifier.padding(12.dp))
                            }
                            items(state.rows, key = { it.id }) { row ->
                                Text(row.text, modifier = Modifier.fillMaxWidth().clickable {
                                    val session = suggestions.select(row) ?: return@clickable
                                    dismissed = true
                                    trip.beginSearch(EndpointTarget.DESTINATION)
                                    trip.placeSelected(EndpointTarget.DESTINATION, row.id, row.text, session)
                                    focus.clearFocus()
                                    keyboard?.hide()
                                }.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Image(painterResource(if (io.github.cataylor5.ibs.ui.theme.IbsAppearance.state.night == true)
                            com.google.android.libraries.places.R.drawable.places_powered_by_google_dark
                            else com.google.android.libraries.places.R.drawable.places_powered_by_google_light),
                            contentDescription = "Powered by Google",
                            modifier = Modifier.padding(8.dp).height(16.dp))
                    }
                }
            }
        }
    }
    if (endpoint.isBusy) Text("Resolving destination…", style = MaterialTheme.typography.bodySmall)
    else if (endpoint.isError) Text(endpoint.message ?: "Couldn't resolve destination. Select a suggestion again.",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
