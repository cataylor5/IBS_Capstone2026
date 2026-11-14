package io.github.cataylor5.ibs

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RestroomReviewTest {
    @Test fun ratingIsRequiredAndAnswersStartUnselected() {
        val draft = RestroomReviewDraft()
        assertFalse(draft.valid)
        assertNull(draft.accessChoice)
        assertNull(draft.privacyChoice)
        assertTrue(draft.copy(overall = 1).valid)
        assertTrue(draft.copy(overall = 5).valid)
        assertFalse(draft.copy(overall = 0).valid)
        assertFalse(draft.copy(overall = 6).valid)
        assertFalse(draft.copy(overall = 4, comment = "x".repeat(4001)).valid)
    }
    @Test fun notSureAndEmptyCommentAreUnansweredAndIdentityComesFromServer() {
        val body = JSONObject(RestroomReviewDraft(4, "  ", "NOT_SURE", "NOT_SURE").payload("arrival", true))
        assertTrue(body.isNull("accessType"))
        assertTrue(body.isNull("privacyType"))
        assertTrue(body.isNull("comment"))
        assertTrue(body.getBoolean("accessAnswered"))
        assertTrue(body.getBoolean("privacyAnswered"))
        val unanswered = JSONObject(RestroomReviewDraft(4).payload("arrival", true))
        assertFalse(unanswered.getBoolean("accessAnswered"))
        assertFalse(unanswered.getBoolean("privacyAnswered"))
        assertFalse(body.has("user_id"))
        assertTrue(body.getBoolean("demo"))
        assertEquals("arrival", body.getString("submissionId"))
    }
    @Test fun explicitReportsUseConfirmationVocabularyRatherThanEditingVerifiedFields() {
        val draft = RestroomReviewDraft(5, " Test ", "MEMBERSHIP_REQUIRED", "PRIVATE")
        val body = JSONObject(draft.payload("arrival", false))
        assertEquals("MEMBERSHIP_REQUIRED", body.getString("accessType"))
        assertEquals("PRIVATE", body.getString("privacyType"))
        assertEquals("Test", body.getString("comment"))
        assertFalse(body.has("verified"))
        assertEquals(body.toString(), draft.payload("arrival", false))
    }
    @Test fun arrivalUsesExactMidwayAndFinalRestroomIds() {
        val origin = NavigationTarget("Origin", 36.0, -80.0)
        val stop = NavigationTarget("Midway business", 36.1, -80.1)
        val final = NavigationTarget("Final business", 36.2, -80.2)
        val plan = NavigationPlan(origin, final, stop)
        assertEquals(ArrivedRestroom(5, "Midway business"),
            arrivedRestroom(plan, NavigationProgress(atRestroom = true), 5, 12))
        assertEquals(ArrivedRestroom(12, "Final business"),
            arrivedRestroom(plan, NavigationProgress(arrived = true), 5, 12))
        assertNull(arrivedRestroom(plan, NavigationProgress(), 5, 12))
        assertNull(arrivedRestroom(plan, NavigationProgress(arrived = true), 5, null))
        assertEquals(ArrivedRestroom(12, "Final business"),
            arrivedRestroom(NavigationPlan(origin, final), NavigationProgress(arrived = true), 12, 12))
    }
}
