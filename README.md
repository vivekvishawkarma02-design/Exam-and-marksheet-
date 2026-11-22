# School Report Card (Universal Multi-School & Multi-Tenant Cloud App)

A native Android application (**Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication**, **Cloud Firestore**, **Room SQLite**, and **Android PdfDocument**) designed for multi-school organisations to manage report cards, students, marks, co-scholastic grades, and print-ready A4 PDFs with strict per-organisation isolation.

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

## Firebase Setup (`google-services.json`)

Keep private Firebase credentials out of public source control where possible:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/).
2. Register your Android app with your `applicationId` (see `app/build.gradle.kts`) and add your debug & release **SHA-1 / SHA-256** signing fingerprints.
3. Download **`google-services.json`** and place it at `app/google-services.json` (add `app/google-services.json` to `.gitignore` in private/public Git repositories so keys are not committed).
4. Enable **Authentication** (Google Sign-In; optionally Email/Password) and **Cloud Firestore**.
5. Deploy the security rules included in this repository:
   ```bash
   firebase deploy --only firestore:rules
   ```

---

## Building a Signed Android App Bundle (`.aab`) for Google Play Store

Google Play requires a release-signed **Android App Bundle (`.aab`)** rather than a debug APK:

1. **Create a Release Upload Keystore** (store this file and its passwords safely offline; never commit it to Git):
   ```bash
   keytool -genkey -v -keystore release-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. **Register Release SHA-1 / SHA-256 in Firebase:**
   - Run `keytool -list -v -keystore release-upload.jks -alias upload` and add the release SHA-1 and SHA-256 to your Firebase Android App settings (and copy the Google Play App Signing SHA-1 from Play Console once enrolled), then re-download `google-services.json`.
3. **Build the Signed AAB:**
   - **Android Studio:** Go to **Build -> Generate Signed App Bundle / APK -> Android App Bundle**, choose `release-upload.jks`, select the `release` build variant, and generate `app/release/app-release.aab`.
   - **Command Line:** Configure your release signing credentials via environment variables / Gradle properties and run:
     ```bash
     gradle :app:bundleRelease
     ```
4. **Google Play Console Compliance Checklist (Required Before Publishing):**
   - **Privacy Policy URL:** Host your public Privacy Policy page (matching the in-app Privacy Policy screen) and paste the HTTPS URL into **Play Console -> App content -> Privacy policy**.
   - **Data Safety Form:** Declare collection of user account identifiers (name, email), school & student educational records, and optional photos stored in Google Cloud Firestore, encrypted in transit, and not sold to third parties.
   - **Account Deletion Support:** Declare that users can delete their account in-app (**Settings -> Delete My Account**, which is blocked if they still own an organisation until ownership is transferred or the organisation is deleted) and provide a matching web deletion-request URL in Play Console.
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
