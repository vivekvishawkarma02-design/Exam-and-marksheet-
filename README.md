# CPI-CPS Report Card

An offline-first native Android application (Kotlin, Jetpack Compose, Material 3, Room SQLite, Android PdfDocument) built for **C.P.S. SCHOOL** (Nursery – 5th) and **C.P.I. SCHOOL** (6th – 8th), Harikaranpur Bijaura Ghazipur (U.P.).

No backend, no login, no API key, and no internet permission required.

## Core Capabilities

1. **Dual School Identities (Auto-selected by Class)**
   - **Nursery, LKG, UKG, 1st – 5th** automatically print with **C.P.S. SCHOOL** branding, logo, address, mobile, and e-mail.
   - **6th – 8th** automatically print with **C.P.I. SCHOOL** branding, logo, address, mobile, and e-mail.
   - Official C.P.S. and C.P.I. logos are bundled as defaults, with Settings support for uploading custom logos, switching to a `"LOGO"` placeholder box, sharing one logo across both schools, and editing the class-to-school mapping.

2. **Exact Class Groups & Editable Subjects**
   - **Nursery – UKG (7 subjects, 700/term):** `HINDI`, `HINDI (ORAL)`, `ENGLISH`, `ENGLISH (ORAL)`, `MATHS`, `MATHS( ORAL)`, `RHYNES`
   - **1st – 5th (6 subjects, 600/term):** `ENGLISH`, `HINDI`, `MATH`, `SCIENCE`, `COMPUTER`, `G.K.`
   - **6th – 8th (8 subjects, 800/term):** `ENGLISH`, `HINDI`, `MATHS`, `SCIENCE / EVS`, `COMPUTER`, `S.ST.`, `G.K.`, `SANSKRIT`

3. **Unified 10 / 30 / 60 Exam Structure (Both Terms)**
   - Every subject is 100 marks per term: **Internal Assessment (10)** + **U.T. (30)** + **Exam (60)** = **100** (Annual Grand Total = **200** per subject).
   - Strict marks validation (`0` to max or `AB` case-insensitively; `AB` counts as `0` and prints as `AB`), auto-save with Undo, and live totals, percentage, 8-point grading (`A1`..`E (Need improvement)`), Pass/Fail (`>= 33%` in every subject), attendance totals, and class rank.

4. **A4 Vector PDF & Batch Class ZIP Export**
   - **UT1, UT2 & Half-Yearly Slips:** A4 Landscape (`297 × 210 mm`, 6 mm margin) with two A5 portrait slips side-by-side and a dashed cut line — supports `2 per page`, `1 per page`, and `Pair (UT + H.Y.)` mode.
   - **Annual Report Card:** A4 Portrait (`210 × 297 mm`, 8 mm margin) guaranteed single-page fit for 6, 7, or 8 subjects, with configurable Photo & Admission No. visibility (off by default) and Class Rank toggle.
   - **Batch Export:** Generate one combined A4 PDF in roll-number order or a **Class ZIP** containing one individual PDF per student, with live preview, Android Print, Save (SAF), and Share (`FileProvider`).

5. **Offline JSON Backup, CSV Import & Sample Data**
   - Export/Share complete JSON backups, restore with **Merge** or **Replace All**, import class rosters via CSV, and preview immediately with preloaded sample students (`Nursery`, `2nd`, `6th`).

## Build & Test Commands

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```
