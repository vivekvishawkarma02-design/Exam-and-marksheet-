import { GoogleAuth } from "google-auth-library";
import { PlayDeveloperApiClient, PlaySubscriptionV2Response } from "./subscriptionLogic";

const PLAY_ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const ADMOB_VERIFIER_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";

export class GooglePlayDeveloperApiClient implements PlayDeveloperApiClient {
  private readonly auth: GoogleAuth;

  constructor(serviceAccountJson?: string) {
    const trimmed = (serviceAccountJson || "").trim();
    if (trimmed) {
      const credentials = JSON.parse(trimmed);
      this.auth = new GoogleAuth({
        credentials,
        scopes: [PLAY_ANDROID_PUBLISHER_SCOPE],
      });
    } else {
      // Uses Application Default Credentials (the Cloud Functions runtime service account
      // granted Financial Data / Orders permissions in Google Play Console).
      this.auth = new GoogleAuth({
        scopes: [PLAY_ANDROID_PUBLISHER_SCOPE],
      });
    }
  }

  private async getAccessToken(): Promise<string> {
    const client = await this.auth.getClient();
    const tokenRes = await client.getAccessToken();
    const token = typeof tokenRes === "string" ? tokenRes : tokenRes?.token;
    if (!token) {
      throw new Error("Could not obtain Google Play Developer API OAuth access token.");
    }
    return token;
  }

  async getSubscriptionV2(
    packageName: string,
    purchaseToken: string
  ): Promise<PlaySubscriptionV2Response> {
    const accessToken = await this.getAccessToken();
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/` +
      `${encodeURIComponent(purchaseToken)}`;

    const res = await fetch(url, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!res.ok) {
      const bodyText = await res.text().catch(() => "");
      throw new Error(`Google Play subscriptionsv2.get failed (${res.status}): ${bodyText.slice(0, 240)}`);
    }
    return (await res.json()) as PlaySubscriptionV2Response;
  }

  async acknowledgeSubscription(
    packageName: string,
    productId: string,
    purchaseToken: string
  ): Promise<void> {
    const accessToken = await this.getAccessToken();
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(packageName)}/purchases/subscriptions/` +
      `${encodeURIComponent(productId)}/tokens/` +
      `${encodeURIComponent(purchaseToken)}:acknowledge`;

    const res = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({}),
    });
    if (!res.ok && res.status !== 204) {
      const bodyText = await res.text().catch(() => "");
      throw new Error(`Google Play acknowledge failed (${res.status}): ${bodyText.slice(0, 240)}`);
    }
  }
}

let cachedAdMobKeys: { fetchedAtMs: number; keys: Map<string, string> } | null = null;
const KEYS_CACHE_TTL_MS = 12 * 3_600_000;

export async function fetchGoogleAdMobVerifierKeys(): Promise<Map<string, string>> {
  const now = Date.now();
  if (cachedAdMobKeys && now - cachedAdMobKeys.fetchedAtMs < KEYS_CACHE_TTL_MS) {
    return cachedAdMobKeys.keys;
  }
  const res = await fetch(ADMOB_VERIFIER_KEYS_URL, { method: "GET" });
  if (!res.ok) {
    if (cachedAdMobKeys) return cachedAdMobKeys.keys;
    throw new Error(`Failed to fetch AdMob verifier keys (${res.status})`);
  }
  const json = (await res.json()) as {
    keys?: Array<{ keyId?: number | string; pem?: string }>;
  };
  const map = new Map<string, string>();
  for (const item of json.keys || []) {
    if (item.keyId !== undefined && item.pem) {
      map.set(String(item.keyId), item.pem);
    }
  }
  cachedAdMobKeys = { fetchedAtMs: now, keys: map };
  return map;
}
