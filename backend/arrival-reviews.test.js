const test = require("node:test");
const assert = require("node:assert/strict");
const { createArrivalReviewHandlers, validateReview } = require("./arrival-reviews");
const body = { overall: 4, comment: "Test review", accessType: "PUBLIC", privacyType: "PRIVATE",
    submissionId: "4ecb77ba-907a-4a5f-844c-16c0908b0cfb", demo: false };

function fixture({ failure, replay, source = true } = {}) {
    const calls = [];
    const client = { async query(sql, args) {
        calls.push({ sql, args });
        if (failure && sql.includes(failure)) throw Object.assign(new Error("injected"), { code: "TEST_FAILURE" });
        if (sql.includes("SELECT restroom_id,payload_hash")) return { rows: replay ? [replay] : [] };
        if (sql.includes("RETURNING review_id")) return { rows: [{ review_id: 91 }] };
        return { rows: [] };
    }, release() { calls.push({ sql: "RELEASE" }); } };
    const main = { async query() { return { rows: source ? [{ restroom_id: 5, business_id: 8,
        business_name: "Fixture", access_type: "PUBLIC", restroom_type: "FAMILY", verified: true }] : [] }; },
        async connect() { calls.push({ sql: "MAIN" }); return client; } };
    const demo = { async connect() { calls.push({ sql: "DEMO" }); return client; } };
    const handler = createArrivalReviewHandlers({ main, demo });
    async function invoke(input = body, userId = 17) {
        const result = { status: 200 };
        const response = { status(v) { result.status = v; return this; }, json(v) { result.body = v; return this; } };
        await handler.submit({ params: { id: "5" }, body: input, session: { userId } }, response,
            error => { result.error = error; });
        return result;
    }
    return { calls, invoke };
}
test("validates required rating and explicit answers before starting a transaction", () => {
    for (const bad of [{ overall: 0 }, { overall: 6 }, { overall: 4.5 }, { demo: null },
        { accessType: "NOT_SURE" }, { privacyType: "SINGLE_STALL" }, { comment: "x".repeat(4001) }, { submissionId: "bad" }])
        assert.equal(validateReview({ ...body, ...bad }), null);
    assert.equal(validateReview({ ...body, comment: "  ", accessType: null, privacyType: null }).comment, null);
});
test("uses server session identity and commits all fields together without changing verified status", async () => {
    const f = fixture();
    assert.equal((await f.invoke({ ...body, user_id: 999 })).body.saved, true);
    const sql = f.calls.map(c => c.sql);
    assert.ok(sql.indexOf("BEGIN") < sql.findIndex(s => s.includes("INSERT INTO public.ratings")));
    assert.ok(sql.includes("COMMIT"));
    const rating = f.calls.find(c => c.sql.includes("INSERT INTO public.ratings"));
    assert.deepEqual(rating.args, [17, 5, 4]);
    assert.ok(sql.some(s => s.includes("ON CONFLICT(user_id,restroom_id) DO UPDATE SET")));
    assert.ok(!sql.some(s => s.startsWith("UPDATE public.restrooms")));
});
test("rollback on confirmation failure prevents partial rating or review save", async () => {
    const f = fixture({ failure: "INSERT INTO public.restroom_confirmations" });
    assert.ok((await f.invoke()).error);
    assert.ok(f.calls.some(c => c.sql === "ROLLBACK"));
    assert.ok(!f.calls.some(c => c.sql === "COMMIT"));
    assert.equal(f.calls.at(-1).sql, "RELEASE");
});
test("demo requests only write to the demo pool", async () => {
    const f = fixture();
    assert.equal((await f.invoke({ ...body, demo: true })).body.demo, true);
    assert.ok(f.calls.some(c => c.sql === "DEMO"));
    assert.ok(!f.calls.some(c => c.sql === "MAIN"));
});
test("unanswered fields create no confirmation or blank review", async () => {
    const f = fixture();
    assert.equal((await f.invoke({ ...body, comment: "", accessType: null, privacyType: null })).body.saved, true);
    assert.ok(!f.calls.some(c => /INSERT INTO public.(reviews|restroom_confirmations)/.test(c.sql)));
});
test("missing session or restroom cannot submit", async () => {
    assert.equal((await fixture().invoke(body, null)).status, 401);
    assert.equal((await fixture({ source: false }).invoke()).status, 404);
});
test("reusing an arrival with changed payload cannot create a second review", async () => {
    const f = fixture({ replay: { restroom_id: 5, payload_hash: "different" } });
    assert.equal((await f.invoke()).status, 409);
    assert.ok(!f.calls.some(c => c.sql.includes("INSERT INTO public.ratings")));
});
