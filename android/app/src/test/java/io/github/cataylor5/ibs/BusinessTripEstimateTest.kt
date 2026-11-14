package io.github.cataylor5.ibs

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BusinessTripEstimateTest {
    private val origin = TripEndpoint("User", 38.5, -120.2)
    private val destination = TripEndpoint("Final destination", 43.252, -126.453)
    private val business = BackendRestroom(12, "Business", "Address", 40.7, -120.95,
        RestroomAccess.PUBLIC, false, false, false, true, false, 4.5, 2)
    private fun direct(seconds: Int = 2400) = JSONObject().put("distanceMeters", 52000)
        .put("durationSeconds", seconds).put("encodedPolyline", "_p~iF~ps|U_ulLnnqC_mqNvxq`@")
    private fun via() = direct(2580).put("stop", JSONObject().put("id", 12)
        .put("name", business.name).put("latitude", business.latitude).put("longitude", business.longitude))
        .put("baseline", direct()).put("extraDrivingSeconds", 180).put("detourLimitMinutes", 10)

    @Test fun blankDestinationUsesDrivingTimeToBusinessForRankingAndLimit() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(direct(420).toString()))
        server.start()
        try {
            val estimate = estimateBusinessTrip(origin, null, business, false, RouteRepository(server.url("/").toString()))
            assertEquals(420.0, estimate.travelSeconds!!, 0.001)
            assertEquals(420.0, estimate.preferenceSeconds!!, 0.001)
            assertFalse(detourMatches(estimate.preferenceSeconds, 5))
            assertTrue(detourMatches(estimate.preferenceSeconds, 10))
            val request = JSONObject(server.takeRequest().body.readUtf8())
            assertFalse(request.has("restroomId"))
            assertEquals(business.latitude!!, request.getJSONObject("destination").getDouble("latitude"), 0.0)
        } finally { server.shutdown() }
    }

    @Test fun finalDestinationUsesArrivalTimeForRankingAndAddedTimeForLimit() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(via().put("stopTravelSeconds", 420).toString()))
        server.start()
        try {
            val estimate = estimateBusinessTrip(origin, destination, business, true, RouteRepository(server.url("/").toString()))
            assertEquals(420.0, estimate.travelSeconds!!, 0.001)
            assertEquals(180.0, estimate.preferenceSeconds!!, 0.001)
            assertTrue(detourMatches(estimate.preferenceSeconds, 5))
            val request = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(12L, request.getLong("restroomId"))
            assertTrue(request.getBoolean("freePublicOnly"))
            assertTrue(request.getBoolean("includeStopTravelTime"))
            assertEquals(destination.latitude!!, request.getJSONObject("destination").getDouble("latitude"), 0.0)
        } finally { server.shutdown() }
    }

    @Test fun olderBackendWithoutLegTimeUsesDirectDrivingEstimateAndRetainsDetour() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(via().toString()))
        server.enqueue(MockResponse().setBody(direct(960).toString()))
        server.start()
        try {
            val estimate = estimateBusinessTrip(origin, destination, business, false, RouteRepository(server.url("/").toString()))
            assertEquals(960.0, estimate.travelSeconds!!, 0.001)
            assertEquals(180.0, estimate.preferenceSeconds!!, 0.001)
            val viaRequest = JSONObject(server.takeRequest().body.readUtf8())
            val arrivalRequest = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(viaRequest.getBoolean("includeStopTravelTime"))
            assertFalse(arrivalRequest.has("restroomId"))
            assertEquals(business.latitude!!, arrivalRequest.getJSONObject("destination").getDouble("latitude"), 0.0)
            assertEquals(origin.longitude!!, arrivalRequest.getJSONObject("origin").getDouble("longitude"), 0.0)
        } finally { server.shutdown() }
    }

    @Test fun missingLegAndFailedDirectLookupStayUnknownWithoutInventingTime() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(via().toString()))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"ROUTES_UNAVAILABLE"}"""))
        server.start()
        try {
            val estimate = estimateBusinessTrip(origin, destination, business, false, RouteRepository(server.url("/").toString()))
            assertNull(estimate.travelSeconds)
            assertEquals(180.0, estimate.preferenceSeconds!!, 0.001)
            assertEquals("Travel time unavailable • 4.5/5", ReplacementStop(business, estimate.travelSeconds).details)
        } finally { server.shutdown() }
    }
}
