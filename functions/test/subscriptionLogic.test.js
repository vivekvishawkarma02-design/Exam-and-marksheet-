const test = require("node:test");
const assert = require("node:assert/strict");
const {
  verifyPurchaseCore,
  handleRtdnNotificationCore,
  hashPurchaseToken,
  DomainFunctionError,
} = require("../lib/subscriptionLogic");

function createInMemoryStore({ owners = new Set(), locks = new Map(), orgSubs = new Map() } = {}) {
  return {
    owners,
    locks,
    orgSubs,
    async isActiveOrgOwner(orgId, uid) {
      return owners.has(`${orgId}:${uid}`);
    },
    async getPurchaseLock(tokenHash) {
      return locks.get(tokenHash) || null;
    },
    async savePurchaseLockAndOrgSubscription(params) {
      const existing = locks.get(params.tokenHash);
      if (existing && existing.orgId !== params.orgId) {
        return { conflictOrgId: existing.orgId };
      }
      if (params.linkedTokenHash) {
        const linked = locks.get(params.linkedTokenHash);
        if (linked && linked.orgId !== params.orgId) {
          return { conflictOrgId: linked.orgId };
        }
      }
      locks.set(params.tokenHash, {
        orgId: params.orgId,
        productId: params.productId,
        plan: params.subscription.plan,
        ownerUid: params.ownerUid,
        linkedAtMs: existing ? existing.linkedAtMs : params.subscription.updatedAtMs,
        updatedAtMs: params.subscription.updatedAtMs,
      });
      orgSubs.set(params.orgId, params.subscription);
      return {};
    },
    async updateOrgSubscriptionOnly(orgId, subscription) {
      orgSubs.set(orgId, subscription);
    },
  };
}

function makePlaySub({
  state = "SUBSCRIPTION_STATE_ACTIVE",
  ack = "ACKNOWLEDGEMENT_STATE_PENDING",
  basePlanId = "yearly",
  autoRenewEnabled = true,
  expiryIso = "2027-01-01T00:00:00Z",
  obfuscatedOrgId = "org-1",
  linkedPurchaseToken = undefined,
} = {}) {
  return {
    subscriptionState: state,
    acknowledgementState: ack,
    linkedPurchaseToken,
    externalAccountIdentifiers: obfuscatedOrgId
      ? { obfuscatedExternalAccountId: obfuscatedOrgId }
      : undefined,
    lineItems: [
      {
        productId: "school_ad_free",
        expiryTime: expiryIso,
        autoRenewingPlan: { autoRenewEnabled },
        offerDetails: { basePlanId },
      },
    ],
  };
}

test("verifyPurchase: active yearly purchase verifies, acknowledges, and never stores raw token", async () => {
  const store = createInMemoryStore({ owners: new Set(["org-1:owner-1"]) });
  const ackCalls = [];
  const playApi = {
    async getSubscriptionV2() {
      return makePlaySub({ state: "SUBSCRIPTION_STATE_ACTIVE", basePlanId: "yearly" });
    },
    async acknowledgeSubscription(pkg, productId, token) {
      ackCalls.push({ pkg, productId, token });
    },
  };

  const nowMs = Date.parse("2026-06-01T00:00:00Z");
  const rawToken = "secret-raw-play-token-12345";
  const res = await verifyPurchaseCore(
    { store, playApi },
    {
      callerUid: "owner-1",
      orgId: "org-1",
      purchaseToken: rawToken,
      productId: "school_ad_free",
      packageName: "com.vivek.schoolreportcard",
      nowMs,
    }
  );

  assert.equal(res.acknowledged, true);
  assert.equal(ackCalls.length, 1);
  assert.equal(res.subscription.status, "active");
  assert.equal(res.subscription.plan, "yearly");
  assert.equal(res.subscription.autoRenewing, true);
  assert.equal(res.subscription.purchaseTokenHash, hashPurchaseToken(rawToken));
  assert.notEqual(res.subscription.purchaseTokenHash, rawToken);
  assert.equal(JSON.stringify(store.orgSubs.get("org-1")).includes(rawToken), false);
});

test("verifyPurchase: non-owner caller is strictly rejected", async () => {
  const store = createInMemoryStore({ owners: new Set(["org-1:owner-1"]) });
  const playApi = {
    async getSubscriptionV2() {
      throw new Error("Play API must not be called for non-owner");
    },
    async acknowledgeSubscription() {},
  };

  await assert.rejects(
    () =>
      verifyPurchaseCore(
        { store, playApi },
        {
          callerUid: "teacher-2",
          orgId: "org-1",
          purchaseToken: "tok-1",
          packageName: "com.vivek.schoolreportcard",
        }
      ),
    (err) => err instanceof DomainFunctionError && err.code === "permission-denied"
  );
});

test("verifyPurchase: prevents double-claim of one purchase token across two organisations", async () => {
  const store = createInMemoryStore({
    owners: new Set(["org-1:owner-1", "org-2:owner-2"]),
  });
  const playApi = {
    async getSubscriptionV2() {
      return makePlaySub({ obfuscatedOrgId: undefined, ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" });
    },
    async acknowledgeSubscription() {},
  };

  await verifyPurchaseCore(
    { store, playApi },
    {
      callerUid: "owner-1",
      orgId: "org-1",
      purchaseToken: "shared-token",
      packageName: "com.vivek.schoolreportcard",
    }
  );

  await assert.rejects(
    () =>
      verifyPurchaseCore(
        { store, playApi },
        {
          callerUid: "owner-2",
          orgId: "org-2",
          purchaseToken: "shared-token",
          packageName: "com.vivek.schoolreportcard",
        }
      ),
    (err) => err instanceof DomainFunctionError && err.code === "already-exists"
  );
});

test("verifyPurchase: rejects obfuscatedExternalAccountId mismatch", async () => {
  const store = createInMemoryStore({ owners: new Set(["org-2:owner-2"]) });
  const playApi = {
    async getSubscriptionV2() {
      return makePlaySub({ obfuscatedOrgId: "org-1" });
    },
    async acknowledgeSubscription() {},
  };

  await assert.rejects(
    () =>
      verifyPurchaseCore(
        { store, playApi },
        {
          callerUid: "owner-2",
          orgId: "org-2",
          purchaseToken: "tok-mismatch",
          packageName: "com.vivek.schoolreportcard",
        }
      ),
    (err) => err instanceof DomainFunctionError && err.code === "permission-denied"
  );
});

test("RTDN lifecycle: renewal, cancellation (valid until date), grace, on_hold, expiry, and refund/revocation", async () => {
  const token = "tok-lifecycle-1";
  const tokenHash = hashPurchaseToken(token);
  const store = createInMemoryStore({
    owners: new Set(["org-1:owner-1"]),
    locks: new Map([
      [
        tokenHash,
        {
          orgId: "org-1",
          productId: "school_ad_free",
          plan: "monthly",
          ownerUid: "owner-1",
          linkedAtMs: 1000,
          updatedAtMs: 1000,
        },
      ],
    ]),
  });

  const nowMs = Date.parse("2026-06-01T00:00:00Z");
  let nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_ACTIVE",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
    autoRenewEnabled: true,
    expiryIso: "2026-07-01T00:00:00Z",
  });
  const playApi = {
    async getSubscriptionV2() {
      return nextResponse;
    },
    async acknowledgeSubscription() {},
  };

  // 1. Renewal (type 2)
  let r = await handleRtdnNotificationCore(
    { store, playApi },
    {
      packageName: "com.vivek.schoolreportcard",
      subscriptionNotification: {
        notificationType: 2,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(r.handled, true);
  assert.equal(store.orgSubs.get("org-1").status, "active");
  assert.equal(store.orgSubs.get("org-1").autoRenewing, true);

  // 2. Cancellation before period end (type 3) -> cancelled_active until expiry
  nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_CANCELED",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
    autoRenewEnabled: false,
    expiryIso: "2026-07-01T00:00:00Z",
  });
  r = await handleRtdnNotificationCore(
    { store, playApi },
    {
      subscriptionNotification: {
        notificationType: 3,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(store.orgSubs.get("org-1").status, "cancelled_active");
  assert.equal(store.orgSubs.get("org-1").autoRenewing, false);

  // 3. Grace period (type 6) -> grace
  nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
  });
  await handleRtdnNotificationCore(
    { store, playApi },
    {
      subscriptionNotification: {
        notificationType: 6,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(store.orgSubs.get("org-1").status, "grace");

  // 4. Account hold (type 5) -> on_hold
  nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_ON_HOLD",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
  });
  await handleRtdnNotificationCore(
    { store, playApi },
    {
      subscriptionNotification: {
        notificationType: 5,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(store.orgSubs.get("org-1").status, "on_hold");

  // 5. Expiry (type 13) -> expired
  nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_EXPIRED",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
    autoRenewEnabled: false,
    expiryIso: "2026-05-01T00:00:00Z",
  });
  await handleRtdnNotificationCore(
    { store, playApi },
    {
      subscriptionNotification: {
        notificationType: 13,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(store.orgSubs.get("org-1").status, "expired");

  // 6. Refund / revocation (type 12) -> revoked immediately
  nextResponse = makePlaySub({
    state: "SUBSCRIPTION_STATE_REVOKED",
    ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    basePlanId: "monthly",
    autoRenewEnabled: false,
  });
  await handleRtdnNotificationCore(
    { store, playApi },
    {
      subscriptionNotification: {
        notificationType: 12,
        purchaseToken: token,
        subscriptionId: "school_ad_free",
      },
    },
    "com.vivek.schoolreportcard",
    nowMs
  );
  assert.equal(store.orgSubs.get("org-1").status, "revoked");
});
