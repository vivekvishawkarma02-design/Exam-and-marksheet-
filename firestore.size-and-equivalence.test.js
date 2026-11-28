/**
 * Step 3 & 4: Firestore Rules Compiled Size Guard + Full Module Role-Matrix Equivalence Test.
 *
 * Verifies:
 *  1. Compiled AST size of firestore.rules (stripped of source positions) is <= 230,000 bytes
 *     (safely under the 256,000-byte Firebase live deployment limit).
 *  2. Across EVERY module collection, the exact RBAC expectations hold for all 6 actors:
 *     - OWNER
 *     - ADMIN
 *     - MEMBER_EDIT (active non-admin member assigned to SCHOOL_1 with target module = 'edit')
 *     - MEMBER_VIEW (active non-admin member assigned to SCHOOL_1 with target module = 'view')
 *     - MEMBER_NONE (active non-admin member assigned to SCHOOL_1 with all modules = 'none')
 *     - OUTSIDER (signed-in user not belonging to ORG_ID)
 *  3. Explicit coverage of:
 *     - Creating and updating a student with all extended transport fields
 *     - Saving fee structures, fee overrides, fee receipts, fee message logs, and fee audit logs
 *     - Saving, editing (with reason), and cancelling cash book entries + settings + day closings + audit logs
 *     - Creating, reading, updating, and deleting transport routes, vehicle documents, and settings
 */

const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
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

const Timestamp = firebaseCompat.firestore.Timestamp;

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const RULES_PATH = process.env.RULES_FILE || path.join(__dirname, "firestore.rules");
const SKIP_SIZE_CHECK = process.env.SKIP_RULES_SIZE_CHECK === "1";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const ORG_ID = "org_equiv_01";
const ORG_CODE = "equiv-school-01";
const SCHOOL_1 = "sch_main_01";

const OWNER_UID = "uid_eq_owner";
const ADMIN_UID = "uid_eq_admin";
const EDIT_UID = "uid_eq_edit";
const VIEW_UID = "uid_eq_view";
const NONE_UID = "uid_eq_none";
const OUTSIDER_UID = "uid_eq_outsider";
const APP_OWNER_EMAIL = "vivekvishawkarma02@gmail.com";

const ALL_MODULES = [
  "students", "marks", "coScholastic", "reports", "timetable", "teachers", "settings", "members",
  "attendance", "fees", "studentRegister", "certificates", "cashBook", "enquiry",
  "exams", "stock", "transport", "salary", "sessionPromotion", "resultAnalysis",
  "classDiary", "studentRemarks", "gate",
];

function perms(overrides = {}) {
  const out = {};
  for (const k of ALL_MODULES) out[k] = "none";
  return { ...out, ...overrides };
}

function pastTs(msAgo = 5000) {
  return Timestamp.fromMillis(Date.now() - msAgo);
}

function memberDoc(uid, role, permissions, allSchools = false) {
  const ts = pastTs(10000);
  return {
    uid,
    name: `User ${uid}`,
    email: `${uid}@school.test`,
    mobile: "9000000000",
    role,
    status: "active",
    allSchools,
    schools: allSchools ? [] : [SCHOOL_1],
    permissions,
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

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules: fs.readFileSync(RULES_PATH, "utf8"),
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  const ts = pastTs(20000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`orgCodes/${ORG_CODE}`).set({
      orgId: ORG_ID,
      name: "Equivalence School",
      ownerUid: OWNER_UID,
      createdAt: ts,
    });
    await db.doc(`organisations/${ORG_ID}`).set({
      name: "Equivalence School",
      code: ORG_CODE,
      ownerUid: OWNER_UID,
      contactMobile: "9000000001",
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
    });
    await db.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}`).set({
      schoolKey: SCHOOL_1,
      name: "Main Campus",
      address: "Campus Road",
      mobile: "9000000001",
      email: "main@school.test",
      logoBase64: "",
      active: true,
      classGroups: [],
      classes: [],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.doc(`organisations/${ORG_ID}/members/${OWNER_UID}`).set(
      memberDoc(OWNER_UID, "OWNER", perms(Object.fromEntries(ALL_MODULES.map((m) => [m, m === "resultAnalysis" ? "view" : "edit"]))), true)
    );
    await db.doc(`organisations/${ORG_ID}/members/${ADMIN_UID}`).set(
      memberDoc(ADMIN_UID, "ADMIN", perms(Object.fromEntries(ALL_MODULES.map((m) => [m, m === "salary" ? "none" : m === "resultAnalysis" ? "view" : "edit"]))), false)
    );
    await db.doc(`organisations/${ORG_ID}/members/${NONE_UID}`).set(
      memberDoc(NONE_UID, "OFFICE_OPERATOR", perms(), false)
    );
    // Pre-seed a student so dependent rules (e.g. class teacher / marks / updates) have a valid target
    await db.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_seed`).set({
      schoolId: SCHOOL_1,
      classId: "cls_5",
      session: "2026-2027",
      admNo: "ADM001",
      name: "Seed Student",
      fatherName: "Parent Name",
      motherName: "Mother Name",
      dob: "2015-01-01",
      mobile: "9000000002",
      address: "Street 1",
      photoBase64: "",
      className: "5",
      section: "A",
      rollNo: 1,
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.doc(`organisations/${ORG_ID}/teachers/tchr_seed`).set({
      name: "Seed Teacher",
      mobile: "9000000003",
      email: "tchr@school.test",
      photoBase64: "",
      subjects: ["Maths"],
      schoolIds: [SCHOOL_1],
      maxPeriodsPerDay: 6,
      maxConsecutivePeriods: 3,
      weeklyOffDays: ["SUN"],
      active: true,
      linkedMemberUid: "",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.doc("appConfig/adsConfig").set({
      globalEnabled: true,
    });
  });
});

async function seedScopedMembers(moduleKey, extraPerms = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const editPerms = moduleKey ? perms({ [moduleKey]: "edit", ...extraPerms }) : perms(extraPerms);
    const viewPerms = moduleKey ? perms({ [moduleKey]: "view" }) : perms();
    await db.doc(`organisations/${ORG_ID}/members/${EDIT_UID}`).set(memberDoc(EDIT_UID, "OFFICE_OPERATOR", editPerms, false));
    await db.doc(`organisations/${ORG_ID}/members/${VIEW_UID}`).set(memberDoc(VIEW_UID, "OFFICE_OPERATOR", viewPerms, false));
  });
}

function actorDbs() {
  return {
    OWNER: { uid: OWNER_UID, db: testEnv.authenticatedContext(OWNER_UID, { email: `${OWNER_UID}@school.test`, email_verified: true }).firestore() },
    ADMIN: { uid: ADMIN_UID, db: testEnv.authenticatedContext(ADMIN_UID, { email: `${ADMIN_UID}@school.test`, email_verified: true }).firestore() },
    EDIT: { uid: EDIT_UID, db: testEnv.authenticatedContext(EDIT_UID, { email: `${EDIT_UID}@school.test`, email_verified: true }).firestore() },
    VIEW: { uid: VIEW_UID, db: testEnv.authenticatedContext(VIEW_UID, { email: `${VIEW_UID}@school.test`, email_verified: true }).firestore() },
    NONE: { uid: NONE_UID, db: testEnv.authenticatedContext(NONE_UID, { email: `${NONE_UID}@school.test`, email_verified: true }).firestore() },
    OUTSIDER: { uid: OUTSIDER_UID, db: testEnv.authenticatedContext(OUTSIDER_UID, { email: `${OUTSIDER_UID}@school.test`, email_verified: true }).firestore() },
  };
}

async function expectBool(promise, shouldSucceed, label) {
  if (shouldSucceed) {
    await assertSucceeds(promise).catch((e) => {
      throw new assert.AssertionError({ message: `Expected ALLOW for ${label}, got DENY: ${e.message}` });
    });
  } else {
    await assertFails(promise).catch((e) => {
      throw new assert.AssertionError({ message: `Expected DENY for ${label}, got ALLOW` });
    });
  }
}

/**
 * Verifies a full 6-actor x 4-operation matrix for any collection.
 */
async function verifyMatrix({
  name,
  moduleKey,
  extraEditPerms = {},
  docPath,
  listPath = null,
  makeCreate,
  makeUpdate,
  seedDoc,
  allowRead,   // { OWNER, ADMIN, EDIT, VIEW, NONE, OUTSIDER }
  allowCreate,
  allowUpdate,
  allowDelete,
}) {
  await seedScopedMembers(moduleKey, extraEditPerms);
  const actors = actorDbs();
  const roles = ["OWNER", "ADMIN", "EDIT", "VIEW", "NONE", "OUTSIDER"];

  // 1. Seed document for GET / LIST
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(docPath("seed")).set(seedDoc(OWNER_UID, "OWNER", "seed"));
  });

  // Check READ (get + list)
  for (const r of roles) {
    const { db } = actors[r];
    await expectBool(db.doc(docPath("seed")).get(), allowRead[r], `${name} GET [${r}]`);
    if (listPath) {
      await expectBool(db.collection(listPath).get(), allowRead[r], `${name} LIST [${r}]`);
    }
  }

  // Check CREATE (ensure target doc does not pre-exist for fixed-ID collections)
  for (const r of roles) {
    const { uid, db } = actors[r];
    const id = `cr_${r.toLowerCase()}`;
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(docPath(id)).delete().catch(() => {});
    });
    await expectBool(db.doc(docPath(id)).set(makeCreate(uid, r, id)), allowCreate[r], `${name} CREATE [${r}]`);
  }

  // Check UPDATE (on fresh seeded doc per actor)
  for (const r of roles) {
    const { uid, db } = actors[r];
    const id = `upd_${r.toLowerCase()}`;
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(docPath(id)).set(seedDoc(uid, r, id));
    });
    await expectBool(db.doc(docPath(id)).update(makeUpdate(uid, r, id)), allowUpdate[r], `${name} UPDATE [${r}]`);
  }

  // Check DELETE (on fresh seeded doc per actor)
  for (const r of roles) {
    const { db } = actors[r];
    const id = `del_${r.toLowerCase()}`;
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(docPath(id)).set(seedDoc(OWNER_UID, "OWNER", id));
    });
    await expectBool(db.doc(docPath(id)).delete(), allowDelete[r], `${name} DELETE [${r}]`);
  }
}

test("0. Size Guard: compiled firestore.rules AST stays safely below 230,000 bytes", () => {
  if (SKIP_SIZE_CHECK) return;
  const out = execFileSync("node", [path.join(__dirname, "scripts/check-firestore-rules-size.mjs"), RULES_PATH, "230000"], {
    encoding: "utf8",
  });
  assert.match(out, /OK: Compiled rules AST/);
});

test("1. Core & Admin collections equivalence (users, orgCodes, organisations, members, joinRequests, inviteCodes, auditLog, deletedUsers, schools)", async () => {
  await seedScopedMembers("members");
  const actors = actorDbs();
  const ts = pastTs(3000);

  // users/{uid}: self-only get/create/update/delete, no list
  await expectBool(actors.EDIT.db.doc(`users/${EDIT_UID}`).set({ uid: EDIT_UID, name: "Edit", email: "e@t.co", memberships: [], pendingRequests: [], createdAt: ts, updatedAt: ts }), true, "users self create");
  await expectBool(actors.EDIT.db.doc(`users/${EDIT_UID}`).get(), true, "users self get");
  await expectBool(actors.OWNER.db.doc(`users/${EDIT_UID}`).get(), false, "users other get");
  await expectBool(actors.OWNER.db.collection("users").get(), false, "users list");

  // organisations/{orgId}: active member get; owner/admin/settings update (no subscription touch); owner-only delete; no list
  for (const r of ["OWNER", "ADMIN", "EDIT", "VIEW", "NONE"]) {
    await expectBool(actors[r].db.doc(`organisations/${ORG_ID}`).get(), true, `org get [${r}]`);
  }
  await expectBool(actors.OUTSIDER.db.doc(`organisations/${ORG_ID}`).get(), false, "org get [OUTSIDER]");
  await expectBool(actors.OWNER.db.collection("organisations").get(), false, "org list");
  await expectBool(actors.OWNER.db.doc(`organisations/${ORG_ID}`).update({ name: "Renamed", updatedAt: ts }), true, "org update [OWNER]");
  await expectBool(actors.ADMIN.db.doc(`organisations/${ORG_ID}`).update({ name: "Renamed2", updatedAt: ts }), true, "org update [ADMIN]");
  await expectBool(actors.VIEW.db.doc(`organisations/${ORG_ID}`).update({ name: "Nope", updatedAt: ts }), false, "org update [VIEW]");
  await expectBool(actors.ADMIN.db.doc(`organisations/${ORG_ID}`).delete(), false, "org delete [ADMIN]");

  // members/{memberUid}: members perm + role matrix (create non-owner by OWNER/ADMIN/members:edit; edit perm; admin/self delete)
  for (const [r, canRead, canCreate, canUpdate, canDelete] of [
    ["OWNER", true, true, true, true],
    ["ADMIN", true, true, true, true],
    ["EDIT", true, true, true, false],
    ["VIEW", true, false, false, false],
    ["NONE", false, false, false, false],
    ["OUTSIDER", false, false, false, false],
  ]) {
    await expectBool(actors[r].db.doc(`organisations/${ORG_ID}/members/${OWNER_UID}`).get(), canRead, `members GET other [${r}]`);
    const cid = `mem_new_${r.toLowerCase()}`;
    await expectBool(actors[r].db.doc(`organisations/${ORG_ID}/members/${cid}`).set(memberDoc(cid, "OFFICE_OPERATOR", perms(), false)), canCreate, `members CREATE [${r}]`);
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`organisations/${ORG_ID}/members/m_upd_${r.toLowerCase()}`).set(memberDoc(`m_upd_${r.toLowerCase()}`, "TEACHER", perms(), false));
    });
    await expectBool(actors[r].db.doc(`organisations/${ORG_ID}/members/m_upd_${r.toLowerCase()}`).update({ status: "removed", updatedAt: ts }), canUpdate, `members UPDATE [${r}]`);
    await expectBool(actors[r].db.doc(`organisations/${ORG_ID}/members/m_upd_${r.toLowerCase()}`).delete(), canDelete, `members DELETE [${r}]`);
  }
  await expectBool(actors.NONE.db.doc(`organisations/${ORG_ID}/members/${NONE_UID}`).get(), true, "members self GET [NONE]");

  // joinRequests
  await expectBool(actors.OUTSIDER.db.doc(`organisations/${ORG_ID}/joinRequests/${OUTSIDER_UID}`).set({ uid: OUTSIDER_UID, name: "Out", email: "o@t.co", message: "Hi", requestedRole: "TEACHER", status: "pending", createdAt: ts, updatedAt: ts }), true, "joinReq outsider create");
  await expectBool(actors.EDIT.db.doc(`organisations/${ORG_ID}/joinRequests/${OUTSIDER_UID}`).update({ status: "approved", reviewedByUid: EDIT_UID, updatedAt: ts }), true, "joinReq edit approve");
  await expectBool(actors.VIEW.db.doc(`organisations/${ORG_ID}/joinRequests/${OUTSIDER_UID}`).get(), true, "joinReq view read");
  await expectBool(actors.NONE.db.doc(`organisations/${ORG_ID}/joinRequests/${OUTSIDER_UID}`).get(), false, "joinReq none read");

  // inviteCodes
  const invPayload = (uid) => ({ code: "INV1234", orgId: ORG_ID, orgName: "Equiv School", presetRole: "TEACHER", allSchools: false, schools: [SCHOOL_1], permissions: perms(), createdByUid: uid, expiresAt: pastTs(-3600000), usedByUid: "", createdAt: ts });
  await expectBool(actors.OWNER.db.doc(`organisations/${ORG_ID}/inviteCodes/INV1234`).set(invPayload(OWNER_UID)), true, "inviteCodes OWNER create");
  await expectBool(actors.EDIT.db.doc(`organisations/${ORG_ID}/inviteCodes/INV9999`).set(invPayload(EDIT_UID)), false, "inviteCodes non-admin create deny");
  await expectBool(actors.VIEW.db.collection(`organisations/${ORG_ID}/inviteCodes`).get(), true, "inviteCodes VIEW list");
  await expectBool(actors.NONE.db.collection(`organisations/${ORG_ID}/inviteCodes`).get(), false, "inviteCodes NONE list");

  // auditLog & deletedUsers
  await expectBool(actors.ADMIN.db.doc(`organisations/${ORG_ID}/auditLog/al_1`).set({ actorUid: ADMIN_UID, actorName: "Admin", action: "TEST", targetType: "student", targetId: "s1", summary: "OK", createdAt: ts }), true, "auditLog create");
  await expectBool(actors.NONE.db.collection(`organisations/${ORG_ID}/auditLog`).get(), false, "auditLog list NONE deny");
  await expectBool(actors.OWNER.db.collection(`organisations/${ORG_ID}/auditLog`).get(), true, "auditLog list OWNER");
  await expectBool(actors.OWNER.db.doc(`organisations/${ORG_ID}/auditLog/al_1`).delete(), false, "auditLog delete denied outside teardown");
  await expectBool(actors.NONE.db.doc(`organisations/${ORG_ID}/deletedUsers/${NONE_UID}`).set({ uid: NONE_UID, deletedAt: ts }), true, "deletedUsers self create");
  await expectBool(actors.NONE.db.doc(`organisations/${ORG_ID}/deletedUsers/${NONE_UID}`).delete(), false, "deletedUsers delete denied");

  // schools (settings edit)
  await seedScopedMembers("settings");
  const schDoc = { schoolKey: "sch_02", name: "Branch 2", address: "Addr", mobile: "90000", email: "b@s.co", logoBase64: "", active: true, classGroups: [], classes: [], createdAt: ts, updatedAt: ts };
  await expectBool(actors.EDIT.db.doc(`organisations/${ORG_ID}/schools/sch_02`).set(schDoc), true, "schools settings:edit create");
  await expectBool(actors.VIEW.db.doc(`organisations/${ORG_ID}/schools/sch_02`).update({ name: "X", updatedAt: ts }), false, "schools settings:view update deny");
  await expectBool(actors.ADMIN.db.doc(`organisations/${ORG_ID}/schools/sch_02`).delete(), true, "schools ADMIN delete");
});

test("2. Students (including extended transport fields), Marks, MarkEditLogs, CoScholastic & TermInfo equivalence", async () => {
  const ts = pastTs(3000);
  const fullStudent = () => ({
    schoolId: SCHOOL_1, classId: "cls_5", session: "2026-2027", admNo: "ADM900", name: "Kavya Verma",
    fatherName: "Suresh Verma", motherName: "Meena Verma", dob: "2016-05-12", mobile: "9876543210",
    secondMobile: "9876543211", address: "Lane 4", photoBase64: "", className: "5", section: "A", rollNo: 12,
    admissionDate: "2024-04-01", previousSchool: "Primary Academy", siblingGroupId: "sib_1",
    usesTransport: true, transportRouteId: "route_north", transportRouteName: "North Line",
    transportStopId: "stop_2", transportStopName: "Main Chowk", transportTripType: "PICKUP_ONLY",
    transportMonthlyAmount: 950, transportStartMonth: "APR", transportEndMonth: "MAR",
    transportFeeOverridden: true, transportOverrideRemark: "Sibling half concession",
    transportPriorMonthlyAmount: 1100, transportFeeEffectiveFromMonth: "JUL",
    status: "ACTIVE", leftDate: "", leftReason: "", sample: false, createdAt: ts, updatedAt: ts,
  });

  await verifyMatrix({
    name: "students (with all transport fields)",
    moduleKey: "students",
    docPath: (id) => `organisations/${ORG_ID}/schools/${SCHOOL_1}/students/${id}`,
    listPath: `organisations/${ORG_ID}/schools/${SCHOOL_1}/students`,
    seedDoc: fullStudent,
    makeCreate: fullStudent,
    makeUpdate: () => ({
      transportRouteId: "route_south",
      transportRouteName: "South Line",
      transportTripType: "DROP_ONLY",
      transportMonthlyAmount: 700,
      transportPriorMonthlyAmount: 950,
      transportFeeEffectiveFromMonth: "AUG",
      updatedAt: ts,
    }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // Also test partial student updates via transport:edit and certificates:edit branches (expression count & field scoping)
  await seedScopedMembers("transport");
  const actors2 = actorDbs();
  await expectBool(
    actors2.EDIT.db.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_seed`).update({
      usesTransport: true,
      transportRouteId: "route_south",
      transportRouteName: "South Line",
      transportStopId: "stop_1",
      transportStopName: "Gate",
      transportTripType: "BOTH_WAYS",
      transportMonthlyAmount: 1000,
      transportStartMonth: "APR",
      transportEndMonth: "MAR",
      transportFeeOverridden: false,
      transportOverrideRemark: "",
      transportPriorMonthlyAmount: 0,
      transportFeeEffectiveFromMonth: "",
      updatedAt: ts,
    }),
    true,
    "students transport-only update [transport:edit]"
  );
  await expectBool(
    actors2.EDIT.db.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_seed`).update({
      name: "Illegal Name Change",
      updatedAt: ts,
    }),
    false,
    "students non-transport field denied for [transport:edit]"
  );

  // marks
  await verifyMatrix({
    name: "marks",
    moduleKey: "marks",
    docPath: (id) => `organisations/${ORG_ID}/schools/${SCHOOL_1}/marks/${id}`,
    listPath: `organisations/${ORG_ID}/schools/${SCHOOL_1}/marks`,
    seedDoc: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, className: "5", subjectName: "Maths", componentKey: "T1_EXAM", rawValue: "88", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, className: "5", subjectName: "Maths", componentKey: "T1_EXAM", rawValue: "88", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ rawValue: "91", updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // markEditLogs (append-only)
  await verifyMatrix({
    name: "markEditLogs",
    moduleKey: "marks",
    docPath: (id) => `organisations/${ORG_ID}/schools/${SCHOOL_1}/markEditLogs/${id}`,
    listPath: `organisations/${ORG_ID}/schools/${SCHOOL_1}/markEditLogs`,
    seedDoc: (uid) => ({ markDocId: "m_1", studentId: "stu_seed", studentName: "Seed Student", schoolId: SCHOOL_1, className: "5", subjectName: "Maths", componentKey: "T1_EXAM", oldValue: "80", newValue: "88", editorUid: uid, editorName: "Editor", clientEditedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ markDocId: "m_1", studentId: "stu_seed", studentName: "Seed Student", schoolId: SCHOOL_1, className: "5", subjectName: "Maths", componentKey: "T1_EXAM", oldValue: "80", newValue: "88", editorUid: uid, editorName: "Editor", clientEditedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ newValue: "99" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // coScholastic
  await verifyMatrix({
    name: "coScholastic",
    moduleKey: "coScholastic",
    docPath: (id) => `organisations/${ORG_ID}/schools/${SCHOOL_1}/coScholastic/${id}`,
    listPath: `organisations/${ORG_ID}/schools/${SCHOOL_1}/coScholastic`,
    seedDoc: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, termNo: 1, areaCode: "WORK_ED", grade: "A", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, termNo: 1, areaCode: "WORK_ED", grade: "A", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ grade: "B", updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // termInfo
  await verifyMatrix({
    name: "termInfo",
    moduleKey: "coScholastic",
    docPath: (id) => `organisations/${ORG_ID}/schools/${SCHOOL_1}/termInfo/${id}`,
    listPath: `organisations/${ORG_ID}/schools/${SCHOOL_1}/termInfo`,
    seedDoc: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, termNo: 1, daysPresent: 90, workingDays: 100, remarks: "Good", promotedTo: "", reportDate: "2026-10-01", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ studentId: "stu_seed", schoolId: SCHOOL_1, termNo: 1, daysPresent: 90, workingDays: 100, remarks: "Good", promotedTo: "", reportDate: "2026-10-01", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ remarks: "Very Good", updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });
});

test("3. Teachers, Timetable & Attendance modules equivalence", async () => {
  const ts = pastTs(3000);
  const todayIso = new Date().toISOString().slice(0, 10);
  const todayEpoch = Math.floor(Date.now() / 86400000);

  // teachers
  await verifyMatrix({
    name: "teachers",
    moduleKey: "teachers",
    docPath: (id) => `organisations/${ORG_ID}/teachers/${id}`,
    listPath: `organisations/${ORG_ID}/teachers`,
    seedDoc: () => ({ name: "Teacher A", mobile: "9000", email: "t@s.co", photoBase64: "", subjects: ["Eng"], schoolIds: [SCHOOL_1], maxPeriodsPerDay: 6, maxConsecutivePeriods: 3, weeklyOffDays: ["SUN"], active: true, linkedMemberUid: "", createdAt: ts, updatedAt: ts }),
    makeCreate: () => ({ name: "Teacher A", mobile: "9000", email: "t@s.co", photoBase64: "", subjects: ["Eng"], schoolIds: [SCHOOL_1], maxPeriodsPerDay: 6, maxConsecutivePeriods: 3, weeklyOffDays: ["SUN"], active: true, linkedMemberUid: "", createdAt: ts, updatedAt: ts }),
    makeUpdate: () => ({ name: "Teacher Updated", updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // periodStructures (keyed by schoolId)
  await verifyMatrix({
    name: "periodStructures",
    moduleKey: "teachers",
    docPath: () => `organisations/${ORG_ID}/periodStructures/${SCHOOL_1}`,
    listPath: `organisations/${ORG_ID}/periodStructures`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, workingDays: ["MON"], slotsByDay: { MON: [] }, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, workingDays: ["MON"], slotsByDay: { MON: [] }, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ workingDays: ["MON", "TUE"], updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // classAssignments
  await verifyMatrix({
    name: "classAssignments",
    moduleKey: "teachers",
    docPath: (id) => `organisations/${ORG_ID}/classAssignments/${id}`,
    listPath: `organisations/${ORG_ID}/classAssignments`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, className: "5", section: "A", classTeacherId: "tchr_seed", subjectAssignments: [], updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, className: "5", section: "A", classTeacherId: "tchr_seed", subjectAssignments: [], updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ classTeacherId: "t2", updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // subjectRules
  await verifyMatrix({
    name: "subjectRules",
    moduleKey: "teachers",
    docPath: (id) => `organisations/${ORG_ID}/subjectRules/${id}`,
    listPath: `organisations/${ORG_ID}/subjectRules`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, subjectName: "Maths", preferredTimeOfDay: "MORNING", allowDoublePeriod: false, maxOncePerDay: true, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, subjectName: "Maths", preferredTimeOfDay: "MORNING", allowDoublePeriod: false, maxOncePerDay: true, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ maxOncePerDay: false, updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // teacherScopes
  await verifyMatrix({
    name: "teacherScopes",
    moduleKey: "teachers",
    docPath: (id) => `organisations/${ORG_ID}/teacherScopes/${id}`,
    listPath: `organisations/${ORG_ID}/teacherScopes`,
    seedDoc: (uid, role, docId) => ({ uid: docId, teacherId: "tchr_seed", teacherName: "T", assignedClassSectionSubjects: [], classTeacherSections: [], updatedByUid: OWNER_UID, updatedAt: ts }),
    makeCreate: (uid, r, docId) => ({ uid: docId, teacherId: "tchr_seed", teacherName: "T", assignedClassSectionSubjects: [], classTeacherSections: [], updatedByUid: uid, updatedAt: ts }),
    makeUpdate: (uid) => ({ teacherName: "T2", updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // timetableCells
  await verifyMatrix({
    name: "timetableCells",
    moduleKey: "timetable",
    docPath: (id) => `organisations/${ORG_ID}/timetableCells/${id}`,
    listPath: `organisations/${ORG_ID}/timetableCells`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", day: "MON", slotId: "p1", subjectName: "Maths", teacherId: "tchr_seed", teacherName: "T", locked: false, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", day: "MON", slotId: "p1", subjectName: "Maths", teacherId: "tchr_seed", teacherName: "T", locked: false, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ subjectName: "Science", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // timetableLayouts
  await verifyMatrix({
    name: "timetableLayouts",
    moduleKey: "timetable",
    docPath: () => `organisations/${ORG_ID}/timetableLayouts/${SCHOOL_1}`,
    listPath: `organisations/${ORG_ID}/timetableLayouts`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, workingDays: ["MON"], slotsByDay: { MON: [] }, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, workingDays: ["MON"], slotsByDay: { MON: [] }, updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ workingDays: ["MON", "WED"], updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // teacherLeaves
  await verifyMatrix({
    name: "teacherLeaves",
    moduleKey: "timetable",
    docPath: (id) => `organisations/${ORG_ID}/teacherLeaves/${id}`,
    listPath: `organisations/${ORG_ID}/teacherLeaves`,
    seedDoc: (uid) => ({ teacherId: "tchr_seed", teacherName: "T", startDate: todayIso, endDate: todayIso, reasonType: "SICK", reasonNote: "Fever", halfDay: false, halfDaySlotIds: [], status: "PENDING", requestedByUid: uid, requestedByName: "U", approvedByUid: "", approvedByName: "", approvedAtMs: 0, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ teacherId: "tchr_seed", teacherName: "T", startDate: todayIso, endDate: todayIso, reasonType: "SICK", reasonNote: "Fever", halfDay: false, halfDaySlotIds: [], status: "PENDING", requestedByUid: uid, requestedByName: "U", approvedByUid: "", approvedByName: "", approvedAtMs: 0, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "APPROVED", approvedByUid: uid, approvedByName: "U", approvedAtMs: 2000, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // substitutions
  await verifyMatrix({
    name: "substitutions",
    moduleKey: "timetable",
    docPath: (id) => `organisations/${ORG_ID}/substitutions/${id}`,
    listPath: `organisations/${ORG_ID}/substitutions`,
    seedDoc: (uid) => ({ date: todayIso, day: "MON", schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", slotId: "p1", slotLabel: "P1", startMinutes: 480, endMinutes: 520, subjectName: "Maths", absentTeacherId: "tchr_seed", absentTeacherName: "T1", leaveId: "", substituteTeacherId: "t2", substituteTeacherName: "T2", overrideUsed: false, overrideReason: "", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ date: todayIso, day: "MON", schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", slotId: "p1", slotLabel: "P1", startMinutes: 480, endMinutes: 520, subjectName: "Maths", absentTeacherId: "tchr_seed", absentTeacherName: "T1", leaveId: "", substituteTeacherId: "t2", substituteTeacherName: "T2", overrideUsed: false, overrideReason: "", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ substituteTeacherId: "t3", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // timetableUndoSnapshots (edit-only even for read!)
  await verifyMatrix({
    name: "timetableUndoSnapshots",
    moduleKey: "timetable",
    docPath: (id) => `organisations/${ORG_ID}/timetableUndoSnapshots/${id}`,
    listPath: `organisations/${ORG_ID}/timetableUndoSnapshots`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", scopeLabel: "All", classNameFilter: "", sectionFilter: "", seedUsed: 1, previousCells: [], affectedCellIds: [], createdByUid: uid, createdByName: "U", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", scopeLabel: "All", classNameFilter: "", sectionFilter: "", seedUsed: 1, previousCells: [], affectedCellIds: [], createdByUid: uid, createdByName: "U", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: () => ({ scopeLabel: "Class 5", updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // teacherAttendance
  await verifyMatrix({
    name: "teacherAttendance",
    moduleKey: "timetable",
    docPath: (id) => `organisations/${ORG_ID}/teacherAttendance/${id}`,
    listPath: `organisations/${ORG_ID}/teacherAttendance`,
    seedDoc: (uid) => ({ date: todayIso, schoolId: SCHOOL_1, teacherId: "tchr_seed", teacherName: "T", status: "PRESENT", note: "", linkedLeaveId: "", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ date: todayIso, schoolId: SCHOOL_1, teacherId: "tchr_seed", teacherName: "T", status: "PRESENT", note: "", linkedLeaveId: "", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "ABSENT", updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // schoolCalendars
  await verifyMatrix({
    name: "schoolCalendars",
    moduleKey: "attendance",
    docPath: (id) => `organisations/${ORG_ID}/schoolCalendars/${id}`,
    listPath: `organisations/${ORG_ID}/schoolCalendars`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", workingDays: ["MON"], holidays: [], halfWorkingDays: [], workingDayExceptions: [], term1StartDate: "2026-04-01", term1EndDate: "2026-09-30", term2StartDate: "2026-10-01", term2EndDate: "2027-03-31", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", workingDays: ["MON"], holidays: [], halfWorkingDays: [], workingDayExceptions: [], term1StartDate: "2026-04-01", term1EndDate: "2026-09-30", term2StartDate: "2026-10-01", term2EndDate: "2027-03-31", updatedByUid: uid, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ workingDays: ["MON", "TUE"], updatedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // studentAttendance
  await verifyMatrix({
    name: "studentAttendance",
    moduleKey: "attendance",
    docPath: (id) => `organisations/${ORG_ID}/studentAttendance/${id}`,
    listPath: `organisations/${ORG_ID}/studentAttendance`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", date: todayIso, dateEpochDay: todayEpoch, entries: { stu_seed: "P" }, presentCount: 1, absentCount: 0, leaveCount: 0, lateCount: 0, halfDayCount: 0, totalStudents: 1, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", date: todayIso, dateEpochDay: todayEpoch, entries: { stu_seed: "P" }, presentCount: 1, absentCount: 0, leaveCount: 0, lateCount: 0, halfDayCount: 0, totalStudents: 1, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ entries: { stu_seed: "A" }, presentCount: 0, absentCount: 1, updatedByUid: uid, updatedByName: "U", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // studentAttendanceEditLogs (append-only)
  await verifyMatrix({
    name: "studentAttendanceEditLogs",
    moduleKey: "attendance",
    docPath: (id) => `organisations/${ORG_ID}/studentAttendanceEditLogs/${id}`,
    listPath: `organisations/${ORG_ID}/studentAttendanceEditLogs`,
    seedDoc: (uid) => ({ attendanceDocId: "att_1", schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", date: todayIso, editorUid: uid, editorName: "U", summary: "Marked", changes: [{ id: "stu_seed" }], clientEditedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ attendanceDocId: "att_1", schoolId: SCHOOL_1, session: "2026-2027", className: "5", section: "A", date: todayIso, editorUid: uid, editorName: "U", summary: "Marked", changes: [{ id: "stu_seed" }], clientEditedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ summary: "Hacked" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });
});

test("4. Fees, Certificates & Cash Book modules equivalence (structures, overrides, receipts, entries, closings)", async () => {
  const ts = pastTs(3000);
  const todayIso = new Date().toISOString().slice(0, 10);

  // feeStructures
  await verifyMatrix({
    name: "feeStructures",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/feeStructures/${id}`,
    listPath: `organisations/${ORG_ID}/feeStructures`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", monthOrder: ["APR"], chargedTuitionMonths: ["APR"], chargedTransportMonths: ["APR"], tuitionByClassAndMonth: {}, examFees: [], optionalCatalogue: [], frozenPastCellAmountsByClass: {}, maxConcessionPercentWithoutAdminApproval: 20, messageTemplates: {}, updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", monthOrder: ["APR"], chargedTuitionMonths: ["APR"], chargedTransportMonths: ["APR"], tuitionByClassAndMonth: {}, examFees: [], optionalCatalogue: [], frozenPastCellAmountsByClass: {}, maxConcessionPercentWithoutAdminApproval: 20, messageTemplates: {}, updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ maxConcessionPercentWithoutAdminApproval: 10, updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // studentFeeOverrides
  await verifyMatrix({
    name: "studentFeeOverrides",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/studentFeeOverrides/${id}`,
    listPath: `organisations/${ORG_ID}/studentFeeOverrides`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentName: "Seed Student", cellOverrides: {}, remark: "Merit scholarship", updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentName: "Seed Student", cellOverrides: {}, remark: "Merit scholarship", updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ remark: "Updated scholarship", updatedByUid: uid, updatedByName: "Fee Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // feeReceipts (create + cancel only; no delete)
  const makeReceipt = (uid, r = "X") => ({
    schoolId: SCHOOL_1, session: "2026-2027", receiptNumber: `REC-${r}-001`, deviceNodeCode: "D1", sequenceNo: 1,
    receiptDateIso: todayIso, studentId: "stu_seed", studentName: "Seed Student", studentAdmNo: "ADM001", fatherName: "Parent",
    className: "5", section: "A", rollNo: 1, lineItems: [{ id: "li1", amount: 1000 }],
    totalBeforeConcession: 1000, concessionMode: "NONE", concessionScope: "WHOLE_RECEIPT",
    concessionTargetLineId: "", concessionInputValue: 0, concessionAmount: 0, concessionRemark: "",
    concessionGivenByUid: "", concessionGivenByName: "", concessionApprovedByAdminUid: "", concessionApprovedByAdminName: "",
    netPayableAmount: 1000, amountPaid: 1000, balanceDueOnReceipt: 0, paymentMode: "CASH", paymentNote: "",
    status: "ISSUED", receivedByUid: uid, receivedByName: "Fee Clerk", issuedAtMs: 1000,
    cancelledAtMs: 0, cancelledByUid: "", cancelledByName: "", cancelReason: "", createdAt: ts, updatedAt: ts,
  });
  await verifyMatrix({
    name: "feeReceipts (save & cancel)",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/feeReceipts/${id}`,
    listPath: `organisations/${ORG_ID}/feeReceipts`,
    seedDoc: makeReceipt,
    makeCreate: makeReceipt,
    makeUpdate: (uid) => ({ status: "CANCELLED", cancelledAtMs: 2000, cancelledByUid: uid, cancelledByName: "Clerk", cancelReason: "Wrong entry", updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // feeMessageSentLogs, schoolNotices, feeAuditLogs
  await verifyMatrix({
    name: "feeMessageSentLogs",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/feeMessageSentLogs/${id}`,
    listPath: `organisations/${ORG_ID}/feeMessageSentLogs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentName: "Seed", className: "5", section: "A", guardianName: "Parent", normalizedMobile: "919000000002", messageType: "FEE_DUE_REMINDER", contextKey: "DUE_AUG", messageText: "Fee due Rs 1000", channel: "WHATSAPP", sentByUid: uid, sentByName: "Clerk", sentAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentName: "Seed", className: "5", section: "A", guardianName: "Parent", normalizedMobile: "919000000002", messageType: "FEE_DUE_REMINDER", contextKey: "DUE_AUG", messageText: "Fee due Rs 1000", channel: "WHATSAPP", sentByUid: uid, sentByName: "Clerk", sentAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ messageText: "Updated text", sentByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "schoolNotices",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/schoolNotices/${id}`,
    listPath: `organisations/${ORG_ID}/schoolNotices`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, title: "Fee Notice", bodyText: "Pay by 10th", noticeDateIso: todayIso, createdByUid: uid, createdByName: "Clerk", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, title: "Fee Notice", bodyText: "Pay by 10th", noticeDateIso: todayIso, createdByUid: uid, createdByName: "Clerk", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ bodyText: "Pay by 15th", createdByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "feeAuditLogs",
    moduleKey: "fees",
    docPath: (id) => `organisations/${ORG_ID}/feeAuditLogs/${id}`,
    listPath: `organisations/${ORG_ID}/feeAuditLogs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", action: "RECEIPT_ISSUED", targetId: "r1", studentId: "stu_seed", studentName: "Seed", summary: "Issued", actorUid: uid, actorName: "Clerk", clientCreatedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", action: "RECEIPT_ISSUED", targetId: "r1", studentId: "stu_seed", studentName: "Seed", summary: "Issued", actorUid: uid, actorName: "Clerk", clientCreatedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ summary: "Changed" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // schoolCertificates
  await verifyMatrix({
    name: "schoolCertificates",
    moduleKey: "certificates",
    docPath: (id) => `organisations/${ORG_ID}/schoolCertificates/${id}`,
    listPath: `organisations/${ORG_ID}/schoolCertificates`,
    seedDoc: (uid, r = "X") => ({ schoolId: SCHOOL_1, session: "2026-2027", certificateType: "BONAFIDE", certificateNumber: `CERT-${r}-1`, deviceNodeCode: "D1", sequenceNo: 1, issueDateIso: todayIso, studentId: "stu_seed", studentName: "Seed Student", studentAdmNo: "ADM001", fatherName: "Parent", motherName: "Mother", dob: "2015-01-01", className: "5", section: "A", rollNo: 1, admissionDate: "2024-04-01", address: "Street", reasonForLeaving: "", lastAttendanceDateIso: todayIso, conduct: "Good", promotionStatus: "Promoted", feeDuesStatus: "Cleared", remarks: "", status: "ISSUED", issuedByUid: uid, issuedByName: "Clerk", issuedAtMs: 1000, cancelledAtMs: 0, cancelledByUid: "", cancelledByName: "", cancelReason: "", reissuedFromCertId: "", reissuedFromCertNumber: "", createdAt: ts, updatedAt: ts }),
    makeCreate: (uid, r) => ({ schoolId: SCHOOL_1, session: "2026-2027", certificateType: "BONAFIDE", certificateNumber: `CERT-${r}-1`, deviceNodeCode: "D1", sequenceNo: 1, issueDateIso: todayIso, studentId: "stu_seed", studentName: "Seed Student", studentAdmNo: "ADM001", fatherName: "Parent", motherName: "Mother", dob: "2015-01-01", className: "5", section: "A", rollNo: 1, admissionDate: "2024-04-01", address: "Street", reasonForLeaving: "", lastAttendanceDateIso: todayIso, conduct: "Good", promotionStatus: "Promoted", feeDuesStatus: "Cleared", remarks: "", status: "ISSUED", issuedByUid: uid, issuedByName: "Clerk", issuedAtMs: 1000, cancelledAtMs: 0, cancelledByUid: "", cancelledByName: "", cancelReason: "", reissuedFromCertId: "", reissuedFromCertNumber: "", createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "CANCELLED", cancelledAtMs: 2000, cancelledByUid: uid, cancelledByName: "Clerk", cancelReason: "Typo", updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // cashBookSettings, cashBookEntries, cashBookDayClosings, cashBookAuditLogs
  await verifyMatrix({
    name: "cashBookSettings",
    moduleKey: "cashBook",
    docPath: () => `organisations/${ORG_ID}/cashBookSettings/cbset_${SCHOOL_1}`,
    listPath: `organisations/${ORG_ID}/cashBookSettings`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, expenseCategories: ["Stationery"], otherIncomeCategories: ["Grant"], expenseApprovalLimitRs: 5000, defaultOpeningBalanceRs: 0, updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, expenseCategories: ["Stationery"], otherIncomeCategories: ["Grant"], expenseApprovalLimitRs: 5000, defaultOpeningBalanceRs: 0, updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ expenseCategories: ["Stationery", "Repairs"], updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  const makeCbEntry = (uid) => ({
    schoolId: SCHOOL_1, dateIso: todayIso, entryType: "EXPENSE", category: "Stationery", amount: 850,
    paymentMode: "CASH", note: "Chalks", billPhotoBase64: "", status: "ACTIVE",
    approvedByAdminUid: "", approvedByAdminName: "", approvalNote: "",
    createdByUid: uid, createdByName: "Clerk", createdAtMs: 1000,
    updatedByUid: uid, updatedByName: "Clerk", lastEditReason: "", clientEditedAtMs: 1000,
    cancelledAtMs: 0, cancelledByUid: "", cancelledByName: "", cancelReason: "", createdAt: ts, updatedAt: ts,
  });
  await verifyMatrix({
    name: "cashBookEntries",
    moduleKey: "cashBook",
    docPath: (id) => `organisations/${ORG_ID}/cashBookEntries/${id}`,
    listPath: `organisations/${ORG_ID}/cashBookEntries`,
    seedDoc: makeCbEntry,
    makeCreate: makeCbEntry,
    makeUpdate: (uid) => ({ amount: 900, lastEditReason: "Bill updated", updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "cashBookDayClosings",
    moduleKey: "cashBook",
    docPath: (id) => `organisations/${ORG_ID}/cashBookDayClosings/${id}`,
    listPath: `organisations/${ORG_ID}/cashBookDayClosings`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, dateIso: todayIso, openingCashBalance: 1000, cashInTotal: 500, cashOutTotal: 200, expectedClosingCash: 1300, countedCash: 1300, cashDifference: 0, nonCashInTotal: 0, nonCashOutTotal: 0, note: "Balanced", closedByUid: uid, closedByName: "Clerk", closedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, dateIso: todayIso, openingCashBalance: 1000, cashInTotal: 500, cashOutTotal: 200, expectedClosingCash: 1300, countedCash: 1300, cashDifference: 0, nonCashInTotal: 0, nonCashOutTotal: 0, note: "Balanced", closedByUid: uid, closedByName: "Clerk", closedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ note: "Recounted", closedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "cashBookAuditLogs",
    moduleKey: "cashBook",
    docPath: (id) => `organisations/${ORG_ID}/cashBookAuditLogs/${id}`,
    listPath: `organisations/${ORG_ID}/cashBookAuditLogs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, action: "ENTRY_CREATED", targetId: "cbe1", dateIso: todayIso, amount: 850, reason: "", summary: "Stationery", actorUid: uid, actorName: "Clerk", clientCreatedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, action: "ENTRY_CREATED", targetId: "cbe1", dateIso: todayIso, amount: 850, reason: "", summary: "Stationery", actorUid: uid, actorName: "Clerk", clientCreatedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ summary: "Modified" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });
});

test("5. Enquiry, Exams, Stock & Transport modules equivalence (routes, vehicles, settings)", async () => {
  const ts = pastTs(3000);
  const todayIso = new Date().toISOString().slice(0, 10);

  // enquirySettings & admissionEnquiries
  await verifyMatrix({
    name: "enquirySettings",
    moduleKey: "enquiry",
    docPath: (id) => `organisations/${ORG_ID}/enquirySettings/${id}`,
    listPath: `organisations/${ORG_ID}/enquirySettings`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, sources: ["Walk-in"], whatsappTemplate: "Hello", updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, sources: ["Walk-in"], whatsappTemplate: "Hello", updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ sources: ["Walk-in", "Referral"], updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "admissionEnquiries",
    moduleKey: "enquiry",
    docPath: (id) => `organisations/${ORG_ID}/admissionEnquiries/${id}`,
    listPath: `organisations/${ORG_ID}/admissionEnquiries`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", childName: "Rohan", classWanted: "1", guardianName: "Mohan", mobile: "9000000011", source: "Walk-in", notes: "", enquiryDateIso: todayIso, nextFollowUpDateIso: todayIso, status: "NEW", lostReason: "", convertedStudentDocId: "", convertedStudentAdmNo: "", convertedAtMs: 0, createdByUid: uid, createdByName: "Desk", createdAtMs: 1000, updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", childName: "Rohan", classWanted: "1", guardianName: "Mohan", mobile: "9000000011", source: "Walk-in", notes: "", enquiryDateIso: todayIso, nextFollowUpDateIso: todayIso, status: "NEW", lostReason: "", convertedStudentDocId: "", convertedStudentAdmNo: "", convertedAtMs: 0, createdByUid: uid, createdByName: "Desk", createdAtMs: 1000, updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "CONTACTED", updatedByUid: uid, updatedByName: "Desk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // exams: examSchedules, examRooms, examSeatings, examInvigilatorDuties
  await verifyMatrix({
    name: "examSchedules",
    moduleKey: "exams",
    docPath: (id) => `organisations/${ORG_ID}/examSchedules/${id}`,
    listPath: `organisations/${ORG_ID}/examSchedules`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examType: "HALF_YEARLY", title: "Half Yearly", entries: [], withholdAdmitCardAboveDuesRs: 0, admitCardOverrideStudentIds: [], admitCardInstructions: "", createdByUid: uid, updatedByUid: uid, updatedByName: "Exam", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examType: "HALF_YEARLY", title: "Half Yearly", entries: [], withholdAdmitCardAboveDuesRs: 0, admitCardOverrideStudentIds: [], admitCardInstructions: "", createdByUid: uid, updatedByUid: uid, updatedByName: "Exam", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ title: "Half Yearly Updated", updatedByUid: uid, updatedByName: "Exam", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "examRooms",
    moduleKey: "exams",
    docPath: (id) => `organisations/${ORG_ID}/examRooms/${id}`,
    listPath: `organisations/${ORG_ID}/examRooms`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, roomName: "Hall A", capacity: 40, active: true, updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, roomName: "Hall A", capacity: 40, active: true, updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ capacity: 45, updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "examSeatings",
    moduleKey: "exams",
    docPath: (id) => `organisations/${ORG_ID}/examSeatings/${id}`,
    listPath: `organisations/${ORG_ID}/examSeatings`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examId: "ex_1", allocationMode: "BY_ROLL_NUMBER", includedClasses: ["5"], roomIds: ["rm_1"], allocations: [], unallocatedStudentIds: [], updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examId: "ex_1", allocationMode: "BY_ROLL_NUMBER", includedClasses: ["5"], roomIds: ["rm_1"], allocations: [], unallocatedStudentIds: [], updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ allocationMode: "MIXED_BY_CLASS", updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "examInvigilatorDuties",
    moduleKey: "exams",
    docPath: (id) => `organisations/${ORG_ID}/examInvigilatorDuties/${id}`,
    listPath: `organisations/${ORG_ID}/examInvigilatorDuties`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examId: "ex_1", examTitle: "HY", dateIso: todayIso, startTime: "09:00", endTime: "12:00", roomId: "rm_1", roomName: "Hall A", teacherId: "tchr_seed", teacherUid: "", teacherName: "T", assignedManually: false, updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", examId: "ex_1", examTitle: "HY", dateIso: todayIso, startTime: "09:00", endTime: "12:00", roomId: "rm_1", roomName: "Hall A", teacherId: "tchr_seed", teacherUid: "", teacherName: "T", assignedManually: false, updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ assignedManually: true, updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // stockTransactions
  await verifyMatrix({
    name: "stockTransactions",
    moduleKey: "stock",
    docPath: (id) => `organisations/${ORG_ID}/stockTransactions/${id}`,
    listPath: `organisations/${ORG_ID}/stockTransactions`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", itemId: "item_book", itemName: "Notebook", txType: "STOCK_IN", dateIso: todayIso, quantityDelta: 50, unitCostRs: 20, totalCostRs: 1000, supplier: "Books Co", reason: "", createdByUid: uid, createdByName: "Store", createdAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", itemId: "item_book", itemName: "Notebook", txType: "STOCK_IN", dateIso: todayIso, quantityDelta: 50, unitCostRs: 20, totalCostRs: 1000, supplier: "Books Co", reason: "", createdByUid: uid, createdByName: "Store", createdAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ quantityDelta: 100 }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // transportRoutes, transportVehicleDocuments, transportSettings
  await verifyMatrix({
    name: "transportRoutes",
    moduleKey: "transport",
    docPath: (id) => `organisations/${ORG_ID}/transportRoutes/${id}`,
    listPath: `organisations/${ORG_ID}/transportRoutes`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, routeName: "North Route", vehicleNumber: "UP01AB1234", vehicleType: "Bus", seatCapacity: 40, driverName: "Mohan", driverPhone: "9800000001", monthlyFeeRs: 1000, pickupOnlyFeeRs: 650, dropOnlyFeeRs: 600, vehicleOwnership: "RENTED", ownerContractorName: "Travels", ownerContractorPhone: "9800000002", monthlyRentRs: 18000, driverLicenceNo: "DL123", driverLicenceExpiryIso: "2029-12-31", conductorName: "Sohan", conductorPhone: "9800000003", stops: [], active: true, updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, routeName: "North Route", vehicleNumber: "UP01AB1234", vehicleType: "Bus", seatCapacity: 40, driverName: "Mohan", driverPhone: "9800000001", monthlyFeeRs: 1000, pickupOnlyFeeRs: 650, dropOnlyFeeRs: 600, vehicleOwnership: "RENTED", ownerContractorName: "Travels", ownerContractorPhone: "9800000002", monthlyRentRs: 18000, driverLicenceNo: "DL123", driverLicenceExpiryIso: "2029-12-31", conductorName: "Sohan", conductorPhone: "9800000003", stops: [], active: true, updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ monthlyFeeRs: 1100, updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "transportVehicleDocuments",
    moduleKey: "transport",
    docPath: (id) => `organisations/${ORG_ID}/transportVehicleDocuments/${id}`,
    listPath: `organisations/${ORG_ID}/transportVehicleDocuments`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, routeId: "route_1", vehicleNumber: "UP01AB1234", docType: "INSURANCE", documentNumber: "INS-01", expiryDateIso: "2028-01-01", photoBase64: "", note: "", updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, routeId: "route_1", vehicleNumber: "UP01AB1234", docType: "INSURANCE", documentNumber: "INS-01", expiryDateIso: "2028-01-01", photoBase64: "", note: "", updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ note: "Renewed", updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "transportSettings",
    moduleKey: "transport",
    docPath: (id) => `organisations/${ORG_ID}/transportSettings/${id}`,
    listPath: `organisations/${ORG_ID}/transportSettings`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, expenseCategories: ["Diesel"], updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, expenseCategories: ["Diesel"], updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ expenseCategories: ["Diesel", "Repair"], updatedByUid: uid, updatedByName: "Clerk", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });
});

test("6. Salary (owner-first) & Session Promotion / Logs equivalence", async () => {
  const ts = pastTs(3000);
  const todayIso = new Date().toISOString().slice(0, 10);

  // Notice ADMIN without salary perm is DENIED on salary collections!
  await verifyMatrix({
    name: "salaryRuleConfigs (ADMIN needs explicit salary perm)",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/salaryRuleConfigs/${id}`,
    listPath: `organisations/${ORG_ID}/salaryRuleConfigs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, perDayBasis: "FIXED_30_DAYS", freeLeavesPerMonth: 2, halfDayAbsenceWeight: 0.5, halfDayConsumesFreeLeave: true, lateDaysPerHalfDayPenalty: 3, fixedPerAbsentDayDeductionRs: 0, defaultAllowances: [], defaultDeductions: [], allowTeachersToViewOwnSlips: false, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, perDayBasis: "FIXED_30_DAYS", freeLeavesPerMonth: 2, halfDayAbsenceWeight: 0.5, halfDayConsumesFreeLeave: true, lateDaysPerHalfDayPenalty: 3, fixedPerAbsentDayDeductionRs: 0, defaultAllowances: [], defaultDeductions: [], allowTeachersToViewOwnSlips: false, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ freeLeavesPerMonth: 3, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "staffSalaryProfiles",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/staffSalaryProfiles/${id}`,
    listPath: `organisations/${ORG_ID}/staffSalaryProfiles`,
    seedDoc: (uid) => ({ staffId: "stf_1", staffName: "Staff One", designation: "Teacher", isTeachingStaff: true, linkedMemberUid: "", joiningDateIso: "2024-04-01", allSchools: false, schoolIds: [SCHOOL_1], primarySchoolId: SCHOOL_1, basicMonthlySalaryRs: 25000, allowances: [], fixedDeductions: [], active: true, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ staffId: "stf_1", staffName: "Staff One", designation: "Teacher", isTeachingStaff: true, linkedMemberUid: "", joiningDateIso: "2024-04-01", allSchools: false, schoolIds: [SCHOOL_1], primarySchoolId: SCHOOL_1, basicMonthlySalaryRs: 25000, allowances: [], fixedDeductions: [], active: true, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ basicMonthlySalaryRs: 26000, updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "salaryMonthlyRecords",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/salaryMonthlyRecords/${id}`,
    listPath: `organisations/${ORG_ID}/salaryMonthlyRecords`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", staffId: "stf_1", staffName: "Staff One", designation: "Teacher", staffUid: "", joiningDateIso: "2024-04-01", workingDaysInMonth: 26, presentDays: 25, absentDays: 1, onLeaveDays: 0, halfDays: 0, lateDays: 0, freeLeavesApplied: 1, unpaidAbsenceDays: 0, perDayRateRs: 833, basicSalaryRs: 25000, allowances: [], totalAllowancesRs: 0, grossEarningsRs: 25000, attendanceDeductionRs: 0, fixedDeductions: [], totalFixedDeductionsRs: 0, advanceDeductionRs: 0, linkedAdvanceIds: [], manualAdjustmentRs: 0, adjustmentRemark: "", netPayableRs: 25000, status: "PENDING", paidDateIso: "", paymentMode: "CASH", paymentReference: "", teacherSelfViewEnabled: false, lastLockOverrideReason: "", updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", staffId: "stf_1", staffName: "Staff One", designation: "Teacher", staffUid: "", joiningDateIso: "2024-04-01", workingDaysInMonth: 26, presentDays: 25, absentDays: 1, onLeaveDays: 0, halfDays: 0, lateDays: 0, freeLeavesApplied: 1, unpaidAbsenceDays: 0, perDayRateRs: 833, basicSalaryRs: 25000, allowances: [], totalAllowancesRs: 0, grossEarningsRs: 25000, attendanceDeductionRs: 0, fixedDeductions: [], totalFixedDeductionsRs: 0, advanceDeductionRs: 0, linkedAdvanceIds: [], manualAdjustmentRs: 0, adjustmentRemark: "", netPayableRs: 25000, status: "PENDING", paidDateIso: "", paymentMode: "CASH", paymentReference: "", teacherSelfViewEnabled: false, lastLockOverrideReason: "", updatedByUid: uid, updatedByName: "Pay", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "PAID", paidDateIso: todayIso, updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "staffSalaryAdvances",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/staffSalaryAdvances/${id}`,
    listPath: `organisations/${ORG_ID}/staffSalaryAdvances`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, staffId: "stf_1", staffName: "Staff One", staffUid: "", advanceDateIso: todayIso, principalAmountRs: 5000, monthlyInstalmentRs: 1000, repaidAmountRs: 0, repayments: [], paymentMode: "CASH", note: "", status: "ACTIVE", createdByUid: uid, createdByName: "Pay", updatedByUid: uid, clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, staffId: "stf_1", staffName: "Staff One", staffUid: "", advanceDateIso: todayIso, principalAmountRs: 5000, monthlyInstalmentRs: 1000, repaidAmountRs: 0, repayments: [], paymentMode: "CASH", note: "", status: "ACTIVE", createdByUid: uid, createdByName: "Pay", updatedByUid: uid, clientEditedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ repaidAmountRs: 1000, updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "salaryMonthLocks (OWNER-only write/delete)",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/salaryMonthLocks/${id}`,
    listPath: `organisations/${ORG_ID}/salaryMonthLocks`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", locked: true, lockedByUid: uid, lockedByName: "Owner", lockedAtMs: 1000, note: "Closed", updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", locked: true, lockedByUid: uid, lockedByName: "Owner", lockedAtMs: 1000, note: "Closed", updatedByUid: uid, clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ note: "Reopened note", updatedByUid: uid, clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "salaryAuditLogs",
    moduleKey: "salary",
    docPath: (id) => `organisations/${ORG_ID}/salaryAuditLogs/${id}`,
    listPath: `organisations/${ORG_ID}/salaryAuditLogs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", staffId: "stf_1", staffName: "Staff One", action: "PAID", reason: "", summary: "Paid August", amountRs: 25000, actorUid: uid, actorName: "Pay", clientCreatedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, yearMonth: "2026-08", staffId: "stf_1", staffName: "Staff One", action: "PAID", reason: "", summary: "Paid August", amountRs: 25000, actorUid: uid, actorName: "Pay", clientCreatedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ summary: "Hacked" }),
    allowRead:   { OWNER: true, ADMIN: false, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: false, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // sessionPromotion: schoolClassOrders, sessionTransitions, promotionRecords
  await verifyMatrix({
    name: "schoolClassOrders",
    moduleKey: "sessionPromotion",
    docPath: () => `organisations/${ORG_ID}/schoolClassOrders/${SCHOOL_1}`,
    listPath: `organisations/${ORG_ID}/schoolClassOrders`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, orderedClassNames: ["1", "2"], updatedByUid: uid, updatedByName: "Promo", clientEditedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, orderedClassNames: ["1", "2"], updatedByUid: uid, updatedByName: "Promo", clientEditedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ orderedClassNames: ["1", "2", "3"], updatedByUid: uid, updatedByName: "Promo", clientEditedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "sessionTransitions",
    moduleKey: "sessionPromotion",
    docPath: (id) => `organisations/${ORG_ID}/sessionTransitions/${id}`,
    listPath: `organisations/${ORG_ID}/sessionTransitions`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, fromSession: "2026-2027", toSession: "2027-2028", startDateIso: "2027-04-01", endDateIso: "2028-03-31", status: "COMPLETED", copyOptions: {}, totalStudentsEvaluated: 10, promotedCount: 9, gracePromotedCount: 1, detainedCount: 0, leftCount: 0, passedOutCount: 0, totalDuesCarriedForwardRs: 0, totalBatches: 1, completedBatches: 1, createdStudentDocIds: [], createdFeeOverrideDocIds: [], createdPromotionRecordIds: [], createdFeeStructureDocId: "", createdCalendarDocId: "", previousCurrentSession: "2026-2027", previousSessionsCsv: "2026-2027", appliedByUid: uid, appliedByName: "Promo", appliedAtMs: 1000, undoExpiresAtMs: 99999999, undoneByUid: "", undoneByName: "", undoneAtMs: 0, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, fromSession: "2026-2027", toSession: "2027-2028", startDateIso: "2027-04-01", endDateIso: "2028-03-31", status: "COMPLETED", copyOptions: {}, totalStudentsEvaluated: 10, promotedCount: 9, gracePromotedCount: 1, detainedCount: 0, leftCount: 0, passedOutCount: 0, totalDuesCarriedForwardRs: 0, totalBatches: 1, completedBatches: 1, createdStudentDocIds: [], createdFeeOverrideDocIds: [], createdPromotionRecordIds: [], createdFeeStructureDocId: "", createdCalendarDocId: "", previousCurrentSession: "2026-2027", previousSessionsCsv: "2026-2027", appliedByUid: uid, appliedByName: "Promo", appliedAtMs: 1000, undoExpiresAtMs: 99999999, undoneByUid: "", undoneByName: "", undoneAtMs: 0, updatedAt: ts }),
    makeUpdate: () => ({ status: "COMPLETED", updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "promotionRecords",
    moduleKey: "sessionPromotion",
    docPath: (id) => `organisations/${ORG_ID}/promotionRecords/${id}`,
    listPath: `organisations/${ORG_ID}/promotionRecords`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, transitionId: "tr_1", fromSession: "2026-2027", toSession: "2027-2028", oldStudentDocId: "stu_seed", newStudentDocId: "stu_new", admNo: "ADM001", studentName: "Seed Student", fatherName: "Parent", mobile: "9000", fromClassName: "5", fromSection: "A", fromRollNo: 1, toClassName: "6", toSection: "A", toRollNo: 1, annualPercent: 85, annualResultStatus: "PASS", decision: "PROMOTED", isAlumni: false, graceRemark: "", graceApprovedByUid: "", graceApprovedByName: "", carriedForwardDueRs: 0, confirmedByUid: uid, confirmedByName: "Promo", confirmedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, transitionId: "tr_1", fromSession: "2026-2027", toSession: "2027-2028", oldStudentDocId: "stu_seed", newStudentDocId: "stu_new", admNo: "ADM001", studentName: "Seed Student", fatherName: "Parent", mobile: "9000", fromClassName: "5", fromSection: "A", fromRollNo: 1, toClassName: "6", toSection: "A", toRollNo: 1, annualPercent: 85, annualResultStatus: "PASS", decision: "PROMOTED", isAlumni: false, graceRemark: "", graceApprovedByUid: "", graceApprovedByName: "", carriedForwardDueRs: 0, confirmedByUid: uid, confirmedByName: "Promo", confirmedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ toRollNo: 2, confirmedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // bulkImportLogs & backupExportLogs (OWNER/ADMIN read/create; OWNER delete; no update)
  await verifyMatrix({
    name: "bulkImportLogs (admin only)",
    moduleKey: "students",
    docPath: (id) => `organisations/${ORG_ID}/bulkImportLogs/${id}`,
    listPath: `organisations/${ORG_ID}/bulkImportLogs`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", fileName: "stu.csv", totalRows: 10, addedCount: 10, updatedCount: 0, skippedCount: 0, errorCount: 0, updateMatchingEnabled: false, actorUid: uid, actorName: "Admin", importedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", fileName: "stu.csv", totalRows: 10, addedCount: 10, updatedCount: 0, skippedCount: 0, errorCount: 0, updateMatchingEnabled: false, actorUid: uid, actorName: "Admin", importedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ totalRows: 11 }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "backupExportLogs (admin only)",
    moduleKey: "settings",
    docPath: (id) => `organisations/${ORG_ID}/backupExportLogs/${id}`,
    listPath: `organisations/${ORG_ID}/backupExportLogs`,
    seedDoc: (uid, r = "OWNER") => ({ scope: "SCHOOL", schoolId: SCHOOL_1, fileName: "bak.zip", includedSalaryCsv: false, studentCount: 10, receiptCount: 2, actorUid: uid, actorName: "Admin", actorRole: r === "ADMIN" ? "ADMIN" : "OWNER", exportedAtMs: 1000, createdAt: ts }),
    makeCreate: (uid, r) => ({ scope: "SCHOOL", schoolId: SCHOOL_1, fileName: "bak.zip", includedSalaryCsv: false, studentCount: 10, receiptCount: 2, actorUid: uid, actorName: "Admin", actorRole: r === "ADMIN" ? "ADMIN" : "OWNER", exportedAtMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ studentCount: 11 }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });
});

test("7. Class Diary, Birthdays, Student Remarks, PTM, Gate & Remote Config equivalence", async () => {
  const ts = pastTs(3000);
  const todayIso = new Date().toISOString().slice(0, 10);

  // classDiaryEntries (author or admin update/delete)
  await verifyMatrix({
    name: "classDiaryEntries",
    moduleKey: "classDiary",
    docPath: (id) => `organisations/${ORG_ID}/classDiaryEntries/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", dateIso: todayIso, className: "5", section: "A", subjectName: "Maths", classwork: "Ch 1", homework: "Ex 1.1", note: "", photoBase64: "", authorUid: uid, authorName: "Author", createdAtMs: 1000, updatedByUid: uid, updatedByName: "Author", updatedAtMs: 1000, editHistory: [], createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", dateIso: todayIso, className: "5", section: "A", subjectName: "Maths", classwork: "Ch 1", homework: "Ex 1.1", note: "", photoBase64: "", authorUid: uid, authorName: "Author", createdAtMs: 1000, updatedByUid: uid, updatedByName: "Author", updatedAtMs: 1000, editHistory: [], createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ homework: "Ex 1.2", updatedByUid: uid, updatedByName: "Author", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false }, // EDIT is not author of OWNER-seeded doc!
  });

  // classDiaryHistory
  await verifyMatrix({
    name: "classDiaryHistory",
    moduleKey: "classDiary",
    docPath: (id) => `organisations/${ORG_ID}/classDiaryHistory/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", entryId: "de_1", dateIso: todayIso, className: "5", section: "A", subjectName: "Maths", action: "CREATED", classwork: "Ch 1", homework: "Ex 1.1", note: "", previousClasswork: "", previousHomework: "", previousNote: "", actorUid: uid, actorName: "Author", timestampMs: 1000, createdAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", entryId: "de_1", dateIso: todayIso, className: "5", section: "A", subjectName: "Maths", action: "CREATED", classwork: "Ch 1", homework: "Ex 1.1", note: "", previousClasswork: "", previousHomework: "", previousNote: "", actorUid: uid, actorName: "Author", timestampMs: 1000, createdAt: ts }),
    makeUpdate: () => ({ homework: "Edited" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // schoolBirthdayConfigs & birthdayWishes (students/teachers)
  await verifyMatrix({
    name: "schoolBirthdayConfigs",
    moduleKey: "students",
    docPath: () => `organisations/${ORG_ID}/schoolBirthdayConfigs/${SCHOOL_1}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, birthdayWishesEnabled: true, wishTemplateEn: "Happy Birthday!", wishTemplateHi: "जन्मदिन की शुभकामनाएँ!", excludedStudentKeys: [], excludedTeacherIds: [], staffDobByTeacherId: {}, updatedByUid: uid, updatedByName: "Clerk", updatedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, birthdayWishesEnabled: true, wishTemplateEn: "Happy Birthday!", wishTemplateHi: "जन्मदिन की शुभकामनाएँ!", excludedStudentKeys: [], excludedTeacherIds: [], staffDobByTeacherId: {}, updatedByUid: uid, updatedByName: "Clerk", updatedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ birthdayWishesEnabled: false, updatedByUid: uid, updatedByName: "Clerk", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  await verifyMatrix({
    name: "birthdayWishes",
    moduleKey: "students",
    docPath: (id) => `organisations/${ORG_ID}/birthdayWishes/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, personType: "STUDENT", personId: "stu_seed", personName: "Seed Student", celebrationDateIso: todayIso, celebrationYear: 2026, wishedByUid: uid, wishedByName: "Clerk", wishedAtMs: 1000, channel: "WHATSAPP", updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, personType: "STUDENT", personId: "stu_seed", personName: "Seed Student", celebrationDateIso: todayIso, celebrationYear: 2026, wishedByUid: uid, wishedByName: "Clerk", wishedAtMs: 1000, channel: "WHATSAPP", updatedAt: ts }),
    makeUpdate: (uid) => ({ channel: "CARD", wishedByUid: uid, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
  });

  // studentRemarks (cancel-with-reason only; no delete)
  await verifyMatrix({
    name: "studentRemarks",
    moduleKey: "studentRemarks",
    docPath: (id) => `organisations/${ORG_ID}/studentRemarks/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", rollNo: 1, dateIso: todayIso, category: "Academic", note: "Doing well", followUpDateIso: "", followUpCompleted: false, followUpCompletedNote: "", status: "ACTIVE", cancelledReason: "", cancelledByUid: "", cancelledByName: "", cancelledAtMs: 0, authorUid: uid, authorName: "Teacher", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", rollNo: 1, dateIso: todayIso, category: "Academic", note: "Doing well", followUpDateIso: "", followUpCompleted: false, followUpCompletedNote: "", status: "ACTIVE", cancelledReason: "", cancelledByUid: "", cancelledByName: "", cancelledAtMs: 0, authorUid: uid, authorName: "Teacher", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "CANCELLED", cancelledReason: "Wrong student", cancelledByUid: uid, cancelledByName: "Clerk", cancelledAtMs: 2000, updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // studentRemarkCategories (admin write, any remarks viewer read)
  await verifyMatrix({
    name: "studentRemarkCategories",
    moduleKey: "studentRemarks",
    docPath: () => `organisations/${ORG_ID}/studentRemarkCategories/${SCHOOL_1}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, categories: ["Academic", "Discipline"], hideSummaryPdfFromNonAdmins: false, updatedByUid: uid, updatedByName: "Admin", updatedAtMs: 1000, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, categories: ["Academic", "Discipline"], hideSummaryPdfFromNonAdmins: false, updatedByUid: uid, updatedByName: "Admin", updatedAtMs: 1000, updatedAt: ts }),
    makeUpdate: (uid) => ({ hideSummaryPdfFromNonAdmins: true, updatedByUid: uid, updatedByName: "Admin", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // parentMeetings
  await verifyMatrix({
    name: "parentMeetings",
    moduleKey: "studentRemarks",
    docPath: (id) => `organisations/${ORG_ID}/parentMeetings/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", title: "PTM 1", dateIso: todayIso, timeLabel: "10 AM", className: "5", section: "A", agenda: "Result discussion", entries: [], createdByUid: uid, createdByName: "Teacher", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, session: "2026-2027", title: "PTM 1", dateIso: todayIso, timeLabel: "10 AM", className: "5", section: "A", agenda: "Result discussion", entries: [], createdByUid: uid, createdByName: "Teacher", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: () => ({ agenda: "Updated agenda", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // studentAuthorizedPickups (studentRegister write; gate/students/studentRegister read)
  await verifyMatrix({
    name: "studentAuthorizedPickups",
    moduleKey: "studentRegister",
    docPath: (id) => `organisations/${ORG_ID}/studentAuthorizedPickups/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", personName: "Father Name", relation: "Father", mobile: "9000000002", photoBase64: "", updatedByUid: uid, updatedByName: "Reg", updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", personName: "Father Name", relation: "Father", mobile: "9000000002", photoBase64: "", updatedByUid: uid, updatedByName: "Reg", updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ mobile: "9111111111", updatedByUid: uid, updatedByName: "Reg", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // gatePasses (append-only; no update/delete)
  await verifyMatrix({
    name: "gatePasses",
    moduleKey: "gate",
    docPath: (id) => `organisations/${ORG_ID}/gatePasses/${id}`,
    seedDoc: (uid, r = "X") => ({ passNumber: `GP-${r}-01`, schoolId: SCHOOL_1, session: "2026-2027", dateIso: todayIso, leaveTime: "12:00 PM", studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", rollNo: 1, reason: "Medical visit", pickupPersonId: "pk1", pickupPersonName: "Parent", pickupRelation: "Father", pickupMobile: "9000", isOneTimePickup: false, oneTimeNote: "", guardianPhoneConfirmed: true, approvedByUid: OWNER_UID, approvedByName: "Principal", markedHalfDayAttendance: false, attendanceNote: "", createdByUid: uid, createdByName: "Gate", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid, r) => ({ passNumber: `GP-${r}-01`, schoolId: SCHOOL_1, session: "2026-2027", dateIso: todayIso, leaveTime: "12:00 PM", studentId: "stu_seed", studentAdmNo: "ADM001", studentName: "Seed", className: "5", section: "A", rollNo: 1, reason: "Medical visit", pickupPersonId: "pk1", pickupPersonName: "Parent", pickupRelation: "Father", pickupMobile: "9000", isOneTimePickup: false, oneTimeNote: "", guardianPhoneConfirmed: true, approvedByUid: OWNER_UID, approvedByName: "Principal", markedHalfDayAttendance: false, attendanceNote: "", createdByUid: uid, createdByName: "Gate", createdAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: () => ({ reason: "Changed" }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // visitorEntries
  await verifyMatrix({
    name: "visitorEntries",
    moduleKey: "gate",
    docPath: (id) => `organisations/${ORG_ID}/visitorEntries/${id}`,
    seedDoc: (uid) => ({ schoolId: SCHOOL_1, dateIso: todayIso, visitorName: "Visitor A", mobile: "9000", purpose: "Parent inquiry", whomToMeet: "Principal", timeIn: "10:00 AM", timeOut: "", status: "IN", photoBase64: "", recordedByUid: uid, recordedByName: "Gate", signedOutByUid: "", signedOutByName: "", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeCreate: (uid) => ({ schoolId: SCHOOL_1, dateIso: todayIso, visitorName: "Visitor A", mobile: "9000", purpose: "Parent inquiry", whomToMeet: "Principal", timeIn: "10:00 AM", timeOut: "", status: "IN", photoBase64: "", recordedByUid: uid, recordedByName: "Gate", signedOutByUid: "", signedOutByName: "", createdAtMs: 1000, updatedAtMs: 1000, createdAt: ts, updatedAt: ts }),
    makeUpdate: (uid) => ({ status: "OUT", timeOut: "10:30 AM", signedOutByUid: uid, signedOutByName: "Gate", updatedAtMs: 2000, updatedAt: ts }),
    allowRead:   { OWNER: true, ADMIN: true, EDIT: true, VIEW: true, NONE: false, OUTSIDER: false },
    allowCreate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowUpdate: { OWNER: true, ADMIN: true, EDIT: true, VIEW: false, NONE: false, OUTSIDER: false },
    allowDelete: { OWNER: false, ADMIN: false, EDIT: false, VIEW: false, NONE: false, OUTSIDER: false },
  });

  // App Owner remote ads config + server-only lock collections
  const appOwnerDb = testEnv.authenticatedContext("app_owner_uid", { email: APP_OWNER_EMAIL, email_verified: true }).firestore();
  const actors = actorDbs();
  await expectBool(actors.OUTSIDER.db.doc("appConfig/adsConfig").get(), true, "adsConfig signed-in get");
  await expectBool(actors.OWNER.db.doc("appConfig/adsConfig").update({ globalEnabled: false }), false, "adsConfig org-owner write deny");
  await expectBool(appOwnerDb.doc("appConfig/adsConfig").update({ globalEnabled: false }), true, "adsConfig App Owner write allow");
  await expectBool(appOwnerDb.doc("subscriptionPurchaseLocks/x").set({ a: 1 }), false, "server-only subscriptionPurchaseLocks deny");
  await expectBool(appOwnerDb.doc("rewardedAdNonces/x").set({ a: 1 }), false, "server-only rewardedAdNonces deny");
  await expectBool(appOwnerDb.doc("rewardedAdTransactions/x").set({ a: 1 }), false, "server-only rewardedAdTransactions deny");
});
