/**
 * Phase 16A Firestore Security Rules Tests (Ads with Ad-Free Time).
 *
 * Verifies:
 *  1. Organisation `subscription` field is strictly READ-ONLY to every client
 *     (even Organisation OWNER and ADMIN are denied creating/updating/deleting it).
 *  2. Members can read their org's `subscription` map cleanly, and Owners can still update normal org fields.
 *  3. Member rewarded ad-free state (`adFreeUntil`, `adsWatchedWindow`):
 *     - User can update ONLY their own member doc (blocked for other users & org owners/admins).
 *     - `adFreeUntil` must NEVER be more than 24 hours after `request.time`.
 *     - `adFreeUntil` can ONLY be extended (never shortened).
 *     - `adsWatchedWindow.totalCount` and same-day/same-window counts CANNOT go down.
 *     - `dailyCount` is capped at 8, `countInWindow` at 4, and `lastRewardedAt` must be serverTimestamp().
 *  4. Remote ads config (`appConfig/adsConfig`):
 *     - Readable by any signed-in user; denied to signed-out users; never listable.
 *     - Writable ONLY by the designated App Owner account; denied to regular users & org owners.
 */

const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { test, before, after, beforeEach } = require("node:test");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

const rulesPkgDir = path.dirname(require.resolve("@firebase/rules-unit-testing/package.json"));
const firebaseCompat = require(require.resolve("firebase/compat/app", { paths: [rulesPkgDir, __dirname] })).default;
require(require.resolve("firebase/compat/firestore", { paths: [rulesPkgDir, __dirname] }));

const serverTimestamp = () => firebaseCompat.firestore.FieldValue.serverTimestamp();
const Timestamp = firebaseCompat.firestore.Timestamp;

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const ORG_ID = "org_p16a_demo";
const OWNER_UID = "uid_owner_p16a";
const ADMIN_UID = "uid_admin_p16a";
const TEACHER_UID = "uid_teacher_p16a";
const APP_OWNER_UID = "uid_app_owner";
const APP_OWNER_EMAIL = "vivekvishawkarma02@gmail.com";

const DEFAULT_PERMS = {
  students: "edit",
  marks: "edit",
  coScholastic: "edit",
  reports: "edit",
  members: "edit",
  settings: "edit",
  teachers: "edit",
  timetable: "edit",
  attendance: "edit",
  fees: "edit",
  studentRegister: "edit",
  certificates: "edit",
  cashBook: "edit",
  enquiry: "edit",
  exams: "edit",
  stock: "edit",
  transport: "edit",
  salary: "none",
  sessionPromotion: "edit",
  resultAnalysis: "view",
  classDiary: "edit",
  studentRemarks: "edit",
  gate: "edit",
};

function pastTimestamp(msAgo = 5000) {
  return Timestamp.fromMillis(Date.now() - msAgo);
}

function baseMemberDoc(uid, role) {
  const ts = pastTimestamp(5000);
  return {
    uid,
    name: `User ${uid}`,
    email: `${uid}@school.test`,
    mobile: "9876543210",
    role,
    status: "active",
    allSchools: true,
    schools: [],
    permissions: DEFAULT_PERMS,
    teacherProfileCompleted: true,
    teacherPhotoBase64: "",
    requestedSubjects: [],
    requestedClasses: [],
    assignedClassSubjects: [],
    lastActiveAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

function baseOrgDoc(extra = {}) {
  const ts = pastTimestamp(5000);
  return {
    name: "Shanti Public School",
    code: "shanti-01",
    ownerUid: OWNER_UID,
    contactMobile: "9876543210",
    contactEmail: "owner@school.test",
    settings: {
      currentSession: "2026-2027",
      sessionsCsv: "2026-2027",
      passPercent: 33,
      showPhotoAndAdmOnAnnual: false,
      showClassRank: true,
      lastBackupEpochMs: 0,
    },
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules: fs.readFileSync(path.join(__dirname, "firestore.rules"), "utf8"),
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`organisations/${ORG_ID}`).set(
      baseOrgDoc({
        subscription: { status: "none", plan: "", expiresAt: null },
      })
    );
    await db.doc(`organisations/${ORG_ID}/members/${OWNER_UID}`).set(baseMemberDoc(OWNER_UID, "OWNER"));
    await db.doc(`organisations/${ORG_ID}/members/${ADMIN_UID}`).set(baseMemberDoc(ADMIN_UID, "ADMIN"));
    await db.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).set(baseMemberDoc(TEACHER_UID, "TEACHER"));
    await db.doc("appConfig/adsConfig").set({
      globalEnabled: true,
      todayBannerEnabled: true,
      orgChooserBannerEnabled: true,
      schoolChooserBannerEnabled: true,
      pdfCloseInterstitialEnabled: true,
      rewardedAdFreeEnabled: true,
    });
  });
});

test("1. Organisation subscription is strictly read-only for every client (owner, admin, teacher)", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@school.test", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: "admin@school.test", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "teacher@school.test", email_verified: true }).firestore();

  // Active member can READ org doc including subscription
  const orgSnap = await assertSucceeds(teacherDb.doc(`organisations/${ORG_ID}`).get());
  assert.equal(orgSnap.data().subscription.status, "none");

  // Owner CAN still update normal org fields (e.g. name) while server-written subscription exists
  await assertSucceeds(
    ownerDb.doc(`organisations/${ORG_ID}`).update({
      name: "Shanti Public School Updated",
      updatedAt: serverTimestamp(),
    })
  );

  // Owner CANNOT create a new org with subscription field set
  await assertFails(
    ownerDb.doc("organisations/org_new_sub_test").set(
      baseOrgDoc({
        code: "new-org-02",
        subscription: { status: "active", plan: "fake", expiresAt: null },
      })
    )
  );

  // Owner CANNOT modify subscription
  await assertFails(
    ownerDb.doc(`organisations/${ORG_ID}`).update({
      subscription: { status: "active", plan: "yearly", expiresAt: null },
      updatedAt: serverTimestamp(),
    })
  );

  // Admin CANNOT modify subscription
  await assertFails(
    adminDb.doc(`organisations/${ORG_ID}`).update({
      subscription: { status: "grace", plan: "monthly", expiresAt: null },
      updatedAt: serverTimestamp(),
    })
  );

  // Teacher CANNOT modify subscription
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}`).update({
      subscription: { status: "active", plan: "hacked", expiresAt: null },
      updatedAt: serverTimestamp(),
    })
  );
});

test("2. Rewarded ad-free state on member doc is strictly bounded (own doc, <= 24h, extend-only, monotonic counts)", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@school.test", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "teacher@school.test", email_verified: true }).firestore();

  const nowMs = Date.now();
  const plus2h = Timestamp.fromMillis(nowMs + 2 * 3600 * 1000);
  const plus3h = Timestamp.fromMillis(nowMs + 3 * 3600 * 1000);
  const plus1h = Timestamp.fromMillis(nowMs + 1 * 3600 * 1000);
  const plus25h = Timestamp.fromMillis(nowMs + 25 * 3600 * 1000);
  const windowStart = Timestamp.fromMillis(nowMs - 1000);
  const todayKey = new Date(nowMs).toISOString().slice(0, 10);

  // Reject if adFreeUntil > 24h from request.time
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus25h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 1,
        dailyDateKey: todayKey,
        dailyCount: 1,
        totalCount: 1,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Reject updating ANOTHER user's adFreeUntil (even as Org Owner!)
  await assertFails(
    ownerDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus2h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 1,
        dailyDateKey: todayKey,
        dailyCount: 1,
        totalCount: 1,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Reject client-forged lastRewardedAt (must equal request.time via serverTimestamp())
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus2h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 1,
        dailyDateKey: todayKey,
        dailyCount: 1,
        totalCount: 1,
        lastRewardedAt: Timestamp.fromMillis(nowMs - 60_000),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Allow valid 1st rewarded ad on own member doc
  await assertSucceeds(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus2h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 1,
        dailyDateKey: todayKey,
        dailyCount: 1,
        totalCount: 1,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Reject shortening adFreeUntil
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus1h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 2,
        dailyDateKey: todayKey,
        dailyCount: 2,
        totalCount: 2,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Allow valid 2nd rewarded ad extending to +3h
  await assertSucceeds(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus3h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 2,
        dailyDateKey: todayKey,
        dailyCount: 2,
        totalCount: 2,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Reject decreasing totalCount / countInWindow / dailyCount
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus3h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 1,
        dailyDateKey: todayKey,
        dailyCount: 1,
        totalCount: 1,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Reject dailyCount > 8
  await assertFails(
    teacherDb.doc(`organisations/${ORG_ID}/members/${TEACHER_UID}`).update({
      adFreeUntil: plus3h,
      adsWatchedWindow: {
        windowStartAt: windowStart,
        countInWindow: 3,
        dailyDateKey: todayKey,
        dailyCount: 9,
        totalCount: 3,
        lastRewardedAt: serverTimestamp(),
      },
      updatedAt: serverTimestamp(),
    })
  );
});

test("3. Remote adsConfig (appConfig/adsConfig) is readable by signed-in users and writable ONLY by App Owner", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@school.test", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "teacher@school.test", email_verified: true }).firestore();
  const appOwnerDb = testEnv.authenticatedContext(APP_OWNER_UID, { email: APP_OWNER_EMAIL, email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Signed-out user CANNOT read adsConfig
  await assertFails(unauthDb.doc("appConfig/adsConfig").get());

  // Signed-in user CAN read adsConfig
  const cfgSnap = await assertSucceeds(teacherDb.doc("appConfig/adsConfig").get());
  assert.equal(cfgSnap.data().globalEnabled, true);

  // Nobody can LIST appConfig collection
  await assertFails(teacherDb.collection("appConfig").get());

  // Regular org owner CANNOT write adsConfig
  await assertFails(
    ownerDb.doc("appConfig/adsConfig").update({
      globalEnabled: false,
    })
  );

  // Designated App Owner CAN update adsConfig including Phase 16B list-banner and SSV flags
  await assertSucceeds(
    appOwnerDb.doc("appConfig/adsConfig").update({
      globalEnabled: true,
      studentListBannerEnabled: true,
      teacherListBannerEnabled: false,
      reportListBannerEnabled: true,
      timetableViewBannerEnabled: true,
      pdfCloseInterstitialEnabled: false,
      requireServerVerifiedRewards: true,
      updatedAt: serverTimestamp(),
      updatedByEmail: APP_OWNER_EMAIL,
    })
  );

  // Non-owner CANNOT delete adsConfig
  await assertFails(ownerDb.doc("appConfig/adsConfig").delete());
});

test("4. Phase 16B: server-written subscription fields remain read-only and billing/SSV collections are strictly denied to all clients", async () => {
  // Seed server-written Phase 16B subscription map (Admin SDK bypasses rules)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = Timestamp.fromMillis(Date.now() + 30 * 24 * 3600 * 1000);
    await db.doc(`organisations/${ORG_ID}`).update({
      subscription: {
        status: "cancelled_active",
        plan: "yearly",
        expiresAt: ts,
        autoRenewing: false,
        updatedAt: pastTimestamp(1000),
        purchaseTokenHash: "a".repeat(64),
      },
    });
    await db.doc("subscriptionPurchaseLocks/hash123").set({
      orgId: ORG_ID,
      productId: "school_ad_free",
    });
    await db.doc("rewardedAdNonces/rwd_testnonce").set({
      orgId: ORG_ID,
      uid: TEACHER_UID,
      used: false,
    });
    await db.doc("rewardedAdTransactions/txhash123").set({
      orgId: ORG_ID,
      uid: TEACHER_UID,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@school.test", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID, { email: "teacher@school.test", email_verified: true }).firestore();
  const appOwnerDb = testEnv.authenticatedContext(APP_OWNER_UID, { email: APP_OWNER_EMAIL, email_verified: true }).firestore();

  // Active member can read the Phase 16B server-written subscription fields
  const snap = await assertSucceeds(teacherDb.doc(`organisations/${ORG_ID}`).get());
  assert.equal(snap.data().subscription.status, "cancelled_active");
  assert.equal(snap.data().subscription.autoRenewing, false);

  // Owner can still update normal org fields (name) when Phase 16B subscription map is present
  await assertSucceeds(
    ownerDb.doc(`organisations/${ORG_ID}`).update({
      name: "Shanti Public School Phase 16B",
      updatedAt: serverTimestamp(),
    })
  );

  // Even Owner CANNOT overwrite Phase 16B subscription status (e.g. from revoked/expired to active)
  await assertFails(
    ownerDb.doc(`organisations/${ORG_ID}`).update({
      subscription: {
        status: "active",
        plan: "yearly",
        expiresAt: null,
        autoRenewing: true,
        updatedAt: serverTimestamp(),
        purchaseTokenHash: "b".repeat(64),
      },
      updatedAt: serverTimestamp(),
    })
  );

  // Every client (including Owner and App Owner) is strictly denied reading or writing server-only billing & SSV collections
  for (const db of [ownerDb, teacherDb, appOwnerDb]) {
    await assertFails(db.doc("subscriptionPurchaseLocks/hash123").get());
    await assertFails(db.doc("subscriptionPurchaseLocks/newlock").set({ orgId: ORG_ID }));
    await assertFails(db.doc("rewardedAdNonces/rwd_testnonce").get());
    await assertFails(db.doc("rewardedAdNonces/rwd_fake").set({ orgId: ORG_ID }));
    await assertFails(db.doc("rewardedAdTransactions/txhash123").get());
    await assertFails(db.doc("rewardedAdTransactions/tx_fake").set({ orgId: ORG_ID }));
  }
});

