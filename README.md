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
