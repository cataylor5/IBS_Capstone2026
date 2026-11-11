const test = require("node:test");
const assert = require("node:assert/strict");
const { createRouteHandler } = require("./routes");

const endpoints = {
    origin: { latitude: 36.0726, longitude: -79.792 },
    destination: { latitude: 36.0999, longitude: -80.2442 }
};
const fixture = { distanceMeters: 52000, duration: "2400.5s", polyline: { encodedPolyline: "_p~iF~ps|U_ulLnnqC_mqNvxq`@" } };

async function invoke(body, options = {}) {
    const result = { status: 200 };
    const res = {
        status(value) { result.status = value; return this; },
        json(value) { result.body = value; return this; }
    };
    await createRouteHandler(options)({ body }, res);
    return result;
}

test("rejects invalid coordinates before calling Google", async () => {
    for (const value of [null, {}, { latitude: 91, longitude: 0 }, { latitude: "36", longitude: 0 },
        { latitude: NaN, longitude: 0 }, { latitude: 0, longitude: 181 }]) {
        const result = await invoke({ ...endpoints, origin: value }, {
            getApiKey: () => "test-key",
            fetchImpl: () => { throw new Error("Must not call provider"); }
        });
        assert.equal(result.status, 400);
        assert.equal(result.body.code, "INVALID_ENDPOINTS");
    }
});

test("missing configuration is actionable and makes no provider request", async () => {
    const result = await invoke(endpoints, {
        getApiKey: () => "",
        fetchImpl: () => { throw new Error("Must not call provider"); }
    });
    assert.equal(result.status, 503);
    assert.equal(result.body.code, "ROUTES_NOT_CONFIGURED");
});

test("requests driving geometry and returns only distance, duration and polyline", async () => {
    let captured;
    const result = await invoke(endpoints, {
        getApiKey: () => "test-key",
        fetchImpl: async (url, options) => {
            captured = { url, options };
            return { ok: true, json: async () => ({ routes: [fixture] }) };
        }
    });
    assert.equal(result.status, 200);
    assert.deepEqual(result.body, {
        distanceMeters: 52000, durationSeconds: 2400.5, encodedPolyline: fixture.polyline.encodedPolyline
    });
    assert.equal(captured.url, "https://routes.googleapis.com/directions/v2:computeRoutes");
    assert.equal(captured.options.headers["X-Goog-FieldMask"],
        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline");
    const body = JSON.parse(captured.options.body);
    assert.equal(body.travelMode, "DRIVE");
    assert.equal(body.routingPreference, "TRAFFIC_UNAWARE");
    assert.deepEqual(body.origin.location.latLng, endpoints.origin);
    assert.deepEqual(body.destination.location.latLng, endpoints.destination);
    assert.ok(!JSON.stringify(result.body).includes("test-key"));
});

test("no route is explicit instead of fabricated geometry", async () => {
    for (const payload of [{ routes: [] }, {}]) {
        const result = await invoke(endpoints, {
            getApiKey: () => "test-key", fetchImpl: async () => ({ ok: true, json: async () => payload })
        });
        assert.equal(result.status, 404);
        assert.equal(result.body.code, "NO_ROUTE");
    }
});

test("upstream access errors do not expose Google's payload", async () => {
    const result = await invoke(endpoints, {
        getApiKey: () => "test-key",
        fetchImpl: async () => ({ ok: false, status: 403, json: async () => ({ private: "private-key-and-location" }) })
    });
    assert.equal(result.status, 502);
    assert.equal(result.body.code, "ROUTES_ACCESS_DENIED");
    assert.equal(result.body.upstreamStatus, 403);
    assert.ok(!JSON.stringify(result.body).includes("private-key-and-location"));
});

test("incomplete success responses are rejected", async () => {
    for (const route of [{}, { ...fixture, duration: "-1s" }, { ...fixture, distanceMeters: -1 },
        { ...fixture, polyline: {} }]) {
        const result = await invoke(endpoints, {
            getApiKey: () => "test-key", fetchImpl: async () => ({ ok: true, json: async () => ({ routes: [route] }) })
        });
        assert.equal(result.status, 502);
    }
});

test("network exceptions and timeouts are sanitized", async () => {
    const network = await invoke(endpoints, {
        getApiKey: () => "test-key", fetchImpl: async () => { throw new Error("private diagnostic"); }
    });
    assert.equal(network.status, 502);
    assert.ok(!JSON.stringify(network.body).includes("private diagnostic"));
    const timeout = await invoke(endpoints, {
        getApiKey: () => "test-key", fetchImpl: async () => { const error = new Error(); error.name = "TimeoutError"; throw error; }
    });
    assert.equal(timeout.status, 504);
    assert.equal(timeout.body.code, "ROUTES_TIMEOUT");
});


const testStop = {
    id: 2, name: "Test restroom", address: "Test address", latitude: "36.08", longitude: "-79.8",
    accessType: "PUBLIC", requiresPurchase: false, feeRequired: null
};
const detourRequest = { ...endpoints, restroomId: 2, maxDetourMinutes: 5, freePublicOnly: false };

function detourOptions({ stop = testStop, baselineSeconds = 2400, viaSeconds = 2580, capture = [] } = {}) {
    return {
        getApiKey: () => "test-key",
        lookupRestroom: async id => { assert.equal(id, 2); return stop; },
        fetchImpl: async (_url, options) => {
            const body = JSON.parse(options.body);
            capture.push(body);
            return { ok: true, json: async () => ({ routes: [{
                ...fixture, duration: (body.intermediates ? viaSeconds : baselineSeconds) + "s"
            }] }) };
        }
    };
}

test("uses server database coordinates and returns direct baseline plus added driving time", async () => {
    const capture = [];
    const result = await invoke({ ...detourRequest, stop: { latitude: 0, longitude: 0 } },
        detourOptions({ capture }));
    assert.equal(result.status, 200);
    assert.equal(result.body.stop.id, 2);
    assert.equal(result.body.stop.name, "Test restroom");
    assert.equal(result.body.baseline.durationSeconds, 2400);
    assert.equal(result.body.durationSeconds, 2580);
    assert.equal(result.body.extraDrivingSeconds, 180);
    assert.equal(result.body.detourLimitMinutes, 5);
    assert.equal(capture.length, 2);
    assert.deepEqual(capture.find(body => body.intermediates).intermediates, [{
        location: { latLng: { latitude: 36.08, longitude: -79.8 } }
    }]);
    assert.ok(capture.every(body => body.travelMode === "DRIVE" && body.routingPreference === "TRAFFIC_UNAWARE"));
    assert.ok(result.body.baseline.encodedPolyline);
});

test("each detour allowance accepts its exact boundary and rejects a longer detour", async () => {
    for (const minutes of [5, 10, 15]) {
        const request = { ...detourRequest, maxDetourMinutes: minutes };
        const allowed = await invoke(request, detourOptions({ viaSeconds: 2400 + minutes * 60 }));
        assert.equal(allowed.status, 200);
        const exceeded = await invoke(request, detourOptions({ viaSeconds: 2401 + minutes * 60 }));
        assert.equal(exceeded.status, 409);
        assert.equal(exceeded.body.code, "DETOUR_LIMIT_EXCEEDED");
        assert.equal(exceeded.body.extraDrivingSeconds, minutes * 60 + 1);
        assert.equal(exceeded.body.detourLimitMinutes, minutes);
        assert.equal(exceeded.body.encodedPolyline, undefined);
    }
});

test("unknown fees or purchase requirements never count as confirmed Free", async () => {
    for (const stop of [testStop, { ...testStop, feeRequired: false, requiresPurchase: null },
        { ...testStop, feeRequired: false, requiresPurchase: true }]) {
        const capture = [];
        const result = await invoke({ ...detourRequest, freePublicOnly: true }, detourOptions({ stop, capture }));
        assert.equal(result.status, 409);
        assert.equal(result.body.code, "RESTROOM_NOT_ELIGIBLE");
        assert.equal(capture.length, 0);
    }
    const accepted = await invoke({ ...detourRequest, freePublicOnly: true },
        detourOptions({ stop: { ...testStop, feeRequired: false } }));
    assert.equal(accepted.status, 200);
});

test("customer-only stops are allowed under Any but not Free", async () => {
    const stop = { ...testStop, accessType: "CUSTOMER_ONLY", requiresPurchase: true };
    assert.equal((await invoke(detourRequest, detourOptions({ stop }))).status, 200);
    assert.equal((await invoke({ ...detourRequest, freePublicOnly: true }, detourOptions({ stop }))).status, 409);
});

test("unavailable and unusable stops are rejected before provider calls", async () => {
    for (const [stop, expected] of [
        [null, "RESTROOM_NOT_FOUND"],
        [{ ...testStop, latitude: null }, "RESTROOM_COORDINATES_UNAVAILABLE"],
        [{ ...testStop, longitude: "not-a-number" }, "RESTROOM_COORDINATES_UNAVAILABLE"],
        [{ ...testStop, accessType: "EMPLOYEE_ONLY" }, "RESTROOM_NOT_ELIGIBLE"],
        [{ ...testStop, accessType: "UNKNOWN" }, "RESTROOM_NOT_ELIGIBLE"]
    ]) {
        const capture = [];
        const result = await invoke(detourRequest, detourOptions({ stop, capture }));
        assert.equal(result.body.code, expected);
        assert.equal(capture.length, 0);
    }
});

test("invalid stop IDs and preference types cannot reach the database or provider", async () => {
    for (const request of [
        { ...detourRequest, restroomId: "2" }, { ...detourRequest, restroomId: -1 },
        { ...detourRequest, maxDetourMinutes: 7 }, { ...detourRequest, freePublicOnly: "false" }
    ]) {
        const result = await invoke(request, {
            getApiKey: () => "test-key",
            lookupRestroom: () => { throw new Error("Must not look up"); },
            fetchImpl: () => { throw new Error("Must not fetch"); }
        });
        assert.equal(result.status, 400);
        assert.equal(result.body.code, "INVALID_DETOUR_PREFERENCES");
    }
});

test("a faster through-stop route has zero added driving time rather than a negative detour", async () => {
    const result = await invoke(detourRequest, detourOptions({ viaSeconds: 2300 }));
    assert.equal(result.status, 200);
    assert.equal(result.body.extraDrivingSeconds, 0);
});

test("database lookup errors do not expose SQL or credentials", async () => {
    const result = await invoke(detourRequest, {
        getApiKey: () => "test-key",
        lookupRestroom: async () => { throw new Error("private SQL payload"); }
    });
    assert.equal(result.status, 503);
    assert.equal(result.body.code, "RESTROOM_LOOKUP_FAILED");
    assert.ok(!JSON.stringify(result.body).includes("private SQL payload"));
});
