// Automated HTTP smoke check; all review writes are demo-only and this fixture is removed afterward.
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { main, demo } = require("./review-pools");
const origin = "http://127.0.0.1:" + (process.env.PORT || 3000);
let userId, submissionId;
(async () => {
    const list = await (await fetch(origin + "/api/restrooms")).json();
    const id = list[0].restroom_id;
    const context = await fetch(origin + "/api/restrooms/" + id + "/arrival-review");
    assert.equal(context.status, 200);
    const cookie = context.headers.getSetCookie().find(value => value.startsWith("connect.sid="))?.split(";")[0];
    assert.ok(cookie, "The review context must issue a guest session cookie.");
    assert.equal((await context.json()).restroom_id, id);
    const session = await fetch(origin + "/api/session", { headers: { Cookie: cookie } });
    userId = (await session.json()).user_id;
    const original = (await main.query("SELECT (SELECT count(*) FROM ratings) AS ratings,(SELECT count(*) FROM reviews) AS reviews,(SELECT count(*) FROM restroom_confirmations) AS confirmations")).rows[0];
    submissionId = crypto.randomUUID();
    const body = { submissionId, demo: true, overall: 4, comment: "Automated DEMO HTTP review", accessType: "PUBLIC", privacyType: "PRIVATE" };
    const request = () => fetch(origin + "/api/restrooms/" + id + "/arrival-review", {
        method: "POST", headers: { "Content-Type": "application/json", Cookie: cookie }, body: JSON.stringify(body)
    });
    const first = await request();
    assert.equal(first.status, 200);
    assert.deepEqual(await first.json(), { saved: true, demo: true, replayed: false });
    const retry = await request();
    assert.equal(retry.status, 200);
    assert.equal((await retry.json()).replayed, true);
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM arrival_review_submissions WHERE user_id=$1 AND submission_id=$2",
        [userId,submissionId])).rows[0].n, 1);
    assert.equal((await demo.query("SELECT count(*)::int AS n FROM reviews WHERE user_id=$1", [userId])).rows[0].n, 1);
    const after = (await main.query("SELECT (SELECT count(*) FROM ratings) AS ratings,(SELECT count(*) FROM reviews) AS reviews,(SELECT count(*) FROM restroom_confirmations) AS confirmations")).rows[0];
    assert.deepEqual(after, original);
    console.log("HTTP review context, guest cookie, demo save and duplicate retry passed; production reports unchanged.");
})().catch(() => { console.error("Arrival review HTTP check failed; inspect backend availability and setup."); process.exitCode = 1; })
.finally(async () => {
    try {
        if (userId) {
            await demo.query("DELETE FROM arrival_review_submissions WHERE user_id=$1", [userId]);
            await demo.query("DELETE FROM restroom_confirmations WHERE user_id=$1", [userId]);
            await demo.query("DELETE FROM reviews WHERE user_id=$1", [userId]);
            await demo.query("DELETE FROM ratings WHERE user_id=$1", [userId]);
            await demo.query("DELETE FROM users WHERE user_id=$1", [userId]);
            await main.query("DELETE FROM ibs_sessions WHERE (sess->>'userId')::integer=$1", [userId]);
            await main.query("DELETE FROM users WHERE user_id=$1 AND username LIKE 'guest_%'", [userId]);
        }
    } finally { await main.end(); await demo.end(); }
});
