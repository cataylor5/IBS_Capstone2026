// Explicit opt-in integration tests. All review writes use ibs_reviews_test, never the real ratings tables.
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { main, demo } = require("./review-pools");
const { createArrivalReviewHandlers } = require("./arrival-reviews");
const { PgSessionStore } = require("./review-session-store");
const handler = createArrivalReviewHandlers({ main, demo });
const userId = 2000000001;
let id, original;
async function invoke(body, custom = handler) {
    const result = { status: 200 };
    const response = { status(v) { result.status = v; return this; }, json(v) { result.body = v; return this; } };
    await custom.submit({ params: { id: String(id) }, body, session: { userId } }, response,
        error => { throw error; });
    return result;
}
const input = () => ({ overall: 4, comment: "Automated DEMO review", accessType: "PUBLIC", privacyType: "PRIVATE",
    submissionId: crypto.randomUUID(), demo: true });
test.before(async () => {
    assert.notEqual((await main.query("SELECT current_database() AS name")).rows[0].name,
        (await demo.query("SELECT current_database() AS name")).rows[0].name);
    assert.ok((await demo.query("SELECT to_regclass('public.ibs_review_test_metadata') AS marker")).rows[0].marker);
    id = (await main.query("SELECT restroom_id FROM public.restrooms ORDER BY restroom_id LIMIT 1")).rows[0].restroom_id;
    original = (await main.query("SELECT (SELECT count(*) FROM public.ratings) AS ratings,(SELECT count(*) FROM public.reviews) AS reviews,(SELECT count(*) FROM public.restroom_confirmations) AS confirmations")).rows[0];
});
test.after(async () => {
    try {
        // Explicitly delete only this test fixture, preserving any manually submitted demo reviews.
        await demo.query("DELETE FROM public.arrival_review_submissions WHERE user_id=$1", [userId]);
        await demo.query("DELETE FROM public.restroom_confirmations WHERE user_id=$1", [userId]);
        await demo.query("DELETE FROM public.reviews WHERE user_id=$1", [userId]);
        await demo.query("DELETE FROM public.ratings WHERE user_id=$1", [userId]);
        await demo.query("DELETE FROM public.users WHERE user_id=$1", [userId]);
    } finally { await main.end(); await demo.end(); }
});
test("concurrent retry saves one review and one rating; confirmations update without changing verified", async () => {
    const body = input();
    const results = await Promise.all([invoke(body), invoke(body)]);
    assert.ok(results.every(r => r.body.saved));
    assert.equal(results.filter(r => r.body.replayed).length, 1);
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM public.reviews WHERE user_id=$1", [userId])).rows[0].n, 1);
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM public.ratings WHERE user_id=$1", [userId])).rows[0].n, 1);
    const before = (await demo.query("SELECT verified FROM public.restrooms WHERE restroom_id=$1", [id])).rows[0].verified;
    await invoke({ ...input(), overall: 5, comment: null, accessType: "MEMBERSHIP_REQUIRED", privacyType: "STALLS" });
    const confirmation = (await demo.query("SELECT * FROM public.restroom_confirmations WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0];
    assert.equal(confirmation.access_type, "MEMBERSHIP_REQUIRED");
    assert.equal(confirmation.privacy_type, "STALLS");
    assert.equal((await demo.query("SELECT overall FROM public.ratings WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0].overall, 5);
    assert.equal((await demo.query("SELECT verified FROM public.restrooms WHERE restroom_id=$1", [id])).rows[0].verified, before);
});
test("database failure rolls back rating and comment as a unit", async () => {
    const pool = { async connect() {
        const client = await demo.connect();
        return { query(sql, args) {
            if (sql.includes("INSERT INTO public.restroom_confirmations")) return client.query("SELECT 1/0");
            return client.query(sql, args);
        }, release() { client.release(); } };
    } };
    const broken = createArrivalReviewHandlers({ main, demo: pool });
    const before = (await demo.query("SELECT overall FROM public.ratings WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0].overall;
    const body = { ...input(), overall: 1 };
    await assert.rejects(invoke(body, broken));
    assert.equal((await demo.query("SELECT overall FROM public.ratings WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0].overall, before);
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM public.arrival_review_submissions WHERE submission_id=$1", [body.submissionId])).rows[0].n, 0);
});
test("Not sure clears only the explicitly answered field; all-unanswered reports have no row", async () => {
    await invoke({ ...input(), comment: null, accessType: null, privacyType: null,
        accessAnswered: true, privacyAnswered: false });
    const partial = (await demo.query("SELECT access_type,privacy_type FROM public.restroom_confirmations WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0];
    assert.equal(partial.access_type, null);
    assert.equal(partial.privacy_type, "STALLS");
    await invoke({ ...input(), comment: null, accessType: null, privacyType: null,
        accessAnswered: false, privacyAnswered: true });
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM public.restroom_confirmations WHERE user_id=$1 AND restroom_id=$2", [userId,id])).rows[0].n, 0);
});
test("demo submissions leave production ratings, reviews and reports untouched", async () => {
    const after = (await main.query("SELECT (SELECT count(*) FROM public.ratings) AS ratings,(SELECT count(*) FROM public.reviews) AS reviews,(SELECT count(*) FROM public.restroom_confirmations) AS confirmations")).rows[0];
    assert.deepEqual(after, original);
});
test("guest session survives creation of a fresh session store", async () => {
    const sid = "automated-review-" + crypto.randomUUID();
    const store = new PgSessionStore(demo);
    const payload = { userId, cookie: { expires: new Date(Date.now() + 60000).toISOString() } };
    await new Promise((resolve,reject) => store.set(sid,payload,error => error ? reject(error) : resolve()));
    const restored = await new Promise((resolve,reject) => new PgSessionStore(demo).get(sid,(error,value) => error ? reject(error) : resolve(value)));
    assert.equal(restored.userId, userId);
    await new Promise((resolve,reject) => store.destroy(sid,error => error ? reject(error) : resolve()));
});
