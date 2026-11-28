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

4. **Transport Module (`transportRoutes`, `transportVehicleDocuments`, `transportSettings`, `transportAuditLogs` + unified `cashBookEntries` + `students` transport fields)**
   - **Clerk Quick Guide (4 Tabs + Summary Bar):**
     - Top summary bar tracks **Students on Transport**, **Seats Used / Capacity**, **Expected Monthly Income**, and **This Month's Dues**. Use the **हिन्दी / EN** toggle anytime for Hindi or English labels.
     - **Routes Tab:** Create/edit bus or van routes with pickup order & timing per stop, separate monthly fees for **Both Ways**, **Pickup Only**, and **Drop Only** (Pickup/Drop default to Both Ways until changed), per-stop overrides, seat capacity, vehicle ownership (**Own** vs **Rented** with contractor name, phone & monthly rent), driver name/phone/licence/expiry, and optional conductor details. Activate or deactivate routes; **deleting a route that has students is blocked** — first move students to another route or deactivate it. Changing a route or stop fee offers **"Apply to Students"** with a per-student Old → New preview (excluding individually overridden students) and an **"Effective From Month"** selector so already-paid months never change. Renaming a route updates displays everywhere automatically (students link by `transportRouteId`). If an assigned stop is removed, students on it show **"Stop needs update"**.
     - **Students Tab:** View all transport students in one searchable list (no 40-student limit) filtered by **Route**, **Class**, **Section**, or **"No Route / Needs Attention"**. Tick multiple students to **Bulk Assign / Move** (shows each student's current route first so moves are explicit), **Change Trip Type**, **Set Start Month**, or **Remove from Transport**. Individual student fees can be **Overridden with a mandatory remark** or **Reset to Route Fee** in 1 tap. Adding a student onto a full vehicle shows an over-capacity warning (Admins/Clerks may acknowledge to proceed).
     - **Student Register & Fee Collection Links:** Toggling **Uses School Transport** in **Student Register** defaults `Start Month` to the current month (or jumps straight to **Routes** if no route exists yet). On **Collect Fees**, a student's header displays live Route, Stop, Trip Type, and Start Month; if Transport is enabled with no route, missing start month, or `₹0` fee, a prominent warning with **"Fix in Student Register"** / **"Open Transport"** shortcuts appears (non-transport students continue to see no Transport column). Existing transport students missing `Start Month` are listed in a reviewable **Legacy Migration** card that assigns the first charged session month so past dues and paid receipts stay identical.
     - **Dues Tab & WhatsApp Queue:** Filter transport dues by **Month**, **Route**, and **Class**, export **Transport Dues & Fee Schedule PDF**, or tap **"Send to WhatsApp Queue"** to open Phase 9's manual Guardian WhatsApp Queue prefilled with the `TRANSPORT_DUE_REMINDER` template (never sent automatically).
     - **Vehicles, Docs & Expenses Tab:** Track **Insurance**, **Fitness**, **Permit**, **PUC**, and **Road Tax** with expiry dates, document numbers, and optional photo attachments (`Valid` / `Expiring ≤30 days` / `Expired`). Expiring/expired alerts appear on the **Today Home** screen for members with `Transport` access only. View **Monthly Income vs Cost (Profit / Loss)** per vehicle/route and school-wide, and export **Route List**, **Driver Pickup Sheet**, **Dues & Schedule**, and **Monthly P&L** as offline **A4 PDFs** or **CSV**.
   - **Single-Record Cash Book Expense Link (How It Works):** Every transport expense (`Diesel`, `Repair`, `Tyre`, `Insurance`, `Driver Pay`, `Rent`, `Other`, with editable categories) is stored **once** inside the Phase 10 **`cashBookEntries`** collection with `sourceModule = "TRANSPORT"`, `transportRouteId`, `vehicleNumber`, and `transportCategory` (edit/cancel always requires a mandatory reason and writes an immutable audit log entry — never silently deleted, and never duplicated between Cash Book and Transport). In **`firestore.rules`**, staff who hold **`transport` edit** permission (even without `cashBook` edit permission) are explicitly allowed to create, edit, and cancel `cashBookEntries` and write `cashBookAuditLogs` **only when `sourceModule == "TRANSPORT"`**, subject to the same school **Admin Approval Limit** rule for high-value expenses.

---

## Phase 12: Staff Salary (Sensitive Owner-First Access)

All Phase 12 features follow the same mobile-first (`>= 48 dp` touch targets), Hindi-friendly (`हिन्दी / EN` toggle), offline-first Firestore sync architecture while keeping Phases 1–11 working unchanged.

1. **Sensitive 18th Access Module (`Salary` — Owner-Only by Default)**
   - Added `salary` (`No access` / `View only` / `View & edit`) as the **18th module** in the member access matrix, enforced in both the Compose UI and `firestore.rules` (with emulator rules tests).
   - **Owner-Only Default & Explicit Admin Grant:** Unlike other modules where Admins automatically receive access, **only the `Owner` has `Salary` access by default**. An `Admin` sees nothing of `Salary` unless the **Owner** explicitly grants `View only` or `View & edit` on that Admin's member permissions (and only the Owner can grant or change the `Salary` permission key).
   - **Missing-Key Rule:** Any member document without the `salary` key evaluates to `No access` (sees nothing).
   - **Teacher Own Salary Slips (Owner Toggle):** When the Owner turns on **"Allow Teachers to View Own Salary Slips"** in **Salary -> Rules & Audit**, linked teachers see a **"My Salary Slips"** card on their Home screen and can download/print **only their own** A4 or A5 salary slips (never any other staff member's salary or setup).

2. **Staff Salary Setup (`staffSalaryProfiles`)**
   - Configure salary profiles for both **teaching staff** (1-tap **"Sync Phase 4 Teachers"**) and **non-teaching staff** (accountant, clerk, driver, peon, guard, etc.).
   - Per staff member: **Basic monthly salary**, **editable Allowances list** (HRA, DA, Conveyance, etc.), **editable Fixed Deductions list** (PF, TDS, Professional Tax, etc.), **joining date**, bank/UPI note, and **Per-School or All-Schools assignment**.

3. **Monthly Salary Sheet & Phase 7 Attendance Rules (`salaryMonthlyRecords`, `salaryRuleConfigs`)**
   - **1-Tap Pull from Phase 7 Teacher Attendance:** Pulls each staff member's **Present**, **Absent**, **Approved Leave**, **Half-Day**, and **Late** days for the selected month (`YYYY-MM`).
   - **Clearly Shown Per-School Attendance Deduction Rule:** Editable per school — choose per-day deduction basis (**Basic ÷ 30 days**, **Basic ÷ Working days in month**, or a fixed ₹/day override), **free leaves per month** (default `1`), whether approved Phase 6 leaves count as paid, **half-day deduction %** (default `50%`), and **late-day penalty** (e.g., every `3` late days = `1` day deduction or fixed ₹/late day). The active rule summary banner is shown clearly at the top of the Monthly Sheet.
   - **Manual Hand Adjustment with Remark:** Any staff member's monthly salary can be adjusted up or down (`+₹` bonus/arrear or `-₹` fine/deduction) with a mandatory **Remark** printed on the slip and logged.

4. **Advances & Loans (`staffSalaryAdvances`)**
   - Give an advance/loan to a staff member with date, principal amount, payment mode, reason, and **monthly instalment amount** (with automatic monthly deduction toggle).
   - Record manual repayments at any time or let the Monthly Salary Sheet automatically deduct `min(monthlyInstalment, remainingBalance)` when salary is marked paid, showing the live remaining balance and auto-closing when the balance reaches `₹0`.

5. **Pay, A4/A5 Salary Slip PDFs, Month Locking & Automatic Cash Book Expense**
   - **Mark Paid & Offline PDFs / CSV:** Mark any staff member paid with date, mode (`Cash`/`UPI`/`Cheque`/`Other`), and reference. Generate **A4 or A5 Salary Slip PDF** per staff member (with school name & logo, month, attendance days, earnings, deductions, advance instalment, adjustment remark, net salary in words, and signature lines), **A4 Month Summary PDF**, and **CSV export**.
   - **Owner Month Locking (`salaryMonthLocks`, `salaryAuditLogs`):** Once a month is finished, the **Owner** can lock it. Any change after locking requires a mandatory override reason and is recorded in the append-only **Salary Audit Log**.
   - **Automatic Phase 10 Cash Book Expense Integration:** Every salary marked `PAID` automatically appears as a `"Salary"` expense in the Phase 10 **Cash Book** on its `paidDateIso` with its payment mode — never typed twice and never double-counted.

---

## How Each Role Uses the App

- **Owner:** Creates the organisation (and first school), has exclusive default access to **Staff Salary** (can explicitly grant `Salary` view/edit to trusted Admins, enable teacher self-slip viewing, and lock finished salary months), manages all 18 modules across schools, exports JSON backups, transfers ownership, or deletes the organisation.
- **Admin:** Day-to-day head across assigned schools — manages members & invite codes, teachers, period structures, class-subject assignments, timetables, auto-generate, leave approvals, daily substitutions, staff & student attendance, fees & concessions, certificates, cash book, admission enquiries, exam schedules/rooms/invigilators/admit card overrides, stock, transport routes, and batch A4 report cards. Sees **Staff Salary** only if explicitly granted by the Owner.
- **Office Operator:** Handles daily administrative operations for their assigned school(s) based on the exact modules granted (defaulting to Students, Marks, Co-Scholastic, Reports, Timetable, Attendance, Fees, Student Register, Certificates, Cash Book, Admission Enquiry, Exams, Stock, and Transport; `Salary` defaults to `No access`).
- **Teacher:** Signs in and lands on **"My Day"**, **"My Class Attendance (Today)"**, **"My Exam Duties"**, and (when enabled by the Owner) **"My Salary Slips"** for viewing/printing their own A4/A5 payslips. Enters marks **only** for assigned class-section-subject combinations during open exams, enters co-scholastic/attendance/remarks and takes daily **Student Attendance** **only** for their own Class Teacher section, and submits/cancels their own `PENDING` leave requests.

---

## Firebase Setup (`google-services.json`, Auth Providers & SHA Fingerprints)

Keep private Firebase credentials out of public source control where possible:

1. Create or open your Firebase project at [Firebase Console](https://console.firebase.google.com/).
2. Register your Android app with package name `com.vivek.schoolreportcard` (`applicationId` in `app/build.gradle.kts`).
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

## Phase 13: "Today" Home Screen & First-Time Setup Guide

Phase 13 makes the home experience immediately useful and friendly for every school role without changing any Phase 1–12 data schemas, calculations, or report layouts:

1. **Role-Aware "Today" Home (Strict Module Visibility)**
   - **Owner & Admin:** Displays tappable live Today cards (hidden automatically for any module the member cannot access):
     - Today's student attendance per school (`sections taken / total`, `% present`) plus clickable chips for **classes/sections where attendance is not yet taken** (1-tap jump straight to that section).
     - Today's fee collection total & receipts count, and today's expenses (including auto-merged Phase 12 paid salaries without double entry).
     - Guardians with dues (1-tap opens Fee Reports & Dues).
     - Teachers absent today & substitutions pending (1-tap opens Staff Attendance / Substitutions).
     - Admission Enquiry follow-ups due today (overdue + today).
     - Low-stock items (unified with Phase 9/11 optional item catalogue).
     - Exams & invigilator duties coming up this week (next 7 days).
   - **Teacher:** Sees only their own items — a big **"Take Attendance / हाज़िरी लें"** button for their own Class Teacher section(s), their periods today, pending marks entry, my exam duties, my leave status (pending/approved/rejected), and own salary slips (when enabled by the Owner).
   - **Fee Clerk (Office Operator / Staff with `Fees` access):** Opens directly to a large **Student Search Box** (by name, admission number, or mobile digits) that starts Fee Collection for that student in **one tap**, paired with Today's Collection total and Unsent Dues in the Send Queue.
2. **Role-Specific Quick Actions Bar:** A floating 3-button bottom shortcut bar tailored to the user's top 3 daily actions (filtered strictly by permissions, with ≥48 dp touch targets).
3. **Friendliness & Clear Sync Status:**
   - Instant **`हिन्दी / EN`** toggle right on the Home header (persisted across restarts in `OfflinePrefsStore`).
   - Clear bilingual **Sync Status** indicator (**Online**, **Offline**, **Syncing** with pending write count), plain-language actionable error messages telling the user what to do next, helpful empty states, confirmation dialogs for risky actions, and **Undo** snackbar support (e.g., when skipping the First-Time Setup Guide).
4. **First-Time Setup Guide (8 Auto-Ticking Steps):**
   - Guides a brand-new school from empty to its first receipt: *(1) Create school -> (2) Add logo -> (3) Create classes & sections -> (4) Add students -> (5) Set fee structure -> (6) Add teachers -> (7) Set timetable -> (8) Add a first fee receipt*.
   - Each step opens the exact screen/tab and ticks itself off automatically once the data exists. Can be skipped anytime (with **Undo**) and reopened from the Home header until all 8 steps are complete.
5. **Fast Offline Rendering:** All Today metrics are computed purely in memory from cached Firestore state via `Phase13TodayHomeEngine` with zero extra network queries.

---

## Phase 14: New Session & Promotion, Bulk Student Import, and Manual Backup ZIP

All Phase 14 features are mobile-first (`>= 48 dp` touch targets), Hindi-friendly with a 1-tap **`हिन्दी / EN`** toggle, offline-first with Firestore sync, and preserve Phases 1–13 report card layouts, marks calculations, grading rules, and all past-session records completely untouched.

1. **19th Access Module (`Session and Promotion`) & Owner/Admin Restrictions**
   - Added `sessionPromotion` (`No access` / `View only` / `View & edit`) to the member access matrix, enforced in both the Compose UI and `firestore.rules` (verified with emulator rules tests).
   - **Backward-Compatible Missing-Key Rule:** Any member document without the `sessionPromotion` key defaults to `No access`. Owners and Admins automatically receive full access.
   - **Strict Owner/Admin Gates:**
     - **Bulk Student Import** and **Backup ZIP Export** are available **only to Owner and Admins** (hidden in UI and rejected in `firestore.rules` for all other roles).
     - **`staff_salary.csv` inside Backup ZIP** is included **only for the Owner** (never included in Admin exports, enforced in both the ZIP engine and `firestore.rules`).
     - **7-Day Undo of a Session Start** is **Owner-only** (enforced in both UI and `firestore.rules`).

2. **New Session Wizard & Class Promotion (Per School, April–March)**
   - **4-Step Guided Wizard with Preview Before Applying:**
     1. Choose new session name (e.g. `2026-2027` -> `2027-2028`) and April 1 – March 31 dates.
     2. Select independently what to copy from the previous session (each with its own checkbox): Class & Section structure, Subjects per class, Fee Structure (tuition, exam fees, optional items — without copying frozen past-session locks), Period structure & timetable settings, School Calendar template (holidays automatically shifted +1 year and editable before apply), Transport routes, and Staff list.
     3. **Per-Class Promotion Review:** Proposed decisions come directly from the old session's Annual result using the **exact same `ReportCalculations.calculateStudentPerformance` 33% per-subject pass rule** (`Promoted`, `Detained`, `Left`, `Passed Out / Alumni` for the final class such as 8th, or `Needs Review` if no annual marks exist). Includes a 1-tap **"Promote All Passed"** bulk action (showing exceptions first), 1-tap individual overrides including **"Promoted by Grace"** (mandatory remark + approver recorded), and section/roll-number assignment (keep previous roll order or re-number alphabetically A–Z, with manual edits).
     4. **Final Preview & Confirm:** Displays exact student counts and **total unpaid fee dues carried forward** before writing anything.
   - **Carry-Over & Previous Session Dues:** Admission number, photo, guardian details, sibling group, and transport assignment carry over automatically. Unpaid dues from the old session never vanish — they carry forward as a clearly labelled **"Previous session dues"** line on the student's new-session Fee Account, Dues List, and Send Queue, remain collectable in full or in part, and leave the old session's receipts and totals untouched.
   - **Editable Class Order, Alumni Search, Student Session History & A4 Promotion Register PDF:** Customise class progression order per school (e.g., `Nursery -> LKG -> UKG -> 1st .. 8th -> Passed Out / Alumni`), search passed-out alumni anytime, view any student's multi-session class history right from the **Student Register**, and export an offline **A4 Promotion Register PDF** per class.
   - **Safe Chunked Batches, Offline Resume/Rollback & Conflict Avoidance:**
     - Promotion commits in atomic batches of **25 students** (`SessionTransitionRecord` tracks `completedBatches / totalBatches`). If a device shuts down or fails halfway, the wizard offers **Resume** or **Rollback** cleanly.
     - **How offline conflicts are avoided when two people promote the same class:** Every created document uses a **deterministic ID** derived from `(schoolId, toSession, admNo / oldStudentDocId)` (`stu_<school>_<toSession>_<admNo>`, `prom_<school>_<fromSession>_<toSession>_<oldStuId>`, `fov_<school>_<toSession>_<newStuId>`, `strans_<school>_<fromSession>_<toSession>`). If two staff members promote the same class offline, syncing writes to the exact same document paths (latest confirmed edit wins) — guaranteeing **zero duplicate students and zero double-counted carried-forward dues**.
   - **7-Day Owner Undo:** Within 7 days, the Owner can undo a session start to restore the previous active session. If any new-session data (marks, attendance, receipts, certificates, or newly admitted students) already exists, the app shows an explicit warning and will never delete new-session records without confirmation.

3. **Bulk Student Import (CSV & Excel `.xlsx`)**
   - Supports **both `.csv` and Excel `.xlsx`** files using a lightweight pure-Kotlin reader (`ZipInputStream` + XXE-hardened XML DOM — zero heavy third-party libraries and zero storage permissions via Android's Document Picker).
   - Provides a 1-tap downloadable **bilingual Hindi + English sample CSV** and an in-app step-by-step guide.
   - **Workflow:** Pick file -> Auto-mapped Column Mapper (Hindi + English headers) -> Preview with highlighted problems (missing name, invalid class, invalid mobile, duplicate admission no. in file/DB, duplicate student) -> Fix inline or Skip rows -> Import.
   - Normalises all Indian mobile numbers to `+91` form. Never overwrites existing students unless **"Update matching admission numbers"** is explicitly turned on (with a field-by-field diff preview). Generates both **CSV** and **A4 PDF** result reports.

4. **Manual Backup ZIP Export & 30-Day Reminder**
   - Export **Active School** or **Whole Organisation** into a single `.zip` containing `README.txt` and UTF-8 Excel-ready CSVs (`students.csv`, `guardians.csv`, `class_structure.csv`, `fee_receipts.csv`, `fee_dues.csv`, `attendance_summary.csv`, `marks.csv`, `teachers.csv`, `cash_book.csv`, `certificates_register.csv`, plus **Owner-only** `staff_salary.csv`).
   - Save directly to Downloads / Google Drive / SD card via Android's Save picker or share via WhatsApp/Email.
   - Displays **Last Backup Date** and a **30-day overdue reminder** on the Today Home screen, clearly noting that this is a manual local export while Firebase continues keeping the cloud copy automatically.

### Yearly April Checklist for the Owner (नए सत्र की वार्षिक जाँच-सूची)

1. **Take a Full End-of-Year Backup ZIP (Late March):** Open **Backup & Export ZIP**, select **Whole Organisation**, export the ZIP (includes `staff_salary.csv` for Owner), and save a copy to Google Drive or a computer.
2. **Finish Annual Marks & Print Report Cards:** Confirm all Annual exam marks and co-scholastic grades are entered and export the final A4 Annual Report Card PDFs for the closing session.
3. **Check Class Order:** Open **Session & Promotion -> Class Order** for each school and verify the progression order (e.g., `Nursery -> LKG -> UKG -> 1st .. 8th`) so the final class graduates to **Passed Out / Alumni**.
4. **Run the "Start New Session" Wizard (1 April):**
   - Pick the new session (e.g., `2027-2028`, `2027-04-01` to `2028-03-31`).
   - Tick what to copy (Classes, Subjects, Fee Structure, Timetable settings, School Calendar holidays, Transport routes, Staff list) and adjust holiday dates for the new year.
   - Review each class's promotions: tap **Promote All Passed**, resolve every **Needs Review** / **Detained** / **Grace** student (enter grace remarks where needed), and pick **Previous Roll** or **Alphabetical** roll numbering.
   - Verify the **Total Dues Carried Forward** summary and tap **Confirm & Apply**.
5. **Print Promotion Registers:** Download/print the A4 **Promotion Register PDF** for each class and file it in the school office.
6. **Import New Admissions in Bulk:** If you have a spreadsheet of newly admitted students, open **Bulk Student Import** (`.csv` or `.xlsx`), verify the preview, and import in one go.
7. **Update Fee Structure & Open Exams:** Review any new-year tuition/transport fee revisions in **Fees -> Fee Structure** and open **UT-I** for teacher marks entry when ready.

---

## Build & Test Commands

```bash
# Compile Android debug APK
gradle :app:assembleDebug

# Run JUnit permission tests + Phase 1 calculation & report tests
gradle :app:testDebugUnitTest

# Run Firestore security rules unit tests against the local Firestore emulator
firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.test.js"

# Build signed release Android App Bundle (.aab) when upload keystore env vars are configured
gradle :app:bundleRelease --no-daemon
```

---

## Phase 15: Play Store Readiness, Phone Upload Keystore & Release AAB

Phase 15 prepares **School Report Card** for Google Play production release without altering any Phase 1–14 calculations, report card layouts, module access rules, or the committed `debug.keystore` workflow (`.github/workflows/build-apk.yml`).

### What Was Added for Play Store Compliance
1. **In-App & Web Account Deletion:**
   - **Settings -> Account -> Delete my account** clearly lists what is deleted vs retained, requires re-authentication (Email/Password or Google Sign-In), removes normal members' Auth login/profile/membership/scope/`users/{uid}` while attributing historical school records to `"deleted user"`, and blocks sole Owners until they transfer ownership or explicitly confirm whole-organisation deletion (typing the organisation code).
   - Public bilingual (Hindi + English) pages in `public/`: `public/privacy.html`, `public/delete-account.html`, and `public/support.html`.
2. **In-App About & Legal Links (`Settings -> About`):**
   - Shows current app version (`BuildConfig.VERSION_NAME` / `VERSION_CODE`), contact email placeholder, and opens Privacy Policy, Support, Terms of Service, and Delete Account & Data guides in-app or via HTTPS URL configured in `app/src/main/java/com/example/domain/LegalConfig.kt`.
3. **Zero PII Logs, Cleartext Disabled, R8 & Versioning:**
   - `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`; PII removed from diagnostic logs.
   - R8 shrinking/obfuscation (`isMinifyEnabled = true`, `isShrinkResources = true`) enabled **only** for `release` builds with Firebase/Room/Credential Manager/model keep rules in `app/proguard-rules.pro`.
   - Centralized `APP_VERSION_CODE` and `APP_VERSION_NAME` in `gradle.properties` (overridable via `-PAPP_VERSION_CODE=...` / `-PAPP_VERSION_NAME=...`).
4. **Store Assets & Checklists:**
   - `/PLAY_STORE_STEPS.md` — simple Hindi step-by-step Play Console upload guide & owner checklist.
   - `/PLAY_DATA_SAFETY.md` — Play Console Data Safety questionnaire mapping.
   - `/store_listing/` — bilingual Play Store listing copy (`store_listing.md`), `icon_512x512.png`, and `feature_graphic_1024x500.png`.

### Phone-Only Upload Keystore & Signed Release AAB Workflows

You never commit `.jks` upload keystores, passwords, or `google-services.json` to Git:

1. **Generate Upload Keystore Once (From Your Phone Browser):**
   - Push the repository to GitHub (**Push to GitHub**), open **Actions -> Create Upload Keystore (One-Time) -> Run workflow**.
   - Download the 1-day private artifact (`upload-keystore-private-1day`) immediately containing `my-upload-key.jks` and `PASSWORDS_AND_BASE64.txt`.
   - Copy the **SHA-1** and **SHA-256** printed in the workflow Summary and register them in **Firebase Console -> Project Settings -> Android App**.
   - **CRITICAL KEY-LOSS WARNING:** Save `my-upload-key.jks` and `PASSWORDS_AND_BASE64.txt` in at least two secure offline/encrypted places. If you lose this upload key, you cannot publish updates to Play Store without requesting a key reset from Google Play Support.
2. **Add GitHub Repository Secrets (`Settings -> Secrets and variables -> Actions`):**
   - `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, and `GOOGLE_SERVICES_JSON`.
3. **Build Signed Release AAB:**
   - Run **Actions -> Build Signed Release AAB** (or push a `v*` tag like `v1.0.0`) and download `app-release.aab` from the workflow artifacts to upload manually to Play Console. (And once Play App Signing is active, also copy Play Console's **App signing key certificate SHA-1 & SHA-256** into Firebase Console so Google Sign-In works for Play Store users.)

---

## Phase 17: Result Analysis (Strictly Read-Only Analytics & Offline A4 PDFs)

Phase 17 adds a **read-only Result Analysis** module that computes academic analytics directly from existing cached marks and student records while reusing `ReportCalculations` (`A1`–`E` grades and `>= 33%` per-subject pass rule) with zero duplication and zero changes to marks entry or report card layouts.

1. **20th Access Module (`Result Analysis` / `resultAnalysis`):**
   - View-only module (`No access` / `View only`; missing key defaults to `No access`; Owner and Admins have full access).
   - **Teacher Scope Isolation:** Teachers with `Result Analysis: View` see analysis **only for the classes, sections, and subjects assigned to them** (`teacherScopes`), enforced in the UI, data layer (`ResultAnalysisAccessEvaluator.scopeDataForMember`), and `firestore.rules`.
2. **Seven Mobile-First Views (Filter by School, Session, Class, Section & Exam: `UT-I`, `UT-II`, `Half-Yearly`, `Yearly`, or `All`):**
   - **Class Overview:** Students appeared, pass/fail counts, pass %, class average %, highest & lowest marks, and `A1`–`E` grade distribution bars.
   - **Toppers:** Top `N` (default 10, editable) per Section, per Class, or School-wide with 3 clear selectable tie-breaking rules (`1, 2, 2, 4 • Include all tied`, `1, 2, 2, 3 • Dense + all tied`, `1, 2, 2, 4 • Strict top N`).
   - **Subject-Wise Analysis:** Sorted **weakest subject first** (by pass %, average %, fail count) with assigned subject teacher names from Phase 4.
   - **Weak Students:** Students failing one or more subjects or scoring below a configurable threshold percentage (default `40%`), listing the exact failed/low subjects.
   - **Student Progress Over the Year:** Side-by-side `UT-I`, `Half-Yearly`, `UT-II`, `Yearly`, and `Overall` scores per subject with `↑` (improving), `↓` (dropping), or `→` (steady) indicators, plus a **manual Copy / Share Guardian Progress Message** (never sent automatically).
   - **Class & Section Comparison:** Compare sections of the same class or classes across the school (`Pass %`, `Avg %`, `High`, `Low`).
   - **Missing Marks Warning & List:** Automatic warning banner and detailed missing-component list whenever marks are incomplete for the selected filter.
3. **Offline A4 PDFs & Owner/Admin CSV Export:**
   - Offline A4 Portrait PDFs (with school name & logo header) for **Class Result Summary**, **Topper List**, **Subject-Wise Analysis**, **Weak Students List**, and **Single Student Progress Sheet** with in-app preview, Print, Save, and Share.
   - Full analysis **CSV export** gated strictly to **Owner and Admins**.

---

## Phase 18: Class Diary and Birthdays

Phase 18 adds a fast daily **Class Diary** and an offline-capable **Birthdays** module while preserving Phases 1–17 unchanged.

1. **21st Access Module (`Class Diary` / `classDiary`) & Automatic Teacher Scope:**
   - Supports `No access` / `View only` / `View and edit` (missing key defaults to `No access`; Owner and Admins have full access).
   - **Zero-Setup Teacher Writing:** Any assigned **Class Teacher** (`teacherScopes/{uid}.classTeacherSections`) or **Subject Teacher** (`teacherScopes/{uid}.assignedClassSectionSubjects`) can automatically view and write the diary for their own assigned class-section and subject without any extra permission toggle, enforced in both the Compose UI and `firestore.rules`.
2. **Class Diary (`classDiaryEntries`, `classDiaryHistory`):**
   - **Sub-1-Minute Daily Entry:** Pick Class, Section, and Date (default Today), tap a subject chip (ordered with the teacher's own assigned subjects first, combined with Phase 3 class subjects), and enter **Classwork (what was taught)**, **Homework**, an optional **Note**, and an optional **Blackboard / Homework Photo** (via Android's zero-permission Photo Picker, compressed offline-safe `< 150 KB`).
   - **Author-Only Edit & Delete with Admin Override & History Kept:** Only the original author (`authorUid`) or an Admin (`OWNER` / `ADMIN`) can edit or delete an entry. Every create, edit, and delete preserves full revision history (`editHistory` + append-only `classDiaryHistory`).
   - **1-Tap "Share as Message":** Builds a clean bilingual/English WhatsApp-ready class diary summary (Date, Class-Section, each subject's classwork/homework/note, and School Name) to copy or share via the Android share sheet (never sent automatically).
   - **Weekly View, A4 Weekly Diary PDF & "Diary Not Written Today":** View the full 7-day Monday–Sunday diary for a class, export an offline **A4 Portrait Weekly Diary PDF**, and (for Owner & Admins) inspect the **"Diary not written today"** class/section list on the Today home screen and inside Class Diary.
3. **Birthdays (`schoolBirthdayConfigs`, `birthdayWishes`):**
   - Uses active Student Register records (`status != "LEFT"`) and Phase 4 teacher/staff DOBs, visible to anyone with access to `Students`, `Student Register`, or `Teachers`.
   - Lists **Today's Birthdays** and the **Next 7 Days** grouped by class (plus `Staff / Teachers`), with a count tile on the Today home screen. Correctly handles **29 February** birthdays (celebrated on **28 February** in non-leap years, **29 February** in leap years) and **December-to-January year rollover**.
   - **1-Tap WhatsApp Wishes, Copy Message, Wished Mark & Birthday Card Image/PDF:** Open `wa.me` with `+91` normalized guardian mobile and an editable template containing `{name}` and `{school}` (never automatic), copy the message, mark as **"Wished"** so nobody is wished twice, or generate a shareable **Birthday Card PNG Image or A4 PDF** with school name, logo, student/staff name, class, and wishes text.
   - **Opt-Out Controls:** Per-school toggle to turn birthday wishes off (`birthdayWishesEnabled`) and per-student / per-staff exclusion list.

---

## Phase 19: Student Remarks, Parent Meetings and Gate Pass

Phase 19 adds sensitive **Student Remarks**, **Parent-Teacher Meetings (PTM)**, **Authorised Pickup Persons**, **Early Leave Gate Passes (A5 Slip)**, and a **Visitor Register** while preserving Phases 1–18 unchanged.

1. **Two New Access Modules (`Student Remarks` / `studentRemarks` & `Gate` / `gate`, Modules #22 and #23):**
   - Both modules support `No access` / `View` / `View and edit` (missing key defaults to `No access`; Owner and Admins have full access).
   - **Sensitive Remark Visibility:** By default, only the student's **Class Teacher** (`teacherScopes/{uid}.classTeacherSections`), the **Owner**, and **Admins** can read or write a student's remarks; other teachers see nothing unless explicitly granted `studentRemarks` access. Enforced in both the UI and `firestore.rules`.
2. **Student Remarks (`studentRemarks`, `studentRemarkCategories`):**
   - **Quick Add in a Few Taps:** Pick date (default Today), category (`Praise`, `Academic`, `Discipline`, `Attendance`, `Health`, `Other` + editable custom category list per school), short note, and optional follow-up date. Records author and timestamp.
   - **No Silent Deletion (Cancel with Reason + Audit Log):** Remarks cannot be deleted; cancelling requires a reason (`status = "CANCELLED"`, `cancelledReason`, `cancelledByUid`, `cancelledByName`, `cancelledAtMs`) and is logged in the organisation audit log.
   - **Student Profile History, Follow-ups Due & Printable A4 Summary:** Shows remark history on the student's profile (only to permitted viewers), a **"Follow-ups due"** list, category counts, filters by class/category/date, and an offline **A4 Student Remark Summary PDF** for **Owner and Admins only** (with a setting to hide the summary PDF option from non-admin users).
3. **Parent Meetings (`parentMeetings`):**
   - Schedule a PTM date for a class or the whole school, pick students to call with **smart suggestions** from Result Analysis (weak students), Attendance (low attendance `< 75%`), and active remarks, and track per student: `Called`, `Attended`, `Not Attended`, discussion note, and decision.
   - Includes a ready-to-share **WhatsApp invitation message** (`wa.me` with `+91` normalised guardian mobile, never automatic) and an **A4 PTM Summary PDF**.
4. **Gate Pass, Authorised Pickup & Visitor Register (`studentAuthorizedPickups`, `gatePasses`, `visitorEntries`):**
   - **Authorised Pickup Persons:** Stored in the Student Register per student (name, relation, mobile, optional compressed photo `< 150 KB`), readable/editable as the Student Register allows and readable by Gate users when issuing a pass.
   - **Early Leave Gate Pass & A5 Pass Slip PDF:** Pick a student, reason, leave time, who is taking the child (from the authorised list or a one-time person with mandatory guardian phone confirmation tick and note), and who approved it. Generates an offline-safe **unique pass number per school and session** (`GP-<SES>-<MMDD>-<SHARD>-<SEQ>`) and an **A5 Pass Slip PDF** for preview, print, save, and share.
   - **Daily Gate Register & Optional Half-Day Attendance:** View early leaves for a date or date range, export **CSV (Owner and Admins only)**, and optionally mark the student's attendance as **Half-Day** (only when the current user has Attendance edit access for that class-section).
   - **Visitor Register & Today Home Tile:** Record visitor name, mobile, purpose, whom to meet, time in, time out, and optional photo, with one-tap **"Sign Out"** and a **Today Home tile** showing today's visitors and early leaves.

---

## Phase 16A: Organisation-Controlled Ads with Rewarded Ad-Free Time (Google AdMob)

Phase 16A adds privacy-safe, non-intrusive Google AdMob advertising for free organisations while preserving all earlier phases (features, data, report layouts, and module access rules) untouched.

1. **Organisation-Level Subscription Gate (`organisations/{orgId}.subscription`):**
   - Read-only map on each organisation document: `{ status: "none" | "active" | "grace" | "expired", plan: "...", expiresAt: Timestamp }` (default `"none"`).
   - `firestore.rules` strictly **denies every client write** to `subscription` (only the Phase 16B server will write it).
   - While `status` is `active` or `grace`, **ZERO ads of any kind** (no banner, no interstitial, no rewarded offer, and no "watch an ad" buttons) are shown to any member of that organisation.
   - **Debug-only local switch:** In `BuildConfig.DEBUG` builds only, **Settings** includes a hidden developer card to locally simulate subscription `ON / OFF` and bypass the 24-hour first-install grace window. It is stripped from release builds.
   - **Settings "Remove ads for everyone" card:** Shows **"Coming soon"** (Phase 16B) for the Organisation `OWNER`, and **"Ask your school owner to remove ads"** for all other members.
2. **Strict Placement Allowlist & Anti-Disruption Rules:**
   - **Allowed banners (fixed `60 dp` reserved height — zero layout shift; collapses to `0 dp` when offline or ineligible):** Bottom of **Today Home**, **Organisation Chooser**, and **School Chooser** only.
   - **Allowed optional interstitial:** Only after closing a **finished** PDF preview, capped at **1 per 15 minutes** and **3 per UTC day per device**, non-blocking if not preloaded.
   - **Strictly forbidden everywhere else:** Login/sign-up, all student/guardian/fee/salary/cash book/marks/attendance/transport/data-entry screens, receipts, certificates, open PDF previews, settings, account deletion, and inside any printed or shared PDF.
   - **Zero ads in the first 24 hours** after first install, **zero ads offline**, and 100% usable if ads fail, are blocked, or Google Play Services is absent.
3. **Optional Rewarded Ad-Free Time Ladder (Free Orgs Only):**
   - Optional card (**"Watch ads to remove ads for a while"**) with a short explanation dialog before any ad starts. Closing early grants nothing.
   - **24-hour ladder (server-time based):** `1 ad` = ad-free until `now + 2h`; `2 ads` = until `firstAd + 3h`; `3 ads` = until `firstAd + 6h`; `4 ads` = until `firstAd + 24h`. After 4 ads, no further offers appear until the ad-free time expires. Capped at **8 rewarded ads per user per day**.
   - Persisted on `organisations/{orgId}/members/{uid}` (`adFreeUntil`, `adsWatchedWindow`) using Firestore server timestamps, bounded in `firestore.rules` (own member doc only, `adFreeUntil <= request.time + 24h`, monotonic extension only, count cannot decrease), and cached offline using the last known server clock offset.
   - **Honest Security Note (Phase 16A vs Phase 16B):** Because Phase 16A has no backend server yet, Firestore rules bound the maximum abuse window (`<= 24 hours` and monotonic counts), but **a determined technical user could still craft a direct Firestore client write to extend their own `adFreeUntil` without actually watching a rewarded ad**. **Phase 16B** closes this completely by verifying rewarded ad completions on the server via **AdMob Server-Side Verification (SSV)** and moving `adFreeUntil` / `subscription` writes strictly to the backend.
4. **Configuration, Remote Kill-Switch, Consent & Zero-PII Guarantee:**
   - Debug builds and unit tests use **only Google's official test ad unit IDs**. Release builds read GitHub Secrets `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, and `ADMOB_REWARDED_ID` (if missing in release, ads are cleanly disabled with no crash).
   - Per-placement remote kill-switch document `appConfig/adsConfig` (readable by signed-in users, writable only by the configured App Owner account).
   - Workplace/all-audiences ad config (`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`, `MAX_AD_CONTENT_RATING_G`), Google UMP consent flow, **"Privacy and ad choices"** in Settings, and **zero student, guardian, staff, or school PII** passed to the ad SDK.
   - Full Hindi setup instructions in `/ADMOB_STEPS.md`, Play Data Safety mapping in `/PLAY_DATA_SAFETY.md`, and updated Privacy Policy in `/public/privacy.html`.
   - **Run Phase 16A/16B Firestore Rules Tests:**
     ```bash
     firebase emulators:exec --only firestore --project demo-no-project "node --test firestore.ads.test.js"
     ```

---

## Phase 16B: School Subscription (Google Play Billing), Cloud Functions Verification & Ad Rule Tuning

Phase 16B adds Google Play school subscriptions verified strictly on the server (Firebase Cloud Functions in `asia-south1`), tunes Phase 16A ad rules, upgrades rewarded ads to support zero-PII AdMob Server-Side Verification (SSV), and provides a phone-friendly GitHub Actions deployment workflow.

1. **Tuned Ad Rules (Equal Across Every Member Role):**
   - **Interstitial cap:** At most **once per 8 minutes** and **8 per UTC day per device**, shown only after finishing a task (closing a finished PDF preview or returning from a list), never mid-task.
   - **Banners (strictly ONE per screen, never two):** Today Home, Organisation Chooser, School Chooser, **Student List**, **Teacher List** (Teachers tab only), **Report List** (hidden when PDF preview is open), and **Timetable View** (view tabs only, hidden on Leave/Subs, dialogs, or open PDF). All data-entry and sensitive screens remain strictly forbidden.
   - **Equal rules for everyone:** While the organisation has no active subscription, every member role (`OWNER`, `ADMIN`, `TEACHER`, `CLERK`, `STAFF`) follows identical ad rules. Rewarded ad-free time (**विज्ञापन-रहित समय** in Hindi) stays per user (`1=2h`, `2=3h`, `3=6h`, `4=24h`, max `8/day`).
2. **Google Play School Subscription (`school_ad_free`):**
   - Single subscription product `school_ad_free` with strictly two base plans: `monthly` and `yearly`.
   - Prices and billing periods are **always read dynamically from Google Play** (never hard-coded); yearly savings are computed dynamically from live Play prices.
   - **Owner Screen (`Settings → Remove ads for my school`):** Replaces Phase 16A's `"Coming soon"` card. Shows current status (`none`, `active` with renewal date, `grace`, `on_hold`, `cancelled_active` with valid-until date, `expired`, `revoked`), dynamic plan cards, **Subscribe**, **Manage subscription**, **Restore purchase**, required Google Play subscription terms, and Privacy Policy/Terms links. Non-owners see only a status line (`"Your school is ad-free"` or `"Ask your school owner to remove ads"`).
   - Removes all banners, interstitials, rewarded offers, and `"Watch an ad"` buttons for every member of the organisation. All school work features stay 100% free and never locked.
3. **Server-Side Verification (`functions/`, TypeScript, region `asia-south1`):**
   - **`verifyPurchase` (callable):** Verifies caller is active Organisation `OWNER`, calls Google Play Developer API v3 (`subscriptionsv2.get`), enforces `obfuscatedExternalAccountId == orgId`, prevents double-claiming across two organisations via server-only `subscriptionPurchaseLocks/{sha256(token)}`, acknowledges purchases on Play, and writes `organisations/{orgId}.subscription { status, plan, expiresAt, autoRenewing, updatedAt, purchaseTokenHash }` via Admin SDK (never storing raw tokens where clients can read them).
   - **`handlePlayRtdn` (Pub/Sub topic `play-billing-rtdn`):** Processes Google Play Real-Time Developer Notifications for renewals, cancellations, grace period, account hold, expiry, refunds, and revocations.
   - **Offline & Resilience:** Pending purchases show a clear pending state; offline purchases queue locally and verify automatically on reconnect; lapsed subscriptions show a short dismissible notice to the Owner; if the server is temporarily unreachable, the app falls back to the last cached subscription status without locking features.
   - **AdMob Rewarded SSV (`createRewardedAdNonce` + `admobRewardedSsv`):** Generates an opaque random single-use nonce (zero PII passed to AdMob) and verifies Google's ECDSA-SHA256 signature with `transaction_id` replay deduplication. Until the SSV Callback URL is configured in AdMob Console and `appConfig/adsConfig.requireServerVerifiedRewards` is set to `true`, the Phase 16A client-bounded Firestore rules remain active as a non-breaking fallback.
4. **Phone-Only Firebase Deployment & Setup Guide:**
   - **`.github/workflows/deploy-firebase.yml` ("Deploy Firebase"):** Manual `workflow_dispatch` workflow that builds & tests `functions/` and deploys `firestore:rules`, `firestore:indexes`, and `functions` using `FIREBASE_SERVICE_ACCOUNT_JSON` and `FIREBASE_PROJECT_ID` secrets — **replacing the earlier manual Firestore rules deployment step**.
   - Complete sequential Hindi setup guide in **`/PLAY_BILLING_STEPS.md`**.
   - **Run Cloud Functions Unit Tests:**
     ```bash
     cd functions && npm install && npm run build && npm test
     ```



