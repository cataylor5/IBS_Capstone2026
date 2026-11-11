package io.github.cataylor5.ibs

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DrivingRouteTest {
    private val origin = TripEndpoint("Test origin", 38.5, -120.2)
    private val destination = TripEndpoint("Test destination", 43.252, -126.453)
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private fun body() = JSONObject().put("distanceMeters", 52000)
        .put("durationSeconds", 2400.5).put("encodedPolyline", encoded).toString()
    private fun fixture() = DrivingRouteJson.decode(body(), origin, destination)

    @Test fun decodesDocumentedGooglePolyline() {
        val points = decodeRoutePolyline(encoded)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.000001)
        assertEquals(-120.2, points[0].longitude, 0.000001)
        assertEquals(40.7, points[1].latitude, 0.000001)
        assertEquals(-120.95, points[1].longitude, 0.000001)
        assertEquals(43.252, points[2].latitude, 0.000001)
        assertEquals(-126.453, points[2].longitude, 0.000001)
    }

    @Test fun truncatedOrInvalidGeometryIsRejected() {
        for (invalid in listOf("!", "_", "?", "~~~~~~~")) {
            try {
                decodeRoutePolyline(invalid)
                fail("Invalid geometry must not be rendered")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun responseKeepsTheResolvedEndpointSnapshotAndExactEstimates() {
        val route = fixture()
        assertEquals(origin, route.origin)
        assertEquals(destination, route.destination)
        assertEquals(52000L, route.distanceMeters)
        assertEquals(2400.5, route.durationSeconds, 0.001)
    }

    @Test(expected = JSONException::class) fun missingGeometryDoesNotBecomeAStraightLine() {
        DrivingRouteJson.decode("""{"distanceMeters":100,"durationSeconds":50}""", origin, destination)
    }

    @Test(expected = JSONException::class) fun aSinglePointIsNotADrivingRoute() {
        DrivingRouteJson.decode("""{"distanceMeters":0,"durationSeconds":0,"encodedPolyline":"??"}""", origin, destination)
    }

    @Test(expected = JSONException::class) fun negativeEstimatesAreRejected() {
        DrivingRouteJson.decode(JSONObject(body()).put("durationSeconds", -1).toString(), origin, destination)
    }

    @Test fun endpointEditsInvalidatePendingRouteSuccessAndFailure() {
        val pending = RouteState().begin()
        val edited = pending.clear()
        assertEquals(edited, edited.complete(pending.revision, fixture()))
        assertEquals(edited, edited.fail(pending.revision, "Old failure"))
        assertNull(edited.route)
        assertFalse(edited.loading)
    }

    @Test fun olderRouteCannotOverwriteANewerRequest() {
        val first = RouteState().begin()
        val second = first.begin()
        val complete = second.complete(second.revision, fixture())
        assertEquals(complete, complete.complete(first.revision, fixture()))
        assertEquals(complete, complete.fail(first.revision, "Old failure"))
        assertFalse(complete.loading)
    }

    @Test fun failedRequestStopsLoadingForRetry() {
        val pending = RouteState().begin()
        val failed = pending.fail(pending.revision, "Please retry")
        assertFalse(failed.loading)
        assertEquals("Please retry", failed.error)
        assertTrue(failed.begin().loading)
        assertNull(failed.begin().error)
    }

    @Test fun sendsCoordinateSnapshotToBackendAndReadsRoadGeometry() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body()))
        server.start()
        try {
            val route = RouteRepository(server.url("/").toString()).compute(origin, destination)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/routes", request.path)
            val json = JSONObject(request.body.readUtf8())
            assertEquals(origin.latitude!!, json.getJSONObject("origin").getDouble("latitude"), 0.000001)
            assertEquals(destination.longitude!!, json.getJSONObject("destination").getDouble("longitude"), 0.000001)
            assertEquals(3, route.points.size)
        } finally { server.shutdown() }
    }

    @Test fun missingServerKeyHasActionableMessageWithoutRawPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody(
            """{"code":"ROUTES_NOT_CONFIGURED","error":"private diagnostic"}"""
        ))
        server.start()
        try {
            try {
                RouteRepository(server.url("/").toString()).compute(origin, destination)
                fail("Missing configuration must fail")
            } catch (error: RouteRequestException) {
                assertEquals("ROUTES_NOT_CONFIGURED", error.code)
                assertTrue(routeErrorMessage(error).contains("Routes API key"))
                assertFalse(routeErrorMessage(error).contains("private diagnostic"))
            }
        } finally { server.shutdown() }
    }

    @Test fun noRouteResponseRemainsAnExplicitFailure() {
        assertTrue(routeErrorMessage(RouteRequestException(404, "NO_ROUTE")).contains("No driving route"))
    }
}
