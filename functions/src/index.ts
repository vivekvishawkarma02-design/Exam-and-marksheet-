import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import * as logger from "firebase-functions/logger";
import {
  AdMobSsvStore,
  createRewardedAdNonceCore,
  processAdMobSsvCallbackCore,
  RewardedNonceRecord,
  ServerMemberRewardState,
} from "./admobSsvLogic";
import { fetchGoogleAdMobVerifierKeys, GooglePlayDeveloperApiClient } from "./playApiClient";
import {
  DomainFunctionError,
  handleRtdnNotificationCore,
  OrgSubscriptionRecord,
  PlayRtdnPayload,
  PurchaseLockRecord,
  SubscriptionDataStore,
  verifyPurchaseCore,
} from "./subscriptionLogic";

admin.initializeApp();
const db = admin.firestore();

const REGION = "asia-south1";
const RTDN_TOPIC = "play-billing-rtdn";
const DEFAULT_PACKAGE_NAME =
  process.env.ANDROID_PACKAGE_NAME || "com.vivek.schoolreportcard";

setGlobalOptions({
  region: REGION,
  maxInstances: 10,
});

function toFirestoreSubscriptionPayload(sub: OrgSubscriptionRecord): Record<string, unknown> {
  return {
    status: sub.status,
    plan: sub.plan,
    expiresAt: sub.expiresAtMs !== null ? admin.firestore.Timestamp.fromMillis(sub.expiresAtMs) : null,
    autoRenewing: sub.autoRenewing,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    purchaseTokenHash: sub.purchaseTokenHash,
  };
}

function createFirestoreSubscriptionStore(): SubscriptionDataStore {
  return {
    async isActiveOrgOwner(orgId: string, uid: string): Promise<boolean> {
      const snap = await db.doc(`organisations/${orgId}/members/${uid}`).get();
      if (!snap.exists) return false;
      const data = snap.data() || {};
      return data.orgRole === "OWNER" && data.status === "ACTIVE";
    },

    async getPurchaseLock(tokenHash: string): Promise<PurchaseLockRecord | null> {
      const snap = await db.doc(`subscriptionPurchaseLocks/${tokenHash}`).get();
      if (!snap.exists) return null;
      const data = snap.data() || {};
      return {
        orgId: String(data.orgId || ""),
        productId: String(data.productId || ""),
        plan: String(data.plan || ""),
        ownerUid: String(data.ownerUid || ""),
        linkedAtMs: Number(data.linkedAtMs || 0),
        updatedAtMs: Number(data.updatedAtMs || 0),
      };
    },

    async savePurchaseLockAndOrgSubscription(params): Promise<{ conflictOrgId?: string }> {
      const lockRef = db.doc(`subscriptionPurchaseLocks/${params.tokenHash}`);
      const linkedRef = params.linkedTokenHash
        ? db.doc(`subscriptionPurchaseLocks/${params.linkedTokenHash}`)
        : null;
      const orgRef = db.doc(`organisations/${params.orgId}`);

      return db.runTransaction(async (tx) => {
        const existingLock = await tx.get(lockRef);
        if (existingLock.exists) {
          const lockedOrgId = String(existingLock.data()?.orgId || "");
          if (lockedOrgId && lockedOrgId !== params.orgId) {
            return { conflictOrgId: lockedOrgId };
          }
        }
        if (linkedRef) {
          const existingLinked = await tx.get(linkedRef);
          if (existingLinked.exists) {
            const linkedOrgId = String(existingLinked.data()?.orgId || "");
            if (linkedOrgId && linkedOrgId !== params.orgId) {
              return { conflictOrgId: linkedOrgId };
            }
          }
        }

        const nowMs = params.subscription.updatedAtMs;
        tx.set(
          lockRef,
          {
            orgId: params.orgId,
            productId: params.productId,
            plan: params.subscription.plan,
            ownerUid: params.ownerUid,
            linkedAtMs: existingLock.exists ? existingLock.data()?.linkedAtMs ?? nowMs : nowMs,
            updatedAtMs: nowMs,
          },
          { merge: true }
        );
        tx.set(
          orgRef,
          { subscription: toFirestoreSubscriptionPayload(params.subscription) },
          { merge: true }
        );
        return {};
      });
    },

    async updateOrgSubscriptionOnly(orgId: string, subscription: OrgSubscriptionRecord): Promise<void> {
      await db
        .doc(`organisations/${orgId}`)
        .set({ subscription: toFirestoreSubscriptionPayload(subscription) }, { merge: true });
    },
  };
}

function createFirestoreAdMobSsvStore(): AdMobSsvStore {
  return {
    async isActiveOrgMember(orgId: string, uid: string): Promise<boolean> {
      const snap = await db.doc(`organisations/${orgId}/members/${uid}`).get();
      if (!snap.exists) return false;
      const data = snap.data() || {};
      return data.status === "ACTIVE";
    },

    async saveNonce(record: RewardedNonceRecord): Promise<void> {
      await db.doc(`rewardedAdNonces/${record.nonce}`).set(record);
    },

    async consumeNonceAndApplyReward(params) {
      const nonceRef = db.doc(`rewardedAdNonces/${params.nonce}`);
      const txRef = db.doc(`rewardedAdTransactions/${params.transactionIdHash}`);

      return db.runTransaction(async (tx) => {
        const existingTx = await tx.get(txRef);
        if (existingTx.exists) {
          return { status: "duplicate_transaction" as const };
        }

        const nonceSnap = await tx.get(nonceRef);
        if (!nonceSnap.exists) {
          return { status: "invalid_or_expired_nonce" as const };
        }
        const nonceData = nonceSnap.data() as RewardedNonceRecord;
        if (nonceData.used || params.nowMs > nonceData.expiresAtMs) {
          return { status: "invalid_or_expired_nonce" as const };
        }

        const memberRef = db.doc(`organisations/${nonceData.orgId}/members/${nonceData.uid}`);
        const memberSnap = await tx.get(memberRef);
        if (!memberSnap.exists || memberSnap.data()?.status !== "ACTIVE") {
          return { status: "invalid_or_expired_nonce" as const };
        }

        const memberData = memberSnap.data() || {};
        const adFreeUntilTs = memberData.adFreeUntil as admin.firestore.Timestamp | undefined;
        const win = (memberData.adsWatchedWindow || {}) as Record<string, unknown>;
        const winStartTs = win.windowStartAt as admin.firestore.Timestamp | undefined;
        const lastRewTs = win.lastRewardedAt as admin.firestore.Timestamp | undefined;

        const current: ServerMemberRewardState = {
          adFreeUntilMs: adFreeUntilTs ? adFreeUntilTs.toMillis() : 0,
          windowStartAtMs: winStartTs ? winStartTs.toMillis() : 0,
          countInWindow: Number(win.countInWindow || 0),
          dailyDateKey: String(win.dailyDateKey || ""),
          dailyCount: Number(win.dailyCount || 0),
          totalCount: Number(win.totalCount || 0),
          lastRewardedAtMs: lastRewTs ? lastRewTs.toMillis() : 0,
        };

        const next = params.computeNextState(current, params.nowMs);
        if (!next) {
          return { status: "cap_reached" as const };
        }

        tx.update(nonceRef, { used: true, usedAtMs: params.nowMs });
        tx.set(txRef, {
          orgId: nonceData.orgId,
          uid: nonceData.uid,
          recordedAtMs: params.nowMs,
        });
        tx.set(
          memberRef,
          {
            adFreeUntil: admin.firestore.Timestamp.fromMillis(next.adFreeUntilMs),
            adsWatchedWindow: {
              windowStartAt: admin.firestore.Timestamp.fromMillis(next.windowStartAtMs),
              countInWindow: next.countInWindow,
              dailyDateKey: next.dailyDateKey,
              dailyCount: next.dailyCount,
              totalCount: next.totalCount,
              lastRewardedAt: admin.firestore.FieldValue.serverTimestamp(),
              verifiedByServer: true,
            },
          },
          { merge: true }
        );
        return { status: "applied" as const, updated: next };
      });
    },
  };
}

function mapErrorToHttps(err: unknown): HttpsError {
  if (err instanceof HttpsError) return err;
  if (err instanceof DomainFunctionError) {
    return new HttpsError(err.code, err.message);
  }
  const message = err instanceof Error ? err.message : "Unexpected server error.";
  return new HttpsError("internal", message);
}

/**
 * Callable Function (asia-south1): verifyPurchase
 * Verifies a Google Play `school_ad_free` purchase token for the caller's organisation.
 * - Verifies caller is active OWNER of `orgId`.
 * - Calls Google Play Developer API v3 (`subscriptionsv2.get`).
 * - Prevents double-claiming one purchase across two organisations.
 * - Acknowledges purchase on Play and writes `organisations/{orgId}.subscription` via Admin SDK.
 */
export const verifyPurchase = onCall({ region: REGION }, async (request) => {
  try {
    const callerUid = request.auth?.uid || "";
    const data = (request.data || {}) as Record<string, unknown>;
    const orgId = typeof data.orgId === "string" ? data.orgId : "";
    const purchaseToken = typeof data.purchaseToken === "string" ? data.purchaseToken : "";
    const productId = typeof data.productId === "string" ? data.productId : undefined;
    const basePlanId = typeof data.basePlanId === "string" ? data.basePlanId : undefined;

    const playApi = new GooglePlayDeveloperApiClient(process.env.PLAY_SERVICE_ACCOUNT_JSON);
    const store = createFirestoreSubscriptionStore();

    const result = await verifyPurchaseCore(
      { store, playApi },
      {
        callerUid,
        orgId,
        purchaseToken,
        productId,
        basePlanId,
        packageName: DEFAULT_PACKAGE_NAME,
      }
    );

    return {
      status: result.subscription.status,
      plan: result.subscription.plan,
      expiresAtMs: result.subscription.expiresAtMs,
      autoRenewing: result.subscription.autoRenewing,
      updatedAtMs: result.subscription.updatedAtMs,
      purchaseTokenHash: result.subscription.purchaseTokenHash,
      acknowledged: result.acknowledged,
    };
  } catch (err) {
    logger.error("verifyPurchase failed", {
      code: err instanceof DomainFunctionError ? err.code : "internal",
      message: err instanceof Error ? err.message : String(err),
    });
    throw mapErrorToHttps(err);
  }
});

/**
 * Pub/Sub-triggered Function (asia-south1): handlePlayRtdn
 * Receives Google Play Real-Time Developer Notifications on topic `play-billing-rtdn`
 * and updates `organisations/{orgId}.subscription` for renewals, cancellations, grace period,
 * account hold, expiry, refunds, and revocations.
 */
export const handlePlayRtdn = onMessagePublished(
  { topic: RTDN_TOPIC, region: REGION },
  async (event) => {
    try {
      const rawData = event.data.message.data;
      const decoded = Buffer.from(rawData, "base64").toString("utf8");
      const payload = JSON.parse(decoded) as PlayRtdnPayload;

      const playApi = new GooglePlayDeveloperApiClient(process.env.PLAY_SERVICE_ACCOUNT_JSON);
      const store = createFirestoreSubscriptionStore();
      const outcome = await handleRtdnNotificationCore(
        { store, playApi },
        payload,
        DEFAULT_PACKAGE_NAME
      );
      logger.info("handlePlayRtdn processed", {
        handled: outcome.handled,
        reason: outcome.reason,
        orgId: outcome.orgId || "",
        status: outcome.subscription?.status || "",
      });
    } catch (err) {
      logger.error("handlePlayRtdn failed", {
        message: err instanceof Error ? err.message : String(err),
      });
      throw err;
    }
  }
);

/**
 * Callable Function (asia-south1): createRewardedAdNonce
 * Issues an opaque, zero-PII single-use nonce for AdMob Rewarded SSV `custom_data`.
 */
export const createRewardedAdNonce = onCall({ region: REGION }, async (request) => {
  try {
    const callerUid = request.auth?.uid || "";
    const data = (request.data || {}) as Record<string, unknown>;
    const orgId = typeof data.orgId === "string" ? data.orgId : "";
    const store = createFirestoreAdMobSsvStore();
    return await createRewardedAdNonceCore(store, { callerUid, orgId });
  } catch (err) {
    throw mapErrorToHttps(err);
  }
});

/**
 * HTTPS Endpoint (asia-south1): admobRewardedSsv
 * AdMob Server-Side Verification (SSV) callback endpoint.
 * Verifies Google's ECDSA-SHA256 signature, deduplicates transaction_id, and applies
 * the 1-4 rewarded ad-free ladder (max 8/day) authoritatively on the member document.
 */
export const admobRewardedSsv = onRequest({ region: REGION }, async (req, res) => {
  try {
    const rawUrl = req.originalUrl || req.url || "";
    const qIdx = rawUrl.indexOf("?");
    const rawQuery = qIdx >= 0 ? rawUrl.slice(qIdx + 1) : "";

    const store = createFirestoreAdMobSsvStore();
    const outcome = await processAdMobSsvCallbackCore(
      {
        store,
        getPublicKeys: fetchGoogleAdMobVerifierKeys,
      },
      rawQuery
    );
    res.status(outcome.httpStatus).json({ ok: outcome.httpStatus === 200, outcome: outcome.outcome });
  } catch (err) {
    logger.error("admobRewardedSsv failed", {
      message: err instanceof Error ? err.message : String(err),
    });
    res.status(500).json({ ok: false, outcome: "internal_error" });
  }
});
