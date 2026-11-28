#!/usr/bin/env node
/**
 * Releases firestore.rules directly via the Firebase Rules REST API v1 to the
 * named Firestore database configured in firebase.json.
 *
 * Uses PATCH .../releases/cloud.firestore/<DATABASE_ID> (falling back to POST
 * .../releases only on HTTP 404) so existing named-database releases update
 * cleanly instead of hitting CLI 409 "Release ... already exists".
 *
 * Retries ONLY on transient failures (503 / unavailable / ETIMEDOUT / ECONNRESET)
 * up to 6 attempts (waits: 20, 40, 60, 90, 120s). Fails immediately and prints
 * the full response body on 400/403/409 or any non-retryable error. Never logs
 * OAuth tokens or service account credentials.
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const MAX_ATTEMPTS = 6;
const RETRY_WAITS_SEC = [20, 40, 60, 90, 120];
const TRANSIENT_RE = /503|unavailable|ETIMEDOUT|ECONNRESET/i;
const NON_RETRYABLE_STATUSES = new Set([400, 401, 403, 404, 409]);

function parseArgs(argv) {
  const opts = {
    projectId: process.env.FIREBASE_PROJECT_ID || "",
    summaryOut: "",
    readbackOnly: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--project" && argv[i + 1]) {
      opts.projectId = argv[++i];
    } else if (arg === "--summary-out" && argv[i + 1]) {
      opts.summaryOut = argv[++i];
    } else if (arg === "--readback-only") {
      opts.readbackOnly = true;
    }
  }
  return opts;
}

function base64Url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isTransient(status, bodyText, err) {
  if (status != null && NON_RETRYABLE_STATUSES.has(status)) {
    return false;
  }
  if (status === 503) {
    return true;
  }
  const haystack = [
    bodyText || "",
    err?.message || "",
    err?.code || "",
    err?.cause?.code || "",
    err?.cause?.message || "",
  ].join(" ");
  return TRANSIENT_RE.test(haystack);
}

async function withRetry(stepLabel, fn) {
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
    console.log(`Attempt ${attempt} of ${MAX_ATTEMPTS}: ${stepLabel}...`);
    try {
      const result = await fn();
      return result;
    } catch (err) {
      const status = err?.httpStatus ?? null;
      const bodyText = err?.responseBody ?? "";
      if (!isTransient(status, bodyText, err) || attempt === MAX_ATTEMPTS) {
        if (bodyText) {
          console.error(`Full error response (${status ?? "network"}):\n${bodyText}`);
        }
        throw err;
      }
      const waitSec = RETRY_WAITS_SEC[attempt - 1];
      console.warn(
        `Transient error during "${stepLabel}" (${status ?? err?.code ?? err?.message}). Waiting ${waitSec}s before retry...`
      );
      await sleep(waitSec * 1000);
    }
  }
}

async function httpJson(url, options) {
  let res;
  let text = "";
  try {
    res = await fetch(url, options);
    text = await res.text();
  } catch (networkErr) {
    const err = new Error(`Network request failed for ${url}: ${networkErr.message}`);
    err.cause = networkErr;
    err.code = networkErr?.code || networkErr?.cause?.code;
    throw err;
  }

  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
  }

  if (!res.ok) {
    const err = new Error(`HTTP ${res.status} ${res.statusText} from ${url}`);
    err.httpStatus = res.status;
    err.responseBody = text;
    err.parsedBody = parsed;
    throw err;
  }
  return parsed ?? {};
}

function createSignedJwt(sa) {
  const tokenUri = sa.token_uri || "https://oauth2.googleapis.com/token";
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64Url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase",
      aud: tokenUri,
      iat: now,
      exp: now + 3600,
    })
  );
  const signingInput = `${header}.${payload}`;
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(signingInput);
  signer.end();
  const signature = base64Url(signer.sign(sa.private_key));
  return { tokenUri, assertion: `${signingInput}.${signature}` };
}

async function getAccessToken(credPath) {
  const raw = fs.readFileSync(credPath, "utf8");
  const sa = JSON.parse(raw);
  if (!sa.client_email || !sa.private_key) {
    throw new Error("Service account JSON is missing client_email or private_key.");
  }
  return withRetry("requesting OAuth2 access token from service account", async () => {
    const { tokenUri, assertion } = createSignedJwt(sa);
    const body = new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString();
    const data = await httpJson(tokenUri, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    if (!data.access_token) {
      throw new Error("OAuth2 token response did not include access_token.");
    }
    return data.access_token;
  });
}

function loadFirestoreConfig() {
  const cfgPath = path.resolve("firebase.json");
  const cfg = JSON.parse(fs.readFileSync(cfgPath, "utf8"));
  const fsEntry = Array.isArray(cfg.firestore) ? cfg.firestore[0] : cfg.firestore;
  if (!fsEntry?.database) {
    throw new Error("firebase.json is missing firestore[].database.");
  }
  return {
    databaseId: fsEntry.database,
    rulesFile: fsEntry.rules || "firestore.rules",
  };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (!opts.projectId) {
    throw new Error("Missing project ID (--project or FIREBASE_PROJECT_ID).");
  }
  const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!credPath || !fs.existsSync(credPath)) {
    throw new Error("Missing GOOGLE_APPLICATION_CREDENTIALS file.");
  }

  const { databaseId, rulesFile } = loadFirestoreConfig();
  const rulesPath = path.resolve(rulesFile);
  const rulesContent = fs.readFileSync(rulesPath, "utf8");

  const accessToken = await getAccessToken(credPath);
  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };

  const baseUrl = `https://firebaserules.googleapis.com/v1/projects/${encodeURIComponent(opts.projectId)}`;
  const releaseShortName = `cloud.firestore/${databaseId}`;
  const fullReleaseName = `projects/${opts.projectId}/releases/${releaseShortName}`;
  const releaseUrl = `${baseUrl}/releases/cloud.firestore/${encodeURIComponent(databaseId)}`;

  let expectedRulesetName = null;

  if (!opts.readbackOnly) {
    // 2a. Create ruleset
    const createdRuleset = await withRetry("creating ruleset via firebaserules.googleapis.com", () =>
      httpJson(`${baseUrl}/rulesets`, {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({
          source: {
            files: [
              {
                name: path.basename(rulesFile),
                content: rulesContent,
              },
            ],
          },
        }),
      })
    );
    expectedRulesetName = createdRuleset.name;
    if (!expectedRulesetName) {
      throw new Error("POST /rulesets succeeded but did not return a ruleset name.");
    }
    console.log(`Created ruleset: ${expectedRulesetName}`);

    // 2b. PATCH existing release; fall back to POST /releases only on HTTP 404
    let needCreate = false;
    try {
      await withRetry(`updating release ${releaseShortName}`, async () => {
        try {
          return await httpJson(releaseUrl, {
            method: "PATCH",
            headers: authHeaders,
            body: JSON.stringify({
              release: {
                name: fullReleaseName,
                rulesetName: expectedRulesetName,
              },
            }),
          });
        } catch (err) {
          if (err?.httpStatus === 404) {
            needCreate = true;
            return null;
          }
          throw err;
        }
      });
    } catch (err) {
      throw err;
    }

    if (needCreate) {
      console.log(`Release ${releaseShortName} returned 404; creating it with POST /releases...`);
      await withRetry(`creating release ${releaseShortName}`, () =>
        httpJson(`${baseUrl}/releases`, {
          method: "POST",
          headers: authHeaders,
          body: JSON.stringify({
            name: fullReleaseName,
            rulesetName: expectedRulesetName,
          }),
        })
      );
    }
  }

  // 2c. Read the release back with GET and verify rulesetName
  const liveRelease = await withRetry(`reading back release ${releaseShortName}`, () =>
    httpJson(releaseUrl, {
      method: "GET",
      headers: { Authorization: `Bearer ${accessToken}` },
    })
  );

  if (!liveRelease?.rulesetName) {
    throw new Error(`GET ${releaseUrl} did not return rulesetName.`);
  }
  if (expectedRulesetName && liveRelease.rulesetName !== expectedRulesetName) {
    throw new Error(
      `Release readback mismatch: expected rulesetName "${expectedRulesetName}", got "${liveRelease.rulesetName}".`
    );
  }

  const summaryLine = `Rules released to database ${databaseId}, ruleset ${liveRelease.rulesetName}`;
  console.log(summaryLine);

  if (opts.summaryOut) {
    fs.writeFileSync(opts.summaryOut, `${summaryLine}\n`, "utf8");
  }
}

main().catch((err) => {
  console.error(`ERROR: ${err.message}`);
  process.exit(1);
});
