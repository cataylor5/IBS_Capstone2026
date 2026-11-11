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
