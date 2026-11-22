import { describe, it, expect } from 'vitest';
import {
  MAX_MARKS,
  DEFAULT_SUBJECTS_BY_GROUP,
  GRADING_SCALE_ROWS,
  validateMarkInput,
  numericMarkValue,
  displayMarkValue,
  calculatePercentage,
  determineGrade,
  evaluatePassStatus,
  calculateTermSubject,
  calculateStudentPerformance,
  calculateUnitTestReport,
  validateAttendance,
  calculateAttendance,
  calculateClassRanks,
} from './calc';

describe('lib/calc.ts — unified 10/30/60 structure', () => {
  it('computes Term-I (IA 8 + UT 25 + Exam 50 = 83 A2) and Annual (83 + 88 = 171/200, 85.5% A2)', () => {
    const report = calculateStudentPerformance('student-1', [
      {
        subject: 'MATH',
        term1: { ia: 8, ut: 25, exam: 50 },
        term2: { ia: 8, ut: 26, exam: 54 },
      },
    ]);

    const math = report.subjects[0];
    expect(math.term1.total).toBe(83);
    expect(math.term1.grade).toBe('A2');
    expect(math.term2.total).toBe(88);
    expect(math.term2.grade).toBe('A2');
    expect(math.grandTotal).toBe(171);
    expect(math.grandMax).toBe(200);
    expect(report.overallPercentage).toBe(85.5);
    expect(math.grandGrade).toBe('A2');
    expect(report.overallGrade).toBe('A2');
    expect(report.overallResult).toBe('PASS');
  });

  it('validates 10/30/60 bounds strictly, accepts 0 and case-insensitive AB, and rejects invalid inputs', () => {
    expect(validateMarkInput('11', MAX_MARKS.IA).valid).toBe(false);
    expect(validateMarkInput('31', MAX_MARKS.UT).valid).toBe(false);
    expect(validateMarkInput('61', MAX_MARKS.EXAM).valid).toBe(false);
    expect(validateMarkInput('-1', MAX_MARKS.IA).valid).toBe(false);
    expect(validateMarkInput('xyz', MAX_MARKS.UT).valid).toBe(false);

    expect(validateMarkInput('0', MAX_MARKS.IA)).toEqual({ valid: true, normalized: '0' });
    expect(validateMarkInput('10', MAX_MARKS.IA)).toEqual({ valid: true, normalized: '10' });
    expect(validateMarkInput('30', MAX_MARKS.UT)).toEqual({ valid: true, normalized: '30' });
    expect(validateMarkInput('60', MAX_MARKS.EXAM)).toEqual({ valid: true, normalized: '60' });
    expect(validateMarkInput('ab', MAX_MARKS.EXAM)).toEqual({ valid: true, normalized: 'AB' });
    expect(validateMarkInput(' Ab ', MAX_MARKS.UT)).toEqual({ valid: true, normalized: 'AB' });

    expect(numericMarkValue('AB')).toBe(0);
    expect(displayMarkValue('ab')).toBe('AB');
    expect(displayMarkValue('')).toBe('-');
  });

  it('handles AB inside a 10/30/60 term row by counting 0 and printing AB', () => {
    const term = calculateTermSubject({ ia: '8', ut: 'ab', exam: '42' });
    expect(term.utDisplay).toBe('AB');
    expect(term.utNumeric).toBe(0);
    expect(term.total).toBe(50);
    expect(term.grade).toBe('C2');
  });

  it('applies 8-point grading scale boundaries accurately with E (Need improvement)', () => {
    expect(determineGrade(100).grade).toBe('A1');
    expect(determineGrade(91).grade).toBe('A1');
    expect(determineGrade(90.6).grade).toBe('A1');
    expect(determineGrade(90.4).grade).toBe('A2');
    expect(determineGrade(81).grade).toBe('A2');
    expect(determineGrade(71).grade).toBe('B1');
    expect(determineGrade(61).grade).toBe('B2');
    expect(determineGrade(51).grade).toBe('C1');
    expect(determineGrade(41).grade).toBe('C2');
    expect(determineGrade(33).grade).toBe('D');
    expect(determineGrade(32)).toEqual({ grade: 'E', remark: 'Need improvement' });
    expect(determineGrade(0).grade).toBe('E');
    expect(GRADING_SCALE_ROWS[GRADING_SCALE_ROWS.length - 1].grade).toBe('E (Need improvement)');
    expect(calculatePercentage(50, 0)).toBe(0);
  });

  it('reproduces real Class 2nd example (506/1200 = 42.17% C2 PASS and 166/208 attendance)', () => {
    const report = calculateStudentPerformance('student-2nd', [
      { subject: 'ENGLISH', term1: { ia: 7, ut: 10, exam: 22 }, term2: { ia: 8, ut: 14, exam: 20 } },
      { subject: 'HINDI', term1: { ia: 6, ut: 10, exam: 20 }, term2: { ia: 9, ut: 17, exam: 30 } },
      { subject: 'MATH', term1: { ia: 7, ut: 12, exam: 21 }, term2: { ia: 8, ut: 18, exam: 30 } },
      { subject: 'SCIENCE', term1: { ia: 6, ut: 10, exam: 20 }, term2: { ia: 7, ut: 15, exam: 20 } },
      { subject: 'COMPUTER', term1: { ia: 6, ut: 9, exam: 20 }, term2: { ia: 7, ut: 15, exam: 20 } },
      { subject: 'G.K.', term1: { ia: 7, ut: 11, exam: 20 }, term2: { ia: 8, ut: 16, exam: 20 } },
    ]);

    expect(report.term1Total).toBe(224);
    expect(report.term1Max).toBe(600);
    expect(report.term2Total).toBe(282);
    expect(report.term2Max).toBe(600);
    expect(report.subjects.map((s) => s.grandTotal)).toEqual([81, 92, 96, 78, 77, 82]);
    expect(report.overallTotal).toBe(506);
    expect(report.overallMax).toBe(1200);
    expect(report.overallPercentage).toBe(42.17);
    expect(report.overallGrade).toBe('C2');
    expect(report.overallResult).toBe('PASS');

    const att = calculateAttendance(84, 111, 82, 97);
    expect(att.term1Label).toBe('84/111');
    expect(att.term2Label).toBe('82/97');
    expect(att.grandTotalLabel).toBe('166/208');
    expect(validateAttendance(84, 111)).toBeNull();
    expect(validateAttendance(112, 111)).not.toBeNull();
  });

  it('returns FAIL when any single subject is below 33% despite high overall percentage', () => {
    const subjects = DEFAULT_SUBJECTS_BY_GROUP.PRIMARY.map((subject, idx) =>
      idx === 0
        ? { subject, term1: { ia: 0, ut: 0, exam: 20 }, term2: { ia: 0, ut: 0, exam: 20 } }
        : { subject, term1: { ia: 10, ut: 30, exam: 60 }, term2: { ia: 10, ut: 30, exam: 60 } }
    );

    const report = calculateStudentPerformance('student-fail', subjects, 33);
    expect(report.overallPercentage).toBe(86.67);
    expect(report.overallResult).toBe('FAIL');
  });

  it('checks exact 33% pass/fail boundaries and Pending state when empty', () => {
    expect(evaluatePassStatus([{ obtained: 33, max: 100 }], true, 33)).toBe('PASS');
    expect(evaluatePassStatus([{ obtained: 32, max: 100 }], true, 33)).toBe('FAIL');
    expect(evaluatePassStatus([{ obtained: 66, max: 200 }], true, 33)).toBe('PASS');
    expect(evaluatePassStatus([{ obtained: 65, max: 200 }], true, 33)).toBe('FAIL');
    expect(evaluatePassStatus([{ obtained: 0, max: 100 }], false, 33)).toBe('Pending');
  });

  it('evaluates UT1/UT2 slips and tie-aware class ranking', () => {
    const ut1 = calculateUnitTestReport([
      { subject: 'ENGLISH', raw: '24' },
      { subject: 'HINDI', raw: 'ab' },
    ]);
    expect(ut1.totalObtained).toBe(24);
    expect(ut1.totalMax).toBe(60);
    expect(ut1.rows[1].displayValue).toBe('AB');
    expect(ut1.resultStatus).toBe('FAIL');

    const r1 = calculateStudentPerformance(1, [{ subject: 'ENGLISH', term1: { exam: 55 } }]);
    const r2 = calculateStudentPerformance(2, [{ subject: 'ENGLISH', term1: { exam: 55 } }]);
    const r3 = calculateStudentPerformance(3, [{ subject: 'ENGLISH', term1: { exam: 40 } }]);
    const r4 = calculateStudentPerformance(4, [{ subject: 'ENGLISH' }]);
    const ranks = calculateClassRanks([r1, r2, r3, r4]);

    expect(ranks.get(1)).toBe(1);
    expect(ranks.get(2)).toBe(1);
    expect(ranks.get(3)).toBe(3);
    expect(ranks.get(4)).toBeUndefined();
  });
});
