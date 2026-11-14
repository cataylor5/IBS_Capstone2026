const test = require("node:test");
const assert = require("node:assert/strict");
const { createRouteHandler } = require("../../backend/routes");

for (const freeOnly of [false, true]) {
  test("membership-required routing with legacy free-only flag " + freeOnly, async () => {
    let status = 200, body, providerCalls = 0;
    const res = { status(value) { status=value; return this; }, json(value) { body=value; return this; } };
    await createRouteHandler({
      getApiKey: () => "test-key",
      lookupRestroom: async () => ({name:"Membership business",latitude:36,longitude:-80,
        accessType:"MEMBERSHIP_REQUIRED",feeRequired:false,requiresPurchase:false}),
      fetchImpl: async (_, request) => {
        providerCalls++;
        return {ok:true,json:async () => ({routes:[{distanceMeters:10000,
          duration:JSON.parse(request.body).intermediates ? "660s" : "600s",
          polyline:{encodedPolyline:"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}}]})};
      }
    })({body:{origin:{latitude:36,longitude:-79},destination:{latitude:36,longitude:-81},
      restroomId:1,maxDetourMinutes:5,freePublicOnly:freeOnly}},res);
    if (freeOnly) {
      assert.equal(status,409);
      assert.equal(body.code,"RESTROOM_NOT_ELIGIBLE");
      assert.equal(providerCalls,0);
    } else {
      assert.equal(status,200);
      assert.equal(body.stop.id,1);
      assert.equal(body.extraDrivingSeconds,60);
    }
  });
}
