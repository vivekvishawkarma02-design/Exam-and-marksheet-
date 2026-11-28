# Google Play Console — Data Safety Questionnaire Guide (`School Report Card`)

Use this mapping 1:1 when filling **App content → Data safety** and **App content → Ads** in Google Play Console. Every answer below is derived strictly from the libraries and code in this repository (`firebase-auth`, `firebase-firestore`, `firebase-functions`, `android-billing-ktx` (Google Play Billing), `androidx.credentials`, `googleid`, `play-services-ads` (Google Mobile Ads / AdMob), and `user-messaging-platform` (Google UMP); **no Firebase Analytics, no Firebase Crashlytics**).

---

## 0. Play Console App Content — Ads Declaration

| Play Console Question | Exact Answer | Reason in Code |
|---|---|---|
| Does your app contain ads? | **Yes** | Free organisations see limited workplace-safe Google AdMob banners (Today home, Organisation chooser, School chooser, Student list, Teacher list, Report list, Timetable view), an optional task-finished interstitial (capped at 1 per 8 min, max 8/day), and optional rewarded ads for ad-free time (विज्ञापन-रहित समय). Paid organisations (`active`, `grace`, `on_hold`, `cancelled_active` before expiry) see **zero** ads. |
| Does your app use the Advertising ID? | **Yes** (`Advertising or marketing`) | `com.google.android.gms.permission.AD_ID` is declared for Google Mobile Ads SDK (`play-services-ads`). No personal school/student/staff data is attached to ad requests (AdMob SSV uses only an opaque random single-use nonce). |
| Is your app directed to children? | **No** (Adults / School Staff only) | Ad requests are tagged `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` and `MAX_AD_CONTENT_RATING_G` (`AdMobRequestFactory`). |

---

## 1. Data Collection and Security Overview

| Play Console Question | Exact Answer | Reason in Code |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Staff sign in with Firebase Auth and store school/user records in Cloud Firestore; School Owners may optionally purchase an ad-free school subscription via Google Play Billing verified by Firebase Cloud Functions; free organisations also load workplace-safe ads via Google Mobile Ads (AdMob) SDK. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Firebase Auth, Firestore, Cloud Functions, Google Play Billing, Credential Manager, Google Mobile Ads SDK, and Google UMP communicate exclusively over HTTPS/TLS (`android:usesCleartextTraffic="false"` in `AndroidManifest.xml`). |
| Which deletion method do you provide? | **In-app deletion + Web deletion link** | In-app `Settings → Account → Delete My Account` + hosted `public/delete-account.html` URL. |

> **Data "Collected" vs. "Shared"**:
> - **School, student, guardian, staff, marks, attendance, fee, salary, photo, and subscription status records**: Mark **Collected = Yes**, **Shared = No**. Google Firebase acts strictly as our cloud backend processor. **Zero** school, student, guardian, or staff PII (no UIDs, emails, names, phone numbers, or school records) is ever passed to the AdMob SDK (verified by `Phase16AdsDomainTest` and code audit of `com.example.ads`).
> - **Google Play Billing (Optional school ad-free subscription)**: All payment cards, UPI, and billing details are processed strictly by **Google Play** — our app and servers NEVER see or store card/UPI numbers. Our Cloud Function verifies the Play purchase token with Google Play Developer API and stores only a one-way SHA-256 hash (`purchaseTokenHash`) plus status/plan/expiry date. Mark **Financial info → Purchase history** as **Collected = Yes**, **Shared = No**, Purpose = **App functionality**, **Account management**, **Fraud prevention, security, and compliance**.
> - **Google Mobile Ads SDK (AdMob) telemetry (Free organisations only)**: Mark **Device or other IDs (Advertising ID)** and **App activity / App info and performance (Ad interactions, crash/diagnostic logs collected automatically by Google Mobile Ads SDK)** as **Collected = Yes**, **Shared = Yes** (with Google LLC as the advertising provider), Purpose = **Advertising or marketing**, **Fraud prevention, security, and compliance**, and **Analytics** (SDK ad performance). Users can opt out via Android Settings (`Settings → Google → Ads`) and Google UMP (**Settings → Privacy and ad choices**).

---

## 2. Data Types Checklist

Check **ONLY** the data types below (leave Precise Location, Payment info / credit card numbers, Contacts, Health/Fitness, Web browsing, Calendar, Messages/SMS unread, Files beyond user-picked images **unchecked**):

| Category | Play Data Type | Collected? | Shared? | Required or Optional? | Purpose | Code Reference |
|---|---|---|---|---|---|---|
| **Personal info** | **Name** | Yes | No | Required | Account management & staff/student/guardian/authorised pickup/visitor records inside the authorised school organisation | `OrgCloudRepository`, `StudentsScreen`, `Phase9StudentRegisterScreen`, `Phase19GateAndVisitorScreen` |
| **Personal info** | **Email address** | Yes | No | Required | Account login/authentication, verification, and optional school contact details | `GoogleAuthHelper`, `AuthAndOnboardingScreens`, `OrgCloudRepository` |
| **Personal info** | **User IDs** | Yes | No | Required | Firebase UID for organisation isolation, security rules, and audit logs (never sent to AdMob) | `firestore.rules`, `OrgCloudRepository` |
| **Personal info** | **Phone number** | Yes | No | Optional (user/staff-entered) | Optional teacher profile mobile & student/guardian/enquiry/pickup/visitor contact records managed by authorised staff | `TeacherProfileOnboardingScreen`, `Phase9StudentRegisterScreen`, `Phase10EnquiryScreen`, `Phase19GateAndVisitorScreen` |
| **Personal info** | **Other info** (student academic / attendance / class diary / birthday wishes / student remarks / PTM notes / gate pass / visitor / fee / salary records entered by school staff) | Yes | No | Optional / App functionality | App functionality: academic marks, daily attendance, class diary & birthday reminders, sensitive student remarks & PTM notes/decisions, early leave gate passes, visitor register, fee receipts, cash book, and payroll management for the school organisation | `Phase4–19` repositories |
| **Financial info** | **Purchase history** (organisation ad-free subscription status, plan ID `monthly`/`yearly`, expiry date, and SHA-256 hash of Play purchase token — NEVER raw card/UPI info) | Yes | No | Optional (only if School Owner subscribes via Google Play) | App functionality (removing ads for all school members), Account management, Fraud prevention, security, and compliance | `Phase16PlayBillingManager`, `functions/src/subscriptionLogic.ts` |
| **Photos and videos** | **Photos** | Yes | No | Optional | School logos, teacher/student profile avatars, expense bill photos, optional class diary blackboard/homework photos, and optional authorised pickup person or visitor photos selected intentionally via Android Photo Picker (`<150 KB` JPEG Base64) | `ActivityResultContracts.PickVisualMedia`, `ImageUtils` |
| **App activity** | **App interactions** (ad impressions/clicks/rewarded completions by Google Mobile Ads SDK; in-app audit logs in Firestore) | Yes | Yes (AdMob SDK ad interactions only; school audit logs = No) | Optional / Required | Advertising or marketing (AdMob SDK on free orgs), App functionality, Fraud prevention, security, and compliance | `Phase16AdMobController`, `OrgCloudRepository` |
| **App info and performance** | **Diagnostics / Crash logs** (collected automatically by Google Mobile Ads SDK for ad delivery & fraud prevention) | Yes | Yes (Google Mobile Ads SDK only) | Optional (only when ads are enabled on free orgs) | Advertising or marketing, Fraud prevention, security, and compliance, App functionality | `play-services-ads` (`Phase16AdMobController`) |
| **Device or other IDs** | **Device or other IDs** (Android Advertising ID via Google Mobile Ads SDK; local offline receipt node code `A1`–`Z9`) | Yes | Yes (Advertising ID by Google Mobile Ads SDK; local node code = No) | Optional (Advertising ID can be reset/deleted in Android Settings; node code required for offline receipts) | Advertising or marketing, Fraud prevention (AdMob SDK); App functionality (offline fee receipt collision prevention) | `Phase16AdMobController`, `OfflinePrefsStore.getOrCreateDeviceNodeCode` |
| **Location** | **Approximate location** (IP-derived coarse region used automatically by Google Mobile Ads SDK for ad serving & UMP consent region check) | Yes (SDK IP-based only; no GPS permission) | Yes (Google Mobile Ads SDK only) | Optional (only when ads load on free orgs) | Advertising or marketing, Fraud prevention, security, and compliance | `play-services-ads`, `user-messaging-platform` |

### Explicitly NOT Collected (Do NOT check these)
- **Precise Location / GPS**: No `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` permissions.
- **Credit/Debit Card or Bank Credentials (User payment info)**: Google Play handles all subscription payment methods directly; fees & salaries are recorded as accounting slips only.
- **Firebase Analytics / Crashlytics**: Not included in the app.
- **Contacts / SMS / Call Logs**: Uses standard share intents / `wa.me` link launcher when staff explicitly choose to open WhatsApp; never reads device contacts or SMS inbox.

---

## 3. Data Usage & Handling Answers (Per Checked Type)

- **Is this data processed ephemerally?** → **No** (school records and subscription entitlement status are stored in Cloud Firestore while the organisation/account is active; AdMob SDK data is handled under Google's Privacy Policy).
- **Data usage purposes:**
  - **Name, Email address, User IDs, Phone number**: `App functionality`, `Account management` (**Shared = No**).
  - **Purchase history (school subscription status)**: `App functionality`, `Account management`, `Fraud prevention, security, and compliance` (**Shared = No**).
  - **Photos, Student/Academic/Fee/Salary records, Device node ID**: `App functionality` (**Shared = No**).
  - **In-app audit log actions**: `App functionality`, `Fraud prevention, security, and compliance` (**Shared = No**).
  - **Advertising ID, Ad interactions, SDK Diagnostics, IP-based Approximate location (Google Mobile Ads SDK)**: `Advertising or marketing`, `Analytics` (ad performance), `Fraud prevention, security, and compliance` (**Shared = Yes**, free orgs only; completely inactive for subscribed orgs and while offline).

---

## 4. Play Console Policy Cross-Checks & Owner Decisions

1. **Account Deletion Link**: Host `public/delete-account.html` and paste its HTTPS URL into Play Console's *Delete account URL* field.
2. **Privacy Policy Link**: Host `public/privacy.html` (updated with Google AdMob & UMP disclosures) and paste its HTTPS URL into Play Console's *Privacy policy URL* field.
3. **AdMob Sensitive Categories Blocking**: Follow `/ADMOB_STEPS.md` in AdMob Console to block **Gambling, Dating, Alcohol, Political, Religious, and Adult** ad categories before publishing.
4. **Owner Decision Needed**:
   - Confirm whether your school staff enter **Staff Salary / Aadhaar-free payroll notes** and confirm you do not enter government-ID numbers like Aadhaar unless strictly necessary (avoid storing sensitive government IDs if not needed).
   - Update `[YOUR_CONTACT_EMAIL]` and `[YOUR_HOST_DOMAIN]` in `public/*.html` and `LegalConfig.kt`.
   - Have a legal/privacy reviewer check your hosted Privacy Policy if your state/country board requires specific wording.
