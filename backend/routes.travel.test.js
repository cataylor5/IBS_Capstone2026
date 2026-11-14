const test = require("node:test");
const assert = require("node:assert/strict");
const { createRouteHandler } = require("./routes");

async function request(legs, include = true) {
    const requests = [];
    const response = { statusCode: 200, status(n) { this.statusCode = n; return this; },
        json(body) { this.body = body; } };
    await createRouteHandler({
        getApiKey: () => "test-key",
        lookupRestroom: async () => ({ name: "Test stop", latitude: 36, longitude: -80,
            accessType: "PUBLIC", feeRequired: false, requiresPurchase: false }),
        fetchImpl: async (_, request) => {
            const body = JSON.parse(request.body);
            const via = !!body.intermediates;
            requests.push({ via, mask: request.headers["X-Goog-FieldMask"] });
            return { ok: true, json: async () => ({ routes: [{
                distanceMeters: 10000, duration: via ? "1800s" : "1500s",
                polyline: { encodedPolyline: "_p~iF~ps|U_ulLnnqC_mqNvxq`@" },
                ...(via ? { legs } : {})
            }] }) };
        }
    })({ body: { origin: { latitude: 36, longitude: -79 }, destination: { latitude: 36, longitude: -81 },
        restroomId: 1, maxDetourMinutes: 15, freePublicOnly: false, includeStopTravelTime: include } }, response);
    return { response, requests };
}

test("first-leg driving time is distinct from total and added detour time", async () => {
    const { response, requests } = await request([{ duration: "420s" }, { duration: "1380s" }]);
    assert.equal(response.statusCode, 200);
    assert.equal(response.body.stopTravelSeconds, 420);
    assert.equal(response.body.durationSeconds, 1800);
    assert.equal(response.body.extraDrivingSeconds, 300);
    assert.ok(requests.find(r => r.via).mask.includes("routes.legs.duration"));
    assert.ok(!requests.find(r => !r.via).mask.includes("routes.legs.duration"));
});

test("missing or invalid first-leg estimate stays unavailable without inventing a time", async () => {
    for (const legs of [undefined, [], [{ duration: "420s" }], [{ duration: "-1s" }, {}],
        [{ duration: "invalid" }, {}], [{ duration: "1801s" }, {}]]) {
        const { response } = await request(legs);
        assert.equal(response.statusCode, 200);
        assert.equal(response.body.stopTravelSeconds, null);
        assert.equal(response.body.extraDrivingSeconds, 300);
    }
});

test("zero and fractional driving seconds are supported", async () => {
    for (const [duration, expected] of [["0s", 0], ["420.5s", 420.5]]) {
        assert.equal((await request([{ duration }, { duration: "1000s" }])).response.body.stopTravelSeconds, expected);
    }
});

test("existing route consumers do not request additional provider fields", async () => {
    const { response, requests } = await request(undefined, false);
    assert.ok(!Object.hasOwn(response.body, "stopTravelSeconds"));
    assert.ok(requests.every(r => !r.mask.includes("routes.legs.duration")));
});
