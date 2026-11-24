const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_phone_a";
const TEACHER_UID = "teacher_phone_b";
const ADMIN_UID = "admin_user_c";
const OTHER_ORG_UID = "outsider_phone_c";

const ORG_ID = "org_sts_001";
const ORG_CODE = "sts-ghazipur";
const SCHOOL_1 = "sch_primary_01";
const SCHOOL_2 = "sch_inter_02";
const OTHER_ORG_ID = "org_rival_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 3600 * 1000) {
  return new Date(Date.now() + msAhead);
}

function fullPermissions(level = "edit") {
  return {
    students: level,
    marks: level,
    coScholastic: level,
    reports: level,
    timetable: level,
    teachers: level,
    settings: level,
    members: level,
  };
}

function teacherPermissions(marksLevel = "edit") {
  return {
    students: "view",
    marks: marksLevel,
    coScholastic: "edit",
    reports: "view",
    timetable: "view",
    teachers: "none",
    settings: "none",
    members: "none",
  };
}

function defaultSettings() {
  return {
    currentSession: "2026-2027",
    sessionsCsv: "2026-2027",
    passPercent: 33,
    showPhotoAndAdmOnAnnual: false,
    showClassRank: true,
    lastBackupEpochMs: 0,
  };
}

function makeMemberPayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    uid,
    name: "Test User",
    email: `${uid}@example.org`,
    mobile: "9000000000",
    role: "TEACHER",
    status: "active",
    allSchools: false,
    schools: [SCHOOL_1],
    permissions: teacherPermissions(),
    teacherProfileCompleted: false,
    teacherPhotoBase64: "",
    requestedSubjects: [],
    requestedClasses: [],
    assignedClassSubjects: [],
    lastActiveAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeSchoolPayload(schoolKey, name) {
  const ts = pastDate(5000);
  return {
    schoolKey,
    name,
    address: "Main Road",
    mobile: "9000000001",
    email: "school@example.org",
    logoBase64: "",
    active: true,
    classGroups: [],
    classes: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeStudentPayload(schoolId, overrides = {}) {
  const ts = pastDate(5000);
  return {
    schoolId,
    classId: "cls_2nd",
    session: "2026-2027",
    admNo: "ADM-101",
    name: "Aarav Sharma",
    fatherName: "Rajesh Sharma",
    motherName: "Sunita Sharma",
    dob: "12-04-2016",
    mobile: "9800000001",
    address: "Station Road",
    photoBase64: "",
    className: "2nd",
    section: "A",
    rollNo: 1,
    sample: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

async function seedOrgWithOwnerAndSchools() {
  const ts = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("orgCodes").doc(ORG_CODE).set({
      orgId: ORG_ID,
      name: "STS Group of Schools",
      ownerUid: OWNER_UID,
      createdAt: ts,
    });
    await db.collection("organisations").doc(ORG_ID).set({
      name: "STS Group of Schools",
      code: ORG_CODE,
      ownerUid: OWNER_UID,
      contactMobile: "9000000001",
      contactEmail: "owner@sts.edu",
      settings: defaultSettings(),
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID).set(
      makeMemberPayload(OWNER_UID, {
        name: "Principal A",
        role: "OWNER",
        allSchools: true,
        schools: [],
        permissions: fullPermissions("edit"),
        teacherProfileCompleted: true,
      })
    );
    await db.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).set(
      makeSchoolPayload(SCHOOL_1, "STS Primary")
    );
    await db.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2).set(
      makeSchoolPayload(SCHOOL_2, "STS Inter")
    );
    await db.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1)
      .collection("students").doc("stu_1").set(makeStudentPayload(SCHOOL_1));
    await db.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2)
      .collection("students").doc("stu_2").set(makeStudentPayload(SCHOOL_2, { admNo: "ADM-201", name: "Riya" }));
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// =============================================================================
// 1. UNAUTHENTICATED DENIAL & EXACT ORGCODE LOOKUP
// =============================================================================
test("Unauthenticated users cannot read or write any collection including orgCodes", async () => {
  await seedOrgWithOwnerAndSchools();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("orgCodes").doc(ORG_CODE).get());
  await assertFails(unauthDb.collection("orgCodes").get());
  await assertFails(unauthDb.collection("organisations").doc(ORG_ID).get());
  await assertFails(unauthDb.collection("users").doc(OWNER_UID).get());
});

test("Acceptance (a): Phone A atomically creates organisation, reserves code, adds first school and OWNER member", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const ts = pastDate(2000);
  const batch = ownerDb.batch();

  batch.set(ownerDb.collection("orgCodes").doc(ORG_CODE), {
    orgId: ORG_ID,
    name: "STS Ghazipur",
    ownerUid: OWNER_UID,
    createdAt: ts,
  });
  batch.set(ownerDb.collection("organisations").doc(ORG_ID), {
    name: "STS Ghazipur",
    code: ORG_CODE,
    ownerUid: OWNER_UID,
    contactMobile: "9000000001",
    contactEmail: "owner@sts.edu",
    settings: defaultSettings(),
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID),
    makeMemberPayload(OWNER_UID, {
      role: "OWNER",
      allSchools: true,
      schools: [],
      permissions: fullPermissions("edit"),
      teacherProfileCompleted: true,
    })
  );
  batch.set(
    ownerDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1),
    makeSchoolPayload(SCHOOL_1, "STS Primary")
  );
  await assertSucceeds(batch.commit());

  // Owner can read org and schools
  await assertSucceeds(ownerDb.collection("organisations").doc(ORG_ID).get());
  await assertSucceeds(ownerDb.collection("organisations").doc(ORG_ID).collection("schools").get());
});

test("Acceptance (b): Non-member Phone B can ONLY get exact orgCodes/{code} and create own joinRequest; zero school/student data exposed", async () => {
  await seedOrgWithOwnerAndSchools();
  const bDb = testEnv.authenticatedContext(TEACHER_UID).firestore();

  // Can look up exact orgCode
  await assertSucceeds(bDb.collection("orgCodes").doc(ORG_CODE).get());
  // Cannot list all orgCodes
  await assertFails(bDb.collection("orgCodes").get());

  // Cannot read org, schools, students, marks, members, or audit log
  await assertFails(bDb.collection("organisations").doc(ORG_ID).get());
  await assertFails(bDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).get());
  await assertFails(bDb.collection("organisations").doc(ORG_ID).collection("schools").get());
  await assertFails(
    bDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("students").get()
  );
  await assertFails(bDb.collection("organisations").doc(ORG_ID).collection("members").get());
  await assertFails(bDb.collection("organisations").doc(ORG_ID).collection("auditLog").get());

  // Can create ONLY their own pending join request
  const ts = pastDate(2000);
  await assertSucceeds(
    bDb.collection("organisations").doc(ORG_ID).collection("joinRequests").doc(TEACHER_UID).set({
      uid: TEACHER_UID,
      name: "Mrs. Sharma",
      email: "sharma@example.org",
      message: "I am Mrs. Sharma, Class 3 teacher",
      requestedRole: "TEACHER",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cannot spoof another user's join request or self-approve
  await assertFails(
    bDb.collection("organisations").doc(ORG_ID).collection("joinRequests").doc("someone_else").set({
      uid: "someone_else",
      name: "Spoof",
      email: "spoof@example.org",
      message: "Hi",
      requestedRole: "TEACHER",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, { role: "ADMIN", allSchools: true })
    )
  );
});

test("Acceptance (c) & (d): Approved Teacher sees only assigned school & modules; editing marks outside access is REFUSED by Firestore", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();

  // Owner approves Phone B as Teacher for SCHOOL_1 ONLY, assigned to 2nd|MATH only
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, {
        name: "Mrs. Sharma",
        role: "TEACHER",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: teacherPermissions("edit"),
        assignedClassSubjects: [`${SCHOOL_1}|2nd|MATH`],
      })
    )
  );

  // Teacher can complete their own teacher profile (allowed fields only)
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).update({
      mobile: "9876543210",
      teacherProfileCompleted: true,
      requestedSubjects: ["MATH"],
      requestedClasses: ["2nd"],
      updatedAt: pastDate(1000),
    })
  );

  // Teacher CANNOT escalate own role, schools, or permissions
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).update({
      role: "ADMIN",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).update({
      allSchools: true,
      updatedAt: pastDate(500),
    })
  );

  // Teacher can read SCHOOL_1 and its students, CANNOT read SCHOOL_2 or list other members or audit logs
  await assertSucceeds(teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).get());
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("students").get()
  );
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2).get());
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2).collection("students").get()
  );
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("members").get());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("auditLog").get());

  // Teacher CAN edit marks for assigned class-subject in SCHOOL_1
  const ts = pastDate(1000);
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1)
      .collection("marks").doc("stu_1_MATH_T1_UT").set({
        studentId: "stu_1",
        schoolId: SCHOOL_1,
        className: "2nd",
        subjectName: "MATH",
        componentKey: "T1_UT",
        rawValue: "28",
        updatedByUid: TEACHER_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Acceptance (d) Case 1: Teacher tries to edit marks for UNASSIGNED subject (ENGLISH) -> REFUSED
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1)
      .collection("marks").doc("stu_1_ENGLISH_T1_UT").set({
        studentId: "stu_1",
        schoolId: SCHOOL_1,
        className: "2nd",
        subjectName: "ENGLISH",
        componentKey: "T1_UT",
        rawValue: "25",
        updatedByUid: TEACHER_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Acceptance (d) Case 2: Teacher tries to edit marks in UNASSIGNED school (SCHOOL_2) -> REFUSED
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2)
      .collection("marks").doc("stu_2_MATH_T1_UT").set({
        studentId: "stu_2",
        schoolId: SCHOOL_2,
        className: "2nd",
        subjectName: "MATH",
        componentKey: "T1_UT",
        rawValue: "24",
        updatedByUid: TEACHER_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Acceptance (d) Case 3: Owner changes Teacher marks permission to "view" -> Firestore REFUSES mark edit
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).update({
      permissions: teacherPermissions("view"),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1)
      .collection("marks").doc("stu_1_MATH_T1_UT").update({
        rawValue: "30",
        updatedByUid: TEACHER_UID,
        updatedAt: pastDate(200),
      })
  );
});

test("Acceptance (e): Third user with their own organisation cannot see or touch any data of sts-ghazipur", async () => {
  await seedOrgWithOwnerAndSchools();
  const outsiderDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();
  const ts = pastDate(2000);

  // Outsider creates their own separate organisation
  const batch = outsiderDb.batch();
  batch.set(outsiderDb.collection("orgCodes").doc("rival-school"), {
    orgId: OTHER_ORG_ID,
    name: "Rival School",
    ownerUid: OTHER_ORG_UID,
    createdAt: ts,
  });
  batch.set(outsiderDb.collection("organisations").doc(OTHER_ORG_ID), {
    name: "Rival School",
    code: "rival-school",
    ownerUid: OTHER_ORG_UID,
    contactMobile: "9111111111",
    contactEmail: "rival@example.org",
    settings: defaultSettings(),
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(
    outsiderDb.collection("organisations").doc(OTHER_ORG_ID).collection("members").doc(OTHER_ORG_UID),
    makeMemberPayload(OTHER_ORG_UID, {
      role: "OWNER",
      allSchools: true,
      schools: [],
      permissions: fullPermissions("edit"),
    })
  );
  await assertSucceeds(batch.commit());

  // Outsider has ZERO access to STS Ghazipur
  await assertFails(outsiderDb.collection("organisations").doc(ORG_ID).get());
  await assertFails(outsiderDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).get());
  await assertFails(
    outsiderDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("students").get()
  );
  await assertFails(outsiderDb.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID).get());
  await assertFails(outsiderDb.collection("organisations").doc(ORG_ID).delete());
});

test("Acceptance (f) & Owner Safety: Admin cannot remove Owner; Owner removing a member revokes access immediately", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();

  // Owner adds an Admin and a Teacher
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(ADMIN_UID).set(
      makeMemberPayload(ADMIN_UID, {
        role: "ADMIN",
        allSchools: true,
        schools: [],
        permissions: fullPermissions("edit"),
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, { role: "TEACHER", schools: [SCHOOL_1] })
    )
  );

  // Admin CANNOT remove Owner, demote Owner, or delete Organisation
  await assertFails(adminDb.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID).delete());
  await assertFails(
    adminDb.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID).update({
      role: "TEACHER",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(adminDb.collection("organisations").doc(ORG_ID).delete());

  // Teacher currently has access to SCHOOL_1
  await assertSucceeds(teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).get());

  // Owner removes Teacher -> Teacher immediately loses access
  await assertSucceeds(ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).delete());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).get());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).get());
});

test("One-time invite code redemption joins atomically without approval and cannot be reused", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const joinerDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const ts = pastDate(2000);
  const code = "INVITE2026A";

  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("inviteCodes").doc(code).set({
      code,
      orgId: ORG_ID,
      orgName: "STS Group of Schools",
      presetRole: "TEACHER",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: teacherPermissions("edit"),
      createdByUid: OWNER_UID,
      expiresAt: futureDate(86400 * 1000),
      usedByUid: "",
      createdAt: ts,
    })
  );

  // Joiner redeems invite atomically in a batch
  const redeemBatch = joinerDb.batch();
  redeemBatch.update(
    joinerDb.collection("organisations").doc(ORG_ID).collection("inviteCodes").doc(code),
    { usedByUid: TEACHER_UID, usedAt: pastDate(1000) }
  );
  redeemBatch.set(
    joinerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID),
    makeMemberPayload(TEACHER_UID, {
      role: "TEACHER",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: teacherPermissions("edit"),
      inviteCodeUsed: code,
    })
  );
  await assertSucceeds(redeemBatch.commit());

  // Second user attempting to reuse the same one-time code fails
  const secondDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();
  const reuseBatch = secondDb.batch();
  reuseBatch.update(
    secondDb.collection("organisations").doc(ORG_ID).collection("inviteCodes").doc(code),
    { usedByUid: OTHER_ORG_UID, usedAt: pastDate(500) }
  );
  reuseBatch.set(
    secondDb.collection("organisations").doc(ORG_ID).collection("members").doc(OTHER_ORG_UID),
    makeMemberPayload(OTHER_ORG_UID, {
      role: "TEACHER",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: teacherPermissions("edit"),
      inviteCodeUsed: code,
    })
  );
  await assertFails(reuseBatch.commit());
});

test("Audit log is append-only for active members and readable only by Admins; users/{uid} is strictly private", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const ts = pastDate(2000);

  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, { role: "TEACHER", schools: [SCHOOL_1] })
    )
  );

  // Member can append an audit log entry with their own actorUid
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("auditLog").doc("log_1").set({
      actorUid: TEACHER_UID,
      actorName: "Mrs. Sharma",
      action: "MARK_UPDATED",
      targetType: "mark",
      targetId: "stu_1_MATH_T1_UT",
      summary: "Updated MATH T1_UT to 28",
      schoolId: SCHOOL_1,
      createdAt: ts,
    })
  );

  // Admin can read auditLog; Teacher cannot read or tamper with auditLog
  await assertSucceeds(ownerDb.collection("organisations").doc(ORG_ID).collection("auditLog").get());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("auditLog").get());
  await assertFails(
    ownerDb.collection("organisations").doc(ORG_ID).collection("auditLog").doc("log_1").update({ summary: "Altered" })
  );

  // Private users/{uid} check
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      uid: OWNER_UID,
      name: "Principal A",
      email: "owner@sts.edu",
      memberships: [],
      pendingRequests: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(teacherDb.collection("users").doc(OWNER_UID).get());
  await assertFails(teacherDb.collection("users").get());
});

test("Owner can atomically transfer ownership to another active member and become ADMIN", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const ts = pastDate(1000);

  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(ADMIN_UID).set(
      makeMemberPayload(ADMIN_UID, {
        name: "Admin C",
        role: "ADMIN",
        allSchools: true,
        schools: [],
        permissions: fullPermissions("edit"),
      })
    )
  );

  const transferBatch = ownerDb.batch();
  transferBatch.update(ownerDb.collection("organisations").doc(ORG_ID), {
    ownerUid: ADMIN_UID,
    updatedAt: ts,
  });
  transferBatch.update(ownerDb.collection("orgCodes").doc(ORG_CODE), {
    ownerUid: ADMIN_UID,
  });
  transferBatch.update(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(ADMIN_UID),
    { role: "OWNER", allSchools: true, permissions: fullPermissions("edit"), updatedAt: ts }
  );
  transferBatch.update(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(OWNER_UID),
    { role: "ADMIN", updatedAt: ts }
  );
  await assertSucceeds(transferBatch.commit());
});

test("Phase 3: Mark edit logs are append-only, respect teacher scope, and enforce cross-org isolation", async () => {
  await seedOrgWithOwnerAndSchools();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();
  const ts = pastDate(2000);

  // Add teacher scoped to SCHOOL_1, Class 2nd, MATHEMATICS only
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, {
        role: "TEACHER",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: teacherPermissions("edit"),
        assignedClassSubjects: [`${SCHOOL_1}|2nd|MATHEMATICS`],
      })
    )
  );

  // Teacher saves a mark with clientEditedAtMs + updatedByName and appends a markEditLog atomically
  const markRef = teacherDb
    .collection("organisations").doc(ORG_ID)
    .collection("schools").doc(SCHOOL_1)
    .collection("marks").doc("stu_1_MATHEMATICS_T1_UT");
  const logRef = teacherDb
    .collection("organisations").doc(ORG_ID)
    .collection("schools").doc(SCHOOL_1)
    .collection("markEditLogs").doc("mlog_001");

  const batch = teacherDb.batch();
  batch.set(markRef, {
    studentId: "stu_1",
    schoolId: SCHOOL_1,
    className: "2nd",
    subjectName: "MATHEMATICS",
    componentKey: "T1_UT",
    rawValue: "28",
    updatedByUid: TEACHER_UID,
    updatedByName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 1000,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(logRef, {
    markDocId: "stu_1_MATHEMATICS_T1_UT",
    studentId: "stu_1",
    studentName: "Aarav Sharma",
    schoolId: SCHOOL_1,
    className: "2nd",
    subjectName: "MATHEMATICS",
    componentKey: "T1_UT",
    oldValue: "",
    newValue: "28",
    editorUid: TEACHER_UID,
    editorName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 1000,
    createdAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Teacher can update mark with new clientEditedAtMs + updatedByName
  await assertSucceeds(
    markRef.update({
      rawValue: "29",
      updatedByUid: TEACHER_UID,
      updatedByName: "Mrs. Sharma",
      clientEditedAtMs: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Teacher CANNOT write a markEditLog for a subject outside their assigned scope (ENGLISH)
  await assertFails(
    teacherDb
      .collection("organisations").doc(ORG_ID)
      .collection("schools").doc(SCHOOL_1)
      .collection("markEditLogs").doc("mlog_bad_scope")
      .set({
        markDocId: "stu_1_ENGLISH_T1_UT",
        studentId: "stu_1",
        studentName: "Aarav Sharma",
        schoolId: SCHOOL_1,
        className: "2nd",
        subjectName: "ENGLISH",
        componentKey: "T1_UT",
        oldValue: "20",
        newValue: "25",
        editorUid: TEACHER_UID,
        editorName: "Mrs. Sharma",
        clientEditedAtMs: Date.now(),
        createdAt: ts,
      })
  );

  // Teacher CANNOT spoof another user's editorUid
  await assertFails(
    teacherDb
      .collection("organisations").doc(ORG_ID)
      .collection("schools").doc(SCHOOL_1)
      .collection("markEditLogs").doc("mlog_spoof")
      .set({
        markDocId: "stu_1_MATHEMATICS_T1_UT",
        studentId: "stu_1",
        studentName: "Aarav Sharma",
        schoolId: SCHOOL_1,
        className: "2nd",
        subjectName: "MATHEMATICS",
        componentKey: "T1_UT",
        oldValue: "28",
        newValue: "30",
        editorUid: OWNER_UID,
        editorName: "Principal A",
        clientEditedAtMs: Date.now(),
        createdAt: ts,
      })
  );

  // Admin can read all markEditLogs in the school; teacher with marks view can also read for conflict view
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("markEditLogs").get()
  );
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("markEditLogs").get()
  );

  // Teacher CANNOT read markEditLogs in SCHOOL_2 (not assigned to SCHOOL_2)
  await assertFails(
    teacherDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_2).collection("markEditLogs").get()
  );

  // Outsider from another org CANNOT read markEditLogs
  await assertFails(
    outsiderDb.collection("organisations").doc(ORG_ID).collection("schools").doc(SCHOOL_1).collection("markEditLogs").get()
  );

  // Nobody (not even Owner) can update or delete an existing markEditLog (strictly append-only)
  await assertFails(
    ownerDb
      .collection("organisations").doc(ORG_ID)
      .collection("schools").doc(SCHOOL_1)
      .collection("markEditLogs").doc("mlog_001")
      .update({ newValue: "30" })
  );
  await assertFails(
    ownerDb
      .collection("organisations").doc(ORG_ID)
      .collection("schools").doc(SCHOOL_1)
      .collection("markEditLogs").doc("mlog_001")
      .delete()
  );
});

test("Phase 4: Teachers and assignments module enforces edit, view-only, no-access, and cross-org isolation across all 4 collections", async () => {
  await seedOrgWithOwnerAndSchools();
  const VIEWER_UID = "viewer_teachers_u1";
  const NO_ACCESS_UID = "no_access_u2";
  const EDITOR_UID = "editor_teachers_u3";

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const editorDb = testEnv.authenticatedContext(EDITOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const noAccessDb = testEnv.authenticatedContext(NO_ACCESS_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();
  const ts = pastDate(2000);

  // Seed 3 non-admin members with different 'teachers' module levels: edit, view, none
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(EDITOR_UID).set(
      makeMemberPayload(EDITOR_UID, {
        role: "OFFICE_OPERATOR",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), teachers: "edit" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(VIEWER_UID).set(
      makeMemberPayload(VIEWER_UID, {
        role: "OFFICE_OPERATOR",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), teachers: "view" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(NO_ACCESS_UID).set(
      makeMemberPayload(NO_ACCESS_UID, {
        role: "OFFICE_OPERATOR",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), teachers: "none" },
      })
    )
  );

  const teacherPayload = {
    name: "Mr. Verma",
    mobile: "9800000011",
    email: "verma@sts.edu",
    photoBase64: "",
    subjects: ["MATHEMATICS", "SCIENCE"],
    schoolIds: [SCHOOL_1, SCHOOL_2],
    maxPeriodsPerDay: 7,
    maxConsecutivePeriods: 3,
    weeklyOffDays: [],
    active: true,
    linkedMemberUid: "",
    createdAt: ts,
    updatedAt: ts,
  };

  const periodStructurePayload = {
    schoolId: SCHOOL_1,
    workingDays: ["MON", "TUE", "WED", "THU", "FRI", "SAT"],
    slotsByDay: {
      MON: [
        { id: "MON_1", type: "ASSEMBLY", label: "Morning Assembly", startMinutes: 480, endMinutes: 495 },
        { id: "MON_2", type: "PERIOD", label: "Period 1", startMinutes: 495, endMinutes: 535 },
      ],
    },
    updatedByUid: EDITOR_UID,
    createdAt: ts,
    updatedAt: ts,
  };

  const classAssignmentPayload = {
    schoolId: SCHOOL_1,
    className: "5th",
    section: "A",
    classTeacherId: "tchr_verma",
    subjectAssignments: [
      { subjectName: "MATHEMATICS", teacherId: "tchr_verma", periodsPerWeek: 6 },
    ],
    updatedByUid: EDITOR_UID,
    createdAt: ts,
    updatedAt: ts,
  };

  const subjectRulePayload = {
    schoolId: SCHOOL_1,
    subjectName: "MATHEMATICS",
    preferredTimeOfDay: "MORNING",
    allowDoublePeriod: true,
    maxOncePerDay: false,
    updatedByUid: EDITOR_UID,
    createdAt: ts,
    updatedAt: ts,
  };

  // 1. Editor (teachers: "edit") CAN create & update all 4 collections
  const orgRefEditor = editorDb.collection("organisations").doc(ORG_ID);
  await assertSucceeds(orgRefEditor.collection("teachers").doc("tchr_verma").set(teacherPayload));
  await assertSucceeds(orgRefEditor.collection("periodStructures").doc(SCHOOL_1).set(periodStructurePayload));
  await assertSucceeds(orgRefEditor.collection("classAssignments").doc("assign_sch1_5th_A").set(classAssignmentPayload));
  await assertSucceeds(orgRefEditor.collection("subjectRules").doc("srule_sch1_math").set(subjectRulePayload));

  // 2. View-only member (teachers: "view") CAN read/list all 4 collections, CANNOT edit or delete ANY
  const orgRefViewer = viewerDb.collection("organisations").doc(ORG_ID);
  await assertSucceeds(orgRefViewer.collection("teachers").get());
  await assertSucceeds(orgRefViewer.collection("periodStructures").get());
  await assertSucceeds(orgRefViewer.collection("classAssignments").get());
  await assertSucceeds(orgRefViewer.collection("subjectRules").get());

  await assertFails(
    orgRefViewer.collection("teachers").doc("tchr_verma").update({ maxPeriodsPerDay: 5, updatedAt: pastDate(500) })
  );
  await assertFails(
    orgRefViewer.collection("teachers").doc("tchr_new").set({ ...teacherPayload, name: "Blocked" })
  );
  await assertFails(
    orgRefViewer.collection("periodStructures").doc(SCHOOL_2).set({
      ...periodStructurePayload,
      schoolId: SCHOOL_2,
      updatedByUid: VIEWER_UID,
    })
  );
  await assertFails(
    orgRefViewer.collection("classAssignments").doc("assign_sch1_5th_A").update({
      classTeacherId: "",
      updatedByUid: VIEWER_UID,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    orgRefViewer.collection("subjectRules").doc("srule_sch1_math").update({
      preferredTimeOfDay: "AFTERNOON",
      updatedByUid: VIEWER_UID,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(orgRefViewer.collection("teachers").doc("tchr_verma").delete());

  // 3. No-access member (teachers: "none") CANNOT read, list, create, update, or delete ANY of the 4 collections
  const orgRefNone = noAccessDb.collection("organisations").doc(ORG_ID);
  await assertFails(orgRefNone.collection("teachers").get());
  await assertFails(orgRefNone.collection("teachers").doc("tchr_verma").get());
  await assertFails(orgRefNone.collection("periodStructures").get());
  await assertFails(orgRefNone.collection("periodStructures").doc(SCHOOL_1).get());
  await assertFails(orgRefNone.collection("classAssignments").get());
  await assertFails(orgRefNone.collection("subjectRules").get());
  await assertFails(orgRefNone.collection("teachers").doc("tchr_none").set(teacherPayload));
  await assertFails(
    orgRefNone.collection("subjectRules").doc("srule_none").set({ ...subjectRulePayload, updatedByUid: NO_ACCESS_UID })
  );

  // 4. Cross-organisation outsider CANNOT read or write ANY Phase 4 collection
  const orgRefOutsider = outsiderDb.collection("organisations").doc(ORG_ID);
  await assertFails(orgRefOutsider.collection("teachers").get());
  await assertFails(orgRefOutsider.collection("periodStructures").get());
  await assertFails(orgRefOutsider.collection("classAssignments").get());
  await assertFails(orgRefOutsider.collection("subjectRules").get());
  await assertFails(orgRefOutsider.collection("teachers").doc("tchr_verma").delete());

  // 5. Approved TEACHER with teachers:"none" can self-sync their own tchr_member_{uid} doc on onboarding,
  //    but STILL cannot read/list teachers or touch any other teacher doc
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TEACHER_UID).set(
      makeMemberPayload(TEACHER_UID, { role: "TEACHER", schools: [SCHOOL_1], permissions: teacherPermissions("edit") })
    )
  );
  await assertSucceeds(
    teacherDb.collection("organisations").doc(ORG_ID).collection("teachers").doc(`tchr_member_${TEACHER_UID}`).set({
      ...teacherPayload,
      name: "Mrs. Sharma",
      linkedMemberUid: TEACHER_UID,
      schoolIds: [SCHOOL_1],
    })
  );
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("teachers").get());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("teachers").doc("tchr_verma").get());
  await assertFails(teacherDb.collection("organisations").doc(ORG_ID).collection("periodStructures").get());
});

test("Phase 5: Timetable module enforces edit, view-only, no-access, school-scope writes, and cross-org isolation", async () => {
  await seedOrgWithOwnerAndSchools();
  const TT_EDITOR_UID = "tt_editor_u1";
  const TT_VIEWER_UID = "tt_viewer_u2";
  const TT_NONE_UID = "tt_none_u3";

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const editorDb = testEnv.authenticatedContext(TT_EDITOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(TT_VIEWER_UID).firestore();
  const noneDb = testEnv.authenticatedContext(TT_NONE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();
  const ts = pastDate(2000);

  // Seed 3 non-admin members with timetable: edit (scoped to SCHOOL_1 only), view, and none
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_EDITOR_UID).set(
      makeMemberPayload(TT_EDITOR_UID, {
        role: "OFFICE_OPERATOR",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: { ...teacherPermissions("view"), timetable: "edit", teachers: "none" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_VIEWER_UID).set(
      makeMemberPayload(TT_VIEWER_UID, {
        role: "TEACHER",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: { ...teacherPermissions("view"), timetable: "view", teachers: "none" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_NONE_UID).set(
      makeMemberPayload(TT_NONE_UID, {
        role: "OTHER",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), timetable: "none", teachers: "none" },
      })
    )
  );

  const cellId = "tt_sch_primary_01_2026_2027_5th_A_MON_MON_slot_2";
  const cellPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    className: "5th",
    section: "A",
    day: "MON",
    slotId: "MON_slot_2",
    subjectName: "MATHEMATICS",
    teacherId: "tchr_verma",
    teacherName: "Mr. Verma",
    locked: false,
    updatedByUid: TT_EDITOR_UID,
    updatedByName: "Office Editor",
    clientEditedAtMs: Date.now() - 1000,
    createdAt: ts,
    updatedAt: ts,
  };

  const layoutPayload = {
    schoolId: SCHOOL_1,
    workingDays: ["MON", "TUE", "WED", "THU", "FRI", "SAT"],
    slotsByDay: {
      MON: [
        { id: "MON_slot_1", type: "ASSEMBLY", label: "Morning Assembly", startMinutes: 480, endMinutes: 495 },
        { id: "MON_slot_2", type: "PERIOD", label: "Period 1", startMinutes: 495, endMinutes: 535 },
      ],
    },
    updatedByUid: TT_EDITOR_UID,
    createdAt: ts,
    updatedAt: ts,
  };

  // 1. Editor (timetable: "edit", assigned SCHOOL_1) CAN create, update, lock, and delete cells in SCHOOL_1
  const orgEditor = editorDb.collection("organisations").doc(ORG_ID);
  await assertSucceeds(orgEditor.collection("timetableCells").doc(cellId).set(cellPayload));
  await assertSucceeds(orgEditor.collection("timetableLayouts").doc(SCHOOL_1).set(layoutPayload));
  await assertSucceeds(
    orgEditor.collection("timetableCells").doc(cellId).update({
      locked: true,
      updatedByUid: TT_EDITOR_UID,
      clientEditedAtMs: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Editor CANNOT write a cell for SCHOOL_2 (not assigned to SCHOOL_2)
  await assertFails(
    orgEditor.collection("timetableCells").doc("tt_sch2_cell").set({
      ...cellPayload,
      schoolId: SCHOOL_2,
    })
  );

  // 2. View-only member (timetable: "view") CAN read timetableCells and timetableLayouts, CANNOT create/update/delete
  const orgViewer = viewerDb.collection("organisations").doc(ORG_ID);
  await assertSucceeds(orgViewer.collection("timetableCells").get());
  await assertSucceeds(orgViewer.collection("timetableLayouts").get());
  await assertFails(
    orgViewer.collection("timetableCells").doc(cellId).update({
      subjectName: "HINDI",
      updatedByUid: TT_VIEWER_UID,
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    orgViewer.collection("timetableCells").doc("tt_viewer_new").set({
      ...cellPayload,
      updatedByUid: TT_VIEWER_UID,
    })
  );
  await assertFails(orgViewer.collection("timetableCells").doc(cellId).delete());

  // 3. No-access member (timetable: "none") CANNOT read, list, or write timetableCells or timetableLayouts
  const orgNone = noneDb.collection("organisations").doc(ORG_ID);
  await assertFails(orgNone.collection("timetableCells").get());
  await assertFails(orgNone.collection("timetableCells").doc(cellId).get());
  await assertFails(orgNone.collection("timetableLayouts").get());
  await assertFails(
    orgNone.collection("timetableCells").doc("tt_none_new").set({
      ...cellPayload,
      updatedByUid: TT_NONE_UID,
    })
  );

  // 4. Cross-organisation outsider CANNOT read or touch timetableCells or timetableLayouts
  const orgOutsider = outsiderDb.collection("organisations").doc(ORG_ID);
  await assertFails(orgOutsider.collection("timetableCells").get());
  await assertFails(orgOutsider.collection("timetableLayouts").get());
  await assertFails(orgOutsider.collection("timetableCells").doc(cellId).delete());

  // 5. Editor can delete (clear) their own school's cell
  await assertSucceeds(orgEditor.collection("timetableCells").doc(cellId).delete());
});

test("Phase 6 Auto-generate undo, teacher leaves (self-request vs approval) and substitutions rules", async () => {
  const ts = pastDate(5000);
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const TT_EDITOR_UID = "tt_p6_editor_uid";
  const TT_VIEWER_UID = "tt_p6_teacher_uid";
  const TT_NONE_UID = "tt_p6_none_uid";
  const editorDb = testEnv.authenticatedContext(TT_EDITOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(TT_VIEWER_UID).firestore();
  const noneDb = testEnv.authenticatedContext(TT_NONE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_ORG_UID).firestore();

  await seedOrgWithOwnerAndSchools();

  // Seed members: Editor (timetable: edit, SCHOOL_1), Linked Teacher (timetable: view, teachers: none), No-access (timetable: none)
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_EDITOR_UID).set(
      makeMemberPayload(TT_EDITOR_UID, {
        role: "OFFICE_OPERATOR",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: { ...teacherPermissions("view"), timetable: "edit", teachers: "view" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_VIEWER_UID).set(
      makeMemberPayload(TT_VIEWER_UID, {
        role: "TEACHER",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), timetable: "view", teachers: "none" },
      })
    )
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("members").doc(TT_NONE_UID).set(
      makeMemberPayload(TT_NONE_UID, {
        role: "TEACHER",
        allSchools: true,
        schools: [],
        permissions: { ...teacherPermissions("view"), timetable: "none", teachers: "none" },
      })
    )
  );

  // Seed two teacher profiles: one linked to TT_VIEWER_UID, one other teacher
  const linkedTeacherId = "tchr_member_" + TT_VIEWER_UID;
  const otherTeacherId = "tchr_other_01";
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("teachers").doc(linkedTeacherId).set({
      name: "Ms. Viewer Teacher",
      mobile: "9999999999",
      email: "viewer@school.org",
      photoBase64: "",
      subjects: ["ENGLISH"],
      schoolIds: [SCHOOL_1],
      maxPeriodsPerDay: 6,
      maxConsecutivePeriods: 3,
      weeklyOffDays: [],
      active: true,
      linkedMemberUid: TT_VIEWER_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ownerDb.collection("organisations").doc(ORG_ID).collection("teachers").doc(otherTeacherId).set({
      name: "Mr. Other Teacher",
      mobile: "8888888888",
      email: "other@school.org",
      photoBase64: "",
      subjects: ["MATH"],
      schoolIds: [SCHOOL_1],
      maxPeriodsPerDay: 6,
      maxConsecutivePeriods: 3,
      weeklyOffDays: [],
      active: true,
      linkedMemberUid: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const orgEditor = editorDb.collection("organisations").doc(ORG_ID);
  const orgViewer = viewerDb.collection("organisations").doc(ORG_ID);
  const orgNone = noneDb.collection("organisations").doc(ORG_ID);
  const orgOutsider = outsiderDb.collection("organisations").doc(ORG_ID);

  // 1. Linked teacher (timetable: view, teachers: none) CAN query their own linked teacher profile, CANNOT list all teachers
  await assertSucceeds(
    orgViewer.collection("teachers").where("linkedMemberUid", "==", TT_VIEWER_UID).get()
  );
  await assertFails(orgViewer.collection("teachers").get());

  // 2. Linked teacher CAN submit a PENDING leave request for themselves
  const selfLeavePayload = {
    teacherId: linkedTeacherId,
    teacherName: "Ms. Viewer Teacher",
    startDate: "2026-11-25",
    endDate: "2026-11-25",
    reasonType: "SICK",
    reasonNote: "Fever",
    halfDay: false,
    halfDaySlotIds: [],
    status: "PENDING",
    requestedByUid: TT_VIEWER_UID,
    requestedByName: "Ms. Viewer Teacher",
    approvedByUid: "",
    approvedByName: "",
    approvedAtMs: 0,
    updatedByUid: TT_VIEWER_UID,
    updatedByName: "Ms. Viewer Teacher",
    clientEditedAtMs: Date.now() - 1000,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(orgViewer.collection("teacherLeaves").doc("lv_self_1").set(selfLeavePayload));

  // Linked teacher CANNOT self-approve their own leave request
  await assertFails(
    orgViewer.collection("teacherLeaves").doc("lv_self_autoapprove").set({
      ...selfLeavePayload,
      status: "APPROVED",
      approvedByUid: TT_VIEWER_UID,
      approvedAtMs: Date.now(),
    })
  );

  // Linked teacher CANNOT request leave for another teacher
  await assertFails(
    orgViewer.collection("teacherLeaves").doc("lv_spoof_other").set({
      ...selfLeavePayload,
      teacherId: otherTeacherId,
      teacherName: "Mr. Other Teacher",
    })
  );

  // Linked teacher CANNOT approve their pending leave via update, but CAN cancel their own pending leave
  await assertFails(
    orgViewer.collection("teacherLeaves").doc("lv_self_1").update({
      status: "APPROVED",
      approvedByUid: TT_VIEWER_UID,
      approvedByName: "Ms. Viewer Teacher",
      approvedAtMs: Date.now(),
      updatedByUid: TT_VIEWER_UID,
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    orgViewer.collection("teacherLeaves").doc("lv_self_1").update({
      status: "CANCELLED",
      updatedByUid: TT_VIEWER_UID,
      updatedAt: pastDate(400),
    })
  );

  // 3. Editor (timetable: edit) CAN create & approve leave for any teacher
  await assertSucceeds(
    orgEditor.collection("teacherLeaves").doc("lv_admin_1").set({
      ...selfLeavePayload,
      teacherId: otherTeacherId,
      teacherName: "Mr. Other Teacher",
      status: "APPROVED",
      requestedByUid: TT_EDITOR_UID,
      requestedByName: "Office Editor",
      approvedByUid: TT_EDITOR_UID,
      approvedByName: "Office Editor",
      approvedAtMs: Date.now() - 500,
      updatedByUid: TT_EDITOR_UID,
      updatedByName: "Office Editor",
    })
  );

  // 4. Substitutions: Editor CAN create/update/delete for assigned SCHOOL_1, CANNOT write for SCHOOL_2
  const subId = "sub_2026_11_25_sch_primary_01_5th_A_MON_slot_2";
  const subPayload = {
    date: "2026-11-25",
    day: "WED",
    schoolId: SCHOOL_1,
    session: "2026-2027",
    className: "5th",
    section: "A",
    slotId: "MON_slot_2",
    slotLabel: "Period 1",
    startMinutes: 495,
    endMinutes: 535,
    subjectName: "MATH",
    absentTeacherId: otherTeacherId,
    absentTeacherName: "Mr. Other Teacher",
    leaveId: "lv_admin_1",
    substituteTeacherId: linkedTeacherId,
    substituteTeacherName: "Ms. Viewer Teacher",
    overrideUsed: false,
    overrideReason: "",
    updatedByUid: TT_EDITOR_UID,
    updatedByName: "Office Editor",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(orgEditor.collection("substitutions").doc(subId).set(subPayload));
  await assertFails(
    orgEditor.collection("substitutions").doc("sub_sch2").set({ ...subPayload, schoolId: SCHOOL_2 })
  );

  // View-only member CAN read leaves & substitutions, CANNOT write substitutions or undo snapshots
  await assertSucceeds(orgViewer.collection("teacherLeaves").get());
  await assertSucceeds(orgViewer.collection("substitutions").get());
  await assertFails(
    orgViewer.collection("substitutions").doc("sub_viewer").set({ ...subPayload, updatedByUid: TT_VIEWER_UID })
  );
  await assertFails(orgViewer.collection("substitutions").doc(subId).delete());

  // 5. Auto-generate undo snapshot: Editor CAN create & delete for SCHOOL_1; View-only CANNOT read or write
  const undoId = "ttundo_sch_primary_01_2026_2027";
  const undoPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    scopeLabel: "Whole school",
    classNameFilter: "",
    sectionFilter: "",
    seedUsed: 12345,
    previousCells: [],
    affectedCellIds: ["cell_1"],
    createdByUid: TT_EDITOR_UID,
    createdByName: "Office Editor",
    createdAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(orgEditor.collection("timetableUndoSnapshots").doc(undoId).set(undoPayload));
  await assertFails(orgViewer.collection("timetableUndoSnapshots").get());
  await assertFails(
    orgViewer.collection("timetableUndoSnapshots").doc("ttundo_v").set({ ...undoPayload, createdByUid: TT_VIEWER_UID })
  );

  // 6. No-access member (timetable: none) & Cross-org outsider CANNOT read or write any Phase 6 collection
  await assertFails(orgNone.collection("teacherLeaves").get());
  await assertFails(orgNone.collection("substitutions").get());
  await assertFails(orgNone.collection("timetableUndoSnapshots").get());
  await assertFails(orgOutsider.collection("teacherLeaves").get());
  await assertFails(orgOutsider.collection("substitutions").get());
  await assertFails(orgOutsider.collection("timetableUndoSnapshots").get());

  // Cleanup check for editor delete
  await assertSucceeds(orgEditor.collection("substitutions").doc(subId).delete());
  await assertSucceeds(orgEditor.collection("timetableUndoSnapshots").doc(undoId).delete());
});

// =============================================================================
// 11. PHASE 7: TEACHER SCOPED MARKS/EXAMS, CLASS-TEACHER CO-SCHOLASTIC,
//     TEACHER SCOPES PRIVACY, TEACHER ATTENDANCE & REPORT CARD LINKS
// =============================================================================
test("Phase 7: Server-side teacher scope enforcement for marks, open exams, class-teacher co-scholastic, teacher attendance & school links", async () => {
  const TEACHER_A = "uid_p7_teacher_a";
  const TEACHER_B = "uid_p7_teacher_b";
  const OPERATOR = "uid_p7_operator";
  const TT_VIEWER = "uid_p7_tt_viewer";
  const OUTSIDER = "uid_p7_outsider";

  await seedOrgWithOwnerAndSchools();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const orgRef = db.collection("organisations").doc(ORG_ID);
    const ts = pastDate();

    // Update SCHOOL_1 with Phase 7 settings: only UT1 open for teachers, optional toggles OFF initially
    await orgRef.collection("schools").doc(SCHOOL_1).set({
      printClassTeacherNameOnReports: false,
      checkReportCardsReadyBeforeBatch: false,
      openExamsForTeachers: ["UT1"],
      classTeacherNamesBySection: { "3rd|A": "Mrs. Sharma" },
      updatedAt: ts,
    }, { merge: true });

    // Seed two students in SCHOOL_1: 3rd-A and 3rd-B
    await orgRef.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_3a").set({
      schoolId: SCHOOL_1,
      classId: "cls_3a",
      session: "2026-2027",
      admNo: "ADM-3A-01",
      name: "Aarav Verma",
      fatherName: "Rajesh Verma",
      motherName: "Sunita Verma",
      dob: "2017-05-12",
      mobile: "9876543210",
      address: "Lucknow",
      photoBase64: "",
      className: "3rd",
      section: "A",
      rollNo: 1,
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await orgRef.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_3b").set({
      schoolId: SCHOOL_1,
      classId: "cls_3b",
      session: "2026-2027",
      admNo: "ADM-3B-01",
      name: "Diya Singh",
      fatherName: "Amit Singh",
      motherName: "Priya Singh",
      dob: "2017-08-21",
      mobile: "9876543211",
      address: "Lucknow",
      photoBase64: "",
      className: "3rd",
      section: "B",
      rollNo: 1,
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });

    // Teacher A: TEACHER role, marks:edit, coScholastic:edit, timetable:view, teachers:none, SCHOOL_1
    await orgRef.collection("members").doc(TEACHER_A).set({
      uid: TEACHER_A,
      name: "Mrs. Sharma",
      email: "sharma@org1.com",
      mobile: "9999900001",
      role: "TEACHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "view",
        marks: "edit",
        coScholastic: "edit",
        reports: "view",
        timetable: "view",
        teachers: "none",
        settings: "none",
        members: "none",
      },
      teacherProfileCompleted: true,
      teacherPhotoBase64: "",
      requestedSubjects: ["Maths"],
      requestedClasses: ["3rd"],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // Teacher B: TEACHER role, marks:edit, coScholastic:edit, NOT a class teacher
    await orgRef.collection("members").doc(TEACHER_B).set({
      uid: TEACHER_B,
      name: "Mr. Verma",
      email: "verma@org1.com",
      mobile: "9999900002",
      role: "TEACHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "view",
        marks: "edit",
        coScholastic: "edit",
        reports: "view",
        timetable: "view",
        teachers: "none",
        settings: "none",
        members: "none",
      },
      teacherProfileCompleted: true,
      teacherPhotoBase64: "",
      requestedSubjects: ["English"],
      requestedClasses: ["3rd"],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // Office Operator: OFFICE_OPERATOR role, marks:edit, coScholastic:edit, timetable:edit, SCHOOL_1 only
    await orgRef.collection("members").doc(OPERATOR).set({
      uid: OPERATOR,
      name: "Office Operator",
      email: "op@org1.com",
      mobile: "9999900003",
      role: "OFFICE_OPERATOR",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "edit",
        marks: "edit",
        coScholastic: "edit",
        reports: "edit",
        timetable: "edit",
        teachers: "view",
        settings: "none",
        members: "none",
      },
      teacherProfileCompleted: false,
      teacherPhotoBase64: "",
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // Timetable viewer: OTHER role, timetable:view only
    await orgRef.collection("members").doc(TT_VIEWER).set({
      uid: TT_VIEWER,
      name: "Timetable Viewer",
      email: "ttv@org1.com",
      mobile: "9999900004",
      role: "OTHER",
      status: "active",
      allSchools: true,
      schools: [],
      permissions: {
        students: "none",
        marks: "none",
        coScholastic: "none",
        reports: "none",
        timetable: "view",
        teachers: "none",
        settings: "none",
        members: "none",
      },
      teacherProfileCompleted: false,
      teacherPhotoBase64: "",
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // Server-verified teacherScopes docs
    await orgRef.collection("teacherScopes").doc(TEACHER_A).set({
      uid: TEACHER_A,
      teacherId: "tchr_sharma",
      teacherName: "Mrs. Sharma",
      assignedClassSectionSubjects: [`${SCHOOL_1}|3rd|A|Maths`],
      classTeacherSections: [`${SCHOOL_1}|3rd|A`],
      updatedByUid: OWNER_UID,
      updatedAt: ts,
    });
    await orgRef.collection("teacherScopes").doc(TEACHER_B).set({
      uid: TEACHER_B,
      teacherId: "tchr_verma",
      teacherName: "Mr. Verma",
      assignedClassSectionSubjects: [`${SCHOOL_1}|3rd|B|English`],
      classTeacherSections: [],
      updatedByUid: OWNER_UID,
      updatedAt: ts,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore().collection("organisations").doc(ORG_ID);
  const teacherADb = testEnv.authenticatedContext(TEACHER_A).firestore().collection("organisations").doc(ORG_ID);
  const teacherBDb = testEnv.authenticatedContext(TEACHER_B).firestore().collection("organisations").doc(ORG_ID);
  const operatorDb = testEnv.authenticatedContext(OPERATOR).firestore().collection("organisations").doc(ORG_ID);
  const ttViewerDb = testEnv.authenticatedContext(TT_VIEWER).firestore().collection("organisations").doc(ORG_ID);
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER).firestore().collection("organisations").doc(ORG_ID);

  const ts = pastDate();
  const s1TeacherA = teacherADb.collection("schools").doc(SCHOOL_1);
  const s1TeacherB = teacherBDb.collection("schools").doc(SCHOOL_1);
  const s1Operator = operatorDb.collection("schools").doc(SCHOOL_1);

  // 1. Teacher A CAN write marks & markEditLogs for assigned (3rd-A Maths) on OPEN exam UT1 (T1_UT)
  await assertSucceeds(s1TeacherA.collection("marks").doc("m_3a_maths_ut1").set({
    studentId: "stu_3a",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "Maths",
    componentKey: "T1_UT",
    rawValue: "18",
    updatedByUid: TEACHER_A,
    updatedByName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertSucceeds(s1TeacherA.collection("markEditLogs").doc("log_3a_maths_ut1").set({
    markDocId: "m_3a_maths_ut1",
    studentId: "stu_3a",
    studentName: "Aarav Verma",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "Maths",
    componentKey: "T1_UT",
    oldValue: "",
    newValue: "18",
    editorUid: TEACHER_A,
    editorName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
  }));

  // 2. Teacher A REFUSED on CLOSED exam (T1_EXAM when only UT1 is open)
  await assertFails(s1TeacherA.collection("marks").doc("m_3a_maths_hy").set({
    studentId: "stu_3a",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "Maths",
    componentKey: "T1_EXAM",
    rawValue: "65",
    updatedByUid: TEACHER_A,
    updatedByName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 3. Teacher A REFUSED on unassigned subject (English in 3rd-A)
  await assertFails(s1TeacherA.collection("marks").doc("m_3a_eng_ut1").set({
    studentId: "stu_3a",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "English",
    componentKey: "T1_UT",
    rawValue: "15",
    updatedByUid: TEACHER_A,
    updatedByName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 4. Teacher A REFUSED on different section (stu_3b in 3rd-B, even for Maths on open UT1)
  await assertFails(s1TeacherA.collection("marks").doc("m_3b_maths_ut1").set({
    studentId: "stu_3b",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "Maths",
    componentKey: "T1_UT",
    rawValue: "19",
    updatedByUid: TEACHER_A,
    updatedByName: "Mrs. Sharma",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 5. Office Operator (non-teacher with marks:edit) KEEPS full access even on closed exams & other sections
  await assertSucceeds(s1Operator.collection("marks").doc("m_3b_maths_hy").set({
    studentId: "stu_3b",
    schoolId: SCHOOL_1,
    className: "3rd",
    subjectName: "Maths",
    componentKey: "T1_EXAM",
    rawValue: "70",
    updatedByUid: OPERATOR,
    updatedByName: "Office Operator",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 6. Co-Scholastic & TermInfo: Class Teacher A CAN edit stu_3a, REFUSED for stu_3b; Subject Teacher B REFUSED for both
  await assertSucceeds(s1TeacherA.collection("coScholastic").doc("co_3a_t1_art").set({
    studentId: "stu_3a",
    schoolId: SCHOOL_1,
    termNo: 1,
    areaCode: "ART",
    grade: "A",
    updatedByUid: TEACHER_A,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertSucceeds(s1TeacherA.collection("termInfo").doc("ti_3a_t1").set({
    studentId: "stu_3a",
    schoolId: SCHOOL_1,
    termNo: 1,
    daysPresent: 92,
    workingDays: 100,
    remarks: "Excellent progress",
    promotedTo: "4th",
    reportDate: "2026-10-15",
    updatedByUid: TEACHER_A,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertFails(s1TeacherA.collection("coScholastic").doc("co_3b_t1_art").set({
    studentId: "stu_3b",
    schoolId: SCHOOL_1,
    termNo: 1,
    areaCode: "ART",
    grade: "A",
    updatedByUid: TEACHER_A,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertFails(s1TeacherB.collection("coScholastic").doc("co_3b_t1_art_b").set({
    studentId: "stu_3b",
    schoolId: SCHOOL_1,
    termNo: 1,
    areaCode: "ART",
    grade: "B",
    updatedByUid: TEACHER_B,
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertFails(s1TeacherB.collection("termInfo").doc("ti_3b_t1_b").set({
    studentId: "stu_3b",
    schoolId: SCHOOL_1,
    termNo: 1,
    daysPresent: 88,
    workingDays: 100,
    remarks: "Good",
    promotedTo: "4th",
    reportDate: "2026-10-15",
    updatedByUid: TEACHER_B,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 7. teacherScopes privacy & tamper resistance: Teacher A CAN read own doc, CANNOT read B's doc, CANNOT edit own doc; Owner CAN edit
  await assertSucceeds(teacherADb.collection("teacherScopes").doc(TEACHER_A).get());
  await assertFails(teacherADb.collection("teacherScopes").doc(TEACHER_B).get());
  await assertFails(teacherADb.collection("teacherScopes").get());
  await assertFails(teacherADb.collection("teacherScopes").doc(TEACHER_A).set({
    uid: TEACHER_A,
    teacherId: "tchr_sharma",
    teacherName: "Mrs. Sharma",
    assignedClassSectionSubjects: [`${SCHOOL_1}|3rd|A|Maths`, `${SCHOOL_1}|3rd|B|Maths`],
    classTeacherSections: [`${SCHOOL_1}|3rd|A`, `${SCHOOL_1}|3rd|B`],
    updatedByUid: TEACHER_A,
    updatedAt: ts,
  }));
  await assertSucceeds(ownerDb.collection("teacherScopes").doc(TEACHER_A).set({
    uid: TEACHER_A,
    teacherId: "tchr_sharma",
    teacherName: "Mrs. Sharma",
    assignedClassSectionSubjects: [`${SCHOOL_1}|3rd|A|Maths`, `${SCHOOL_1}|3rd|A|Science`],
    classTeacherSections: [`${SCHOOL_1}|3rd|A`],
    updatedByUid: OWNER_UID,
    updatedAt: ts,
  }));

  // 8. teacherAttendance: Operator (timetable:edit, SCHOOL_1) CAN write SCHOOL_1, REFUSED SCHOOL_2; TT_VIEWER CAN read, REFUSED write; Outsider REFUSED
  const attId = `tatt_2026_09_14_${SCHOOL_1}_tchr_sharma`;
  const attPayload = {
    date: "2026-09-14",
    schoolId: SCHOOL_1,
    teacherId: "tchr_sharma",
    teacherName: "Mrs. Sharma",
    status: "PRESENT",
    note: "",
    linkedLeaveId: "",
    updatedByUid: OPERATOR,
    updatedByName: "Office Operator",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(operatorDb.collection("teacherAttendance").doc(attId).set(attPayload));
  await assertFails(operatorDb.collection("teacherAttendance").doc("tatt_sch2").set({
    ...attPayload,
    schoolId: SCHOOL_2,
  }));
  await assertSucceeds(ttViewerDb.collection("teacherAttendance").get());
  await assertFails(ttViewerDb.collection("teacherAttendance").doc("tatt_viewer").set({
    ...attPayload,
    updatedByUid: TT_VIEWER,
  }));
  await assertFails(outsiderDb.collection("teacherAttendance").get());

  // 9. Per-school Report Card Links & Open Exams: Owner CAN update, Teacher REFUSED
  await assertSucceeds(ownerDb.collection("schools").doc(SCHOOL_1).update({
    printClassTeacherNameOnReports: true,
    checkReportCardsReadyBeforeBatch: true,
    openExamsForTeachers: ["UT1", "HALF_YEARLY"],
    updatedAt: ts,
  }));
  await assertFails(teacherADb.collection("schools").doc(SCHOOL_1).update({
    printClassTeacherNameOnReports: false,
    updatedAt: ts,
  }));
});

// =============================================================================
// 12. PHASE 8: STUDENT ATTENDANCE (9th MODULE), BACKWARD-COMPATIBLE 8-KEY MIGRATION,
//     CLASS TEACHER OWN-SECTION & DAYS-BACK LIMIT, CALENDAR & APPEND-ONLY EDIT LOGS
// =============================================================================
test("Phase 8: Student Attendance 9th module, legacy 8-key migration, class teacher own-section + days-back limit, calendar, edit logs & cross-org isolation", async () => {
  const LEGACY_CT = "uid_p8_legacy_ct";      // Legacy 8-key member (NO 'attendance' key), Class Teacher of SCHOOL_1 3rd-A
  const SUBJ_TEACHER = "uid_p8_subj_tchr";   // 9-key member with attendance: 'none', NOT a class teacher
  const EDIT_TEACHER = "uid_p8_edit_tchr";   // 9-key member with attendance: 'edit' in SCHOOL_1
  const VIEW_ONLY = "uid_p8_view_only";      // 9-key member with attendance: 'view' in SCHOOL_1
  const NO_ACCESS = "uid_p8_no_access";      // 9-key member with attendance: 'none', NOT a class teacher
  const OUTSIDER = "uid_p8_outsider";        // Not a member of ORG_ID

  await seedOrgWithOwnerAndSchools();

  const nowEpochDay = Math.floor(Date.now() / 86400000);
  const isoFromEpochDay = (ed) => {
    const d = new Date(ed * 86400000);
    const y = d.getUTCFullYear();
    const m = String(d.getUTCMonth() + 1).padStart(2, "0");
    const day = String(d.getUTCDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };
  const todayIso = isoFromEpochDay(nowEpochDay);
  const twoDaysAgoEpoch = nowEpochDay - 2;
  const twoDaysAgoIso = isoFromEpochDay(twoDaysAgoEpoch);
  const thirtyDaysAgoEpoch = nowEpochDay - 30;
  const thirtyDaysAgoIso = isoFromEpochDay(thirtyDaysAgoEpoch);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const orgRef = db.collection("organisations").doc(ORG_ID);
    const ts = pastDate();

    // Configure SCHOOL_1 with Phase 8 defaults: autoFillReportAttendance=false, classTeacherAttendanceEditDaysBack=7
    await orgRef.collection("schools").doc(SCHOOL_1).set({
      autoFillReportAttendance: false,
      classTeacherAttendanceEditDaysBack: 7,
      updatedAt: ts,
    }, { merge: true });

    // Legacy 8-key member (NO 'attendance' key) — Class Teacher of 3rd-A
    await orgRef.collection("members").doc(LEGACY_CT).set({
      uid: LEGACY_CT,
      name: "Mrs. Legacy CT",
      email: "legacyct@org1.com",
      mobile: "9999900011",
      role: "TEACHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "view",
        marks: "edit",
        coScholastic: "edit",
        reports: "view",
        timetable: "view",
        teachers: "none",
        settings: "none",
        members: "none",
      },
      teacherProfileCompleted: true,
      teacherPhotoBase64: "",
      requestedSubjects: ["Maths"],
      requestedClasses: ["3rd"],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    await orgRef.collection("teacherScopes").doc(LEGACY_CT).set({
      uid: LEGACY_CT,
      teacherId: "tchr_legacy_ct",
      teacherName: "Mrs. Legacy CT",
      assignedClassSectionSubjects: [`${SCHOOL_1}|3rd|A|Maths`],
      classTeacherSections: [`${SCHOOL_1}|3rd|A`],
      updatedByUid: OWNER_UID,
      updatedAt: ts,
    });

    // Subject Teacher (attendance: 'none', NOT a class teacher)
    await orgRef.collection("members").doc(SUBJ_TEACHER).set({
      uid: SUBJ_TEACHER,
      name: "Mr. Subject Only",
      email: "subj@org1.com",
      mobile: "9999900012",
      role: "TEACHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "view",
        marks: "edit",
        coScholastic: "none",
        reports: "none",
        timetable: "view",
        teachers: "none",
        settings: "none",
        members: "none",
        attendance: "none",
      },
      teacherProfileCompleted: true,
      teacherPhotoBase64: "",
      requestedSubjects: ["Hindi"],
      requestedClasses: ["3rd"],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // Teacher explicitly granted attendance: 'edit' in SCHOOL_1
    await orgRef.collection("members").doc(EDIT_TEACHER).set({
      uid: EDIT_TEACHER,
      name: "Ms. Att Editor",
      email: "attedit@org1.com",
      mobile: "9999900013",
      role: "OFFICE_OPERATOR",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "view",
        marks: "none",
        coScholastic: "none",
        reports: "none",
        timetable: "none",
        teachers: "none",
        settings: "none",
        members: "none",
        attendance: "edit",
      },
      teacherProfileCompleted: false,
      teacherPhotoBase64: "",
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // View-only attendance member
    await orgRef.collection("members").doc(VIEW_ONLY).set({
      uid: VIEW_ONLY,
      name: "Att Viewer",
      email: "attview@org1.com",
      mobile: "9999900014",
      role: "OTHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "none",
        marks: "none",
        coScholastic: "none",
        reports: "none",
        timetable: "none",
        teachers: "none",
        settings: "none",
        members: "none",
        attendance: "view",
      },
      teacherProfileCompleted: false,
      teacherPhotoBase64: "",
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });

    // No-access member (attendance: 'none', not a class teacher)
    await orgRef.collection("members").doc(NO_ACCESS).set({
      uid: NO_ACCESS,
      name: "No Att Access",
      email: "noatt@org1.com",
      mobile: "9999900015",
      role: "OTHER",
      status: "active",
      allSchools: true,
      schools: [],
      permissions: {
        students: "none",
        marks: "none",
        coScholastic: "none",
        reports: "none",
        timetable: "none",
        teachers: "none",
        settings: "none",
        members: "none",
        attendance: "none",
      },
      teacherProfileCompleted: false,
      teacherPhotoBase64: "",
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerOrg = testEnv.authenticatedContext(OWNER_UID).firestore().collection("organisations").doc(ORG_ID);
  const ctOrg = testEnv.authenticatedContext(LEGACY_CT).firestore().collection("organisations").doc(ORG_ID);
  const subjOrg = testEnv.authenticatedContext(SUBJ_TEACHER).firestore().collection("organisations").doc(ORG_ID);
  const editOrg = testEnv.authenticatedContext(EDIT_TEACHER).firestore().collection("organisations").doc(ORG_ID);
  const viewOrg = testEnv.authenticatedContext(VIEW_ONLY).firestore().collection("organisations").doc(ORG_ID);
  const noneOrg = testEnv.authenticatedContext(NO_ACCESS).firestore().collection("organisations").doc(ORG_ID);
  const outsiderOrg = testEnv.authenticatedContext(OUTSIDER).firestore().collection("organisations").doc(ORG_ID);
  const ts = pastDate();

  const makeSheet = (uid, name, overrides = {}) => ({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    className: "3rd",
    section: "A",
    date: todayIso,
    dateEpochDay: nowEpochDay,
    entries: { stu_3a: { status: "P", reason: "" } },
    presentCount: 1,
    absentCount: 0,
    leaveCount: 0,
    lateCount: 0,
    halfDayCount: 0,
    totalStudents: 1,
    updatedByUid: uid,
    updatedByName: name,
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  });

  // 1. Legacy 8-key Class Teacher CAN write attendance for OWN section (3rd-A) today & 2 days ago
  const ctDocIdToday = `satt_${SCHOOL_1}_2026_2027_3rd_A_${todayIso.replace(/-/g, "_")}`;
  await assertSucceeds(ctOrg.collection("studentAttendance").doc(ctDocIdToday).set(makeSheet(LEGACY_CT, "Mrs. Legacy CT")));
  const ctDocId2Days = `satt_${SCHOOL_1}_2026_2027_3rd_A_${twoDaysAgoIso.replace(/-/g, "_")}`;
  await assertSucceeds(ctOrg.collection("studentAttendance").doc(ctDocId2Days).set(
    makeSheet(LEGACY_CT, "Mrs. Legacy CT", { date: twoDaysAgoIso, dateEpochDay: twoDaysAgoEpoch })
  ));

  // 2. Legacy Class Teacher REFUSED writing another section (3rd-B)
  await assertFails(ctOrg.collection("studentAttendance").doc("satt_3b_today").set(
    makeSheet(LEGACY_CT, "Mrs. Legacy CT", { section: "B" })
  ));

  // 3. Legacy Class Teacher REFUSED writing 30 days ago (exceeds 7-day limit), while Owner/Admin ALLOWED
  const oldDocId = `satt_${SCHOOL_1}_2026_2027_3rd_A_${thirtyDaysAgoIso.replace(/-/g, "_")}`;
  await assertFails(ctOrg.collection("studentAttendance").doc(oldDocId).set(
    makeSheet(LEGACY_CT, "Mrs. Legacy CT", { date: thirtyDaysAgoIso, dateEpochDay: thirtyDaysAgoEpoch })
  ));
  await assertSucceeds(ownerOrg.collection("studentAttendance").doc(oldDocId).set(
    makeSheet(OWNER_UID, "Principal Owner", { date: thirtyDaysAgoIso, dateEpochDay: thirtyDaysAgoEpoch })
  ));

  // 4. Class Teacher CAN query own section sheets, CANNOT list all org attendance sheets
  await assertSucceeds(
    ctOrg.collection("studentAttendance")
      .where("schoolId", "==", SCHOOL_1)
      .where("className", "==", "3rd")
      .where("section", "==", "A")
      .get()
  );
  await assertFails(ctOrg.collection("studentAttendance").get());

  // 5. Subject teacher (attendance: 'none', not CT) REFUSED read & write
  await assertFails(subjOrg.collection("studentAttendance").doc(ctDocIdToday).get());
  await assertFails(subjOrg.collection("studentAttendance").get());
  await assertFails(subjOrg.collection("studentAttendance").doc("satt_subj_write").set(makeSheet(SUBJ_TEACHER, "Mr. Subject Only")));

  // 6. Member with attendance: 'edit' in SCHOOL_1 ALLOWED to write 3rd-B in SCHOOL_1, REFUSED in SCHOOL_2
  await assertSucceeds(editOrg.collection("studentAttendance").doc("satt_edit_3b").set(
    makeSheet(EDIT_TEACHER, "Ms. Att Editor", { section: "B" })
  ));
  await assertFails(editOrg.collection("studentAttendance").doc("satt_edit_sch2").set(
    makeSheet(EDIT_TEACHER, "Ms. Att Editor", { schoolId: SCHOOL_2 })
  ));

  // 7. View-only member (attendance: 'view') ALLOWED to read SCHOOL_1, REFUSED write & delete
  await assertSucceeds(viewOrg.collection("studentAttendance").doc(ctDocIdToday).get());
  await assertFails(viewOrg.collection("studentAttendance").doc("satt_view_write").set(makeSheet(VIEW_ONLY, "Att Viewer")));
  await assertFails(viewOrg.collection("studentAttendance").doc(ctDocIdToday).delete());

  // 8. No-access member & Cross-org outsider REFUSED read & write on all Phase 8 collections
  await assertFails(noneOrg.collection("studentAttendance").doc(ctDocIdToday).get());
  await assertFails(noneOrg.collection("studentAttendance").get());
  await assertFails(noneOrg.collection("schoolCalendars").get());
  await assertFails(outsiderOrg.collection("studentAttendance").doc(ctDocIdToday).get());
  await assertFails(outsiderOrg.collection("schoolCalendars").get());
  await assertFails(outsiderOrg.collection("studentAttendanceEditLogs").get());

  // 9. School Calendar rules: Owner ALLOWED valid calendar, REFUSED overlapping terms; Legacy CT CAN read, CANNOT write
  const calDocId = `cal_${SCHOOL_1}_2026_2027`;
  const validCalendar = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    workingDays: ["MON", "TUE", "WED", "THU", "FRI", "SAT"],
    holidays: [{ id: "h1", name: "Diwali", startDate: "2026-11-08", endDate: "2026-11-12", kind: "HOLIDAY" }],
    halfWorkingDays: [{ id: "hd1", date: "2026-08-14", name: "PTM Half Day" }],
    workingDayExceptions: [],
    term1StartDate: "2026-04-01",
    term1EndDate: "2026-09-30",
    term2StartDate: "2026-10-01",
    term2EndDate: "2027-03-31",
    updatedByUid: OWNER_UID,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(ownerOrg.collection("schoolCalendars").doc(calDocId).set(validCalendar));
  await assertFails(ownerOrg.collection("schoolCalendars").doc("cal_overlap").set({
    ...validCalendar,
    term1EndDate: "2026-10-15",
    term2StartDate: "2026-10-01",
  }));
  await assertSucceeds(ctOrg.collection("schoolCalendars").doc(calDocId).get());
  await assertFails(ctOrg.collection("schoolCalendars").doc(calDocId).update({
    workingDays: ["MON", "TUE"],
    updatedByUid: LEGACY_CT,
    updatedAt: ts,
  }));

  // 10. Student Attendance Edit Logs: append-only (create allowed for authorized editor, update & delete DENIED even for Owner)
  const logPayload = {
    attendanceDocId: ctDocId2Days,
    schoolId: SCHOOL_1,
    session: "2026-2027",
    className: "3rd",
    section: "A",
    date: twoDaysAgoIso,
    editorUid: LEGACY_CT,
    editorName: "Mrs. Legacy CT",
    summary: "Changed Aarav from P to A",
    changes: [{ studentId: "stu_3a", studentName: "Aarav", oldStatus: "P", newStatus: "A", oldReason: "", newReason: "" }],
    clientEditedAtMs: Date.now() - 200,
    createdAt: ts,
  };
  await assertSucceeds(ctOrg.collection("studentAttendanceEditLogs").doc("salog_01").set(logPayload));
  await assertFails(ownerOrg.collection("studentAttendanceEditLogs").doc("salog_01").update({ summary: "Tampered" }));
  await assertFails(ownerOrg.collection("studentAttendanceEditLogs").doc("salog_01").delete());

  // 11. Per-school Phase 8 settings update: Owner ALLOWED, Class Teacher REFUSED
  await assertSucceeds(ownerOrg.collection("schools").doc(SCHOOL_1).update({
    autoFillReportAttendance: true,
    classTeacherAttendanceEditDaysBack: 10,
    updatedAt: ts,
  }));
  await assertFails(ctOrg.collection("schools").doc(SCHOOL_1).update({
    autoFillReportAttendance: false,
    updatedAt: ts,
  }));
});






