# AI Studio prompt: C.P.I. School Report Card app (Android)

## Pehle ye padhiye (prompt ke bahar ki baatein)

- AI Studio ka Build mode aam taur par **web app** (React) banata hai, native Android app nahi. Isliye prompt aisa likha hai ki **mobile-first, offline chalne wala installable app (PWA)** bane. Android Chrome mein "Add to Home screen" karne par ye app ki tarah khulta hai. Ye maine AI Studio mein chalakar nahi dekha, isliye jo app bane use pehle test data par check kijiye.
- Agar aapko Play Store wali asli native app chahiye, to is file ke aakhir mein **Prompt B (native Kotlin)** diya hai.
- Prompt ko neeche "PROMPT A" se "END OF PROMPT A" tak copy karke AI Studio ke Build box mein paste kijiye. Pehli baar mein sab ek saath na ban paaye to usse hissa-hissa karke banwaiye (neeche "Step-by-step order" dekhiye).
- Data phone mein hi rehta hai (browser ka IndexedDB). **Browser data clear karne par data mit jaata hai**, isliye app mein backup (JSON export) rakha hai. Mahine mein ek baar backup zaroor lijiye.

---

# PROMPT A (web / installable Android app)

You are an expert React + TypeScript developer and mobile UI designer. Build a complete, production-quality, **mobile-first, offline-first Progressive Web App** called **"CPI Report Card"** for **C.P.I. SCHOOL, Harikaranpur Bijaura-Ghazipur (U.P.)**. Teachers will use it on Android phones to enter student marks **once** and automatically generate UT1, UT2, Half-Yearly (Term-I) and Annual (Consolidated) report cards as printable A4 PDFs.

## 1. Tech stack and rules
- React + TypeScript + Vite, Tailwind CSS, `react-router` (hash routing).
- Local database: **IndexedDB via Dexie**. No backend, no login, no network needed. Everything works offline.
- PWA: web manifest (name "CPI Report Card", theme color #1F3A5F, standalone display, icons) and a service worker so it installs on Android and works offline.
- PDF/print: build each report as an HTML component with print CSS (`@page { size: A4; margin: 10mm }`) and export with the browser print dialog ("Save as PDF"). Also offer a "Download PDF" button using `jspdf` + `html2canvas` where it works.
- No Gemini or other external API is required. Do not hard-code any API key.
- Keep business logic (totals, grades, results) in pure functions in `src/lib/calc.ts` with unit tests (Vitest).
- All user-visible text in simple English. Support Hindi names for students (Unicode, Noto Sans Devanagari font fallback in reports).

## 2. Data model (Dexie tables)
- `settings`: id, schoolName ("C.P.I. SCHOOL"), address ("Harikaranpur Bijaura-Ghazipur (U.P.)"), mobile, logoDataUrl, currentSession ("2026-2027"), passPercent (33).
- `students` (one per child per session): id, session, admNo, name, fatherName, motherName, dob, mobile, address, photoDataUrl, classId, section, rollNo. Unique: (session, admNo).
- `classes`: Nursery, LKG, UKG, I, II, III, IV, V, VI, VII, VIII with a `group`: 1 = Nursery-UKG, 2 = I-V, 3 = VI-VIII.
- `subjects` per class group, in this exact printed order:
  - Group 1 (7 subjects, max total 700): HINDI, HINDI (ORAL), ENGLISH, ENGLISH (ORAL), MATHS, MATHS( ORAL), RHYNES
  - Group 2 (6 subjects, max total 600): ENGLISH, HINDI, MATHS, SCIENCE / EVS, COMPUTER, G.K.
  - Group 3 (8 subjects, max total 800): ENGLISH, HINDI, MATHS, SCIENCE / EVS, COMPUTER, S.ST., G.K., SANSKRIT
  - The subject list for each class group must be editable in Settings (add, rename, reorder, remove).
- `marks`: one row per (studentId, subject, component). Marks are stored as a **string**: a number, "AB" (absent) or empty (not entered).
- `coScholastic`: (studentId, termNo 1|2, areaCode, grade A|B|C). Areas: Skill; Work Education (Or Pre-Vocational Education); Art Education; Health and Physical Education; Discipline.
- `termInfo`: (studentId, termNo, daysPresent, workingDays, remarks, promotedTo, reportDate).
- `termConfig`: (session, termNo, scheme "A"|"B") (see section 3).

## 3. Exam structure: the key rule (read carefully)
Each subject is worth 100 per term. Two ways to split the 100:
- **Scheme A (Half-Yearly slip):** Internal Assessment (max 10) + U.T. 1st (max 30) + H.Y. Exam (max 60).
- **Scheme B (Annual card):** Note Book (5) + Subject Enrich (5) + Periodic Test (10) + Exam (80).

These two cannot be converted into each other, so:
- Settings has a toggle per session: **Term-I scheme = A or B**. Term-II is always scheme B.
- The marks-entry screen shows only the components of the chosen scheme. Switching the scheme never deletes the other scheme's marks; they are ignored until switched back.
- **UT1** = the 30-mark U.T. 1st (scheme A) or the 10-mark Periodic Test of Term-I (scheme B). **UT2** = the 10-mark Periodic Test of Term-II.
- Term-I subject total = sum of the active scheme's components. Term-II subject total = NB + SE + PT + Exam.

## 4. Calculations (pure functions, unit-tested)
- "AB" or empty counts as 0 in totals. "AB" must still print as "AB" on reports.
- Validation: a mark must be a number from 0 to the component max, or "AB" (case-insensitive, store as "AB"). Reject anything else with a clear inline error and do not save the invalid value. Accept "0".
- Subject Grand Total (Annual) = Term-I total + Term-II total (max 200).
- Term totals = sum over the student's class subjects (max = 100 x number of subjects).
- Percentage = total / max x 100, rounded to 2 decimals. Never divide by zero (show 0.00).
- Overall total = Term-I + Term-II (max 200 x subjects: 1400 / 1200 / 1600).
- **Grade (8-point scale)**, applied to a percentage rounded to the nearest whole number: 91-100 A1, 81-90 A2, 71-80 B1, 61-70 B2, 51-60 C1, 41-50 C2, 33-40 D, 32 and below E (Needs Improvement). Subject grade uses subject total / subject max.
- **Result:** PASS only if every subject's total is at least `passPercent` of its maximum, else FAIL. Show "Result pending" if no marks are entered.
- Class rank (optional): rank by overall total within the same session, class and section.
- Attendance Grand Total = (T1 present + T2 present) / (T1 working + T2 working), for example 106/111 + 87/97 = 193/208. Validate present <= working.
- Co-scholastic grades use only A, B, C.

Example for tests (Scheme A, Term-I): IA 8, UT1 25, HY 50 gives 83, grade A2. Term-I scheme B: NB 4, SE 5, PT 9, Exam 70 gives 88, grade A2. Annual subject total 83 + 88 = 171 of 200 (85.5%, A2).

## 5. Screens (bottom navigation, big touch targets, Material-style)
1. **Dashboard:** pickers for Session, Class, Section, and a student picker. Big buttons: Students, Enter Marks, Co-scholastic & Attendance, Reports, Settings, Backup. Show counts (students, marks entered %).
2. **Students:** list with search, add/edit form, photo from camera or gallery (resize to under 150 KB), import from CSV, delete with confirmation. Roll number auto-suggest.
3. **Enter Marks (the main screen):** choose Session, Class, Section, Exam (UT1, UT2, Half-Yearly, Annual) and Subject. Show a **grid**: one row per student (roll no, name) and one column per component for that exam, with a numeric keyboard and an "AB" toggle button per cell. Autosave on blur, jump to the next student with the keyboard "Next" key. Show each column's max in the header, a live row total, and red highlight for invalid values. A second view lets the user pick one student and enter all subjects together.
   - Exam to components: **UT1** (scheme A: U.T. 1st 30; scheme B: Periodic Test 10), **UT2** (Periodic Test 10), **Half-Yearly** (the Term-I components), **Annual** (the Term-II components).
4. **Co-scholastic and Attendance:** per student and term: the five co-scholastic areas (A/B/C chips), days present, working days, remarks, promoted-to class, date.
5. **Reports:** choose report type, then Preview / Print / Download PDF for **one student** or **the whole class and section** (single combined PDF; optional ZIP of one PDF per student). A progress bar for batch export.
6. **Settings:** school name, address, mobile, logo upload, session list, pass percent, Term-I scheme toggle, subject lists per class group.
7. **Backup:** export everything as one JSON file (saved with the device's share/download sheet) and import it back (with a "replace" or "merge" choice). Show the last backup date and warn if none for 30 days.

## 6. Reports (A4, print-ready, modern clean design)
Design tokens: navy #1F3A5F for title bands and table headers (white text), steel blue #2E6F9E for secondary bands, light #DCE5F0 for label cells, #EEF2F7 zebra rows, borders #9FB0C3 at 0.2-0.3 mm. Font: Calibri/Arial with Noto Sans Devanagari fallback. Body text 9-10 pt. Must also print clearly in black and white. No clipped text, no overlapping, no overflow across pages.

**Header on every report:** logo, school name (large, navy), address, mobile, and the report title with the session.

**Report 1: UT1 slip and Report 2: UT2 slip** (one student, or a class list): name, father's name, class, sec, roll no.; table Subject / Marks Obtained / Max / Grade; total, percentage, grade; signatures.

**Report 3: Half-Yearly slip (Term-I, two per A4 page, or one per page)**
- Profile: Name, Father's Name, Class, Sec, R.N., Address, Session.
- Columns per subject: Internal Assessment (10), U.T. 1st (30), H.Y. Exam (60), Total (100), Grade. (If Term-I is scheme B, show Note Book (5), Subject Enrich (5), Periodic Test (10), H.Y. Exam (80), Total.)
- Rows follow the class-group subject list (7, 6 or 8 rows).
- Summary strip: Total (out of 700/600/800), Percentage, Grade, Attendance (present/working), Art, Discipline, Date, Remarks.
- Grading scale box: A1 91-100, A2 81-90, B1 71-80, B2 61-70, C1 51-60, C2 41-50, D 33-40, E 32 and below.
- Signatures: Guardian's Sign, Class Teacher's Sign, Principal's Sign.
- Layout: each slip is about 135 mm tall so two fit on one A4 page with a dashed cut line; an option "one per page" for single students.

**Report 4: Annual / Consolidated Report Card (full A4 page)**
- Logo area, school name, address, "Report Card (Session 2026-2027)", mobile.
- Profile: Admission No., Class, Sec, Roll No., Student Name, Date of Birth, Mob. No., Mother's Name, Father's Name, Address, and a **PHOTO** box (uses the student's photo; shows "PHOTO" placeholder if none).
- Attendance strip: "Attendance: (Total attendance of Students / Total working Days)" with Term-I (e.g. 106/111), Term-II (e.g. 87/97) and Grand Total (auto, e.g. 193/208).
- "ACADEMIC PERFORMANCE: SCHOLASTIC AREAS (8 POINT SCALE)" table, one row per subject:
  - TERM-I (100): Note Book (5), Subject Enrich (5), Periodic Test (10), H.Y. Exam (80), Full Marks (100)
  - TERM-II (100): Note Book (5), Subject Enrich (5), Periodic Test (10), Annual Exam (80), Full Marks (100)
  - GRAND TOTAL MARKS (200) and Grade
  - If Term-I was entered under scheme A, show the Term-I Full Marks and print "-" in the four Term-I component cells.
- Totals: Term-I Total (/700, /600 or /800) with percentage, Term-II Total with percentage, OVERALL TOTAL (/1400, /1200 or /1600) with percentage, overall grade, Result (PASS/FAIL), and optional class rank.
- Bottom, two-column grid: left "Co-Scholastic Areas [on a 3-point (A-C) grading scale]" with Term-I and Term-II columns for the five areas; right "Marks Range / Grade" reference box with the 8-point scale (32 and Below = E, Needs Improvement).
- Class Teacher Remarks, Promoted to Class, Date, and three signature lines.
- Every card must fit on exactly one A4 page for 6, 7 and 8 subjects.

## 7. Quality requirements
- Works fully offline after first load; data persists across restarts.
- Fast with 500 students (virtualized lists, indexed queries).
- Accessible: min 44 px touch targets, labels on inputs, high contrast, works in portrait on a 360 px wide screen.
- Confirmation before any delete. Undo for the last mark edit.
- Seed the app with **sample data**: 1 student per class group with some marks, so reports can be previewed immediately. A "Clear sample data" button in Settings.
- Include a README with how to run, build and install on Android (Add to Home screen), and how backup works.

## 8. Acceptance checklist (the app must pass these)
1. Enter a Class VI student with all 8 subjects: Annual card shows /1600 overall and fits one page.
2. Enter 11 for Internal Assessment: rejected. Enter "ab": saved as AB, counted as 0, printed as AB.
3. A Nursery student shows exactly 7 subjects with the correct names (including "MATHS( ORAL)").
4. Switch Term-I scheme from A to B and back: no marks are lost.
5. Attendance 106/111 + 87/97 prints 193/208.
6. A student with one subject below 33% shows FAIL even with a high overall percentage.
7. Batch export of one class and section produces one PDF with all students in roll-number order.
8. Export a JSON backup, clear data, import the backup: everything is restored.

## 9. Step-by-step order to build (do the first step now, then continue when I say "next")
1. Project setup, data model, calc functions with tests.
2. Settings, Students, and the subject lists.
3. Marks entry grid with validation and AB.
4. Co-scholastic, attendance and term info.
5. Reports: UT slips, Half-Yearly slip (2 per page), Annual card.
6. Batch export, backup/restore, PWA install, sample data, README.

# END OF PROMPT A

---

# PROMPT B (sirf agar native Android app chahiye)

Ye tab use kijiye jab aapko Android Studio mein khulne wala asli project chahiye. AI Studio ke baare mein pakka nahi, isliye ye prompt kisi bhi AI code tool mein chalega.

You are an expert Android developer. Create a native Android app (**Kotlin, Jetpack Compose, Material 3, Room, minSdk 26, targetSdk 35**) named "CPI Report Card" for C.P.I. School, Harikaranpur Bijaura-Ghazipur (U.P.). Use MVVM with ViewModel and StateFlow, Hilt for dependency injection, and no network permission.

Implement **exactly** the data model, exam structure (schemes A and B), calculation rules, grading scale, pass rule, class-wise subject lists (Nursery-UKG 7 subjects, I-V 6 subjects, VI-VIII 8 subjects, with exact names and order), screens (Dashboard, Students, Marks Grid, Co-scholastic and Attendance, Reports, Settings, Backup) and the acceptance checklist described in Prompt A sections 2 to 8. Translate those requirements to Room entities and DAOs, Compose screens, and JUnit tests for the calculation functions.

Reports: render each report with `android.graphics.pdf.PdfDocument` (A4 = 595 x 842 points) using Canvas drawing, or an off-screen WebView printed through `PrintManager` from HTML templates. Save PDFs through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`) and share through `FileProvider`. Student photo: pick with the Photo Picker, resize to under 150 KB. Backup: export and import one JSON file via the Storage Access Framework.

Deliver the complete project structure, all source files, `build.gradle.kts`, the manifest, and instructions to build the APK in Android Studio.
