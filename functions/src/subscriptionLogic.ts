import { createHash } from "crypto";

export const SUBSCRIPTION_PRODUCT_ID = "school_ad_free";
export const ALLOWED_BASE_PLANS = new Set(["monthly", "yearly"]);

export type OrgSubscriptionStatusWire =
  | "none"
  | "active"
  | "grace"
  | "on_hold"
  | "cancelled_active"
  | "expired"
  | "revoked";

export interface OrgSubscriptionRecord {
  status: OrgSubscriptionStatusWire;
  plan: string;
  expiresAtMs: number | null;
  autoRenewing: boolean;
  updatedAtMs: number;
  purchaseTokenHash: string;
}

export interface PlaySubscriptionLineItem {
  productId?: string;
  expiryTime?: string;
  autoRenewingPlan?: {
    autoRenewEnabled?: boolean;
  };
  offerDetails?: {
    basePlanId?: string;
    offerId?: string;
  };
}

export interface PlaySubscriptionV2Response {
  kind?: string;
  subscriptionState?: string;
  acknowledgementState?: string;
  latestOrderId?: string;
  linkedPurchaseToken?: string;
  externalAccountIdentifiers?: {
    obfuscatedExternalAccountId?: string;
    obfuscatedExternalProfileId?: string;
  };
  lineItems?: PlaySubscriptionLineItem[];
  canceledStateContext?: Record<string, unknown>;
}

export interface PlayDeveloperApiClient {
  getSubscriptionV2(packageName: string, purchaseToken: string): Promise<PlaySubscriptionV2Response>;
  acknowledgeSubscription(packageName: string, productId: string, purchaseToken: string): Promise<void>;
}

export interface PurchaseLockRecord {
  orgId: string;
  productId: string;
  plan: string;
  ownerUid: string;
  linkedAtMs: number;
  updatedAtMs: number;
}

export interface SubscriptionDataStore {
  isActiveOrgOwner(orgId: string, uid: string): Promise<boolean>;
  getPurchaseLock(tokenHash: string): Promise<PurchaseLockRecord | null>;
  savePurchaseLockAndOrgSubscription(params: {
    tokenHash: string;
    linkedTokenHash?: string | null;
    orgId: string;
    ownerUid: string;
    productId: string;
    subscription: OrgSubscriptionRecord;
  }): Promise<{ conflictOrgId?: string }>;
  updateOrgSubscriptionOnly(orgId: string, subscription: OrgSubscriptionRecord): Promise<void>;
}

export class DomainFunctionError extends Error {
  constructor(
    public readonly code:
      | "unauthenticated"
      | "permission-denied"
      | "invalid-argument"
      | "already-exists"
      | "failed-precondition"
      | "internal",
    message: string
  ) {
    super(message);
    this.name = "DomainFunctionError";
  }
}

export function hashPurchaseToken(purchaseToken: string): string {
  return createHash("sha256").update(purchaseToken.trim()).digest("hex");
}

function parseIsoMs(iso?: string): number | null {
  if (!iso || typeof iso !== "string") return null;
  const parsed = Date.parse(iso);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

export function extractSchoolSubscriptionLineItem(
  playSub: PlaySubscriptionV2Response
): PlaySubscriptionLineItem | null {
  const items = Array.isArray(playSub.lineItems) ? playSub.lineItems : [];
  return items.find((item) => item.productId === SUBSCRIPTION_PRODUCT_ID) ?? null;
}

export function mapPlaySubscriptionV2ToOrgSubscription(
  playSub: PlaySubscriptionV2Response,
  tokenHash: string,
  nowMs: number,
  fallbackPlan = ""
): OrgSubscriptionRecord {
  const lineItem = extractSchoolSubscriptionLineItem(playSub);
  const rawPlan = (lineItem?.offerDetails?.basePlanId || fallbackPlan || "").trim().toLowerCase();
  const plan = ALLOWED_BASE_PLANS.has(rawPlan) ? rawPlan : "";
  const expiresAtMs = parseIsoMs(lineItem?.expiryTime);
  const autoRenewing = Boolean(lineItem?.autoRenewingPlan?.autoRenewEnabled);
  const state = (playSub.subscriptionState || "").trim().toUpperCase();

  let status: OrgSubscriptionStatusWire;
  switch (state) {
    case "SUBSCRIPTION_STATE_ACTIVE":
      if (autoRenewing) {
        status = "active";
      } else if (expiresAtMs !== null && expiresAtMs > nowMs) {
        status = "cancelled_active";
      } else {
        status = expiresAtMs !== null ? "expired" : "active";
      }
      break;
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
      status = "grace";
      break;
    case "SUBSCRIPTION_STATE_ON_HOLD":
      status = "on_hold";
      break;
    case "SUBSCRIPTION_STATE_PAUSED":
      status = expiresAtMs !== null && expiresAtMs <= nowMs ? "expired" : "on_hold";
      break;
    case "SUBSCRIPTION_STATE_CANCELED":
      if (expiresAtMs !== null && expiresAtMs > nowMs) {
        status = "cancelled_active";
      } else {
        status = "expired";
      }
      break;
    case "SUBSCRIPTION_STATE_REVOKED":
      status = "revoked";
      break;
    case "SUBSCRIPTION_STATE_EXPIRED":
      status = "expired";
      break;
    case "SUBSCRIPTION_STATE_PENDING":
    default:
      status = "none";
      break;
  }

  return {
    status,
    plan,
    expiresAtMs,
    autoRenewing,
    updatedAtMs: nowMs,
    purchaseTokenHash: tokenHash,
  };
}

export function shouldAcknowledgePlaySubscription(
  playSub: PlaySubscriptionV2Response,
  mappedStatus: OrgSubscriptionStatusWire
): boolean {
  const ack = (playSub.acknowledgementState || "").trim().toUpperCase();
  if (ack === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED") return false;
  return (
    mappedStatus === "active" ||
    mappedStatus === "grace" ||
    mappedStatus === "on_hold" ||
    mappedStatus === "cancelled_active"
  );
}

export interface VerifyPurchaseInput {
  callerUid: string;
  orgId: string;
  purchaseToken: string;
  productId?: string;
  basePlanId?: string;
  packageName: string;
  nowMs?: number;
}

export async function verifyPurchaseCore(
  deps: { store: SubscriptionDataStore; playApi: PlayDeveloperApiClient },
  input: VerifyPurchaseInput
): Promise<{ subscription: OrgSubscriptionRecord; acknowledged: boolean }> {
  const callerUid = (input.callerUid || "").trim();
  const orgId = (input.orgId || "").trim();
  const purchaseToken = (input.purchaseToken || "").trim();
  const requestedProduct = (input.productId || SUBSCRIPTION_PRODUCT_ID).trim();
  const nowMs = input.nowMs ?? Date.now();

  if (!callerUid) {
    throw new DomainFunctionError("unauthenticated", "Sign in before verifying a purchase.");
  }
  if (!orgId || !purchaseToken) {
    throw new DomainFunctionError("invalid-argument", "Organisation ID and purchase token are required.");
  }
  if (requestedProduct !== SUBSCRIPTION_PRODUCT_ID) {
    throw new DomainFunctionError("invalid-argument", "Unsupported subscription product.");
  }

  // 1. Strictly enforce Organisation OWNER role
  const isOwner = await deps.store.isActiveOrgOwner(orgId, callerUid);
  if (!isOwner) {
    throw new DomainFunctionError(
      "permission-denied",
      "Only the active school organisation owner can verify or claim a school subscription."
    );
  }

  // 2. Check double-claim lock BEFORE calling Play API
  const tokenHash = hashPurchaseToken(purchaseToken);
  const existingLock = await deps.store.getPurchaseLock(tokenHash);
  if (existingLock && existingLock.orgId !== orgId) {
    throw new DomainFunctionError(
      "already-exists",
      "This Google Play subscription is already claimed by another organisation."
    );
  }

  // 3. Query Google Play Developer API v3 (subscriptionsv2.get)
  const playSub = await deps.playApi.getSubscriptionV2(input.packageName, purchaseToken);
  const lineItem = extractSchoolSubscriptionLineItem(playSub);
  if (!lineItem) {
    throw new DomainFunctionError(
      "invalid-argument",
      "Purchase does not contain the school_ad_free subscription product."
    );
  }

  // 4. Verify obfuscated account id matches this organisation (if set by BillingClient)
  const obfuscatedOrgId = (playSub.externalAccountIdentifiers?.obfuscatedExternalAccountId || "").trim();
  if (obfuscatedOrgId && obfuscatedOrgId !== orgId) {
    throw new DomainFunctionError(
      "permission-denied",
      "This purchase was created for another organisation and cannot be claimed here."
    );
  }

  // 5. Check linkedPurchaseToken lock too (for plan upgrades / resubscriptions)
  const linkedTokenHash = playSub.linkedPurchaseToken
    ? hashPurchaseToken(playSub.linkedPurchaseToken)
    : null;
  if (linkedTokenHash) {
    const linkedLock = await deps.store.getPurchaseLock(linkedTokenHash);
    if (linkedLock && linkedLock.orgId !== orgId) {
      throw new DomainFunctionError(
        "already-exists",
        "The original linked subscription belongs to another organisation."
      );
    }
  }

  // 6. Map Google Play v2 status -> Firestore subscription snapshot (NO raw token stored!)
  const mapped = mapPlaySubscriptionV2ToOrgSubscription(
    playSub,
    tokenHash,
    nowMs,
    input.basePlanId || ""
  );

  // 7. Atomically claim lock and write organisations/{orgId}.subscription
  const writeResult = await deps.store.savePurchaseLockAndOrgSubscription({
    tokenHash,
    linkedTokenHash,
    orgId,
    ownerUid: callerUid,
    productId: SUBSCRIPTION_PRODUCT_ID,
    subscription: mapped,
  });
  if (writeResult.conflictOrgId && writeResult.conflictOrgId !== orgId) {
    throw new DomainFunctionError(
      "already-exists",
      "This Google Play subscription is already claimed by another organisation."
    );
  }

  // 8. Acknowledge purchase on Google Play if unacknowledged so Play does not auto-refund after 3 days
  let acknowledged = false;
  if (shouldAcknowledgePlaySubscription(playSub, mapped.status)) {
    await deps.playApi.acknowledgeSubscription(input.packageName, SUBSCRIPTION_PRODUCT_ID, purchaseToken);
    acknowledged = true;
  }

  return { subscription: mapped, acknowledged };
}

export interface PlayRtdnPayload {
  version?: string;
  packageName?: string;
  eventTimeMillis?: string;
  testNotification?: { version?: string };
  subscriptionNotification?: {
    version?: string;
    notificationType?: number;
    purchaseToken?: string;
    subscriptionId?: string;
  };
}

/**
 * Handles Google Play Real-Time Developer Notifications (RTDN) via Pub/Sub.
 * Notification types (official Google Play enum):
 *  1=RECOVERED, 2=RENEWED, 3=CANCELED, 4=PURCHASED, 5=ON_HOLD, 6=IN_GRACE_PERIOD,
 *  7=RESTARTED, 8=PRICE_CHANGE_CONFIRMED, 9=DEFERRED, 10=PAUSED, 11=PAUSE_SCHEDULE_CHANGED,
 *  12=REVOKED (refund/revocation), 13=EXPIRED, 20=PENDING_PURCHASE_CANCELED
 */
export async function handleRtdnNotificationCore(
  deps: { store: SubscriptionDataStore; playApi: PlayDeveloperApiClient },
  payload: PlayRtdnPayload,
  defaultPackageName: string,
  nowMs = Date.now()
): Promise<{
  handled: boolean;
  reason: string;
  orgId?: string;
  subscription?: OrgSubscriptionRecord;
  acknowledged?: boolean;
}> {
  if (payload.testNotification) {
    return { handled: false, reason: "test_notification" };
  }
  const subNotif = payload.subscriptionNotification;
  if (!subNotif || !subNotif.purchaseToken) {
    return { handled: false, reason: "missing_subscription_notification" };
  }
  const subId = (subNotif.subscriptionId || "").trim();
  if (subId && subId !== SUBSCRIPTION_PRODUCT_ID) {
    return { handled: false, reason: "unrelated_product" };
  }

  const packageName = (payload.packageName || defaultPackageName).trim();
  const purchaseToken = subNotif.purchaseToken.trim();
  const tokenHash = hashPurchaseToken(purchaseToken);

  // Fetch authoritative state from Google Play Developer API v3
  const playSub = await deps.playApi.getSubscriptionV2(packageName, purchaseToken);
  const lineItem = extractSchoolSubscriptionLineItem(playSub);
  if (!lineItem) {
    return { handled: false, reason: "product_line_item_missing" };
  }

  // Locate owning organisation: prefer existing purchase lock, then linkedPurchaseToken lock, then obfuscatedExternalAccountId
  const existingLock = await deps.store.getPurchaseLock(tokenHash);
  const linkedTokenHash = playSub.linkedPurchaseToken
    ? hashPurchaseToken(playSub.linkedPurchaseToken)
    : null;
  const linkedLock = linkedTokenHash ? await deps.store.getPurchaseLock(linkedTokenHash) : null;
  const obfuscatedOrgId = (playSub.externalAccountIdentifiers?.obfuscatedExternalAccountId || "").trim();

  const orgId = existingLock?.orgId || linkedLock?.orgId || obfuscatedOrgId;
  if (!orgId) {
    return { handled: false, reason: "unlinked_organisation" };
  }

  // Never allow an RTDN with a mismatched obfuscatedExternalAccountId to overwrite another org
  if (existingLock && obfuscatedOrgId && existingLock.orgId !== obfuscatedOrgId) {
    return { handled: false, reason: "org_mismatch" };
  }

  const mapped = mapPlaySubscriptionV2ToOrgSubscription(
    playSub,
    tokenHash,
    nowMs,
    existingLock?.plan || linkedLock?.plan || ""
  );

  // Explicit override if RTDN type is REVOKED (12) — refund/revocation removes ad-free benefit immediately
  if (subNotif.notificationType === 12) {
    mapped.status = "revoked";
    mapped.autoRenewing = false;
  }

  if (existingLock) {
    await deps.store.updateOrgSubscriptionOnly(orgId, mapped);
  } else {
    const saveRes = await deps.store.savePurchaseLockAndOrgSubscription({
      tokenHash,
      linkedTokenHash,
      orgId,
      ownerUid: linkedLock?.ownerUid || "play_rtdn",
      productId: SUBSCRIPTION_PRODUCT_ID,
      subscription: mapped,
    });
    if (saveRes.conflictOrgId && saveRes.conflictOrgId !== orgId) {
      return { handled: false, reason: "lock_conflict" };
    }
  }

  let acknowledged = false;
  if (shouldAcknowledgePlaySubscription(playSub, mapped.status)) {
    await deps.playApi.acknowledgeSubscription(packageName, SUBSCRIPTION_PRODUCT_ID, purchaseToken);
    acknowledged = true;
  }

  return {
    handled: true,
    reason: "updated",
    orgId,
    subscription: mapped,
    acknowledged,
  };
}
