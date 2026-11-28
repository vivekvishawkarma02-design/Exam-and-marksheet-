import { createHash, createVerify, randomBytes } from "crypto";
import { DomainFunctionError } from "./subscriptionLogic";

export const REWARDED_HOUR_MS = 3_600_000;
export const REWARDED_WINDOW_MS = 24 * REWARDED_HOUR_MS;
export const REWARDED_MAX_PER_LADDER = 4;
export const REWARDED_MAX_PER_DAY = 8;
export const NONCE_TTL_MS = 30 * 60_000;

export interface RewardedNonceRecord {
  nonce: string;
  orgId: string;
  uid: string;
  createdAtMs: number;
  expiresAtMs: number;
  used: boolean;
}

export interface ServerMemberRewardState {
  adFreeUntilMs: number;
  windowStartAtMs: number;
  countInWindow: number;
  dailyDateKey: string;
  dailyCount: number;
  totalCount: number;
  lastRewardedAtMs: number;
}

export interface AdMobSsvStore {
  isActiveOrgMember(orgId: string, uid: string): Promise<boolean>;
  saveNonce(record: RewardedNonceRecord): Promise<void>;
  consumeNonceAndApplyReward(params: {
    nonce: string;
    transactionIdHash: string;
    nowMs: number;
    computeNextState: (current: ServerMemberRewardState, nowMs: number) => ServerMemberRewardState | null;
  }): Promise<{
    status: "applied" | "duplicate_transaction" | "invalid_or_expired_nonce" | "cap_reached";
    updated?: ServerMemberRewardState;
  }>;
}

export function utcDateKey(nowMs: number): string {
  const d = new Date(Math.max(0, nowMs));
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function ladderTotalHoursForStep(step: number): number {
  switch (step) {
    case 1:
      return 2;
    case 2:
      return 3;
    case 3:
      return 6;
    default:
      return 24;
  }
}

export function computeNextServerRewardState(
  current: ServerMemberRewardState,
  nowMs: number
): ServerMemberRewardState | null {
  if (nowMs <= 0) return null;
  const today = utcDateKey(nowMs);
  const currentDaily = current.dailyDateKey === today ? Math.max(0, current.dailyCount) : 0;
  if (currentDaily >= REWARDED_MAX_PER_DAY) return null;

  const within24h =
    current.windowStartAtMs > 0 &&
    nowMs - current.windowStartAtMs >= 0 &&
    nowMs - current.windowStartAtMs < REWARDED_WINDOW_MS;

  let completedInActiveWindow = 0;
  if (within24h && current.countInWindow > 0) {
    const isAdFree = current.adFreeUntilMs > nowMs;
    if (current.countInWindow >= REWARDED_MAX_PER_LADDER && !isAdFree) {
      completedInActiveWindow = 0;
    } else if (current.countInWindow < REWARDED_MAX_PER_LADDER && !isAdFree) {
      const nextTarget =
        current.windowStartAtMs + ladderTotalHoursForStep(current.countInWindow + 1) * REWARDED_HOUR_MS;
      completedInActiveWindow = nextTarget <= nowMs ? 0 : current.countInWindow;
    } else {
      completedInActiveWindow = Math.min(current.countInWindow, REWARDED_MAX_PER_LADDER);
    }
  }

  if (completedInActiveWindow >= REWARDED_MAX_PER_LADDER && current.adFreeUntilMs > nowMs) {
    return null;
  }

  const maxAllowedUntil = nowMs + REWARDED_WINDOW_MS;
  let newWindowStart: number;
  let newCountInWindow: number;
  let candidateUntil: number;

  if (completedInActiveWindow === 0) {
    newWindowStart = nowMs;
    newCountInWindow = 1;
    candidateUntil = nowMs + ladderTotalHoursForStep(1) * REWARDED_HOUR_MS;
  } else {
    newWindowStart = current.windowStartAtMs;
    newCountInWindow = Math.min(completedInActiveWindow + 1, REWARDED_MAX_PER_LADDER);
    candidateUntil = newWindowStart + ladderTotalHoursForStep(newCountInWindow) * REWARDED_HOUR_MS;
  }

  const boundedUntil = Math.min(Math.max(candidateUntil, current.adFreeUntilMs), maxAllowedUntil);
  const safeUntil = Math.max(boundedUntil, current.adFreeUntilMs);

  return {
    adFreeUntilMs: safeUntil,
    windowStartAtMs: newWindowStart,
    countInWindow: newCountInWindow,
    dailyDateKey: today,
    dailyCount: currentDaily + 1,
    totalCount: Math.max(0, current.totalCount) + 1,
    lastRewardedAtMs: nowMs,
  };
}

/**
 * Generates an opaque, zero-PII single-use nonce for AdMob SSV `custom_data`.
 * Neither UID, email, phone, nor organisation ID is ever embedded in the nonce.
 */
export async function createRewardedAdNonceCore(
  store: Pick<AdMobSsvStore, "isActiveOrgMember" | "saveNonce">,
  input: { callerUid: string; orgId: string; nowMs?: number }
): Promise<{ nonce: string; expiresAtMs: number }> {
  const callerUid = (input.callerUid || "").trim();
  const orgId = (input.orgId || "").trim();
  const nowMs = input.nowMs ?? Date.now();

  if (!callerUid) {
    throw new DomainFunctionError("unauthenticated", "Sign in first.");
  }
  if (!orgId) {
    throw new DomainFunctionError("invalid-argument", "Organisation ID is required.");
  }

  const active = await store.isActiveOrgMember(orgId, callerUid);
  if (!active) {
    throw new DomainFunctionError("permission-denied", "Active organisation membership required.");
  }

  const nonce = `rwd_${randomBytes(20).toString("hex")}`;
  const expiresAtMs = nowMs + NONCE_TTL_MS;
  await store.saveNonce({
    nonce,
    orgId,
    uid: callerUid,
    createdAtMs: nowMs,
    expiresAtMs,
    used: false,
  });

  return { nonce, expiresAtMs };
}

/**
 * Verifies Google AdMob SSV ECDSA-SHA256 signature per official specification:
 * - Signed payload is the exact raw query string substring BEFORE `&signature=`.
 * - Signature is URL-safe Base64url (DER-encoded ECDSA signature).
 * - Public key is looked up by `key_id` from Google's verifier-keys.json.
 */
export function verifyAdMobSsvSignature(
  rawQueryString: string,
  publicKeyPemById: Map<string, string>
): { valid: boolean; params: URLSearchParams; reason?: string } {
  const cleaned = rawQueryString.startsWith("?") ? rawQueryString.slice(1) : rawQueryString;
  const sigMarker = "&signature=";
  const sigIdx = cleaned.indexOf(sigMarker);
  const params = new URLSearchParams(cleaned);

  if (sigIdx <= 0) {
    return { valid: false, params, reason: "missing_signature" };
  }

  const signedContent = cleaned.slice(0, sigIdx);
  const signatureB64Url = params.get("signature") || "";
  const keyId = params.get("key_id") || "";
  if (!signatureB64Url || !keyId) {
    return { valid: false, params, reason: "missing_signature_or_key_id" };
  }

  const pem = publicKeyPemById.get(keyId);
  if (!pem) {
    return { valid: false, params, reason: "unknown_key_id" };
  }

  try {
    const standardB64 = signatureB64Url
      .replace(/-/g, "+")
      .replace(/_/g, "/")
      .padEnd(Math.ceil(signatureB64Url.length / 4) * 4, "=");
    const sigBytes = Buffer.from(standardB64, "base64");
    const verifier = createVerify("SHA256");
    verifier.update(signedContent, "utf8");
    verifier.end();
    const ok = verifier.verify(pem, sigBytes);
    return { valid: ok, params, reason: ok ? undefined : "invalid_signature" };
  } catch {
    return { valid: false, params, reason: "verify_exception" };
  }
}

export async function processAdMobSsvCallbackCore(
  deps: {
    store: AdMobSsvStore;
    getPublicKeys: () => Promise<Map<string, string>>;
  },
  rawQueryString: string,
  nowMs = Date.now()
): Promise<{
  httpStatus: number;
  outcome:
    | "applied"
    | "duplicate_transaction"
    | "invalid_signature"
    | "missing_fields"
    | "invalid_or_expired_nonce"
    | "cap_reached";
  updated?: ServerMemberRewardState;
}> {
  const keys = await deps.getPublicKeys();
  const check = verifyAdMobSsvSignature(rawQueryString, keys);
  if (!check.valid) {
    return { httpStatus: 400, outcome: "invalid_signature" };
  }

  const nonce = (check.params.get("custom_data") || "").trim();
  const transactionId = (check.params.get("transaction_id") || "").trim();
  if (!nonce || !transactionId) {
    return { httpStatus: 400, outcome: "missing_fields" };
  }

  const transactionIdHash = createHash("sha256").update(transactionId).digest("hex");
  const result = await deps.store.consumeNonceAndApplyReward({
    nonce,
    transactionIdHash,
    nowMs,
    computeNextState: computeNextServerRewardState,
  });

  if (result.status === "applied" || result.status === "duplicate_transaction") {
    return { httpStatus: 200, outcome: result.status, updated: result.updated };
  }
  if (result.status === "cap_reached") {
    return { httpStatus: 200, outcome: "cap_reached" };
  }
  return { httpStatus: 400, outcome: "invalid_or_expired_nonce" };
}
