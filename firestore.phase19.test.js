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
const CLASS_TEACHER_10A_UID = "ct_10a_uid";
const OTHER_TEACHER_9B_UID = "ct_9b_uid";
const GRANTED_REMARKS_VIEW_UID = "granted_remarks_view_uid";
const GATE_OPERATOR_UID = "gate_operator_uid";

const ORG_ID = "org_demo_001";
const SCHOOL_1 = "sch_primary_01";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
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
    studentAttendance: level,
    studentRegister: level,
    fees: level,
    certificates: level,
    cashBook: level,
    enquiry: level,
    exams: level,
    stock: level,
    transport: level,
    salary: level,
    sessionPromotion: level,
    resultAnalysis: level === "edit" ? "view" : level,
    classDiary: level,
    studentRemarks: level,
    gate: level,
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
    permissions: fullPermissions("none"),
    assignedClassSubjects: [],
    joinedAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules: fs.readFileSync("firestore.rules", "utf8"),
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
});

test("24. Phase 19: Sensitive Student Remarks (Class Teacher/Owner/Admin only, cancel-with-reason, no silent delete), PTM, Authorised Pickups, Gate Pass & Visitor Register RBAC", async () => {
  const ts = pastDate(10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`organisations/${ORG_ID}`).set({
      name: "Demo School Group",
      code: "demo-schools",
      ownerUid: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.doc(`organisations/${ORG_ID}/members/${OWNER_UID}`).set(
      makeMemberPayload(OWNER_UID, {
        role: "OWNER",
        allSchools: true,
        permissions: fullPermissions("edit"),
      })
    );
    // Class Teacher of 10th-A (studentRemarks permission is "none", gets access via classTeacherSections)
    await db.doc(`organisations/${ORG_ID}/members/${CLASS_TEACHER_10A_UID}`).set(
      makeMemberPayload(CLASS_TEACHER_10A_UID, {
        name: "Ramesh Sir (10-A CT)",
        role: "TEACHER",
        schools: [SCHOOL_1],
        permissions: {
          ...fullPermissions("none"),
          students: "view",
          studentRemarks: "none",
        },
      })
    );
    await db.doc(`organisations/${ORG_ID}/teacherScopes/${CLASS_TEACHER_10A_UID}`).set({
      uid: CLASS_TEACHER_10A_UID,
      teacherId: "t_10a",
      schoolIds: [SCHOOL_1],
      assignedClassSectionSubjects: ["sch_primary_01|10th|A|Maths"],
      classTeacherSections: ["sch_primary_01|10th|A"],
      updatedAt: ts,
    });

    // Other Teacher (Class Teacher of 9th-B, not 10th-A, no studentRemarks permission)
    await db.doc(`organisations/${ORG_ID}/members/${OTHER_TEACHER_9B_UID}`).set(
      makeMemberPayload(OTHER_TEACHER_9B_UID, {
        name: "Suresh Sir (9-B CT)",
        role: "TEACHER",
        schools: [SCHOOL_1],
        permissions: {
          ...fullPermissions("none"),
          students: "view",
          studentRemarks: "none",
        },
      })
    );
    await db.doc(`organisations/${ORG_ID}/teacherScopes/${OTHER_TEACHER_9B_UID}`).set({
      uid: OTHER_TEACHER_9B_UID,
      teacherId: "t_9b",
      schoolIds: [SCHOOL_1],
      assignedClassSectionSubjects: ["sch_primary_01|9th|B|Hindi"],
      classTeacherSections: ["sch_primary_01|9th|B"],
      updatedAt: ts,
    });

    // Teacher explicitly granted studentRemarks: "view"
    await db.doc(`organisations/${ORG_ID}/members/${GRANTED_REMARKS_VIEW_UID}`).set(
      makeMemberPayload(GRANTED_REMARKS_VIEW_UID, {
        name: "Counsellor Teacher",
        role: "TEACHER",
        schools: [SCHOOL_1],
        permissions: {
          ...fullPermissions("none"),
          students: "view",
          studentRemarks: "view",
        },
      })
    );

    // Gate & Student Register Operator
    await db.doc(`organisations/${ORG_ID}/members/${GATE_OPERATOR_UID}`).set(
      makeMemberPayload(GATE_OPERATOR_UID, {
        name: "Gate & Front Desk",
        role: "OFFICE_OPERATOR",
        schools: [SCHOOL_1],
        permissions: {
          ...fullPermissions("none"),
          students: "view",
          studentRegister: "edit",
          gate: "edit",
        },
      })
    );
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const ct10aDb = testEnv.authenticatedContext(CLASS_TEACHER_10A_UID).firestore();
  const other9bDb = testEnv.authenticatedContext(OTHER_TEACHER_9B_UID).firestore();
  const grantedViewDb = testEnv.authenticatedContext(GRANTED_REMARKS_VIEW_UID).firestore();
  const gateDb = testEnv.authenticatedContext(GATE_OPERATOR_UID).firestore();

  const remarkRefId = "rem_stu101_001";
  const remarkPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    studentId: "stu_101",
    studentAdmNo: "ADM101",
    studentName: "Aarav Kumar",
    className: "10th",
    section: "A",
    rollNo: 1,
    dateIso: "2026-08-10",
    category: "Academic",
    note: "Needs extra practice in Quadratic Equations.",
    followUpDateIso: "2026-08-17",
    followUpCompleted: false,
    followUpCompletedNote: "",
    status: "ACTIVE",
    cancelledReason: "",
    cancelledByUid: "",
    cancelledByName: "",
    cancelledAtMs: 0,
    authorUid: CLASS_TEACHER_10A_UID,
    authorName: "Ramesh Sir (10-A CT)",
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };

  // 1. Unrelated teacher (9th-B CT) CANNOT create a remark for a 10th-A student
  await assertFails(
    other9bDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).set({
      ...remarkPayload,
      authorUid: OTHER_TEACHER_9B_UID,
    })
  );

  // 2. Class Teacher of 10th-A CAN create a remark for their 10th-A student
  await assertSucceeds(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).set(remarkPayload)
  );

  // 3. Class Teacher of 10th-A, Owner, and explicitly granted viewer CAN read the 10th-A remark
  await assertSucceeds(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );
  await assertSucceeds(
    ownerDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );
  await assertSucceeds(
    grantedViewDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );

  // 4. Other teacher (9th-B CT without studentRemarks grant) and Gate operator CANNOT read the 10th-A student's remark
  await assertFails(
    other9bDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );
  await assertFails(
    gateDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );

  // 5. Silent deletion is strictly FORBIDDEN (even for Class Teacher and Owner!)
  await assertFails(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).delete()
  );
  await assertFails(
    ownerDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).delete()
  );

  // 6. Cancelling without a reason fails; cancelling with a valid reason succeeds and preserves note/category/author
  await assertFails(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).update({
      status: "CANCELLED",
      cancelledReason: "",
      cancelledByUid: CLASS_TEACHER_10A_UID,
      cancelledByName: "Ramesh Sir (10-A CT)",
      cancelledAtMs: Date.now(),
      updatedAtMs: Date.now(),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).update({
      status: "CANCELLED",
      cancelledReason: "Entered on wrong roll number; re-entered on Roll 2",
      cancelledByUid: CLASS_TEACHER_10A_UID,
      cancelledByName: "Ramesh Sir (10-A CT)",
      cancelledAtMs: Date.now(),
      updatedAtMs: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // 6b. Owner can configure school remark categories & PDF privacy setting; teacher cannot
  const catConfigPayload = {
    schoolId: SCHOOL_1,
    categories: ["Praise", "Academic", "Discipline", "Attendance", "Health", "Other", "Sports"],
    hideSummaryPdfFromNonAdmins: true,
    updatedByUid: OWNER_UID,
    updatedByName: "Owner",
    updatedAtMs: Date.now(),
    updatedAt: pastDate(1000),
  };
  await assertFails(
    ct10aDb.doc(`organisations/${ORG_ID}/studentRemarkCategories/${SCHOOL_1}`).set({
      ...catConfigPayload,
      updatedByUid: CLASS_TEACHER_10A_UID,
    })
  );
  await assertSucceeds(
    ownerDb.doc(`organisations/${ORG_ID}/studentRemarkCategories/${SCHOOL_1}`).set(catConfigPayload)
  );

  // 7. Parent Meetings (PTM): Class teacher of 10th-A can create PTM for 10th-A; 9th-B teacher cannot
  const ptmId = `ptm_${SCHOOL_1}_2026-2027_2026-08-20_10TH_A`;
  const ptmPayload = {
    schoolId: SCHOOL_1,
    session: "2026-2027",
    title: "Class 10-A PTM",
    dateIso: "2026-08-20",
    timeLabel: "10:00 AM - 01:00 PM",
    className: "10th",
    section: "A",
    agenda: "Half-yearly preparation",
    entries: [
      {
        studentId: "stu_101",
        studentAdmNo: "ADM101",
        studentName: "Aarav Kumar",
        className: "10th",
        section: "A",
        rollNo: 1,
        guardianName: "Rajesh Kumar",
        mobile: "9876543210",
        suggestionReason: "Weak Result",
        status: "CALLED",
        discussionNote: "",
        decision: "",
        updatedByUid: "",
        updatedByName: "",
        updatedAtMs: 0,
      },
    ],
    createdByUid: CLASS_TEACHER_10A_UID,
    createdByName: "Ramesh Sir (10-A CT)",
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
  await assertFails(
    other9bDb.doc(`organisations/${ORG_ID}/parentMeetings/${ptmId}`).set({
      ...ptmPayload,
      createdByUid: OTHER_TEACHER_9B_UID,
    })
  );
  await assertSucceeds(
    ct10aDb.doc(`organisations/${ORG_ID}/parentMeetings/${ptmId}`).set(ptmPayload)
  );

  // 8. Authorised Pickup Persons: stored in Student Register, readable by Gate operator
  const pickupId = "pickup_stu101_father";
  await assertSucceeds(
    gateDb.doc(`organisations/${ORG_ID}/studentAuthorizedPickups/${pickupId}`).set({
      schoolId: SCHOOL_1,
      studentId: "stu_101",
      studentAdmNo: "ADM101",
      studentName: "Aarav Kumar",
      className: "10th",
      section: "A",
      personName: "Rajesh Kumar",
      relation: "Father",
      mobile: "9876543210",
      photoBase64: "",
      updatedByUid: GATE_OPERATOR_UID,
      updatedByName: "Gate & Front Desk",
      updatedAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 9. Early Leave Gate Pass: One-time pickup without guardianPhoneConfirmed fails; with guardianPhoneConfirmed succeeds
  const passId = "gp_2026_0810_a1b2_001";
  const basePassPayload = {
    passNumber: "GP-2627-0810-A1B2-001",
    schoolId: SCHOOL_1,
    session: "2026-2027",
    dateIso: "2026-08-10",
    leaveTime: "12:15 PM",
    studentId: "stu_101",
    studentAdmNo: "ADM101",
    studentName: "Aarav Kumar",
    className: "10th",
    section: "A",
    rollNo: 1,
    reason: "Dental appointment",
    pickupPersonId: "",
    pickupPersonName: "Manoj Kumar",
    pickupRelation: "Uncle",
    pickupMobile: "9123456780",
    isOneTimePickup: true,
    oneTimeNote: "Aadhaar verified at gate",
    guardianPhoneConfirmed: false,
    approvedByUid: OWNER_UID,
    approvedByName: "Principal",
    markedHalfDayAttendance: false,
    attendanceNote: "",
    createdByUid: GATE_OPERATOR_UID,
    createdByName: "Gate & Front Desk",
    createdAtMs: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
  await assertFails(
    gateDb.doc(`organisations/${ORG_ID}/gatePasses/${passId}`).set(basePassPayload)
  );
  await assertSucceeds(
    gateDb.doc(`organisations/${ORG_ID}/gatePasses/${passId}`).set({
      ...basePassPayload,
      guardianPhoneConfirmed: true,
    })
  );

  // Gate operator CANNOT read remarks
  await assertFails(
    gateDb.doc(`organisations/${ORG_ID}/studentRemarks/${remarkRefId}`).get()
  );

  // 10. Visitor Register: Gate operator can check in visitor (IN) and sign out visitor (OUT)
  const visitorId = "vis_2026_0810_001";
  const visitorPayload = {
    schoolId: SCHOOL_1,
    dateIso: "2026-08-10",
    visitorName: "Vikram Verma",
    mobile: "9988776655",
    purpose: "Book supplier meeting",
    whomToMeet: "Principal",
    timeIn: "11:00 AM",
    timeOut: "",
    status: "IN",
    photoBase64: "",
    recordedByUid: GATE_OPERATOR_UID,
    recordedByName: "Gate & Front Desk",
    signedOutByUid: "",
    signedOutByName: "",
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
  await assertSucceeds(
    gateDb.doc(`organisations/${ORG_ID}/visitorEntries/${visitorId}`).set(visitorPayload)
  );
  await assertSucceeds(
    gateDb.doc(`organisations/${ORG_ID}/visitorEntries/${visitorId}`).update({
      status: "OUT",
      timeOut: "11:45 AM",
      signedOutByUid: GATE_OPERATOR_UID,
      signedOutByName: "Gate & Front Desk",
      updatedAtMs: Date.now(),
      updatedAt: pastDate(1000),
    })
  );
});
