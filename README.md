# School Report Card (Universal Multi-School & Multi-Tenant Cloud App)

A native Android application (**Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication**, **Cloud Firestore**, **Room SQLite**, and **Android PdfDocument**) designed for multi-school organisations to manage report cards, students, marks, co-scholastic grades, and print-ready A4 PDFs with strict per-organisation isolation.

---

## Phase 5 Capabilities (Timetable: Manual Edit, Conflict Check, Clash Report & Offline A4 PDF Print)

All Phase 5 timetable data belongs to an organisation, lives in Cloud Firestore (`organisations/{orgId}/timetableCells/{deterministicId}` + `organisations/{orgId}/timetableLayouts/{schoolId}`), works 100% offline with Phase 3 sync, and is strictly controlled by the existing **"Timetable"** module (`No access` / `View only` / `View & edit`) in both UI and `firestore.rules`.

1. **Deterministic Timetable Cells & Offline Sync**
   - One timetable per school and session using deterministic document IDs (`tt_<school>_<session>_<class>_<section>_<day>_<slot>`) so cells can never be duplicated even when two phones edit offline.
   - Grid rows automatically follow each school's Phase 4 period structure (days as columns, periods as rows, assembly / short breaks / lunch rendered as fixed shaded non-editable rows).
   - Latest-edit-wins notice alerts editors if an offline cell edit was replaced by a newer edit from another device upon sync.

2. **Manual Cell Editing, Hard Conflicts & Soft Warnings**
   - **Class Timetable Editor:** Tap any teaching slot to pick a subject (from the class's subjects) and a teacher (ordered: Phase 4 class-subject assigned first, then others who teach the subject, then others — each with a live **Free / Busy / Off Day / Limit / Inactive** indicator). Supports **Clear cell**, **Copy cell to other days**, **Lock / Unlock**, and live weekly subject summary chips (e.g., `Maths 4/6`).
   - **Hard Conflict Checks (block saving with a clear message):**
     - Cross-school teacher double-booking checked across **all schools of the organisation using real clock times** (naming the teacher, day, time, and conflicting class & school).
     - Class double-booking in the same or overlapping slot.
     - Teacher scheduled on their weekly off day.
     - Teacher exceeding `maxPeriodsPerDay` across all schools.
     - Inactive teacher.
   - **Soft Warnings (allow saving, display amber warning):**
     - Same subject placed twice in one day (suppressed when Phase 4 `allowDoublePeriod` is enabled and the two periods are adjacent).
     - Teacher exceeding `maxConsecutivePeriods` (gap `<= 20 min` continues streak; lunch break `>= 25 min` or a free period resets it).
     - Subject weekly count exceeding Phase 4 assigned periods/week.
     - Subject placed against Phase 4 `preferredTimeOfDay` (`Morning < 12:00`, `Afternoon >= 12:00`).
   - **Post-Sync Clash Report:** Re-runs all hard checks automatically after every sync without silently overwriting any cell; displays a live Clash Report badge and list with 1-tap **Jump & Fix** directly into the conflicting cell.

3. **Four Timetable Views (Mobile-First 2D Scroll, Zoom & Sticky Headers)**
   - **Class Timetable**, **Teacher Timetable** (all schools combined with school name in every cell, total periods/week, and free periods), **Master Timetable** (entire school: rows = class-section, columns = day × period), and **Free Periods** (pick day & period slot to see all free active teachers across schools using real clock times + periods today).
   - Pinch-to-zoom and `- / + / 100%` zoom buttons, synchronized 2D scrolling, sticky top header row, sticky first column, and `>= 48 dp` touch targets.

4. **100% Offline A4 PDF Printing & Batch ZIP Export**
   - Clean, print-safe dark-navy/slate A4 vector PDFs matching the Report Card aesthetic (prints crisply in B&W): **Class Timetable** (Landscape or Portrait A4, includes Class Teacher if set), **Teacher Timetable** (all schools combined, A4 Landscape), **Master Timetable** (A4 Landscape, automatically splits by Class Group with repeated headers and fixed readable row heights), plus **Batch All Classes** and **Batch All Teachers** (combined PDF + optional ZIP of individual PDFs) with live preview, Print, Save, and Share.

---

## Phase 4 Capabilities (Teachers, Period Structure, Class-Subject-Teacher Assignments & Subject Rules)

All Phase 4 data belongs to an organisation (and one or more of its schools), lives in Cloud Firestore under `organisations/{orgId}/`, works 100% offline with Phase 3 sync (including pending-write counting on the live Status Chip), and is strictly controlled by the **"Teachers and assignments"** module (`No access` / `View only` / `View & edit`) in both UI and `firestore.rules`.

1. **Teachers Directory (`organisations/{orgId}/teachers/{teacherId}`)**
   - Approved members who completed their Phase 2 teacher profile automatically sync into the Teachers directory (and any existing completed Phase 2 teacher members can be imported in 1 tap from the top banner).
   - Admins/editors can also add a **no-login teacher record** (for timetable planning only) and link it to a member account later.
   - Fields: name, mobile, e-mail, optional `< 150 KB` photo, teachable subjects, schools worked in (one or multiple schools within the organisation), max periods per day (default `7`), max consecutive periods (default `3`), weekly off day(s), and active flag.
   - Search by name/mobile/e-mail, filter by school, subject, or active-only, edit details, activate/deactivate, and safely delete (**teachers with any subject assignment or Class Teacher role can never be deleted**).

2. **Per-School Period Structure (`organisations/{orgId}/periodStructures/{schoolId}`)**
   - Editable working days per school (default Monday–Saturday).
   - Ordered daily slot list with slot types (**Period**, **Assembly / Prayer**, **Short Break**, **Lunch Break**), start time, and end time.
   - Built-in editable default template: Morning Assembly, 8 periods of 40 minutes, Short Break after Period 3, Lunch after Period 5 (and 5 periods on Saturday). Different schools can configure completely different schedules.
   - **"Copy This Day"** copies one day's ordered slots to any selected target days.
   - Strict live validation prevents saving overlapping intervals, invalid bounds, or out-of-order slot times.

3. **Class-Subject-Teacher Assignments & Teacher Workload (`organisations/{orgId}/classAssignments/{assignmentId}`)**
   - Assign a **Class Teacher** per class & section, and assign a **Teacher** + **Periods/Week** for every subject in that class's class group.
   - Non-blocking **Warnings** (never block saving):
     - Class total assigned periods/week does not match available teaching period slots in that school's week.
     - Teacher's total periods/week across all schools exceed their weekly limit (`maxPeriodsPerDay × effectiveWorkingDaysCount`, where effective working days = union of working days across the teacher's schools minus their weekly off days).
     - Teacher assigned a subject not listed in their teachable subjects.
     - Teacher assigned in two schools whose teaching period timings overlap on any common non-off day.
     - Inactive teacher still assigned to a subject or as a Class Teacher.
   - **Workload Tab:** Per-teacher summary showing weekly load vs. weekly limit, Class Teacher duties, warnings, and full itemized cross-school class-subject assignments.

4. **Optional Subject Rules (`organisations/{orgId}/subjectRules/{ruleId}`)**
   - Per-subject scheduling preferences stored per school (ready for Phases 5 & 6): Preferred time of day (`No preference` / `Morning` / `Afternoon`), `Can be a double period`, and `Maximum once per day`.

---

## Phase 3 Capabilities (Offline-First with Sync)

1. **Offline Storage & Single Source of Truth (150 MB Cache)**
   - **Firestore Persistent Disk Cache:** Explicitly configured to **150 MB** (`150L * 1024L * 1024L` via `PersistentCacheSettings`) in `FirestoreProvider`.
   - **Single Source of Truth:** **Cloud Firestore + its 150 MB local disk cache** is the sole source of truth for all active multi-tenant organisation data (schools, classes, subjects, students, marks, mark edit logs, co-scholastic grades, attendance, remarks, settings, members). There are no duplicate or out-of-sync local copies.
   - **Room Database Role:** Room (`AppDatabase`) is kept **read-only strictly for legacy Phase 1 phone data** so existing owners can run the one-time idempotent cloud migration uploader. Active Phase 2/3 screens never read or write academic data through Room.
   - **Offline Startup & Session Persistence:** After one online sign-in and opening an organisation, the last selected organisation ID is remembered per signed-in user (`OfflinePrefsStore`), allowing the app to launch straight into the cached organisation in airplane mode.

2. **Live Status Chip & Non-Blocking Offline Writes**
   - The main top header displays a live, tappable status chip that updates automatically on connectivity and write-queue changes without ever blocking data entry:
     - **Online** — Connected and synced.
     - **Offline • Saved on phone** / **Offline • N saved** — Working offline; changes are persisted locally and queued.
     - **Syncing (N)** — Reconnected and uploading `N` pending local writes in order.
     - **Sync error** — Tap to inspect the error reason, last sync time, cache details, and **Retry Sync**.
   - **Immediate Offline Writes:** All day-to-day academic operations (marks grid/single-student entry, mark edit logs, students, co-scholastic grades, attendance/remarks, school edits, settings, and JSON backup restore) commit immediately against the local Firestore cache without waiting for a server round-trip, then upload automatically in order when internet returns.
   - **Unavoidable Online-Only Transactions:** Actions requiring global uniqueness, atomic token redemption, or server-authoritative membership enforcement explicitly require internet and show a clear offline notice:
     1. First-time Google Sign-In (`AuthScreen`)
     2. Create Organisation & reserve unique Organisation Code (`orgCodes/{code}`)
     3. Join Organisation lookup, Join Request submission/cancellation, and One-Time Invite Code redemption
     4. Admin member management (approve/reject requests, create/revoke invite codes, edit member roles/schools/permissions, remove member, leave organisation)
     5. Owner actions (transfer ownership, delete organisation, delete account)

3. **Conflicts (Latest Edit Wins) & Append-Only Marks Edit Log**
   - If the same mark is edited on two phones, the **latest edit wins**.
   - Every mark edit atomically appends an immutable entry in `markEditLogs` (who, when, old value -> new value, subject, component, student). Admins/Owners can inspect any student's **Marks History** from the Students list or the Marks Entry screen.
   - If an offline edit made on a phone is overwritten by a newer edit from another user upon reconnect, the losing phone displays a subtle dismissible banner (**"Some of your changes were replaced by newer ones"**) with a **"View Log"** button.

4. **Security & Cache Clearing on Sign-Out or Access Reduction**
   - Signing out wipes the local Firestore disk cache (`clearPersistence()`) and local offline preferences.
   - When a phone reconnects and a confirmed server snapshot shows that a member's access was **revoked OR reduced** (e.g., removed from a school, demoted from admin, lowered module access, or narrowed teacher subject scope), the app immediately clears the local Firestore disk cache and re-initializes listeners so previously cached records outside their new scope cannot remain readable offline.

5. **100% Offline PDF Reports & Duplicate-Free Offline JSON Backup**
   - Single & batch A4 report card previews, printing, PDF export, and batch ZIP export work completely offline from cached Firestore data.
   - JSON backup export and restore work both offline and online using deterministic document IDs (`bk_sch_<slug>`, `bk_stu_<session>_<admNo>`), guaranteeing zero duplicate schools or students when offline restores sync to the cloud.

---

## Phase 2 Capabilities (Multi-Tenant Organisations, Login, Roles & Cloud Sync)

1. **Login-First Gate & Persistent Session**
   - When no user is signed in, the app shows only the Login screen (Google Sign-In via Jetpack Credential Manager) with links to **Privacy Policy** and **Terms of Service** and a clear cloud storage notice.
   - No organisation or academic data screen is reachable before authentication. Sessions persist automatically across app restarts until sign-out.
   - *Optional Email/Password note:* In AI Studio environments, Google Sign-In is automatically provisioned via `google-services.json`. If you deploy this repository to your own Firebase project and want Email/Password sign-in in addition to Google Sign-In, enable **Email/Password** under **Firebase Console -> Authentication -> Sign-in method**.

2. **Multi-Tenant Organisations (Create or Join)**
   - **Create Organisation:** Live availability check for a unique Organisation Code (`a-z`, `0-9`, `-`, 4–30 characters), contact mobile & e-mail, and the details + logo (`< 150 KB`) of the **first school**. Submitting atomically creates the organisation, reserves the code, creates the first school with default class groups/subjects, and makes the creator the **Owner**.
   - **Join an Organisation:** Look up an organisation by its exact code (displays **only** the organisation name — never lists other organisations or exposes any school/student data), submit a join request with your name, message, and requested role (`Teacher`, `Office Operator`, or `Other`), and wait for admin approval — OR redeem an expiring **One-Time Invite Code** to join immediately without waiting.
   - **Organisation Picker:** Users belonging to multiple organisations can switch organisations at any time and still create or join additional organisations.

3. **Roles, Per-School Access & Per-Module Permissions**
   - Built-in role presets: **Owner**, **Admin**, **Office Operator**, **Teacher**, and **Other Staff**.
   - Admins can assign each member **All Schools** or specific schools inside the organisation, plus granular **`No access` / `View only` / `View & edit`** permissions across 8 modules: `Students`, `Marks`, `Co-Scholastic`, `Reports`, `Timetable`, `Teachers`, `Settings`, and `Members`.
   - Modules with `No access` are hidden in the navigation bar, dashboard, and screens AND enforced server-side in `firestore.rules`.
   - **Safety Guards:**
     - The Owner can never be removed or demoted directly.
     - Owners can transfer ownership to another active member or delete the organisation (deleting requires exporting a JSON backup first and typing the exact Organisation Code).
     - An organisation must always keep at least one active Admin.
     - Non-owner members can leave an organisation from **Settings**.
   - **Admin Members Screen:** Manage active members, approve/reject join requests (choosing role, schools, and permissions before approving), generate/revoke 7-day one-time invite codes, and inspect the append-only **Audit Log**.

4. **Teacher Profile Onboarding**
   - Approved teachers are guided to complete their teacher profile (name, mobile, optional `< 150 KB` photo, and classes/subjects they teach) on first entry, and can update their own profile from the Dashboard.
   - When an admin assigns specific class-subject scopes to a teacher, marks editing is restricted to those scopes both in the UI and in `firestore.rules`.

5. **Cloud Sync, Offline Cache & Local Phone Data Migration**
   - **Architecture Choice (Room + Firestore):** **Cloud Firestore** is the multi-tenant source of truth with Android persistent offline disk caching enabled (`PersistentCacheSettings`), displaying a live **Online / Offline / Syncing** status chip in the top bar. **Room** is preserved strictly for pre-existing local Phase 1 data on the phone; when an admin creates their first organisation and local Room records are detected, an **idempotent batch uploader** (also accessible from Settings) uploads all local schools, classes, subjects, students, marks, co-scholastic grades, attendance, and settings with a live progress bar while keeping the phone's local Room database intact (safe to re-run without duplicates).
   - **No Firebase Storage Required:** School logos and teacher/student photos are resized under **150 KB** and stored directly inside Firestore documents.
   - **Important Offline Member-Removal Caveat:** Because Cloud Firestore caches previously synced documents locally for offline use, removing a member or lowering their permissions takes effect immediately when they are online, and takes effect the **next time their device connects online** if they were offline when removed. Signing out clears the local Firestore cache on that device.

---

## Phase 1 Capabilities (Preserved Unchanged)

- **Multi-School Management:** Unlimited schools per organisation with custom logo, address, mobile, e-mail, active flag, custom class groups, custom classes (`Nursery`–`10th`, etc.), and editable/reorderable subjects.
- **Unified 10 / 30 / 60 Exam Structure:** Internal Assessment (10) + U.T. (30) + Exam (60) = 100 per term (200 Annual Grand Total), `AB` handling, CSV import/export, 8-point grading (`A1`..`E`), Pass/Fail (`>= 33%`), attendance totals, and class rank.
- **A4 Vector PDF & Batch ZIP:** UT1/UT2/Half-Yearly A4 Landscape slips (`2/page`, `1/page`, `Pair UT+H.Y.`) and single-page A4 Portrait Annual Report Cards, pulling school branding dynamically from the student's school.
- **Multi-School JSON Backup & Legacy Backup Migration:** Export all schools or the active school to JSON, merge/replace restore, and guided legacy backup import.

---

## Phase 6: Auto-Generate Timetable, Teacher Leave & Daily Substitutions

- **Access Control Decision (Existing 8-Module Matrix Preserved):** Phase 6 uses the existing **`Timetable`** module (`no access` / `view only` / `view and edit`) without adding a 9th module so existing organisation member records stay 100% compatible:
  - **Auto-generate, Undo, Approve/Reject Leave, and Assign/Clear Substitutions** require **`Timetable: view and edit`**.
  - **Linked Teachers with `Timetable: view only`** can view daily substitutions and **request or cancel their own `PENDING` leave**, enforced in both UI and Firestore Security Rules.
  - **Members with `Timetable: no access`** cannot view or modify any leave, substitution, or undo data.
- **Auto-Generate Timetable:**
  - Generate for a **whole school (all classes & sections)** or a **single class & section** on a background coroutine (`Dispatchers.Default`) with live progress and a **Cancel** button.
  - Strictly enforces all **Hard Rules** (cross-school real clock time overlap across all schools in the organisation, one subject per class slot, weekly off-days, daily period limits, and active teachers) and optimizes **Soft Goals** in order (spread subject across week / respect double periods, heavy/morning subjects in morning, preferred time of day, limit consecutive periods, balance daily teacher load).
  - Never modifies **locked cells** (and optionally keeps existing unlocked cells). Supports **deterministic seeds** (same seed produces the same timetable; **Regenerate** uses a fresh seed), **partial results with an Unplaced report**, and **1-tap Undo auto-generate** backed by `timetableUndoSnapshots`.
- **Teacher Leave & Approvals:**
  - Record single-day, multi-day, or half-day (specific periods) leave (`Casual Leave`, `Sick Leave`, `Official Duty`, `Other`) under `organisations/{orgId}/teacherLeaves/{leaveId}` with audit logging and offline sync + concurrent-edit conflict alerts.
- **Daily Substitutions & Printable Slip:**
  - Pick any date (`YYYY-MM-DD`) to see every affected period across schools for teachers on **approved** leave.
  - Ranks suggested substitutes by: **(1) teaches the same subject**, **(2) teaches that class already**, **(3) lowest substitution count this month**, **(4) lowest total load that day (regular + substitutions)**, excluding absent, busy (across all schools), off-day, inactive, or at-limit teachers (available via **Manual Override** with a clear `OVERRIDE` flag).
  - Stored per date/school/class/section/slot under `organisations/{orgId}/substitutions/{subId}` without ever modifying the weekly timetable.
  - Automatically re-flags periods as **Needs substitute** if an assigned substitute later becomes absent or inactive.
  - Counts assigned substitutions in weekly and monthly teacher workloads, exports an offline **A4 Portrait Daily Substitution Slip PDF** (with Signature column), and supports WhatsApp/SMS-ready text sharing.

---

## Phase 7: Teacher Access ("My Day"), Marks Pending List, Workload Report, Teacher Attendance & Report Card Links

All Phase 7 data lives under `organisations/{orgId}/`, works 100% offline with Phase 3 sync, and uses the existing Phase 2 **8-module per-member access matrix** (enforced in both the UI and `firestore.rules`):

1. **Teacher Home ("My Day") & Restricted Entry**
   - Any member linked to a Phase 4 teacher profile sees a **"My Day"** home card on the Dashboard displaying today's timetable (with real clock times, class-section, subject, and school), today's substitutions assigned to them, an interactive weekly timetable switcher (Mon–Sat), their Class Teacher section(s), and their own pending marks tasks.
   - **Marks Entry Enforcement (UI + `firestore.rules`):** Teachers can enter and edit marks **only** for `(school, class, section, subject)` combinations assigned to them in Phase 4 (`organisations/{orgId}/teacherScopes/{uid}`) AND only for exams the admin has opened (`openExamsForTeachers` per school). Unassigned classes/sections/subjects are hidden in the UI and rejected by Firestore rules (verified against the student's actual `className` and `section` on the server). Every edit preserves the existing immutable `markEditLogs` audit trail. Admins and non-teacher members with `Marks: view & edit` keep full access.
   - **Class Teacher Restriction (UI + `firestore.rules`):** Only the assigned Class Teacher of a section (or Admin / non-teacher with `Co-Scholastic: edit`) can edit co-scholastic grades, attendance, remarks, and promoted-to for students in that section. Subject teachers cannot.

2. **Marks Pending List & WhatsApp/SMS Reminders**
   - For each exam (`UT1`, `UT2`, `Half-Yearly`, `Annual`), lists incomplete class-section-subject sheets with `entered / total` students and responsible teacher, filterable by **School**, **Class**, **Exam**, and **Teacher**.
   - Teachers see only their own pending tasks; Admins/non-restricted members see all pending items across schools, see a **Marks Pending by Exam** summary tile on the Dashboard, and can tap **"Remind"** to share a ready WhatsApp/SMS message (e.g., `"Mrs. Sharma: Class 3-A Maths UT1 marks are pending, 12 of 40 entered."`).

3. **Workload Report (Per School & Across Schools + Offline A4 PDF / CSV)**
   - Shows per-teacher periods/week broken down by class-section & subject, free periods/week, substitutions taken this week and this month, and total load (per school and combined across schools for multi-school teachers).
   - Filter by School, Teacher, and Week/Month, and export offline as an **A4 Landscape PDF** (with school name & logo header) or **CSV**.

4. **Teacher Attendance (Governed by `Timetable: view & edit`)**
   - Daily attendance sheet per school (`Present`, `Half-Day`, `On Leave`, `Absent`) under `organisations/{orgId}/teacherAttendance/{docId}` — reusing the existing **`Timetable`** module (`view` to view, `edit` to mark) so no extra access module is needed.
   - Approved leaves from Phase 6 automatically appear as **On Leave** (or **Half-Day**). Marking a teacher **Absent** offers a 1-tap **"Create Approved Leave & Open Substitutions"** button that creates the approved leave and opens Substitutions for that exact date.
   - Includes a **Monthly Summary** tab and offline **A4 Portrait Monthly Attendance PDF** export.

5. **Optional Report Card Links (Both OFF by Default, Per School)**
   - Configured per school under **Settings -> Report Card Links & Open Exams** (`Settings: edit`):
     - **Print Class Teacher's name on reports** (OFF by default): when ON, prints the assigned Class Teacher's name neatly beneath `"CLASS TEACHER'S SIGN"` on UT slips, Half-Yearly slips, and Annual Report Cards without altering existing layouts.
     - **"Report cards ready" check before batch export** (OFF by default): when ON, warns before batch PDF or Class ZIP export whenever any student in the section is missing marks, attendance, co-scholastic grades, or remarks, with a detailed student list and an **Export Anyway** option.
     - **Exams Open for Teacher Marks Entry:** toggle which exams (`UT1`, `UT2`, `Half-Yearly`, `Annual`) teachers can edit.

---

## Phase 8: Student Attendance Module

All Phase 8 student attendance data belongs to an organisation in Cloud Firestore (`schoolCalendars`, `studentAttendance`, `studentAttendanceEditLogs`), works 100% offline with Phase 3 sync, and stays completely separate from Phase 7 **Teacher Attendance**. Report card layouts, marks calculations, and grading rules remain completely unchanged.

1. **9th Access Module (`Attendance`) & Zero-Downtime 8→9 Migration**
   - Added a 9th module **`Attendance`** (`no access` / `view only` / `view and edit`) with the same per-school scope as the other 8 modules.
   - **Backward-Compatible Migration:** Both the app and `firestore.rules` accept legacy 8-key member documents (without `"attendance"`) and 9-key documents side-by-side. A missing `"attendance"` key is treated as `no access`, **except that any Class Teacher always automatically has edit access to their own assigned section(s)** (verified via `teacherScopes`). Admins and Owners keep full access, and saving any member or invite code lazily writes all 9 keys with zero downtime.
   - Enforced in both the UI and `firestore.rules` (with emulator tests): Class Teachers can write only for their own section within the school's edit window; other subject teachers cannot unless granted `Attendance: edit`; view-only members cannot write; members without access see nothing; cross-organisation isolation stays intact.

2. **Per-School & Session Calendar (`organisations/{orgId}/schoolCalendars/{docId}`)**
   - Working weekdays default from the school's Phase 4 period structure and can be overridden per session.
   - Supports single-day holidays, date ranges (e.g., Diwali Break), later **"Declared Off"** days, optional **Half Working Days**, and Admin **"Working Day Exceptions"** (so an Admin can declare a holiday/weekend open when needed).
   - Editable **Term-I** and **Term-II** start/end dates validated to never overlap and to stay within the active session, plus a **Live Working-Days Calculator** for any custom date range.

3. **Daily Student Attendance Entry (`organisations/{orgId}/studentAttendance/{docId}`)**
   - One deterministic document per section per date (`satt_<school>_<session>_<class>_<section>_<date>`) so offline edits never create duplicates, synced with latest-edit-wins conflict alerts.
   - Students ordered by roll number starting as **Present (`P`)**; 1-tap cycles or opens a quick chooser for **Present (`P`)**, **Absent (`A`)**, **Leave (`L`, with optional reason)**, **Late (`Lt`)**, and **Half-day (`H`)**, with big **"Mark All Present"** and **"Save"** buttons, live counts, and a search box.
   - Blocks holidays and non-working days (with an Admin **"It is a working day — add exception"** button). Past dates are allowed up to the school's configurable **Class teacher edit limit** (default **7 days**, configurable in **Settings**; Admins unlimited). Any post-day or subsequent edit appends an immutable audit record to `studentAttendanceEditLogs` (`who`, `when`, `what changed`).

4. **Optional Report Card Attendance Link (OFF by Default)**
   - Per-school switch in **Settings -> Student Attendance Settings**: **"Fill report card attendance automatically from daily attendance"** (**OFF by default** so existing report cards are untouched).
   - Weighting: **Present = 1.0**, **Late = 1.0**, **Half-day = 0.5**, **Absent = 0.0**, **Leave = 0.0** (rounded half-up to integers on report cards so existing Annual Grand Total math like `84/111 + 82/97 = 166/208` works identically).
   - Manually typed report card attendance values stay protected with a **"Manual"** badge; use **"Recalculate"** (per student or per section & term) with confirmation to refresh from daily records.

5. **Offline A4 PDF Reports, Low Attendance CSV & Guardian Share Texts**
   - **Daily Attendance Sheet** (A4 Portrait), **Monthly Attendance Register** (A4 Landscape, 31-day grid, shaded holidays, `P`/`A`/`L`/`Lt`/`H`, per-student & per-day totals, paginated for 50+ students), **Student-Wise Date-Range Summary** (A4 Portrait), and **Low Attendance List** (section or whole school, configurable threshold % default `75%`, A4 Portrait PDF + CSV export).
   - **Guardian WhatsApp/SMS Share:** Share an individual absent-student message or a full class absent list using Android's standard share sheet (never auto-sent).

### Quick Guide for Class Teachers (Taking Daily Student Attendance)

1. Open the app — on your **Home** screen you will see **"My Class Attendance (Today)"** showing whether today's attendance for your section is taken or pending.
2. Tap **"Take Attendance"** to open your section's roster ordered by roll number (everyone starts marked **Present**).
3. Tap a student's status pill to cycle (`P -> A -> L -> Lt -> H`) or tap the edit icon to pick a status and add an optional **Leave reason** (e.g., *Sick*). Use **Search** to jump straight to any student.
4. Check the live **Present / Absent / Leave / Late / Half** counters at the top, then tap **"Save Attendance"**. Works offline too — saved on your phone and synced automatically when internet returns.
5. Need to inform parents? Tap **Share** next to an absent student (or **"Share Absent List"** at the top) to send a ready-made message via WhatsApp or SMS.
6. Need to fix a recent day? Use the date arrows to step back (up to the school's edit window, default 7 days). All post-day changes are recorded in the **Edit History** log; for older dates or holiday overrides, ask your school Admin.

---

## Phase 10: Certificates, Cash Book & Admission Enquiry

All Phase 10 modules are mobile-first (touch targets `>= 48 dp`), Hindi-friendly with a 1-tap **`हिन्दी / EN`** toggle on every screen, work 100% offline with Phase 3 Firestore sync, and preserve Phases 1–9 unchanged.

1. **Three New Access Modules (`Certificates`, `Cash Book`, `Enquiry`)**
   - Added `certificates`, `cashBook`, and `enquiry` (`No access` / `View only` / `View & edit`) to the per-member access matrix (now 14 modules total), enforced in both UI and `firestore.rules` (with emulator rules tests).
   - **Backward-Compatible Missing-Key Rule:** Any older member document missing these keys evaluates to `No access` (sees nothing). Owners and Admins always keep full access across all schools; `Office Operator` defaults to `View & edit` for all three new modules.

2. **Certificates (`organisations/{orgId}/schoolCertificates/{docId}`)**
   - Issue **Transfer Certificate (TC)**, **Bonafide Certificate**, or **Character Certificate** directly from any student's profile in the **Student Register** or from the **Certificates** screen.
   - 1-tap prepare pre-fills student particulars from the Student Register, lets staff review/edit fields (`Reason for leaving`, `Date`, `Conduct`, `Promotion status`, `Fee dues`, `Remarks`), and generates an offline **A4 Portrait PDF** with school logo, border, details, date, Office Seal box, and Principal signature line (Preview, Print, Save, Share).
   - **Duplicate-Free Offline Numbering:** Every phone generates a deterministic 3-character `deviceNodeCode` (e.g. `A1B`, `K4M`) stored locally and issues running numbers per `(school, session, certificateType, deviceNodeCode)` in the format `{TYPE}-{SESSION}-{NODE}-{SEQ}` (e.g. `TC-2627-A1B-0001`, `BON-2627-A1B-0001`, `CHR-2627-A1B-0001`). Because each phone increments inside its own device namespace, two phones offline at the same time never collide when they sync.
   - **Immutable Register + Cancel & Re-issue:** Issued certificates can never be edited or silently deleted. Staff can **Cancel** with a mandatory reason (stamping a diagonal red `CANCELLED` watermark on the PDF) and **Re-issue** a fresh numbered certificate linked to the cancelled original. Issuing a **TC** immediately offers to mark the student as **"Left"** in the Student Register with the date and reason.

3. **Cash Book (`cashBookEntries`, `cashBookDayClosings`, `cashBookSettings`, `cashBookAuditLogs`)**
   - **Daily Expense & Other Income Entry:** Record date, category (editable per school), amount, payment mode (`Cash`/`UPI`/`Bank`/`Cheque`), note, and optional **Bill Photo** (using Android's zero-permission Photo Picker, compressed `< 180 KB`). Edits and cancellations require a mandatory reason and append an immutable audit log entry — nothing is silently deleted.
   - **Automatic Phase 9 Fee Income:** Active Phase 9 `feeReceipts` are read automatically as Cash Book income (never typed twice), even for accounts staff who have `Cash Book` access without `Fees` access.
   - **Today, Month & Day Closing:** View today's Money In, Money Out, and Closing Balance; Month view with category-wise totals and share bars; and **Day Closing** showing Opening Cash, Cash In/Out, Expected Closing Cash, Actual Counted Cash, and live **Difference** (`Counted − Expected`, with non-cash UPI/Bank totals shown separately).
   - **Large Expense Approval Limit:** Expenses above a configurable per-school threshold (default **₹5,000**, editable by Admins/Owners only) require an **Admin Approver** (enforced in UI, domain engine, and `firestore.rules`).
   - **Offline A4 PDFs & CSV Export:** Export **Day Book PDF**, **Month Summary PDF**, **Category-Wise Expense PDF**, and **Day / Month CSV**.

4. **Admission Enquiry (`admissionEnquiries`, `enquirySettings`)**
   - **Under-1-Minute Entry:** Capture child name, class wanted (1-tap chips), guardian name, mobile, editable source list, notes, and next follow-up date (`Today`, `+2 days`, `+1 week`).
   - **Follow-Up Today Queue (Overdue First):** Lists open enquiries (`New`, `Contacted`, `Visit Planned`) with **Overdue** strictly at the top (oldest first), followed by **Due Today**. Supports `Admitted` and `Lost` (with mandatory reason).
   - **1-Tap WhatsApp & Call:** **"Send on WhatsApp"** opens an editable message template and launches `wa.me` with `+91` mobile normalisation (never automatic); **"Call"** opens the phone dialer.
   - **Convert to Admission & Monthly Summary:** 1-tap **"Convert to Admission"** creates the student directly in the **Student Register** with pre-filled details and links the enquiry to the new Admission No. Monthly summary card tracks **Enquiries this month**, **Admitted**, and **Conversion %**.

---

## Phase 11: Exam Schedule & Admit Cards, Stock & Transport Routes

All Phase 11 modules follow the same mobile-first (`>= 48 dp` touch targets), Hindi-friendly (`हिन्दी / EN` toggle), offline-first Firestore sync architecture and leave Phases 1–10 report cards, marks, grading, fees, attendance, certificates, cash book, and enquiry behaviour untouched.

1. **Three New Access Modules (`Exams`, `Stock`, `Transport`)**
   - Added `exams`, `stock`, and `transport` (`No access` / `View only` / `View & edit`) to the member access matrix (**17 modules total**), enforced in both the Compose UI and `firestore.rules` (verified with emulator rules tests).
   - **Backward-Compatible Missing-Key Rule:** Older member records missing these keys evaluate to `No access`. Owners and Admins keep full access across all schools; `Office Operator` defaults to `View & edit`; `Teacher` and `Other` default to `No access` (linked teachers can still read **only their own** assigned exam duties to see **"My Exam Duties"** on Teacher Home).

2. **Exam Schedule & Admit Cards (`examSchedules`, `examRooms`, `examSeatings`, `examInvigilatorDuties`)**
   - **Exam Timetable with Validation:** Create exams (`UT-I`, `UT-II`, `Half-Yearly`, `Yearly`, or `Custom`) per school and session. Add class-wise papers (subject, date, start/end time, max marks) with automatic validation preventing same-day time clashes for a class, duplicate subjects, or dates outside the session (April 1 – March 31).
   - **Rooms & Seating Plan:** Define exam rooms with seat capacities and auto-allocate active students **By Roll Number** or **Mixed by Class** (interleaving classes across adjacent seats), with 1-tap manual room reassignment and per-room seating lists.
   - **Invigilator Duty Roster:** Auto-assign teachers to every exam slot and room while avoiding Phase 6 approved leaves and weekly off-days, preventing overlapping double-booking, and balancing total duty load across teachers. Supports manual overrides and displays **"My Exam Duties"** on each linked teacher's Home screen.
   - **Optional Fee-Dues Withholding Warning:** Set an optional per-exam dues threshold (`0` = disabled); students above the limit show a clear warning on Admit Cards, and Admins/Owners can override individually.
   - **Offline A4 PDFs (Preview, Print, Share):** Class Exam Timetable, Student Admit Cards (**2 cards per A4 page** with dashed cut line, school logo, student photo if present, roll no, class, subjects/dates/times, room & seat, signatures), Room Seating List, and Invigilator Duty List.

3. **Unified Stock (`stockTransactions` + Phase 9 `feeStructures.optionalCatalogue`)**
   - **Single Shared Catalogue:** Stock items (tie, belt, books, uniform, diary, etc.) live in the exact same `optionalCatalogue` list used by the Phase 9 Fee Counter — adding unit, opening stock, and low-stock warning level without duplicating item lists.
   - **Automatic Stock Out & Cancel Return:** Record purchases (**Stock In** with date, quantity, unit/total cost, supplier) and reasoned **Manual Adjustments**. Whenever an optional item is sold on a Phase 9 fee receipt, stock drops automatically; if that receipt is cancelled, the stock returns automatically — computed offline-safe from active receipts and stock transactions.
   - **Low-Stock Warnings & Below-Zero Guard:** Highlights items at or below their warning level, exports an **A4 Stock Summary Report PDF**, and shows a below-zero warning on the Phase 9 Fee Counter with an Admin override checkbox.

4. **Transport Routes (`transportRoutes` + `students` route/stop fields)**
   - **Routes & Stops:** Manage bus/van routes with vehicle number, seat capacity, driver name & phone (with 1-tap Call), stops, and monthly fee per stop or route.
   - **Safe Student Assignment:** Assign students to a route & stop from either the **Transport** screen or the **Student Register**. Students who already have a custom positive Phase 9 transport monthly fee keep their existing amount by default unless you choose to apply the route/stop fee.
   - **Summary & Offline A4 PDFs:** Live cards for students per route, seats used / over-capacity warnings, and expected monthly transport income, plus **Route-Wise Student List PDF** and **Driver's Pickup & Drop Sheet PDF** with guardian mobiles.

---

## How Each Role Uses the App

- **Owner:** Creates the organisation (and first school), can add/edit multiple schools, manage all academic/timetable/attendance/fees/certificates/cash book/enquiry/exams/stock/transport modules, approve members & set granular per-school/per-module access (all 17 modules), configure report card links, attendance auto-fill, cash book approval limits & open exams, export JSON backups, transfer ownership, or delete the organisation (requires a backup first).
- **Admin:** Day-to-day head across assigned schools — manages members & invite codes, teachers, period structures, class-subject assignments, timetables, auto-generate, leave approvals, daily substitutions, staff & student attendance, fees & concessions, certificates, cash book (including approving large expenses and editing limits), admission enquiries, exam schedules/rooms/invigilators/admit card overrides, stock & below-zero overrides, transport routes, and batch A4 report cards.
- **Office Operator:** Handles daily administrative operations for their assigned school(s) based on the exact modules granted by the Admin (defaulting to Students, Marks, Co-Scholastic, Reports, Timetable, Attendance, Fees, Student Register, Certificates, Cash Book, Admission Enquiry, Exams, Stock, and Transport).
- **Teacher:** Signs in and lands on **"My Day"**, **"My Class Attendance (Today)"**, and **"My Exam Duties"** showing today's periods, assigned substitutions, upcoming invigilator duties, weekly timetable, Class Teacher section(s), and own pending marks. Enters marks **only** for their assigned class-section-subject combinations during open exams, enters co-scholastic/attendance/remarks and takes daily **Student Attendance** **only** for their own Class Teacher section, and can submit or cancel their own `PENDING` leave requests.

---

## Firebase Setup (`google-services.json`, Auth Providers & SHA Fingerprints)

Keep private Firebase credentials out of public source control where possible:

1. Create or open your Firebase project at [Firebase Console](https://console.firebase.google.com/).
2. Register your Android app with your `applicationId` (see `app/build.gradle.kts`).
3. **Enable BOTH Sign-In Providers:**
   - Go to **Firebase Console -> Authentication -> Sign-in method**.
   - Enable **Google** (select a project support email and save).
   - Enable **Email/Password** (turn on the first toggle "Email/Password" and save).
   - Under **Authentication -> Settings -> User account linking**, keep **"Prevent creation of multiple accounts with the same email address"** enabled so Google and Email/Password stay linked to a single account.
4. **Add ALL THREE SHA-1 & SHA-256 Key Pairs (Required so Google Sign-In works in Debug, Local Release, and Play Store builds):**
   Open **Firebase Console -> Project Settings -> General -> Your apps -> Android app -> SHA certificate fingerprints -> Add fingerprint** and add **both SHA-1 and SHA-256** from each of the 3 sources below:
   - **A. Debug Keystore (local development & testing):**
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     *(On Windows: `%USERPROFILE%\.android\debug.keystore`; in AI Studio builds: project `debug.keystore`.)*
     Copy the **SHA1** and **SHA256** values and add both in Firebase Console.
   - **B. Release Upload Keystore (local release APK / AAB testing):**
     If you do not have one yet, generate it once and back it up safely offline (never commit to Git):
     ```bash
     keytool -genkey -v -keystore release-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
     ```
     Print its fingerprints:
     ```bash
     keytool -list -v -keystore release-upload.jks -alias upload
     ```
     Copy the **SHA1** and **SHA256** values and add both in Firebase Console.
   - **C. Google Play App Signing Key (REQUIRED for Play Store downloads):**
     Google Play re-signs every `.aab` you upload with Google's Play App Signing key. Without this key in Firebase, **Continue with Google** will fail for Play Store users!
     1. Upload your first `.aab` to an internal/closed test track in [Google Play Console](https://play.google.com/console/).
     2. In Play Console, open your app -> **Test and release -> Setup -> App signing** (or **Release -> Setup -> App integrity -> App signing**).
     3. Under **App signing key certificate**, copy both the **SHA-1 certificate fingerprint** and **SHA-256 certificate fingerprint**.
     4. Paste both into **Firebase Console -> Project Settings -> Your Android app -> Add fingerprint**.
   - **Re-download `google-services.json`:** After adding all SHA-1/SHA-256 keys, download the fresh **`google-services.json`** from Firebase Console and replace `app/google-services.json` before building your Play Store `.aab`. (Add `app/google-services.json` to `.gitignore` in private/public Git repositories so credentials are not committed.)
5. Enable **Cloud Firestore** and deploy the security rules included in this repository:
   ```bash
   firebase deploy --only firestore:rules
   ```

---

## Building a Signed Android App Bundle (`.aab`) for Google Play Store

Google Play requires a release-signed **Android App Bundle (`.aab`)** rather than a debug APK:

1. **Ensure all SHA-1 & SHA-256 keys above are registered** (Debug, Release Upload, and Google Play App Signing) and `app/google-services.json` is freshly downloaded from Firebase Console.
2. **Build the Signed AAB:**
   - **Android Studio:** Go to **Build -> Generate Signed App Bundle / APK -> Android App Bundle**, choose `release-upload.jks`, select the `release` build variant, and generate `app/release/app-release.aab`.
   - **Command Line:** Configure your release signing credentials via environment variables / Gradle properties and run:
     ```bash
     gradle :app:bundleRelease
     ```
3. **Google Play Console Compliance Checklist (Required Before Publishing):**
   - **Privacy Policy URL:** Host your public Privacy Policy page (matching the in-app Privacy Policy screen) and paste the HTTPS URL into **Play Console -> App content -> Privacy policy**.
   - **Data Safety Form:** Declare collection of user account identifiers (name, email), school & student educational records, and optional photos stored in Google Cloud Firestore, encrypted in transit, and not sold to third parties.
   - **Account Deletion Support:** Declare that users can delete their account in-app (**Settings -> Delete My Account** — requires re-authentication via the same sign-in method used, and is blocked if they still own an organisation until ownership is transferred or the organisation is deleted) and provide a matching web deletion-request URL in Play Console.
   - **Target Audience & Content Rating:** Complete the IARC questionnaire and declare the app is designed for school staff/administrators (not directed to children).

---

## Build & Test Commands

```bash
# Compile Android debug APK
gradle :app:assembleDebug

# Run JUnit permission tests + Phase 1 calculation & report tests
gradle :app:testDebugUnitTest

# Run Firestore security rules unit tests against the local Firestore emulator
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"
```
