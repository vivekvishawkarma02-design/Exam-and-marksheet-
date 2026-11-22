/**
 * Pure calculation & validation logic for School Report Card.
 * Unified 10 / 30 / 60 exam structure for both Term-I and Term-II:
 * - Internal Assessment (IA): max 10
 * - Unit Test (UT1 / UT2): max 30
 * - Term Exam (Half-Yearly / Yearly): max 60
 * Total = 100 per subject per term, 200 per subject for Annual Grand Total.
 */

export const MAX_MARKS = {
  IA: 10,
  UT: 30,
  EXAM: 60,
  TERM_TOTAL: 100,
  ANNUAL_TOTAL: 200,
} as const;

export type MarkComponentKey =
  | 'T1_IA'
  | 'T1_UT'
  | 'T1_EXAM'
  | 'T2_IA'
  | 'T2_UT'
  | 'T2_EXAM';

export type ResultStatus = 'PASS' | 'FAIL' | 'Pending';

export type GradeCode = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2' | 'D' | 'E';

export interface GradeInfo {
  grade: GradeCode;
  remark: string;
}

export interface GradingScaleRow {
  range: string;
  grade: string;
}

export const GRADING_SCALE_ROWS: readonly GradingScaleRow[] = [
  { range: '91 - 100', grade: 'A1' },
  { range: '81 - 90', grade: 'A2' },
  { range: '71 - 80', grade: 'B1' },
  { range: '61 - 70', grade: 'B2' },
  { range: '51 - 60', grade: 'C1' },
  { range: '41 - 50', grade: 'C2' },
  { range: '33 - 40', grade: 'D' },
  { range: '32 & Below', grade: 'E (Need improvement)' },
] as const;

export const DEFAULT_SUBJECTS_BY_GROUP = {
  PRE_PRIMARY: [
    'HINDI',
    'HINDI (ORAL)',
    'ENGLISH',
    'ENGLISH (ORAL)',
    'MATHS',
    'MATHS( ORAL)',
    'RHYNES',
  ],
  PRIMARY: ['ENGLISH', 'HINDI', 'MATH', 'SCIENCE', 'COMPUTER', 'G.K.'],
  MIDDLE: [
    'ENGLISH',
    'HINDI',
    'MATHS',
    'SCIENCE / EVS',
    'COMPUTER',
    'S.ST.',
    'G.K.',
    'SANSKRIT',
  ],
} as const;

export type MarkValidationResult =
  | { valid: true; normalized: string }
  | { valid: false; error: string };

export interface TermMarksInput {
  ia?: string | number | null;
  ut?: string | number | null;
  exam?: string | number | null;
}

export interface SubjectMarksInput {
  subject: string;
  term1?: TermMarksInput;
  term2?: TermMarksInput;
}

export interface TermSubjectResult {
  iaDisplay: string;
  utDisplay: string;
  examDisplay: string;
  iaNumeric: number;
  utNumeric: number;
  examNumeric: number;
  total: number;
  maxMarks: number;
  percentage: number;
  grade: GradeCode | '-';
  hasAnyEntry: boolean;
}

export interface SubjectPerformance {
  subject: string;
  term1: TermSubjectResult;
  term2: TermSubjectResult;
  grandTotal: number;
  grandMax: number;
  grandPercentage: number;
  grandGrade: GradeCode | '-';
  hasAnyEntry: boolean;
}

export interface StudentPerformanceReport {
  studentId: string | number;
  subjects: SubjectPerformance[];
  term1Total: number;
  term1Max: number;
  term1Percentage: number;
  term1Grade: GradeCode | '-';
  term1Result: ResultStatus;
  term2Total: number;
  term2Max: number;
  term2Percentage: number;
  term2Grade: GradeCode | '-';
  term2Result: ResultStatus;
  overallTotal: number;
  overallMax: number;
  overallPercentage: number;
  overallGrade: GradeCode | '-';
  overallResult: ResultStatus;
  hasAnyAnnualMarks: boolean;
}

export interface SingleExamRow {
  subject: string;
  rawValue: string;
  displayValue: string;
  obtained: number;
  maxMarks: number;
  grade: GradeCode | '-';
  hasEntry: boolean;
}

export interface SingleExamReport {
  rows: SingleExamRow[];
  totalObtained: number;
  totalMax: number;
  percentage: number;
  grade: GradeCode | '-';
  resultStatus: ResultStatus;
}

export interface AttendanceSummary {
  t1Present: number;
  t1Working: number;
  t2Present: number;
  t2Working: number;
  totalPresent: number;
  totalWorking: number;
  term1Label: string;
  term2Label: string;
  grandTotalLabel: string;
}

/**
 * Formats a number cleanly without trailing ".00" zeros.
 */
export function formatNumber(value: number): string {
  const rounded = Math.round(value);
  if (Math.abs(value - rounded) < 0.0001) {
    return String(rounded);
  }
  return value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

/**
 * Validates a mark input against [0, maxMarks] or "AB" (case-insensitive) or blank.
 */
export function validateMarkInput(
  raw: string | number | null | undefined,
  maxMarks: number
): MarkValidationResult {
  const trimmed = raw === null || raw === undefined ? '' : String(raw).trim();
  if (trimmed === '') {
    return { valid: true, normalized: '' };
  }
  if (trimmed.toUpperCase() === 'AB') {
    return { valid: true, normalized: 'AB' };
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    return { valid: false, error: `Enter 0–${maxMarks} or AB` };
  }
  if (parsed < 0) {
    return { valid: false, error: 'Mark cannot be negative' };
  }
  if (parsed > maxMarks) {
    return { valid: false, error: `Max allowed is ${maxMarks}` };
  }
  return { valid: true, normalized: formatNumber(parsed) };
}

/**
 * Returns the numeric value of a raw mark entry ("AB" and empty count as 0).
 */
export function numericMarkValue(raw: string | number | null | undefined): number {
  const trimmed = raw === null || raw === undefined ? '' : String(raw).trim();
  if (trimmed === '' || trimmed.toUpperCase() === 'AB') {
    return 0;
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

/**
 * Returns the printable mark string ("-" for empty, "AB" for absent, formatted number otherwise).
 */
export function displayMarkValue(raw: string | number | null | undefined): string {
  const trimmed = raw === null || raw === undefined ? '' : String(raw).trim();
  if (trimmed === '') return '-';
  if (trimmed.toUpperCase() === 'AB') return 'AB';
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? formatNumber(parsed) : trimmed;
}

/**
 * Calculates percentage rounded to 2 decimal places, safely handling max <= 0.
 */
export function calculatePercentage(obtained: number, max: number): number {
  if (max <= 0) return 0;
  return Math.round((obtained / max) * 10000) / 100;
}

/**
 * 8-point grading scale applied to percentage rounded to the nearest whole number:
 * 91-100 A1, 81-90 A2, 71-80 B1, 61-70 B2, 51-60 C1, 41-50 C2, 33-40 D, <=32 E (Need improvement).
 */
export function determineGrade(percentage: number): GradeInfo {
  const rounded = Math.max(0, Math.min(100, Math.round(percentage)));
  if (rounded >= 91) return { grade: 'A1', remark: 'Outstanding' };
  if (rounded >= 81) return { grade: 'A2', remark: 'Excellent' };
  if (rounded >= 71) return { grade: 'B1', remark: 'Very Good' };
  if (rounded >= 61) return { grade: 'B2', remark: 'Good' };
  if (rounded >= 51) return { grade: 'C1', remark: 'Satisfactory' };
  if (rounded >= 41) return { grade: 'C2', remark: 'Average' };
  if (rounded >= 33) return { grade: 'D', remark: 'Pass' };
  return { grade: 'E', remark: 'Need improvement' };
}

/**
 * Evaluates PASS / FAIL / Pending:
 * - "Pending" when no marks have been entered.
 * - "PASS" only when every subject meets >= passPercent (default 33%).
 * - "FAIL" if any single subject is below passPercent.
 */
export function evaluatePassStatus(
  subjectTotals: ReadonlyArray<{ obtained: number; max: number }>,
  hasAnyMarks: boolean,
  passPercent = 33
): ResultStatus {
  if (!hasAnyMarks || subjectTotals.length === 0) {
    return 'Pending';
  }
  const allPassed = subjectTotals.every(({ obtained, max }) => {
    if (max <= 0) return true;
    const pct = (obtained / max) * 100;
    return pct + 1e-6 >= passPercent;
  });
  return allPassed ? 'PASS' : 'FAIL';
}

/**
 * Calculates one term (IA 10 + UT 30 + Exam 60 = 100) for a single subject.
 */
export function calculateTermSubject(input?: TermMarksInput): TermSubjectResult {
  const iaRaw = input?.ia === null || input?.ia === undefined ? '' : String(input.ia).trim();
  const utRaw = input?.ut === null || input?.ut === undefined ? '' : String(input.ut).trim();
  const examRaw = input?.exam === null || input?.exam === undefined ? '' : String(input.exam).trim();

  const hasAnyEntry = iaRaw !== '' || utRaw !== '' || examRaw !== '';
  const iaNumeric = numericMarkValue(iaRaw);
  const utNumeric = numericMarkValue(utRaw);
  const examNumeric = numericMarkValue(examRaw);
  const total = iaNumeric + utNumeric + examNumeric;
  const percentage = calculatePercentage(total, MAX_MARKS.TERM_TOTAL);

  return {
    iaDisplay: displayMarkValue(iaRaw),
    utDisplay: displayMarkValue(utRaw),
    examDisplay: displayMarkValue(examRaw),
    iaNumeric,
    utNumeric,
    examNumeric,
    total,
    maxMarks: MAX_MARKS.TERM_TOTAL,
    percentage,
    grade: hasAnyEntry ? determineGrade(percentage).grade : '-',
    hasAnyEntry,
  };
}

/**
 * Calculates complete Term-I, Term-II, and Annual Grand Total performance for a student.
 */
export function calculateStudentPerformance(
  studentId: string | number,
  subjects: ReadonlyArray<SubjectMarksInput>,
  passPercent = 33
): StudentPerformanceReport {
  let term1Total = 0;
  let term1Max = 0;
  let term1Any = false;

  let term2Total = 0;
  let term2Max = 0;
  let term2Any = false;

  const subjectResults: SubjectPerformance[] = subjects.map((entry) => {
    const term1 = calculateTermSubject(entry.term1);
    const term2 = calculateTermSubject(entry.term2);

    if (term1.hasAnyEntry) term1Any = true;
    if (term2.hasAnyEntry) term2Any = true;

    const grandTotal = term1.total + term2.total;
    const grandMax = term1.maxMarks + term2.maxMarks;
    const grandPercentage = calculatePercentage(grandTotal, grandMax);
    const hasAnyEntry = term1.hasAnyEntry || term2.hasAnyEntry;

    term1Total += term1.total;
    term1Max += term1.maxMarks;
    term2Total += term2.total;
    term2Max += term2.maxMarks;

    return {
      subject: entry.subject,
      term1,
      term2,
      grandTotal,
      grandMax,
      grandPercentage,
      grandGrade: hasAnyEntry ? determineGrade(grandPercentage).grade : '-',
      hasAnyEntry,
    };
  });

  const term1Percentage = calculatePercentage(term1Total, term1Max);
  const term2Percentage = calculatePercentage(term2Total, term2Max);
  const overallTotal = term1Total + term2Total;
  const overallMax = term1Max + term2Max;
  const overallPercentage = calculatePercentage(overallTotal, overallMax);
  const hasAnyAnnualMarks = term1Any || term2Any;

  const term1Result = evaluatePassStatus(
    subjectResults.map((s) => ({ obtained: s.term1.total, max: s.term1.maxMarks })),
    term1Any,
    passPercent
  );
  const term2Result = evaluatePassStatus(
    subjectResults.map((s) => ({ obtained: s.term2.total, max: s.term2.maxMarks })),
    term2Any,
    passPercent
  );
  const overallResult = evaluatePassStatus(
    subjectResults.map((s) => ({ obtained: s.grandTotal, max: s.grandMax })),
    hasAnyAnnualMarks,
    passPercent
  );

  return {
    studentId,
    subjects: subjectResults,
    term1Total,
    term1Max,
    term1Percentage,
    term1Grade: term1Any ? determineGrade(term1Percentage).grade : '-',
    term1Result,
    term2Total,
    term2Max,
    term2Percentage,
    term2Grade: term2Any ? determineGrade(term2Percentage).grade : '-',
    term2Result,
    overallTotal,
    overallMax,
    overallPercentage,
    overallGrade: hasAnyAnnualMarks ? determineGrade(overallPercentage).grade : '-',
    overallResult,
    hasAnyAnnualMarks,
  };
}

/**
 * Calculates a single UT1 or UT2 slip (each subject out of 30).
 */
export function calculateUnitTestReport(
  entries: ReadonlyArray<{ subject: string; raw?: string | number | null }>,
  passPercent = 33
): SingleExamReport {
  let totalObtained = 0;
  let totalMax = 0;
  let anyEntered = false;

  const rows: SingleExamRow[] = entries.map(({ subject, raw }) => {
    const trimmed = raw === null || raw === undefined ? '' : String(raw).trim();
    const hasEntry = trimmed !== '';
    if (hasEntry) anyEntered = true;
    const obtained = numericMarkValue(trimmed);
    const pct = calculatePercentage(obtained, MAX_MARKS.UT);
    totalObtained += obtained;
    totalMax += MAX_MARKS.UT;

    return {
      subject,
      rawValue: trimmed.toUpperCase() === 'AB' ? 'AB' : trimmed,
      displayValue: displayMarkValue(trimmed),
      obtained,
      maxMarks: MAX_MARKS.UT,
      grade: hasEntry ? determineGrade(pct).grade : '-',
      hasEntry,
    };
  });

  const percentage = calculatePercentage(totalObtained, totalMax);
  const resultStatus = evaluatePassStatus(
    rows.map((r) => ({ obtained: r.obtained, max: r.maxMarks })),
    anyEntered,
    passPercent
  );

  return {
    rows,
    totalObtained,
    totalMax,
    percentage,
    grade: anyEntered ? determineGrade(percentage).grade : '-',
    resultStatus,
  };
}

/**
 * Validates and summarizes Term-I + Term-II attendance.
 */
export function validateAttendance(present: number, working: number): string | null {
  if (present < 0 || working < 0) return 'Attendance days cannot be negative';
  if (present > working) return `Days present (${present}) cannot exceed working days (${working})`;
  return null;
}

export function calculateAttendance(
  t1Present: number,
  t1Working: number,
  t2Present: number,
  t2Working: number
): AttendanceSummary {
  const totalPresent = t1Present + t2Present;
  const totalWorking = t1Working + t2Working;
  return {
    t1Present,
    t1Working,
    t2Present,
    t2Working,
    totalPresent,
    totalWorking,
    term1Label: t1Working > 0 || t1Present > 0 ? `${t1Present}/${t1Working}` : '-',
    term2Label: t2Working > 0 || t2Present > 0 ? `${t2Present}/${t2Working}` : '-',
    grandTotalLabel: totalWorking > 0 || totalPresent > 0 ? `${totalPresent}/${totalWorking}` : '-',
  };
}


/**
 * Competition class ranking (1, 1, 3) by overallTotal, excluding pending students.
 */
export function calculateClassRanks(
  reports: ReadonlyArray<StudentPerformanceReport>
): Map<string | number, number> {
  const eligible = reports
    .filter((r) => r.hasAnyAnnualMarks)
    .slice()
    .sort((a, b) => b.overallTotal - a.overallTotal);

  const ranks = new Map<string | number, number>();
  let lastTotal: number | null = null;
  let currentRank = 0;

  eligible.forEach((report, idx) => {
    if (lastTotal === null || Math.abs(report.overallTotal - lastTotal) > 0.0001) {
      currentRank = idx + 1;
      lastTotal = report.overallTotal;
    }
    ranks.set(report.studentId, currentRank);
  });

  return ranks;
}
