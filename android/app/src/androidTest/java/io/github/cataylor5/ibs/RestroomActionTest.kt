package io.github.cataylor5.ibs

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cataylor5.ibs.ui.theme.IBSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RestroomActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneTapRequestsTheStopWithoutConfirmation() {
        var requested: Long? = null
        compose.setContent {
            val vm = androidx.compose.runtime.remember {
                TripPlanningViewModel(
                    InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
                    SavedStateHandle(mapOf(
                        "origin_text" to "Current location", "origin_lat" to 36.0, "origin_lng" to -79.0,
                        "destination_text" to "Destination", "destination_lat" to 36.1, "destination_lng" to -80.0
                    ))
                )
            }
            IBSTheme {
                RestroomRouteAction(
                    BackendRestroom(42, "Test business", null, 36.0, -80.0,
                        RestroomAccess.PUBLIC, false, false, false, true, false, null, 0),
                    vm, LocalBackendAccess({ action, id ->
                        if (action == RestroomRequest.ROUTE_VIA_STOP) requested = id
                    }, null)
                )
            }
        }
        compose.onNodeWithText("Use this stop").performClick()
        compose.runOnIdle { assertEquals(42L, requested) }
    }
}
