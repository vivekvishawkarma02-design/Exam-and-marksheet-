const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid";
const TRANSPORT_CLERK_UID = "transport_clerk_uid";
const TRANSPORT_VIEW_UID = "transport_view_uid";
const FEE_CLERK_UID = "fee_clerk_uid";
const REGISTER_CLERK_UID = "register_clerk_uid";
const CASHBOOK_CLERK_UID = "cashbook_clerk_uid";

const ORG_ID = "org_transport_test";
const SCHOOL_1 = "sch_01";
const SCHOOL_2 = "sch_02";

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
    name: uid,
    email: `${uid}@example.org`,
    mobile: "9000000000",
    role: "STAFF",
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
  const ts = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`organisations/${ORG_ID}`).set({
      name: "Transport Test School",
      code: "tts-01",
      ownerUid: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.doc(`organisations/${ORG_ID}/members/${OWNER_UID}`).set(
      makeMemberPayload(OWNER_UID, { role: "OWNER", allSchools: true, permissions: fullPermissions("edit") })
    );
    await db.doc(`organisations/${ORG_ID}/members/${TRANSPORT_CLERK_UID}`).set(
      makeMemberPayload(TRANSPORT_CLERK_UID, {
        permissions: { ...fullPermissions("none"), transport: "edit", students: "view" },
      })
    );
    await db.doc(`organisations/${ORG_ID}/members/${TRANSPORT_VIEW_UID}`).set(
      makeMemberPayload(TRANSPORT_VIEW_UID, {
        permissions: { ...fullPermissions("none"), transport: "view" },
      })
    );
    await db.doc(`organisations/${ORG_ID}/members/${FEE_CLERK_UID}`).set(
      makeMemberPayload(FEE_CLERK_UID, {
        permissions: { ...fullPermissions("none"), fees: "edit", students: "view" },
      })
    );
    await db.doc(`organisations/${ORG_ID}/members/${REGISTER_CLERK_UID}`).set(
      makeMemberPayload(REGISTER_CLERK_UID, {
        permissions: { ...fullPermissions("none"), studentRegister: "edit", students: "edit" },
      })
    );
    await db.doc(`organisations/${ORG_ID}/members/${CASHBOOK_CLERK_UID}`).set(
      makeMemberPayload(CASHBOOK_CLERK_UID, {
        permissions: { ...fullPermissions("none"), cashBook: "view" },
      })
    );
    // Legacy student doc (without new transport fields)
    await db.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_legacy`).set({
      schoolId: SCHOOL_1,
      classId: "cls_5",
      session: "2026-2027",
      admNo: "ADM001",
      name: "Aarav Kumar",
      fatherName: "Rajesh Kumar",
      motherName: "Sunita Devi",
      dob: "2015-04-10",
      mobile: "9876543210",
      address: "Main Road",
      photoBase64: "",
      className: "5",
      section: "A",
      rollNo: 1,
      usesTransport: true,
      transportRouteId: "route_1",
      transportRouteName: "Route 1",
      transportStopName: "Gate",
      transportMonthlyAmount: 800,
      sample: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });
});

test("1. Transport Routes, Vehicle Documents, Settings & Cross-Module Reads RBAC", async () => {
  const clerkDb = testEnv.authenticatedContext(TRANSPORT_CLERK_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(TRANSPORT_VIEW_UID).firestore();
  const feeDb = testEnv.authenticatedContext(FEE_CLERK_UID).firestore();
  const ts = pastDate(2000);

  // Transport clerk can create a route with trip fees, contractor rental, driver licence, conductor
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/transportRoutes/route_1`).set({
      schoolId: SCHOOL_1,
      routeName: "North Route",
      vehicleNumber: "UP65AB1234",
      vehicleType: "Bus",
      seatCapacity: 40,
      driverName: "Mohan Lal",
      driverPhone: "9811111111",
      monthlyFeeRs: 1000,
      pickupOnlyFeeRs: 650,
      dropOnlyFeeRs: 600,
      vehicleOwnership: "RENTED",
      ownerContractorName: "Verma Travels",
      ownerContractorPhone: "9822222222",
      monthlyRentRs: 18000,
      driverLicenceNo: "UP652020001234",
      driverLicenceExpiryIso: "2028-12-31",
      conductorName: "Sohan",
      conductorPhone: "9833333333",
      stops: [{ stopId: "s1", stopName: "Bus Stand", pickupTime: "07:30", monthlyFeeRs: 900, pickupOnlyFeeRs: 550, dropOnlyFeeRs: 500 }],
      active: true,
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      clientEditedAtMs: Date.now(),
      updatedAt: ts,
    })
  );

  // Fee clerk and transport viewer can read routes; viewer cannot write
  await assertSucceeds(feeDb.doc(`organisations/${ORG_ID}/transportRoutes/route_1`).get());
  await assertSucceeds(viewerDb.doc(`organisations/${ORG_ID}/transportRoutes/route_1`).get());
  await assertFails(viewerDb.doc(`organisations/${ORG_ID}/transportRoutes/route_1`).update({ routeName: "X" }));

  // Transport clerk cannot create route for unassigned SCHOOL_2
  await assertFails(
    clerkDb.doc(`organisations/${ORG_ID}/transportRoutes/route_s2`).set({
      schoolId: SCHOOL_2,
      routeName: "Other",
      vehicleNumber: "",
      vehicleType: "Bus",
      seatCapacity: 30,
      driverName: "",
      driverPhone: "",
      monthlyFeeRs: 500,
      stops: [],
      active: true,
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      clientEditedAtMs: Date.now(),
      updatedAt: ts,
    })
  );

  // Vehicle documents CRUD
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/transportVehicleDocuments/doc_ins_1`).set({
      schoolId: SCHOOL_1,
      routeId: "route_1",
      vehicleNumber: "UP65AB1234",
      docType: "INSURANCE",
      documentNumber: "INS-9988",
      expiryDateIso: "2026-12-15",
      photoBase64: "",
      note: "Comprehensive",
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      clientEditedAtMs: Date.now(),
      updatedAt: ts,
    })
  );
  await assertSucceeds(viewerDb.doc(`organisations/${ORG_ID}/transportVehicleDocuments/doc_ins_1`).get());
  await assertFails(feeDb.doc(`organisations/${ORG_ID}/transportVehicleDocuments/doc_ins_1`).get());

  // Transport settings CRUD
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/transportSettings/tset_sch_01`).set({
      schoolId: SCHOOL_1,
      expenseCategories: ["Diesel", "Repair", "Tyre", "Driver Pay"],
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      clientEditedAtMs: Date.now(),
      updatedAt: ts,
    })
  );
  await assertFails(
    viewerDb.doc(`organisations/${ORG_ID}/transportSettings/tset_sch_01`).set({
      schoolId: SCHOOL_1,
      expenseCategories: ["Diesel"],
      updatedByUid: TRANSPORT_VIEW_UID,
      updatedByName: "Viewer",
      clientEditedAtMs: Date.now(),
      updatedAt: ts,
    })
  );
});

test("2. Student Transport Extended Fields (Transport Clerk & Student Register RBAC + Legacy Safety)", async () => {
  const clerkDb = testEnv.authenticatedContext(TRANSPORT_CLERK_UID).firestore();
  const regDb = testEnv.authenticatedContext(REGISTER_CLERK_UID).firestore();
  const ts = pastDate(2000);

  // Transport clerk can patch extended transport fields ONLY on a legacy student doc
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_legacy`).update({
      usesTransport: true,
      transportRouteId: "route_1",
      transportRouteName: "North Route",
      transportStopId: "s1",
      transportStopName: "Bus Stand",
      transportTripType: "PICKUP_ONLY",
      transportMonthlyAmount: 650,
      transportStartMonth: "AUG",
      transportEndMonth: "",
      transportFeeOverridden: true,
      transportOverrideRemark: "Half route concession",
      transportPriorMonthlyAmount: 800,
      transportFeeEffectiveFromMonth: "AUG",
      updatedAt: ts,
    })
  );

  // Transport clerk CANNOT modify non-transport student fields (name/rollNo)
  await assertFails(
    clerkDb.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_legacy`).update({
      name: "Hacked Name",
      updatedAt: ts,
    })
  );

  // Student Register clerk can also update transport fields alongside register edit
  await assertSucceeds(
    regDb.doc(`organisations/${ORG_ID}/schools/${SCHOOL_1}/students/stu_legacy`).update({
      transportTripType: "DROP_ONLY",
      transportFeeOverridden: false,
      transportOverrideRemark: "",
      updatedAt: ts,
    })
  );
});

test("3. Transport Expense -> Cash Book Unified Single Record & Transport Due Sent Log", async () => {
  const clerkDb = testEnv.authenticatedContext(TRANSPORT_CLERK_UID).firestore();
  const cbViewDb = testEnv.authenticatedContext(CASHBOOK_CLERK_UID).firestore();
  const feeDb = testEnv.authenticatedContext(FEE_CLERK_UID).firestore();
  const ts = pastDate(2000);

  // Transport clerk (cashBook: none) CAN create a transport expense inside cashBookEntries
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/cashBookEntries/cbe_diesel_1`).set({
      schoolId: SCHOOL_1,
      dateIso: "2026-11-15",
      entryType: "EXPENSE",
      category: "Transport - Diesel",
      amount: 2500,
      paymentMode: "CASH",
      note: "40L Diesel",
      billPhotoBase64: "",
      status: "ACTIVE",
      approvedByAdminUid: "",
      approvedByAdminName: "",
      approvalNote: "",
      createdByUid: TRANSPORT_CLERK_UID,
      createdByName: "Clerk",
      createdAtMs: 1000,
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      lastEditReason: "",
      clientEditedAtMs: 1000,
      cancelledAtMs: 0,
      cancelledByUid: "",
      cancelledByName: "",
      cancelReason: "",
      sourceModule: "TRANSPORT",
      transportRouteId: "route_1",
      vehicleNumber: "UP65AB1234",
      transportCategory: "Diesel",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Transport clerk CAN query cashBookEntries filtered by sourceModule == 'TRANSPORT'
  await assertSucceeds(
    clerkDb.collection(`organisations/${ORG_ID}/cashBookEntries`).where("sourceModule", "==", "TRANSPORT").get()
  );

  // Cash Book viewer sees the exact same entry (single source of truth!)
  await assertSucceeds(cbViewDb.doc(`organisations/${ORG_ID}/cashBookEntries/cbe_diesel_1`).get());

  // Transport clerk CANNOT create a non-TRANSPORT Cash Book entry or OTHER_INCOME
  await assertFails(
    clerkDb.doc(`organisations/${ORG_ID}/cashBookEntries/cbe_bad_1`).set({
      schoolId: SCHOOL_1,
      dateIso: "2026-11-15",
      entryType: "OTHER_INCOME",
      category: "Donation",
      amount: 500,
      paymentMode: "CASH",
      note: "",
      billPhotoBase64: "",
      status: "ACTIVE",
      approvedByAdminUid: "",
      approvedByAdminName: "",
      approvalNote: "",
      createdByUid: TRANSPORT_CLERK_UID,
      createdByName: "Clerk",
      createdAtMs: 1000,
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      lastEditReason: "",
      clientEditedAtMs: 1000,
      cancelledAtMs: 0,
      cancelledByUid: "",
      cancelledByName: "",
      cancelReason: "",
      sourceModule: "TRANSPORT",
      transportRouteId: "",
      vehicleNumber: "",
      transportCategory: "Diesel",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Transport clerk CAN edit with reason, cancel with reason, and append audit log, but NEVER delete
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/cashBookEntries/cbe_diesel_1`).update({
      amount: 2600,
      lastEditReason: "Corrected bill amount",
      updatedByUid: TRANSPORT_CLERK_UID,
      updatedByName: "Clerk",
      clientEditedAtMs: 2000,
      updatedAt: ts,
    })
  );
  await assertFails(clerkDb.doc(`organisations/${ORG_ID}/cashBookEntries/cbe_diesel_1`).delete());
  await assertSucceeds(
    clerkDb.doc(`organisations/${ORG_ID}/cashBookAuditLogs/cbal_1`).set({
      schoolId: SCHOOL_1,
      action: "ENTRY_EDITED",
      targetId: "cbe_diesel_1",
      dateIso: "2026-11-15",
      amount: 2600,
      reason: "Corrected bill amount",
      summary: "Edited Diesel expense",
      actorUid: TRANSPORT_CLERK_UID,
      actorName: "Clerk",
      clientCreatedAtMs: 2000,
      sourceModule: "TRANSPORT",
      createdAt: ts,
    })
  );

  // Fees clerk CAN record TRANSPORT_DUE_REMINDER in feeMessageSentLogs
  await assertSucceeds(
    feeDb.doc(`organisations/${ORG_ID}/feeMessageSentLogs/msg_tdue_1`).set({
      schoolId: SCHOOL_1,
      session: "2026-2027",
      studentId: "stu_legacy",
      studentName: "Aarav Kumar",
      className: "5",
      section: "A",
      guardianName: "Rajesh Kumar",
      normalizedMobile: "919876543210",
      messageType: "TRANSPORT_DUE_REMINDER",
      contextKey: "TDUE_AUG",
      messageText: "Transport fee due Rs 800 for AUG",
      channel: "WHATSAPP",
      sentByUid: FEE_CLERK_UID,
      sentByName: "Fee Clerk",
      sentAtMs: 3000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
