package io.github.cataylor5.ibs

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class RestroomApiTest {
    private val record = """
        {
          "restroom_id": 12,
          "business_name": "API contract test",
          "address": "Test address",
          "latitude": "36.072600",
          "longitude": "-79.792000",
          "access_type": "PUBLIC",
          "requires_purchase": false,
          "requires_code": true,
          "accessible": true,
          "verified": false,
          "average_rating": "4.5",
          "rating_count": 2
        }
    """.trimIndent()

    @Test fun readsPostgresNumericStringsAndBareArray() {
        val room = RestroomJson.list("[$record]").single()
        assertEquals(12L, room.id)
        assertEquals("API contract test", room.name)
        assertEquals(36.0726, room.latitude!!, 0.000001)
        assertEquals(-79.792, room.longitude!!, 0.000001)
        assertEquals(4.5, room.averageRating!!, 0.001)
        assertEquals(2, room.ratingCount)
        assertTrue(room.requiresCode!!)
    }

    @Test fun readsBusinessHoursByBusinessIdWithoutChangingRatings() {
        val json = org.json.JSONObject(record).put("business_id", 9)
            .put("business_hours", org.json.JSONArray("""[
                {"iso_weekday":7,"opens_at":"22:00:00","closes_at":"02:00:00"}
            ]"""))
        val room = RestroomJson.detail(json.toString())
        assertEquals(9L, room.businessId)
        assertEquals(listOf(BusinessHours(7, "22:00:00", "02:00:00")), room.businessHours)
        assertEquals(4.5, room.averageRating!!, 0.001)
        assertTrue(RestroomJson.detail(record).businessHours.isEmpty())
        json.put("business_hours", org.json.JSONArray("""[{"iso_weekday":0,"opens_at":"bad"}]"""))
        assertEquals("Hours unavailable", businessHoursCountdown(RestroomJson.detail(json.toString()).businessHours))
    }

    @Test fun circleKSevenDayHoursAndMidnightClosingSurviveApiDecoding() {
        val hours = org.json.JSONArray()
        for (day in 1..7) hours.put(org.json.JSONObject()
            .put("iso_weekday", day).put("opens_at", "06:00:00").put("closes_at", "00:00:00"))
        val json = org.json.JSONObject(record).put("restroom_id", 5).put("business_id", 8)
            .put("business_name", "Circle K").put("business_hours", hours)
        val listRoom = RestroomJson.list("[$json]").single()
        val detail = RestroomJson.detail(json.toString())
        assertEquals(8L, listRoom.businessId)
        assertEquals(7, listRoom.businessHours.size)
        assertEquals(detail.businessHours, listRoom.businessHours)
        assertEquals("Closes in 30 min", businessHoursCountdown(detail.businessHours,
            java.time.Instant.parse("2026-11-10T04:30:00Z")))
        assertEquals("Opens in 5 hr 30 min", businessHoursCountdown(detail.businessHours,
            java.time.Instant.parse("2026-11-10T05:30:00Z")))
    }

    @Test fun membershipPrivacyAndUnroundedAverageAreReadFromSameRecord() {
        val room = RestroomJson.detail(org.json.JSONObject(record).put("access_type", "MEMBERSHIP_REQUIRED")
            .put("restroom_type", "FAMILY").put("average_rating", "4.5999999999999999").toString())
        assertEquals(RestroomAccess.MEMBERSHIP_REQUIRED, room.access)
        assertEquals("FAMILY", room.restroomType)
        assertTrue(room.canUseForRoute(false))
        assertFalse(RestroomPreferences(minimumStars = 5).matches(room))
    }

    @Test fun absentFeeInformationIsUnknownAndExcludedFromFreeFilter() {
        val room = RestroomJson.detail(record)
        assertNull(room.feeRequired)
        assertFalse(room.isConfirmedFreePublic)
    }

    @Test fun freeFilterRequiresConfirmedFeeAndNoPurchase() {
        val room = RestroomJson.detail(record)
        assertTrue(room.copy(feeRequired = false).isConfirmedFreePublic)
        assertFalse(room.copy(feeRequired = false, requiresPurchase = true).isConfirmedFreePublic)
        assertFalse(room.copy(feeRequired = false, requiresPurchase = null).isConfirmedFreePublic)
        assertFalse(room.copy(feeRequired = false, access = RestroomAccess.CUSTOMER_ONLY).isConfirmedFreePublic)
    }

    @Test fun preservesUnknownFieldsAndNullRatings() {
        val room = RestroomJson.detail("""
            {"restroom_id":1,"business_name":"Unknown data","access_type":"FUTURE_VALUE",
             "average_rating":null,"rating_count":0,"latitude":null,"longitude":null}
        """)
        assertEquals(RestroomAccess.UNKNOWN, room.access)
        assertNull(room.latitude)
        assertNull(room.feeRequired)
        assertNull(room.averageRating)
        assertNull(room.requiresPurchase)
    }

    @Test(expected = JSONException::class) fun rejectsMissingRecordIdentity() {
        RestroomJson.detail("""{"business_name":"Missing ID"}""")
    }

    @Test fun listAndDetailUseActualBackendPaths() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[$record]"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(record))
        server.start()
        try {
            val repo = HttpRestroomRepository(server.url("/").toString())
            assertEquals(12L, repo.list().single().id)
            assertEquals(12L, repo.details(12).id)
            assertEquals("/api/restrooms", server.takeRequest().path)
            assertEquals("/api/restrooms/12", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test fun reportsServerFailureWithoutLeakingResponseBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500).setBody("private server diagnostic"))
        server.start()
        try {
            val repo = HttpRestroomRepository(server.url("/").toString())
            try {
                repo.list()
                fail("HTTP 500 should fail")
            } catch (error: BackendHttpException) {
                assertEquals(500, error.status)
                assertFalse(restroomErrorMessage(error).contains("private server diagnostic"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun emptyServiceConfigurationHasActionableError() = runBlocking {
        try {
            HttpRestroomRepository("").list()
            fail("Missing configuration must not make a request")
        } catch (error: BackendNotConfiguredException) {
            assertTrue(restroomErrorMessage(error).contains("isn't configured"))
        }
    }
}
