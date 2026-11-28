const test = require("node:test");
const assert = require("node:assert/strict");
const { generateKeyPairSync, createSign } = require("crypto");
const {
  createRewardedAdNonceCore,
  processAdMobSsvCallbackCore,
  REWARDED_HOUR_MS,
} = require("../lib/admobSsvLogic");

function createInMemorySsvStore({ activeMembers = new Set() } = {}) {
  const nonces = new Map();
  const transactions = new Set();
  const rewards = new Map();

  return {
    nonces,
    transactions,
    rewards,
    async isActiveOrgMember(orgId, uid) {
      return activeMembers.has(`${orgId}:${uid}`);
    },
    async saveNonce(rec) {
      nonces.set(rec.nonce, { ...rec });
    },
    async consumeNonceAndApplyReward({ nonce, transactionIdHash, nowMs, computeNextState }) {
      if (transactions.has(transactionIdHash)) {
        return { status: "duplicate_transaction" };
      }
      const rec = nonces.get(nonce);
      if (!rec || rec.used || nowMs > rec.expiresAtMs) {
        return { status: "invalid_or_expired_nonce" };
      }
      const key = `${rec.orgId}:${rec.uid}`;
      const cur = rewards.get(key) || {
        adFreeUntilMs: 0,
        windowStartAtMs: 0,
        countInWindow: 0,
        dailyDateKey: "",
        dailyCount: 0,
        totalCount: 0,
        lastRewardedAtMs: 0,
      };
      const next = computeNextState(cur, nowMs);
      if (!next) return { status: "cap_reached" };
      rec.used = true;
      transactions.add(transactionIdHash);
      rewards.set(key, next);
      return { status: "applied", updated: next };
    },
  };
}

function buildSignedAdMobQuery(privateKeyPem, keyId, paramsObj) {
  const baseQuery = Object.entries(paramsObj)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join("&");
  const signer = createSign("SHA256");
  signer.update(baseQuery, "utf8");
  signer.end();
  const sigB64Url = signer
    .sign(privateKeyPem)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
  return `${baseQuery}&signature=${sigB64Url}&key_id=${keyId}`;
}

test("AdMob SSV: zero-PII nonce, valid ECDSA signature advances 1-4 ladder, replay blocked, tampered rejected", async () => {
  const { privateKey, publicKey } = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  const keyId = "3335741209";
  const keysMap = new Map([[keyId, publicKey]]);

  const store = createInMemorySsvStore({
    activeMembers: new Set(["org-school-1:teacher-uid-secret"]),
  });

  const t0 = Date.parse("2026-06-01T10:00:00Z");
  const { nonce: n1 } = await createRewardedAdNonceCore(store, {
    callerUid: "teacher-uid-secret",
    orgId: "org-school-1",
    nowMs: t0,
  });

  // Zero PII check: nonce must never embed UID or orgId
  assert.ok(n1.startsWith("rwd_"));
  assert.equal(n1.includes("teacher-uid-secret"), false);
  assert.equal(n1.includes("org-school-1"), false);

  // Step 1: 1st verified ad -> +2h
  const q1 = buildSignedAdMobQuery(privateKey, keyId, {
    ad_network: "5450213213286189855",
    ad_unit: "1234567890",
    custom_data: n1,
    reward_amount: "1",
    reward_item: "ad_free",
    timestamp: String(t0),
    transaction_id: "tx-001",
  });
  const r1 = await processAdMobSsvCallbackCore(
    { store, getPublicKeys: async () => keysMap },
    q1,
    t0
  );
  assert.equal(r1.httpStatus, 200);
  assert.equal(r1.outcome, "applied");
  assert.equal(r1.updated.adFreeUntilMs, t0 + 2 * REWARDED_HOUR_MS);
  assert.equal(r1.updated.countInWindow, 1);

  // Duplicate transaction_id replay -> 200 duplicate_transaction, count unchanged
  const r1Dup = await processAdMobSsvCallbackCore(
    { store, getPublicKeys: async () => keysMap },
    q1,
    t0 + 1000
  );
  assert.equal(r1Dup.httpStatus, 200);
  assert.equal(r1Dup.outcome, "duplicate_transaction");
  assert.equal(store.rewards.get("org-school-1:teacher-uid-secret").countInWindow, 1);

  // Tampered signature -> 400 invalid_signature
  const tampered = q1.replace("tx-001", "tx-999");
  const rTampered = await processAdMobSsvCallbackCore(
    { store, getPublicKeys: async () => keysMap },
    tampered,
    t0 + 2000
  );
  assert.equal(rTampered.httpStatus, 400);
  assert.equal(rTampered.outcome, "invalid_signature");

  // Steps 2, 3, 4 -> 3h, 6h, 24h from first ad
  const expectedHours = [3, 6, 24];
  for (let i = 0; i < expectedHours.length; i++) {
    const tStep = t0 + (i + 1) * 60_000;
    const { nonce } = await createRewardedAdNonceCore(store, {
      callerUid: "teacher-uid-secret",
      orgId: "org-school-1",
      nowMs: tStep,
    });
    const q = buildSignedAdMobQuery(privateKey, keyId, {
      custom_data: nonce,
      reward_amount: "1",
      reward_item: "ad_free",
      timestamp: String(tStep),
      transaction_id: `tx-00${i + 2}`,
    });
    const r = await processAdMobSsvCallbackCore(
      { store, getPublicKeys: async () => keysMap },
      q,
      tStep
    );
    assert.equal(r.outcome, "applied");
    assert.equal(r.updated.adFreeUntilMs, t0 + expectedHours[i] * REWARDED_HOUR_MS);
    assert.equal(r.updated.countInWindow, i + 2);
  }
});
