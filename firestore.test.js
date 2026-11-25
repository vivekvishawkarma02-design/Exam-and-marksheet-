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

// =============================================================================
// 13. PHASE 9: STUDENT REGISTER (11th MODULE) & FEE MANAGEMENT (10th MODULE)
//     BACKWARD-COMPATIBLE 8/9-KEY MIGRATION, RECEIPT IMMUTABILITY, CONCESSION
//     APPROVAL LIMIT, NEGATIVE AMOUNT REJECTION, CANCEL-ONLY & CROSS-ORG ISOLATION
// =============================================================================
test("Phase 9: Student Register (11th module) & Fees (10th module) permissions, receipt immutability, concession approval limit, negative amount rejection, cancel-only & cross-org isolation", async () => {
  const FEE_CLERK = "uid_p9_fee_clerk";         // fees: 'edit', studentRegister: 'edit' in SCHOOL_1
  const FEE_VIEWER = "uid_p9_fee_viewer";       // fees: 'view', studentRegister: 'view' in SCHOOL_1
  const REG_ONLY_EDITOR = "uid_p9_reg_editor";  // students: 'none', studentRegister: 'edit', fees: 'none' in SCHOOL_1
  const LEGACY_MEMBER = "uid_p9_legacy_9key";   // 9-key member (missing 'fees' & 'studentRegister' keys -> no access)
  const OUTSIDER = "uid_p9_outsider";           // Not a member of ORG_ID

  await seedOrgWithOwnerAndSchools();
  const ts = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const orgRef = db.collection("organisations").doc(ORG_ID);

    // 1. Seed 11-key Fee Clerk (fees: 'edit', studentRegister: 'edit' in SCHOOL_1)
    await orgRef.collection("members").doc(FEE_CLERK).set({
      uid: FEE_CLERK,
      name: "Mr. Fee Clerk",
      email: "clerk@sts.edu",
      mobile: "9800000101",
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
        attendance: "none",
        fees: "edit",
        studentRegister: "edit",
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

    // 2. Seed 11-key Fee Viewer (fees: 'view', studentRegister: 'view' in SCHOOL_1)
    await orgRef.collection("members").doc(FEE_VIEWER).set({
      uid: FEE_VIEWER,
      name: "Ms. Fee Viewer",
      email: "viewer@sts.edu",
      mobile: "9800000102",
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
        attendance: "none",
        fees: "view",
        studentRegister: "view",
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

    // 3. Seed Student Register Editor (students: 'none', studentRegister: 'edit', fees: 'none' in SCHOOL_1)
    await orgRef.collection("members").doc(REG_ONLY_EDITOR).set({
      uid: REG_ONLY_EDITOR,
      name: "Register Clerk",
      email: "reg@sts.edu",
      mobile: "9800000103",
      role: "OFFICE_OPERATOR",
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
        attendance: "none",
        fees: "none",
        studentRegister: "edit",
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

    // 4. Seed Legacy 9-key member (missing 'fees' and 'studentRegister' keys -> defaults to no access)
    await orgRef.collection("members").doc(LEGACY_MEMBER).set({
      uid: LEGACY_MEMBER,
      name: "Legacy Teacher",
      email: "legacy9@sts.edu",
      mobile: "9800000104",
      role: "TEACHER",
      status: "active",
      allSchools: false,
      schools: [SCHOOL_1],
      permissions: {
        students: "none",
        marks: "none",
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
      requestedSubjects: [],
      requestedClasses: [],
      assignedClassSubjects: [],
      lastActiveAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerOrg = testEnv.authenticatedContext(OWNER_UID).firestore().collection("organisations").doc(ORG_ID);
  const clerkOrg = testEnv.authenticatedContext(FEE_CLERK).firestore().collection("organisations").doc(ORG_ID);
  const viewerOrg = testEnv.authenticatedContext(FEE_VIEWER).firestore().collection("organisations").doc(ORG_ID);
  const regOrg = testEnv.authenticatedContext(REG_ONLY_EDITOR).firestore().collection("organisations").doc(ORG_ID);
  const legacyOrg = testEnv.authenticatedContext(LEGACY_MEMBER).firestore().collection("organisations").doc(ORG_ID);
  const outsiderOrg = testEnv.authenticatedContext(OUTSIDER).firestore().collection("organisations").doc(ORG_ID);

  // 1. PART A: Student Register (11th module) allows creating & updating students with extended Phase 9 fields
  const s1RegStudents = regOrg.collection("schools").doc(SCHOOL_1).collection("students");
  await assertSucceeds(s1RegStudents.doc("stu_p9_reg_1").set({
    schoolId: SCHOOL_1,
    classId: "cls_3rd",
    session: "2026-2027",
    admNo: "ADM-P9-01",
    name: "Ananya Tiwari",
    fatherName: "Manoj Tiwari",
    motherName: "Kavita Tiwari",
    dob: "2017-03-14",
    mobile: "9839100001",
    secondMobile: "9839100002",
    address: "Civil Lines, Ghazipur",
    photoBase64: "",
    className: "3rd",
    section: "A",
    rollNo: 5,
    admissionDate: "2026-04-02",
    previousSchool: "Little Angels School",
    siblingGroupId: "FAM_ADM_P9_01",
    usesTransport: true,
    transportRouteName: "Route 2 - Lanka",
    transportMonthlyAmount: 450,
    status: "ACTIVE",
    leftDate: "",
    leftReason: "",
    sample: false,
    createdAt: ts,
    updatedAt: ts,
  }));

  // Student Register editor can change status to LEFT with date & reason
  await assertSucceeds(s1RegStudents.doc("stu_p9_reg_1").update({
    status: "LEFT",
    leftDate: "2026-10-15",
    leftReason: "Transferred to Varanasi",
    updatedAt: ts,
  }));

  // Fee Viewer (studentRegister: 'view', students: 'none') CAN read students, CANNOT edit
  await assertSucceeds(viewerOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p9_reg_1").get());
  await assertFails(viewerOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p9_reg_1").update({
    section: "B",
    updatedAt: ts,
  }));

  // Legacy member without 'students', 'studentRegister', or 'fees' view CANNOT read students
  await assertFails(legacyOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p9_reg_1").get());

  // 2. PART B: Fee Structure & Student Fee Overrides (10th module 'fees')
  const structureDocId = `fstr_${SCHOOL_1}_2026_2027`;
  const makeStructure = (uid, maxConcPct = 20) => ({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    monthOrder: ["APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
    chargedTuitionMonths: ["APR", "MAY", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
    chargedTransportMonths: ["APR", "MAY", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
    tuitionByClassAndMonth: {
      "3rd": { DEFAULT: 800, JUL: 800 },
    },
    examFees: [
      { examHeadKey: "EXAM_UT1", monthCode: "JUL", amountByClass: { "3rd": 150 } },
      { examHeadKey: "EXAM_HY", monthCode: "OCT", amountByClass: { "3rd": 250 } },
    ],
    optionalCatalogue: [
      { id: "cat_tie", name: "Tie", price: 60, classFilter: [], hidden: false },
      { id: "cat_belt", name: "Belt", price: 80, classFilter: [], hidden: false },
    ],
    frozenPastCellAmountsByClass: {},
    maxConcessionPercentWithoutAdminApproval: maxConcPct,
    messageTemplates: {
      feeDueTemplate: "Dear {guardian}, fee of Rs. {amount} for {student} is due.",
      feeReceiptTemplate: "Received Rs. {amount} for {student}.",
      generalTemplate: "Update from {school}.",
    },
    updatedByUid: uid,
    updatedByName: "Mr. Fee Clerk",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  });

  // Fee Clerk (fees: 'edit', SCHOOL_1) CAN save FeeStructure & StudentFeeOverride in SCHOOL_1, REFUSED in SCHOOL_2
  await assertSucceeds(clerkOrg.collection("feeStructures").doc(structureDocId).set(makeStructure(FEE_CLERK, 20)));
  await assertFails(clerkOrg.collection("feeStructures").doc(`fstr_${SCHOOL_2}_2026_2027`).set({
    ...makeStructure(FEE_CLERK, 20),
    schoolId: SCHOOL_2,
  }));

  await assertSucceeds(clerkOrg.collection("studentFeeOverrides").doc(`fovr_${SCHOOL_1}_2026_2027_stu_1`).set({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    studentId: "stu_1",
    studentName: "Aarav Sharma",
    cellOverrides: { "ALL|TUITION": 650 },
    remark: "Staff ward concession rate",
    updatedByUid: FEE_CLERK,
    updatedByName: "Mr. Fee Clerk",
    clientEditedAtMs: Date.now() - 300,
    createdAt: ts,
    updatedAt: ts,
  }));

  // Override WITHOUT a remark is REJECTED by rules
  await assertFails(clerkOrg.collection("studentFeeOverrides").doc("fovr_empty_remark").set({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    studentId: "stu_1",
    studentName: "Aarav Sharma",
    cellOverrides: { "ALL|TUITION": 650 },
    remark: "",
    updatedByUid: FEE_CLERK,
    updatedByName: "Mr. Fee Clerk",
    clientEditedAtMs: Date.now() - 300,
    createdAt: ts,
    updatedAt: ts,
  }));

  // 3. PART C & PART E: Fee Receipts — positive amounts, concession limit, immutability, cancel-only
  const makeReceiptPayload = (uid, overrides = {}) => ({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    receiptNumber: "RCP-2627-A1B-0001",
    deviceNodeCode: "A1B",
    sequenceNo: 1,
    receiptDateIso: "2026-07-10",
    studentId: "stu_p9_reg_1",
    studentName: "Ananya Tiwari",
    studentAdmNo: "ADM-P9-01",
    fatherName: "Manoj Tiwari",
    className: "3rd",
    section: "A",
    rollNo: 5,
    lineItems: [
      {
        lineId: "GRID_JUL_TUITION",
        lineType: "GRID_CELL",
        monthCode: "JUL",
        headKey: "TUITION",
        title: "July Tuition Fee",
        quantity: 1,
        unitPrice: 800,
        lineGrossAmount: 800,
        concessionAllocated: 80,
        netPayableOnLine: 720,
        paidAllocated: 720,
        balanceDueAfterLine: 0,
        catalogueItemId: "",
        saveToCatalogue: false,
      },
    ],
    totalBeforeConcession: 800,
    concessionMode: "PERCENTAGE",
    concessionScope: "WHOLE_RECEIPT",
    concessionTargetLineId: "",
    concessionInputValue: 10,
    concessionAmount: 80,
    concessionRemark: "Sibling concession",
    concessionGivenByUid: uid,
    concessionGivenByName: "Mr. Fee Clerk",
    concessionApprovedByAdminUid: "",
    concessionApprovedByAdminName: "",
    netPayableAmount: 720,
    amountPaid: 720,
    balanceDueOnReceipt: 0,
    paymentMode: "CASH",
    paymentNote: "Paid at counter",
    status: "ISSUED",
    receivedByUid: uid,
    receivedByName: "Mr. Fee Clerk",
    issuedAtMs: Date.now() - 200,
    cancelledAtMs: 0,
    cancelledByUid: "",
    cancelledByName: "",
    cancelReason: "",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  });

  // 3a. Clerk CAN issue a valid receipt with 10% concession (within 20% limit)
  const rcpDocId = `frcp_${SCHOOL_1}_2026_2027_RCP_2627_A1B_0001`;
  await assertSucceeds(clerkOrg.collection("feeReceipts").doc(rcpDocId).set(makeReceiptPayload(FEE_CLERK)));

  // 3b. View-only member CANNOT create or cancel a receipt, but CAN read receipts & structures
  await assertSucceeds(viewerOrg.collection("feeStructures").doc(structureDocId).get());
  await assertSucceeds(viewerOrg.collection("feeReceipts").doc(rcpDocId).get());
  await assertFails(viewerOrg.collection("feeReceipts").doc("frcp_viewer_try").set(makeReceiptPayload(FEE_VIEWER)));
  await assertFails(viewerOrg.collection("feeReceipts").doc(rcpDocId).update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: FEE_VIEWER,
    cancelledByName: "Ms. Fee Viewer",
    cancelReason: "Unauthorized cancel",
    updatedAt: ts,
  }));

  // 3c. Negative or zero amounts, or mismatched math are REJECTED by rules
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_neg_paid").set(
    makeReceiptPayload(FEE_CLERK, { amountPaid: -100, balanceDueOnReceipt: 820 })
  ));
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_zero_paid").set(
    makeReceiptPayload(FEE_CLERK, { amountPaid: 0, balanceDueOnReceipt: 720 })
  ));
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_neg_conc").set(
    makeReceiptPayload(FEE_CLERK, { concessionAmount: -50, netPayableAmount: 850, amountPaid: 850 })
  ));
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_no_conc_remark").set(
    makeReceiptPayload(FEE_CLERK, { concessionRemark: "" })
  ));

  // 3d. Concession above 20% limit by non-admin Clerk WITHOUT admin approval is REJECTED by rules
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_over_limit_no_admin").set(
    makeReceiptPayload(FEE_CLERK, {
      concessionInputValue: 30,
      concessionAmount: 240,
      netPayableAmount: 560,
      amountPaid: 560,
      concessionApprovedByAdminUid: "",
    })
  ));

  // Non-admin Clerk forging their own UID as concessionApprovedByAdminUid is REJECTED by rules
  await assertFails(clerkOrg.collection("feeReceipts").doc("frcp_over_limit_fake_admin").set(
    makeReceiptPayload(FEE_CLERK, {
      concessionInputValue: 30,
      concessionAmount: 240,
      netPayableAmount: 560,
      amountPaid: 560,
      concessionApprovedByAdminUid: FEE_CLERK,
      concessionApprovedByAdminName: "Self Forged",
    })
  ));

  // Concession above 20% limit WITH real active OWNER/ADMIN approval OR issued by OWNER/ADMIN SUCCEEDS
  await assertSucceeds(clerkOrg.collection("feeReceipts").doc("frcp_over_limit_with_admin").set(
    makeReceiptPayload(FEE_CLERK, {
      receiptNumber: "RCP-2627-A1B-0002",
      sequenceNo: 2,
      concessionInputValue: 30,
      concessionAmount: 240,
      netPayableAmount: 560,
      amountPaid: 560,
      concessionApprovedByAdminUid: OWNER_UID,
      concessionApprovedByAdminName: "Principal A",
    })
  ));

  // 3e. Receipt immutability: CANNOT edit amountPaid, receiptNumber, or lineItems on an issued receipt; CANNOT delete receipt
  await assertFails(clerkOrg.collection("feeReceipts").doc(rcpDocId).update({
    amountPaid: 500,
    balanceDueOnReceipt: 220,
    updatedAt: ts,
  }));
  await assertFails(ownerOrg.collection("feeReceipts").doc(rcpDocId).update({
    receiptNumber: "RCP-TAMPERED-9999",
    updatedAt: ts,
  }));
  await assertFails(clerkOrg.collection("feeReceipts").doc(rcpDocId).delete());
  await assertFails(ownerOrg.collection("feeReceipts").doc(rcpDocId).delete());

  // 3f. Cancelling a receipt without a reason FAILS; cancelling with a reason SUCCEEDS; re-cancelling or reverting FAILS
  await assertFails(clerkOrg.collection("feeReceipts").doc(rcpDocId).update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: FEE_CLERK,
    cancelledByName: "Mr. Fee Clerk",
    cancelReason: "",
    updatedAt: ts,
  }));
  await assertSucceeds(clerkOrg.collection("feeReceipts").doc(rcpDocId).update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: FEE_CLERK,
    cancelledByName: "Mr. Fee Clerk",
    cancelReason: "Entered wrong payment mode, reissuing as UPI",
    updatedAt: ts,
  }));
  await assertFails(clerkOrg.collection("feeReceipts").doc(rcpDocId).update({
    status: "ISSUED",
    cancelledAtMs: 0,
    cancelledByUid: "",
    cancelledByName: "",
    cancelReason: "",
    updatedAt: ts,
  }));

  // 4. PART D2: Send Queue message sent logs, Notice Maker & Append-Only Fee Audit Logs
  const msgLogId = `fmsg_${SCHOOL_1}_2026_2027_stu_p9_reg_1_FEE_DUE_REMINDER_DUE_2026_11`;
  await assertSucceeds(clerkOrg.collection("feeMessageSentLogs").doc(msgLogId).set({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    studentId: "stu_p9_reg_1",
    studentName: "Ananya Tiwari",
    className: "3rd",
    section: "A",
    guardianName: "Manoj Tiwari",
    normalizedMobile: "+919839100001",
    messageType: "FEE_DUE_REMINDER",
    contextKey: "DUE_2026_11",
    messageText: "Dear Manoj Tiwari, fee of Rs. 800 is due.",
    channel: "WHATSAPP",
    sentByUid: FEE_CLERK,
    sentByName: "Mr. Fee Clerk",
    sentAtMs: Date.now() - 100,
    createdAt: ts,
    updatedAt: ts,
  }));

  const noticeId = "not_p9_001";
  await assertSucceeds(clerkOrg.collection("schoolNotices").doc(noticeId).set({
    schoolId: SCHOOL_1,
    title: "Winter Timings Notice",
    bodyText: "School will open at 8:30 AM from Monday.",
    noticeDateIso: "2026-11-23",
    createdByUid: FEE_CLERK,
    createdByName: "Mr. Fee Clerk",
    createdAtMs: Date.now() - 100,
    createdAt: ts,
    updatedAt: ts,
  }));

  const auditId = "faud_p9_001";
  await assertSucceeds(clerkOrg.collection("feeAuditLogs").doc(auditId).set({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    action: "RECEIPT_ISSUED",
    targetId: rcpDocId,
    studentId: "stu_p9_reg_1",
    studentName: "Ananya Tiwari",
    summary: "Issued RCP-2627-A1B-0001 for Ananya Tiwari: Paid Rs. 720",
    actorUid: FEE_CLERK,
    actorName: "Mr. Fee Clerk",
    clientCreatedAtMs: Date.now() - 100,
    createdAt: ts,
  }));
  // Fee audit logs are strictly append-only (update & delete denied even for Owner)
  await assertFails(ownerOrg.collection("feeAuditLogs").doc(auditId).update({ summary: "Altered" }));
  await assertFails(ownerOrg.collection("feeAuditLogs").doc(auditId).delete());

  // 5. No-access members (including legacy 9-key member and Register-only member) & Cross-Org Outsider DENIED on all Fees collections
  await assertFails(regOrg.collection("feeStructures").get());
  await assertFails(regOrg.collection("feeReceipts").get());
  await assertFails(legacyOrg.collection("feeStructures").get());
  await assertFails(legacyOrg.collection("feeReceipts").get());
  await assertFails(outsiderOrg.collection("feeStructures").get());
  await assertFails(outsiderOrg.collection("feeReceipts").get());
  await assertFails(outsiderOrg.collection("studentFeeOverrides").get());
  await assertFails(outsiderOrg.collection("feeMessageSentLogs").get());
  await assertFails(outsiderOrg.collection("schoolNotices").get());
  await assertFails(outsiderOrg.collection("feeAuditLogs").get());
});

// ============================================================================
// TEST 17: PHASE 10 — CERTIFICATES, CASH BOOK & ADMISSION ENQUIRY RULES
// ============================================================================
test("17. Phase 10: Certificates, Cash Book & Enquiry RBAC, missing-key denial, immutability, approval limit & conversion", async () => {
  await seedOrgWithOwnerAndSchools();
  const ts = pastDate(5000);

  const CERT_EDITOR = "p10_cert_editor";
  const CERT_VIEWER = "p10_cert_viewer";
  const CB_EDITOR = "p10_cb_editor";
  const CB_VIEWER = "p10_cb_viewer";
  const ENQ_EDITOR = "p10_enq_editor";
  const ENQ_VIEWER = "p10_enq_viewer";
  const LEGACY_NO_P10 = "p10_legacy_missing_keys";
  const OUTSIDER = "p10_outsider";

  const baseNone = {
    students: "none",
    marks: "none",
    coScholastic: "none",
    reports: "none",
    timetable: "none",
    teachers: "none",
    settings: "none",
    members: "none",
    attendance: "none",
    fees: "none",
    studentRegister: "none",
    certificates: "none",
    cashBook: "none",
    enquiry: "none",
  };

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const orgRef = db.collection("organisations").doc(ORG_ID);
    const seedMember = (uid, name, perms, role = "OFFICE_OPERATOR") =>
      orgRef.collection("members").doc(uid).set({
        uid,
        name,
        email: `${uid}@sts.edu`,
        mobile: "9800000200",
        role,
        status: "active",
        allSchools: false,
        schools: [SCHOOL_1],
        permissions: perms,
        teacherProfileCompleted: true,
        teacherPhotoBase64: "",
        requestedSubjects: [],
        requestedClasses: [],
        assignedClassSubjects: [],
        lastActiveAt: ts,
        createdAt: ts,
        updatedAt: ts,
      });

    await seedMember(CERT_EDITOR, "Cert Clerk", { ...baseNone, certificates: "edit" });
    await seedMember(CERT_VIEWER, "Cert Viewer", { ...baseNone, certificates: "view" });
    await seedMember(CB_EDITOR, "Cash Clerk", { ...baseNone, cashBook: "edit" });
    await seedMember(CB_VIEWER, "Cash Viewer", { ...baseNone, cashBook: "view" });
    await seedMember(ENQ_EDITOR, "Enquiry Clerk", { ...baseNone, enquiry: "edit" });
    await seedMember(ENQ_VIEWER, "Enquiry Viewer", { ...baseNone, enquiry: "view" });
    // Legacy member WITHOUT certificates/cashBook/enquiry keys -> MUST be denied (missing key = no access)
    await seedMember(LEGACY_NO_P10, "Legacy Teacher", {
      students: "none",
      marks: "none",
      coScholastic: "none",
      reports: "none",
      timetable: "view",
      teachers: "none",
      settings: "none",
      members: "none",
      attendance: "none",
      fees: "none",
      studentRegister: "none",
    }, "TEACHER");

    // Seed an active student in SCHOOL_1 for TC mark-left test
    await orgRef.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p10_01").set({
      schoolId: SCHOOL_1,
      classId: "cls_5th",
      session: "2026-2027",
      admNo: "ADM-P10-01",
      name: "Rohan Verma",
      fatherName: "Rajesh Verma",
      motherName: "Sunita Verma",
      dob: "2015-06-12",
      mobile: "9839100055",
      secondMobile: "",
      address: "Station Road",
      photoBase64: "",
      className: "5th",
      section: "A",
      rollNo: 1,
      admissionDate: "2024-04-01",
      previousSchool: "",
      siblingGroupId: "",
      usesTransport: false,
      transportRouteName: "",
      transportMonthlyAmount: 0,
      status: "ACTIVE",
      leftDate: "",
      leftReason: "",
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });

    // Seed a Phase 9 fee receipt so Cash Book user (with fees: 'none') can auto-read fee income
    await orgRef.collection("feeReceipts").doc("frcp_p10_income_1").set({
      schoolId: SCHOOL_1,
      session: "2026-2027",
      receiptNumber: "RCP-2627-A1B-0010",
      deviceNodeCode: "A1B",
      sequenceNo: 10,
      receiptDateIso: "2026-11-24",
      studentId: "stu_p10_01",
      studentName: "Rohan Verma",
      studentAdmNo: "ADM-P10-01",
      fatherName: "Rajesh Verma",
      className: "5th",
      section: "A",
      rollNo: 1,
      lineItems: [{ title: "Tuition", amount: 1200 }],
      totalBeforeConcession: 1200,
      concessionMode: "NONE",
      concessionScope: "WHOLE_RECEIPT",
      concessionTargetLineId: "",
      concessionInputValue: 0,
      concessionAmount: 0,
      concessionRemark: "",
      concessionGivenByUid: "",
      concessionGivenByName: "",
      concessionApprovedByAdminUid: "",
      concessionApprovedByAdminName: "",
      netPayableAmount: 1200,
      amountPaid: 1200,
      balanceDueOnReceipt: 0,
      paymentMode: "CASH",
      paymentNote: "",
      status: "ISSUED",
      receivedByUid: OWNER_UID,
      receivedByName: "Principal",
      issuedAtMs: Date.now() - 1000,
      cancelledAtMs: 0,
      cancelledByUid: "",
      cancelledByName: "",
      cancelReason: "",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerOrg = testEnv.authenticatedContext(OWNER_UID).firestore().collection("organisations").doc(ORG_ID);
  const certEdOrg = testEnv.authenticatedContext(CERT_EDITOR).firestore().collection("organisations").doc(ORG_ID);
  const certVwOrg = testEnv.authenticatedContext(CERT_VIEWER).firestore().collection("organisations").doc(ORG_ID);
  const cbEdOrg = testEnv.authenticatedContext(CB_EDITOR).firestore().collection("organisations").doc(ORG_ID);
  const cbVwOrg = testEnv.authenticatedContext(CB_VIEWER).firestore().collection("organisations").doc(ORG_ID);
  const enqEdOrg = testEnv.authenticatedContext(ENQ_EDITOR).firestore().collection("organisations").doc(ORG_ID);
  const enqVwOrg = testEnv.authenticatedContext(ENQ_VIEWER).firestore().collection("organisations").doc(ORG_ID);
  const legacyOrg = testEnv.authenticatedContext(LEGACY_NO_P10).firestore().collection("organisations").doc(ORG_ID);
  const outsiderOrg = testEnv.authenticatedContext(OUTSIDER).firestore().collection("organisations").doc(ORG_ID);

  // =========================================================================
  // PART 1: CERTIFICATES MODULE
  // =========================================================================
  const certPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    certificateType: "TC",
    certificateNumber: "TC-2627-A1B-0001",
    deviceNodeCode: "A1B",
    sequenceNo: 1,
    issueDateIso: "2026-11-24",
    studentId: "stu_p10_01",
    studentName: "Rohan Verma",
    studentAdmNo: "ADM-P10-01",
    fatherName: "Rajesh Verma",
    motherName: "Sunita Verma",
    dob: "2015-06-12",
    className: "5th",
    section: "A",
    rollNo: 1,
    admissionDate: "2024-04-01",
    address: "Station Road",
    reasonForLeaving: "Family relocated",
    lastAttendanceDateIso: "2026-11-20",
    conduct: "Good",
    promotionStatus: "Promoted to Class 6th",
    feeDuesStatus: "All school dues cleared",
    remarks: "",
    status: "ISSUED",
    issuedByUid: CERT_EDITOR,
    issuedByName: "Cert Clerk",
    issuedAtMs: Date.now() - 1000,
    cancelledAtMs: 0,
    cancelledByUid: "",
    cancelledByName: "",
    cancelReason: "",
    reissuedFromCertId: "",
    reissuedFromCertNumber: "",
    createdAt: ts,
    updatedAt: ts,
  };
  const certDocId = `cert_${SCHOOL_1}_2026_2027_TC_2627_A1B_0001`;

  // Cert Editor can read students in SCHOOL_1 and issue TC in SCHOOL_1, NOT SCHOOL_2
  await assertSucceeds(certEdOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p10_01").get());
  await assertSucceeds(certEdOrg.collection("schoolCertificates").doc(certDocId).set(certPayload));
  await assertFails(certEdOrg.collection("schoolCertificates").doc("cert_sch2").set({ ...certPayload, schoolId: SCHOOL_2 }));

  // Cert Editor can mark student LEFT (only status, leftDate, leftReason, updatedAt), NOT rename student
  await assertFails(certEdOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p10_01").update({
    name: "Hacked Name",
    updatedAt: ts,
  }));
  await assertSucceeds(certEdOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p10_01").update({
    status: "LEFT",
    leftDate: "2026-11-24",
    leftReason: "TC issued",
    updatedAt: ts,
  }));

  // Cert Viewer can read certificates, CANNOT issue or cancel
  await assertSucceeds(certVwOrg.collection("schoolCertificates").doc(certDocId).get());
  await assertFails(certVwOrg.collection("schoolCertificates").doc("cert_vw_try").set({ ...certPayload, issuedByUid: CERT_VIEWER }));

  // Issued certificate is IMMUTABLE (no edit, no delete, no cancel without reason); cancel WITH reason succeeds
  await assertFails(certEdOrg.collection("schoolCertificates").doc(certDocId).update({ conduct: "Excellent", updatedAt: ts }));
  await assertFails(ownerOrg.collection("schoolCertificates").doc(certDocId).delete());
  await assertFails(certEdOrg.collection("schoolCertificates").doc(certDocId).update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: CERT_EDITOR,
    cancelledByName: "Cert Clerk",
    cancelReason: "",
    updatedAt: ts,
  }));
  await assertSucceeds(certEdOrg.collection("schoolCertificates").doc(certDocId).update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: CERT_EDITOR,
    cancelledByName: "Cert Clerk",
    cancelReason: "Typo in father name",
    updatedAt: ts,
  }));

  // =========================================================================
  // PART 2: CASH BOOK MODULE
  // =========================================================================
  // Cash Book viewer/editor (with fees: 'none') CAN auto-read Phase 9 feeReceipts!
  await assertSucceeds(cbVwOrg.collection("feeReceipts").doc("frcp_p10_income_1").get());
  await assertSucceeds(cbEdOrg.collection("feeReceipts").doc("frcp_p10_income_1").get());

  // Cash Book settings: non-admin cannot change expenseApprovalLimitRs from default 5000; Owner CAN
  const cbSetId = `cbset_${SCHOOL_1}`;
  const makeCbSettings = (uid, limit = 5000) => ({
    schoolId: SCHOOL_1,
    expenseCategories: ["Electricity", "Stationery", "Repairs", "Other"],
    otherIncomeCategories: ["Donation", "Other Income"],
    expenseApprovalLimitRs: limit,
    defaultOpeningBalanceRs: 1000,
    updatedByUid: uid,
    updatedByName: "Staff",
    clientEditedAtMs: Date.now() - 100,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertFails(cbEdOrg.collection("cashBookSettings").doc(cbSetId).set(makeCbSettings(CB_EDITOR, 99999)));
  await assertSucceeds(ownerOrg.collection("cashBookSettings").doc(cbSetId).set(makeCbSettings(OWNER_UID, 5000)));

  // Cash Book entry <= 5000 succeeds for Cash Clerk
  const makeCbEntry = (uid, overrides = {}) => ({
    schoolId: SCHOOL_1,
    dateIso: "2026-11-24",
    entryType: "EXPENSE",
    category: "Stationery",
    amount: 850,
    paymentMode: "CASH",
    note: "Chalk & register",
    billPhotoBase64: "",
    status: "ACTIVE",
    approvedByAdminUid: "",
    approvedByAdminName: "",
    approvalNote: "",
    createdByUid: uid,
    createdByName: "Cash Clerk",
    createdAtMs: Date.now() - 500,
    updatedByUid: uid,
    updatedByName: "Cash Clerk",
    lastEditReason: "",
    clientEditedAtMs: Date.now() - 500,
    cancelledAtMs: 0,
    cancelledByUid: "",
    cancelledByName: "",
    cancelReason: "",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  });
  await assertSucceeds(cbEdOrg.collection("cashBookEntries").doc("cbe_small_1").set(makeCbEntry(CB_EDITOR)));

  // Large expense > 5000 by non-admin WITHOUT real admin approval FAILS; forged self-approval FAILS; with OWNER approval SUCCEEDS
  await assertFails(cbEdOrg.collection("cashBookEntries").doc("cbe_large_no_appr").set(makeCbEntry(CB_EDITOR, { amount: 6500 })));
  await assertFails(cbEdOrg.collection("cashBookEntries").doc("cbe_large_fake_appr").set(
    makeCbEntry(CB_EDITOR, { amount: 6500, approvedByAdminUid: CB_EDITOR, approvedByAdminName: "Self" })
  ));
  await assertSucceeds(cbEdOrg.collection("cashBookEntries").doc("cbe_large_with_admin").set(
    makeCbEntry(CB_EDITOR, { amount: 6500, approvedByAdminUid: OWNER_UID, approvedByAdminName: "Principal", approvalNote: "Approved" })
  ));

  // Edit requires lastEditReason; Cancel requires cancelReason; Delete is ALWAYS denied
  await assertFails(cbEdOrg.collection("cashBookEntries").doc("cbe_small_1").update({
    amount: 900,
    lastEditReason: "",
    updatedByUid: CB_EDITOR,
    updatedByName: "Cash Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  await assertSucceeds(cbEdOrg.collection("cashBookEntries").doc("cbe_small_1").update({
    amount: 900,
    lastEditReason: "Corrected bill total",
    updatedByUid: CB_EDITOR,
    updatedByName: "Cash Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  await assertSucceeds(cbEdOrg.collection("cashBookEntries").doc("cbe_small_1").update({
    status: "CANCELLED",
    cancelledAtMs: Date.now(),
    cancelledByUid: CB_EDITOR,
    cancelledByName: "Cash Clerk",
    cancelReason: "Duplicate entry",
    updatedByUid: CB_EDITOR,
    updatedByName: "Cash Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  await assertFails(ownerOrg.collection("cashBookEntries").doc("cbe_small_1").delete());

  // Day Closing & Append-only Cash Book Audit Log
  await assertSucceeds(cbEdOrg.collection("cashBookDayClosings").doc(`cbcls_${SCHOOL_1}_2026_11_24`).set({
    schoolId: SCHOOL_1,
    dateIso: "2026-11-24",
    openingCashBalance: 1000,
    cashInTotal: 1200,
    cashOutTotal: 6500,
    expectedClosingCash: -4300,
    countedCash: -4300,
    cashDifference: 0,
    nonCashInTotal: 0,
    nonCashOutTotal: 0,
    note: "Matched",
    closedByUid: CB_EDITOR,
    closedByName: "Cash Clerk",
    closedAtMs: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertSucceeds(cbEdOrg.collection("cashBookAuditLogs").doc("cbal_01").set({
    schoolId: SCHOOL_1,
    action: "EXPENSE_ADDED",
    targetId: "cbe_small_1",
    dateIso: "2026-11-24",
    amount: 850,
    reason: "Chalk",
    summary: "Added expense Rs 850",
    actorUid: CB_EDITOR,
    actorName: "Cash Clerk",
    clientCreatedAtMs: Date.now(),
    createdAt: ts,
  }));
  await assertFails(ownerOrg.collection("cashBookAuditLogs").doc("cbal_01").update({ summary: "Tampered" }));
  await assertFails(ownerOrg.collection("cashBookAuditLogs").doc("cbal_01").delete());
  await assertFails(cbVwOrg.collection("cashBookEntries").doc("cbe_vw_try").set(makeCbEntry(CB_VIEWER)));

  // =========================================================================
  // PART 3: ADMISSION ENQUIRY MODULE
  // =========================================================================
  const enqPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    childName: "Kavya Singh",
    classWanted: "1st",
    guardianName: "Amit Singh",
    mobile: "9839100099",
    source: "Walk-in",
    notes: "Interested in transport",
    enquiryDateIso: "2026-11-24",
    nextFollowUpDateIso: "2026-11-25",
    status: "NEW",
    lostReason: "",
    convertedStudentDocId: "",
    convertedStudentAdmNo: "",
    convertedAtMs: 0,
    createdByUid: ENQ_EDITOR,
    createdByName: "Enquiry Clerk",
    createdAtMs: Date.now() - 500,
    updatedByUid: ENQ_EDITOR,
    updatedByName: "Enquiry Clerk",
    clientEditedAtMs: Date.now() - 500,
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(enqEdOrg.collection("admissionEnquiries").doc("enq_01").set(enqPayload));
  // Marking LOST without lostReason fails; with lostReason succeeds
  await assertFails(enqEdOrg.collection("admissionEnquiries").doc("enq_01").update({
    status: "LOST",
    lostReason: "",
    updatedByUid: ENQ_EDITOR,
    updatedByName: "Enquiry Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  // Enquiry Editor can create a Student Register doc during Convert to Admission
  await assertSucceeds(enqEdOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_from_enq_01").set({
    schoolId: SCHOOL_1,
    classId: "cls_1st",
    session: "2026-2027",
    admNo: "ADM-ENQ-01",
    name: "Kavya Singh",
    fatherName: "Amit Singh",
    motherName: "",
    dob: "",
    mobile: "9839100099",
    secondMobile: "",
    address: "",
    photoBase64: "",
    className: "1st",
    section: "A",
    rollNo: 1,
    admissionDate: "2026-11-24",
    previousSchool: "",
    siblingGroupId: "",
    usesTransport: false,
    transportRouteName: "",
    transportMonthlyAmount: 0,
    status: "ACTIVE",
    leftDate: "",
    leftReason: "",
    sample: false,
    createdAt: ts,
    updatedAt: ts,
  }));
  // Enquiry Viewer can read, CANNOT create or edit
  await assertSucceeds(enqVwOrg.collection("admissionEnquiries").doc("enq_01").get());
  await assertFails(enqVwOrg.collection("admissionEnquiries").doc("enq_vw_try").set({ ...enqPayload, createdByUid: ENQ_VIEWER, updatedByUid: ENQ_VIEWER }));

  // =========================================================================
  // PART 4: MISSING KEY = NO ACCESS, MODULE ISOLATION & CROSS-ORG ISOLATION
  // =========================================================================
  for (const deniedOrg of [legacyOrg, outsiderOrg]) {
    await assertFails(deniedOrg.collection("schoolCertificates").get());
    await assertFails(deniedOrg.collection("cashBookSettings").get());
    await assertFails(deniedOrg.collection("cashBookEntries").get());
    await assertFails(deniedOrg.collection("cashBookDayClosings").get());
    await assertFails(deniedOrg.collection("cashBookAuditLogs").get());
    await assertFails(deniedOrg.collection("enquirySettings").get());
    await assertFails(deniedOrg.collection("admissionEnquiries").get());
  }
  // Cross-module isolation: Cert editor cannot read Cash Book or Enquiry; Cash editor cannot read Certs or Enquiry; Enquiry editor cannot read Certs or Cash Book
  await assertFails(certEdOrg.collection("cashBookEntries").get());
  await assertFails(certEdOrg.collection("admissionEnquiries").get());
  await assertFails(cbEdOrg.collection("schoolCertificates").get());
  await assertFails(cbEdOrg.collection("admissionEnquiries").get());
  await assertFails(enqEdOrg.collection("schoolCertificates").get());
  await assertFails(enqEdOrg.collection("cashBookEntries").get());
});

// =============================================================================
// 18. PHASE 11: EXAMS ('exams'), STOCK ('stock'), TRANSPORT ('transport')
//     - Missing key = no access, none / view / edit, Owner/Admin full access
//     - Linked teacher self-only read on examInvigilatorDuties
//     - Stock editor can update ONLY optionalCatalogue on feeStructures (tuition blocked)
//     - Stock transactions append-only; ADJUSTMENT requires non-empty reason
//     - Transport editor can update ONLY transport fields on students (name/status blocked)
//     - Cross-module & cross-org isolation
// =============================================================================
test("Phase 11: Exams, Stock & Transport RBAC, teacher self-duty read, stock catalogue-only update, transport-only student update & isolation", async () => {
  const EXAMS_ED = "uid_p11_exams_ed";
  const EXAMS_VW = "uid_p11_exams_vw";
  const STOCK_ED = "uid_p11_stock_ed";
  const STOCK_VW = "uid_p11_stock_vw";
  const TRANS_ED = "uid_p11_trans_ed";
  const TRANS_VW = "uid_p11_trans_vw";
  const TEACHER_A = "uid_p11_tchr_a";
  const TEACHER_B = "uid_p11_tchr_b";
  const LEGACY_NO_KEYS = "uid_p11_legacy";
  const OUTSIDER = "uid_p11_outsider";

  await seedOrgWithOwnerAndSchools();
  const ts = pastDate();

  const baseNoAccess = {
    students: "none",
    marks: "none",
    coScholastic: "none",
    reports: "none",
    timetable: "none",
    teachers: "none",
    settings: "none",
    members: "none",
    attendance: "none",
    fees: "none",
    studentRegister: "none",
    certificates: "none",
    cashBook: "none",
    enquiry: "none",
    exams: "none",
    stock: "none",
    transport: "none",
  };

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const orgRef = ctx.firestore().collection("organisations").doc(ORG_ID);
    const seedMember = async (uid, role, perms, schools = [SCHOOL_1]) => {
      await orgRef.collection("members").doc(uid).set({
        uid,
        name: uid,
        email: `${uid}@org1.com`,
        mobile: "9999900000",
        role,
        status: "active",
        allSchools: false,
        schools,
        permissions: perms,
        teacherProfileCompleted: true,
        teacherPhotoBase64: "",
        requestedSubjects: [],
        requestedClasses: [],
        assignedClassSubjects: [],
        lastActiveAt: ts,
        createdAt: ts,
        updatedAt: ts,
      });
    };

    await seedMember(EXAMS_ED, "OFFICE_OPERATOR", { ...baseNoAccess, exams: "edit" });
    await seedMember(EXAMS_VW, "OTHER", { ...baseNoAccess, exams: "view" });
    await seedMember(STOCK_ED, "OFFICE_OPERATOR", { ...baseNoAccess, stock: "edit" });
    await seedMember(STOCK_VW, "OTHER", { ...baseNoAccess, stock: "view" });
    await seedMember(TRANS_ED, "OFFICE_OPERATOR", { ...baseNoAccess, transport: "edit" });
    await seedMember(TRANS_VW, "OTHER", { ...baseNoAccess, transport: "view" });
    await seedMember(TEACHER_A, "TEACHER", { ...baseNoAccess, marks: "edit" });
    await seedMember(TEACHER_B, "TEACHER", { ...baseNoAccess, marks: "edit" });

    // Legacy 8-key member (missing exams/stock/transport keys -> must be NONE)
    await seedMember(LEGACY_NO_KEYS, "OTHER", {
      students: "view",
      marks: "none",
      coScholastic: "none",
      reports: "none",
      timetable: "none",
      teachers: "none",
      settings: "none",
      members: "none",
    });

    // Seed a student in SCHOOL_1 and a feeStructure for SCHOOL_1
    await orgRef.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p11_01").set({
      schoolId: SCHOOL_1,
      classId: "cls_5th",
      session: "2026-2027",
      admNo: "ADM-P11-01",
      name: "Aarav Kumar",
      fatherName: "Rajesh Kumar",
      motherName: "Sunita Devi",
      dob: "2015-04-10",
      mobile: "9839001122",
      secondMobile: "",
      address: "Main Road",
      photoBase64: "",
      className: "5th",
      section: "A",
      rollNo: 1,
      admissionDate: "2024-04-01",
      previousSchool: "",
      siblingGroupId: "",
      usesTransport: true,
      transportRouteName: "Old Route",
      transportMonthlyAmount: 750,
      status: "ACTIVE",
      leftDate: "",
      leftReason: "",
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });

    await orgRef.collection("feeStructures").doc(`feestruct_${SCHOOL_1}_2026_2027`).set({
      schoolId: SCHOOL_1,
      session: "2026-2027",
      monthOrder: ["APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
      chargedTuitionMonths: ["APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
      chargedTransportMonths: ["APR", "MAY", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR"],
      tuitionByClassAndMonth: { "5th": { APR: 1200 } },
      examFees: [],
      optionalCatalogue: [
        { id: "opt_tie", name: "Tie", price: 120, hidden: false, unit: "pcs", openingStock: 20, lowStockLevel: 5 },
      ],
      frozenPastCellAmountsByClass: {},
      maxConcessionPercentWithoutAdminApproval: 10,
      messageTemplates: {
        receiptTemplate: "Receipt",
        reminderTemplate: "Reminder",
        noticeTemplate: "Notice",
      },
      updatedByUid: OWNER_UID,
      updatedByName: "Owner",
      clientEditedAtMs: Date.now() - 5000,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const authedOrg = (uid) => testEnv.authenticatedContext(uid).firestore().collection("organisations").doc(ORG_ID);
  const examsEdOrg = authedOrg(EXAMS_ED);
  const examsVwOrg = authedOrg(EXAMS_VW);
  const stockEdOrg = authedOrg(STOCK_ED);
  const stockVwOrg = authedOrg(STOCK_VW);
  const transEdOrg = authedOrg(TRANS_ED);
  const transVwOrg = authedOrg(TRANS_VW);
  const teacherAOrg = authedOrg(TEACHER_A);
  const teacherBOrg = authedOrg(TEACHER_B);
  const legacyOrg = authedOrg(LEGACY_NO_KEYS);
  const outsiderOrg = authedOrg(OUTSIDER);

  // =========================================================================
  // PART 1: EXAMS MODULE + TEACHER SELF-DUTY ISOLATION
  // =========================================================================
  const examPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    examType: "HALF_YEARLY",
    title: "Half-Yearly 2026",
    entries: [
      { entryId: "p1", className: "5th", subjectName: "Maths", dateIso: "2026-10-12", startTime: "09:00", endTime: "12:00", maxMarks: 100 },
    ],
    withholdAdmitCardAboveDuesRs: 1000,
    admitCardOverrideStudentIds: [],
    admitCardInstructions: "Bring admit card.",
    createdByUid: EXAMS_ED,
    updatedByUid: EXAMS_ED,
    updatedByName: "Exams Coord",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  };
  await assertSucceeds(examsEdOrg.collection("examSchedules").doc("exam_hy_01").set(examPayload));
  // Exams editor cannot write to SCHOOL_2 (out of school scope)
  await assertFails(examsEdOrg.collection("examSchedules").doc("exam_sch2").set({ ...examPayload, schoolId: SCHOOL_2 }));
  // Exams viewer can read, cannot create
  await assertSucceeds(examsVwOrg.collection("examSchedules").doc("exam_hy_01").get());
  await assertFails(examsVwOrg.collection("examSchedules").doc("exam_vw_try").set({ ...examPayload, createdByUid: EXAMS_VW, updatedByUid: EXAMS_VW }));

  // Exam Room & Seating
  await assertSucceeds(examsEdOrg.collection("examRooms").doc("eroom_101").set({
    schoolId: SCHOOL_1,
    roomName: "Room 101",
    capacity: 30,
    active: true,
    updatedByUid: EXAMS_ED,
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  await assertSucceeds(examsEdOrg.collection("examSeatings").doc("seat_exam_hy_01").set({
    schoolId: SCHOOL_1,
    session: "2026-2027",
    examId: "exam_hy_01",
    allocationMode: "MIXED_BY_CLASS",
    includedClasses: ["5th"],
    roomIds: ["eroom_101"],
    allocations: [],
    unallocatedStudentIds: [],
    updatedByUid: EXAMS_ED,
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));

  // Invigilator Duty for TEACHER_A
  const dutyPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    examId: "exam_hy_01",
    examTitle: "Half-Yearly 2026",
    dateIso: "2026-10-12",
    startTime: "09:00",
    endTime: "12:00",
    roomId: "eroom_101",
    roomName: "Room 101",
    teacherId: "tchr_a",
    teacherUid: TEACHER_A,
    teacherName: "Mrs. A",
    assignedManually: false,
    updatedByUid: EXAMS_ED,
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  };
  await assertSucceeds(examsEdOrg.collection("examInvigilatorDuties").doc("duty_01").set(dutyPayload));
  // TEACHER_A (without exams module) CAN read own duty via self query, CANNOT read all duties or examSchedules
  await assertSucceeds(teacherAOrg.collection("examInvigilatorDuties").where("teacherUid", "==", TEACHER_A).get());
  await assertFails(teacherAOrg.collection("examInvigilatorDuties").get());
  await assertFails(teacherAOrg.collection("examSchedules").get());
  // TEACHER_B CANNOT read TEACHER_A's duty
  await assertFails(teacherBOrg.collection("examInvigilatorDuties").doc("duty_01").get());

  // =========================================================================
  // PART 2: STOCK MODULE (CATALOGUE-ONLY UPDATE + APPEND-ONLY TRANSACTIONS)
  // =========================================================================
  const feeStructRefStockEd = stockEdOrg.collection("feeStructures").doc(`feestruct_${SCHOOL_1}_2026_2027`);
  // Stock editor CAN update ONLY optionalCatalogue + audit fields
  await assertSucceeds(feeStructRefStockEd.update({
    optionalCatalogue: [
      { id: "opt_tie", name: "Tie", price: 130, hidden: false, unit: "pcs", openingStock: 25, lowStockLevel: 5 },
    ],
    updatedByUid: STOCK_ED,
    updatedByName: "Store Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));
  // Stock editor CANNOT tamper with tuitionByClassAndMonth!
  await assertFails(feeStructRefStockEd.update({
    tuitionByClassAndMonth: { "5th": { APR: 9999 } },
    updatedByUid: STOCK_ED,
    updatedByName: "Store Clerk",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  }));

  // Stock In transaction succeeds; Adjustment without reason FAILS; Adjustment with reason SUCCEEDS; update/delete FAILS
  const stockInPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    itemId: "opt_tie",
    itemName: "Tie",
    txType: "STOCK_IN",
    dateIso: "2026-10-10",
    quantityDelta: 50,
    unitCostRs: 80,
    totalCostRs: 4000,
    supplier: "Uniform Depot",
    reason: "",
    createdByUid: STOCK_ED,
    createdByName: "Store Clerk",
    createdAtMs: Date.now(),
    createdAt: ts,
  };
  await assertSucceeds(stockEdOrg.collection("stockTransactions").doc("stk_in_01").set(stockInPayload));
  await assertFails(stockEdOrg.collection("stockTransactions").doc("stk_adj_bad").set({
    ...stockInPayload,
    txType: "ADJUSTMENT",
    quantityDelta: -2,
    reason: "",
  }));
  await assertSucceeds(stockEdOrg.collection("stockTransactions").doc("stk_adj_ok").set({
    ...stockInPayload,
    txType: "ADJUSTMENT",
    quantityDelta: -2,
    reason: "2 ties damaged in rain",
  }));
  await assertFails(stockEdOrg.collection("stockTransactions").doc("stk_in_01").update({ quantityDelta: 100 }));
  await assertFails(stockEdOrg.collection("stockTransactions").doc("stk_in_01").delete());
  // Stock viewer can read, cannot create
  await assertSucceeds(stockVwOrg.collection("stockTransactions").doc("stk_in_01").get());
  await assertFails(stockVwOrg.collection("stockTransactions").doc("stk_vw_try").set({ ...stockInPayload, createdByUid: STOCK_VW }));

  // =========================================================================
  // PART 3: TRANSPORT ROUTES + LEAST-PRIVILEGE STUDENT TRANSPORT UPDATE
  // =========================================================================
  const routePayload = {
    schoolId: SCHOOL_1,
    routeName: "Route 1 - Station Road",
    vehicleNumber: "UP32-AB-1234",
    vehicleType: "Bus",
    seatCapacity: 32,
    driverName: "Ramesh Yadav",
    driverPhone: "9839009988",
    monthlyFeeRs: 800,
    stops: [{ stopId: "st_1", stopName: "Station Chowk", pickupTime: "07:30", monthlyFeeRs: 800 }],
    active: true,
    updatedByUid: TRANS_ED,
    updatedByName: "Transport In-Charge",
    clientEditedAtMs: Date.now(),
    updatedAt: ts,
  };
  await assertSucceeds(transEdOrg.collection("transportRoutes").doc("troute_01").set(routePayload));
  await assertSucceeds(transVwOrg.collection("transportRoutes").doc("troute_01").get());
  await assertFails(transVwOrg.collection("transportRoutes").doc("troute_vw").set({ ...routePayload, updatedByUid: TRANS_VW }));

  // Transport editor CAN update ONLY transport fields on a student
  const stuRefTransEd = transEdOrg.collection("schools").doc(SCHOOL_1).collection("students").doc("stu_p11_01");
  await assertSucceeds(stuRefTransEd.update({
    usesTransport: true,
    transportRouteId: "troute_01",
    transportRouteName: "Route 1 - Station Road - Station Chowk",
    transportStopName: "Station Chowk",
    transportMonthlyAmount: 750,
    updatedAt: ts,
  }));
  // Transport editor CANNOT modify student's name or status!
  await assertFails(stuRefTransEd.update({
    name: "Hacked Name",
    updatedAt: ts,
  }));

  // =========================================================================
  // PART 4: MISSING KEY = NO ACCESS, CROSS-MODULE & CROSS-ORG ISOLATION
  // =========================================================================
  for (const deniedOrg of [legacyOrg, outsiderOrg]) {
    await assertFails(deniedOrg.collection("examSchedules").get());
    await assertFails(deniedOrg.collection("examRooms").get());
    await assertFails(deniedOrg.collection("examSeatings").get());
    await assertFails(deniedOrg.collection("stockTransactions").get());
    await assertFails(deniedOrg.collection("transportRoutes").get());
  }
  await assertFails(examsEdOrg.collection("stockTransactions").get());
  await assertFails(examsEdOrg.collection("transportRoutes").get());
  await assertFails(stockEdOrg.collection("examSchedules").get());
  await assertFails(stockEdOrg.collection("transportRoutes").get());
  await assertFails(transEdOrg.collection("examSchedules").get());
  await assertFails(transEdOrg.collection("stockTransactions").get());
});









