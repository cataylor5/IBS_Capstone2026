package io.github.cataylor5.ibs

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RestroomDetourTest {
    private val origin = TripEndpoint("Test origin", 38.5, -120.2)
    private val destination = TripEndpoint("Test destination", 43.252, -126.453)
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private fun direct() = JSONObject().put("distanceMeters", 52000)
        .put("durationSeconds", 2400).put("encodedPolyline", encoded)
    private fun detour() = JSONObject(direct().toString()).put("durationSeconds", 2580)
        .put("stop", JSONObject().put("id", 12).put("name", "Test restroom").put("address", "Test address")
            .put("latitude", 40.7).put("longitude", -120.95))
        .put("baseline", direct()).put("extraDrivingSeconds", 180).put("detourLimitMinutes", 5)
    private fun route() = DrivingRouteJson.decode(detour().toString(), origin, destination)

    @Test fun readsRestroomBaselineAndExactAddedTime() {
        val route = route()
        assertEquals(12L, route.stop!!.id)
        assertEquals("Test restroom", route.stop!!.name)
        assertEquals(180.0, route.extraDrivingSeconds!!, 0.001)
        assertEquals(2400.0, route.baseline!!.durationSeconds, 0.001)
        assertNull(route.baseline!!.stop)
        assertEquals(5, route.detourLimitMinutes)
    }

    @Test(expected = JSONException::class) fun missingBaselineCannotProduceAnInventedDetour() {
        DrivingRouteJson.decode(detour().apply { remove("baseline") }.toString(), origin, destination)
    }

    @Test(expected = JSONException::class) fun inconsistentAddedTimeIsRejected() {
        DrivingRouteJson.decode(detour().put("extraDrivingSeconds", 10).toString(), origin, destination)
    }

    @Test(expected = JSONException::class) fun aSuccessfulResponseStillMustRespectTheAllowance() {
        DrivingRouteJson.decode(detour().put("durationSeconds", 2800)
            .put("extraDrivingSeconds", 400).toString(), origin, destination)
    }

    @Test fun failedDetourPreservesTheExistingDirectRoute() {
        val direct = route().baseline!!
        val state = RouteState(route = direct).begin(stopId = 12, keepRoute = true)
        val failed = state.fail(state.revision, "Too far")
        assertEquals(direct, failed.route)
        assertFalse(failed.loading)
        assertNull(failed.requestedStopId)
        assertEquals("Too far", failed.error)
    }

    @Test fun removingStopRestoresBaselineAndRejectsALatePendingDetour() {
        val active = RouteState(route = route()).begin(stopId = 13, keepRoute = true)
        val removed = active.withoutStop()
        assertEquals(active.route!!.baseline, removed.route)
        assertNull(removed.route!!.stop)
        assertFalse(removed.loading)
        assertEquals(removed, removed.complete(active.revision, route()))
        assertEquals(removed, removed.fail(active.revision, "Late failure"))
    }

    @Test fun endpointEditsClearBothRestroomAndBaseline() {
        val edited = RouteState(route = route()).begin(stopId = 12, keepRoute = true).clear()
        assertNull(edited.route)
        assertNull(edited.requestedStopId)
        assertFalse(edited.loading)
    }

    @Test fun sendsOnlyStopIdAndPreferencesInsteadOfClientStopCoordinates() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(detour().toString()))
        server.start()
        try {
            val result = RouteRepository(server.url("/").toString()).compute(origin, destination, 12, 5, false)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(12L, body.getLong("restroomId"))
            assertEquals(5, body.getInt("maxDetourMinutes"))
            assertFalse(body.getBoolean("freePublicOnly"))
            assertFalse(body.has("stop"))
            assertEquals(12L, result.stop!!.id)
        } finally { server.shutdown() }
    }

    @Test fun refusesAResponseForADifferentRestroom() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(detour().toString()))
        server.start()
        try {
            try {
                RouteRepository(server.url("/").toString()).compute(origin, destination, 99, 5, false)
                fail("Unexpected stop must not be displayed")
            } catch (_: JSONException) { }
        } finally { server.shutdown() }
    }

    @Test fun exceededAllowanceErrorUsesSanitizedProviderMetrics() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(409).setBody(
            """{"code":"DETOUR_LIMIT_EXCEEDED","extraDrivingSeconds":301,"detourLimitMinutes":5,"error":"private diagnostic"}"""
        ))
        server.start()
        try {
            try {
                RouteRepository(server.url("/").toString()).compute(origin, destination, 12, 5, false)
                fail("Over-limit stop must fail")
            } catch (error: RouteRequestException) {
                assertTrue(routeErrorMessage(error).contains("6 minutes"))
                assertTrue(routeErrorMessage(error).contains("5-minute"))
                assertFalse(routeErrorMessage(error).contains("private diagnostic"))
            }
        } finally { server.shutdown() }
    }

    @Test fun routeStopEligibilityRequiresCoordinatesAndKnownAccess() {
        val room = RestroomJson.detail("""
            {"restroom_id":12,"business_name":"Test","latitude":"40.7","longitude":"-120.95",
             "access_type":"PUBLIC","requires_purchase":false}
        """)
        assertTrue(room.canUseForRoute(false))
        assertFalse(room.canUseForRoute(true))
        assertFalse(room.copy(latitude = null).canUseForRoute(false))
        assertFalse(room.copy(access = RestroomAccess.EMPLOYEE_ONLY).canUseForRoute(false))
        assertTrue(room.copy(feeRequired = false).canUseForRoute(true))
    }
}
