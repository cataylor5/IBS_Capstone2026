const test = require("node:test");
const assert = require("node:assert/strict");
const { createRouteHandler } = require("./routes");

test("explicit farther-stop choice returns the real route beyond the preference", async () => {
    const response = { status: value => { throw new Error("Unexpected HTTP " + value); },
        json: body => { response.body = body; } };
    await createRouteHandler({
        getApiKey: () => "test-key",
        lookupRestroom: async () => ({ name: "Test", latitude: 36, longitude: -80,
            accessType: "PUBLIC", feeRequired: false, requiresPurchase: false }),
        fetchImpl: async (_, request) => ({ ok: true, json: async () => ({ routes: [{
            distanceMeters: 10000, duration: JSON.parse(request.body).intermediates ? "1800s" : "600s",
            polyline: { encodedPolyline: "_p~iF~ps|U_ulLnnqC_mqNvxq`@" }
        }] }) })
    })({ body: { origin: { latitude: 36, longitude: -79 }, destination: { latitude: 36, longitude: -81 },
        restroomId: 1, maxDetourMinutes: 5, freePublicOnly: false, allowBeyondPreference: true } }, response);
    assert.equal(response.body.extraDrivingSeconds, 1200);
    assert.equal(response.body.detourLimitMinutes, 5);
    assert.equal(response.body.stop.id, 1);
});
