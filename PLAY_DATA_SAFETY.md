# Google Play Console — Data Safety Questionnaire Guide (`School Report Card`)

Use this mapping 1:1 when filling **App content → Data safety** in Google Play Console. Every answer below is derived strictly from the libraries and code in this repository (`firebase-auth`, `firebase-firestore`, `androidx.credentials`, `googleid`; **no Firebase Analytics, no Crashlytics, no Ads SDK**).

---

## 1. Data Collection and Security Overview

| Play Console Question | Exact Answer | Reason in Code |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Staff sign in with Firebase Auth and store school/user records in Cloud Firestore. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Firebase Auth, Firestore, and Credential Manager communicate exclusively over HTTPS/TLS (`android:usesCleartextTraffic="false"` in `AndroidManifest.xml`). |
| Which deletion method do you provide? | **In-app deletion + Web deletion link** | In-app `Settings → Account → Delete My Account` + hosted `public/delete-account.html` URL. |

> **Data "Collected" vs. "Shared"**: Mark collected types as **Collected = Yes**, **Shared = No**. Google Firebase acts strictly as our cloud backend/storage processor; we do not transfer user data to third-party advertisers, brokers, or unrelated services.

---

## 2. Data Types Checklist

Check **ONLY** the data types below (leave Location, Financial payment card numbers, Contacts, Health/Fitness, Web browsing, Calendar, Messages/SMS unread, Files beyond user-picked images **unchecked**):

| Category | Play Data Type | Collected? | Shared? | Required or Optional? | Purpose | Code Reference |
|---|---|---|---|---|---|---|
| **Personal info** | **Name** | Yes | No | Required | Account management & staff/student/guardian/authorised pickup/visitor records inside the authorised school organisation | `OrgCloudRepository`, `StudentsScreen`, `Phase9StudentRegisterScreen`, `Phase19GateAndVisitorScreen` |
| **Personal info** | **Email address** | Yes | No | Required | Account login/authentication, verification, and optional school contact details | `GoogleAuthHelper`, `AuthAndOnboardingScreens`, `OrgCloudRepository` |
| **Personal info** | **User IDs** | Yes | No | Required | Firebase UID for organisation isolation, security rules, and audit logs | `firestore.rules`, `OrgCloudRepository` |
| **Personal info** | **Phone number** | Yes | No | Optional (user/staff-entered) | Optional teacher profile mobile & student/guardian/enquiry/pickup/visitor contact records managed by authorised staff | `TeacherProfileOnboardingScreen`, `Phase9StudentRegisterScreen`, `Phase10EnquiryScreen`, `Phase19GateAndVisitorScreen` |
| **Personal info** | **Other info** (student academic / attendance / class diary / birthday wishes / student remarks / PTM notes / gate pass / visitor / fee / salary records entered by school staff) | Yes | No | Optional / App functionality | App functionality: academic marks, daily attendance, class diary & birthday reminders, sensitive student remarks & PTM notes/decisions, early leave gate passes, visitor register, fee receipts, cash book, and payroll management for the school organisation | `Phase4–19` repositories |
| **Photos and videos** | **Photos** | Yes | No | Optional | School logos, teacher/student profile avatars, expense bill photos, optional class diary blackboard/homework photos, and optional authorised pickup person or visitor photos selected intentionally via Android Photo Picker (`<150 KB` JPEG Base64) | `ActivityResultContracts.PickVisualMedia`, `ImageUtils` |
| **App activity** | **Other actions** (audit logs inside the organisation) | Yes | No | Required | Security, fraud prevention, and edit-history transparency (`markEditLogs`, `auditLog`, `feeAuditLogs`, `cashBookAuditLogs`, `salaryAuditLogs`, `classDiaryHistory`, `studentRemarks` cancellation audit) | `OrgCloudRepository`, `Phase9–19` |
| **Device or other IDs** | **Device or other IDs** (installation node code `A1`–`Z9`) | Yes | No | Required (offline fee receipt numbering) | Offline fee receipt collision prevention via locally derived short node code in SharedPreferences (no Advertising ID / SSAID collected) | `OfflinePrefsStore.getOrCreateDeviceNodeCode` |

### Explicitly NOT Collected (Do NOT check these)
- **Precise / Approximate Location**: No location permissions or SDKs.
- **Credit/Debit Card or Bank Credentials**: Fees & salaries are recorded as accounting receipts/slips only; the app does **not** process online card/UPI payments inside the app.
- **Advertising ID / Analytics / Crashlytics**: No Google Mobile Ads, no Firebase Analytics, no Firebase Crashlytics.
- **Contacts / SMS / Call Logs**: Uses standard share intents / `wa.me` link launcher when staff explicitly choose to open WhatsApp; never reads device contacts or SMS inbox.

---

## 3. Data Usage & Handling Answers (Per Checked Type)

- **Is this data processed ephemerally?** → **No** (stored in Cloud Firestore while the organisation/account is active).
- **Data usage purposes:**
  - **Name, Email address, User IDs, Phone number**: `App functionality`, `Account management`.
  - **Photos, Student/Academic/Fee/Salary records, Device node ID**: `App functionality`.
  - **In-app audit log actions**: `App functionality`, `Fraud prevention, security, and compliance`.

---

## 4. Play Console Policy Cross-Checks & Owner Decisions

1. **Account Deletion Link**: Host `public/delete-account.html` and paste its HTTPS URL into Play Console's *Delete account URL* field.
2. **Privacy Policy Link**: Host `public/privacy.html` and paste its HTTPS URL into Play Console's *Privacy policy URL* field.
3. **Owner Decision Needed**:
   - Confirm whether your school staff enter **Staff Salary / Aadhaar-free payroll notes** and confirm you do not enter government-ID numbers like Aadhaar unless strictly necessary (avoid storing sensitive government IDs if not needed).
   - Update `[YOUR_CONTACT_EMAIL]` and `[YOUR_HOST_DOMAIN]` in `public/*.html` and `LegalConfig.kt`.
   - Have a legal/privacy reviewer check your hosted Privacy Policy if your state/country board requires specific wording.
