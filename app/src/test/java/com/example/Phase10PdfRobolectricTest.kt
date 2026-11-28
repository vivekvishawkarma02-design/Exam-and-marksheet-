package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.SchoolEntity
import com.example.domain.CashBookDayClosingRecord
import com.example.domain.CashBookEngine
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookSettingsConfig
import com.example.domain.CertificateStatus
import com.example.domain.CertificateType
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.FeeReceiptRecord
import com.example.domain.PaymentMode
import com.example.domain.SchoolCertificateRecord
import com.example.util.CashBookCategoryExpensePdfBundle
import com.example.util.CashBookDayBookPdfBundle
import com.example.util.CashBookMonthSummaryPdfBundle
import com.example.util.CertificatePdfBundle
import com.example.util.Phase10PdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase10PdfRobolectricTest {

    private val school = SchoolEntity(
        id = 1L,
        name = "DEMO MODEL SCHOOL",
        address = "123 Station Road, Demo Nagar",
        mobile = "9876543210",
        email = "office@demoschool.example",
        active = true
    )

    private fun makeCertificate(
        type: CertificateType,
        status: CertificateStatus = CertificateStatus.ISSUED,
        reissuedFrom: String = ""
    ): SchoolCertificateRecord = SchoolCertificateRecord(
        id = "cert_${type.wireValue}_${status.wireValue}",
        schoolId = "sch_1",
        session = "2026-2027",
        certificateType = type,
        certificateNumber = "${type.numberPrefix}-2627-A1B-0001",
        deviceNodeCode = "A1B",
        sequenceNo = 1,
        issueDateIso = "2026-04-15",
        studentId = "stu_1",
        studentName = "Aarav Kumar",
        studentAdmNo = "ADM-001",
        fatherName = "Rajesh Kumar",
        motherName = "Sunita Devi",
        dob = "15-04-2016",
        className = "5th",
        section = "A",
        rollNo = 1,
        admissionDate = "01-04-2021",
        address = "123 Station Road, Demo Nagar",
        reasonForLeaving = "Parent's request / family relocation",
        lastAttendanceDateIso = "2026-04-12",
        conduct = "Very Good",
        promotionStatus = "Promoted to Class 6th",
        feeDuesStatus = "All school dues cleared",
        remarks = "Obedient and punctual student.",
        status = status,
        issuedByUid = "u_clerk",
        issuedByName = "Office Operator",
        issuedAtMs = 1713160000000L,
        cancelledAtMs = if (status == CertificateStatus.CANCELLED) 1713165000000L else 0L,
        cancelledByUid = if (status == CertificateStatus.CANCELLED) "u_admin" else "",
        cancelledByName = if (status == CertificateStatus.CANCELLED) "Principal" else "",
        cancelReason = if (status == CertificateStatus.CANCELLED) "Spelling correction required" else "",
        reissuedFromCertNumber = reissuedFrom
    )

    private fun verifyA4Bitmap(bmp: Bitmap, label: String) {
        assertEquals("$label A4 width", Phase10PdfGenerator.A4_W, bmp.width)
        assertEquals("$label A4 height", Phase10PdfGenerator.A4_H, bmp.height)
        var nonWhite = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) nonWhite++
            }
        }
        bmp.recycle()
        assertTrue("$label should have visible rendered vector ink", nonWhite > 400)
    }

    @Test
    fun allThreeCertificatesAndCancelledWatermarkRenderCleanlyOnA4() {
        // 1. Transfer Certificate (TC)
        val tcBmp = Phase10PdfGenerator.renderCertificatePreviewBitmap(
            CertificatePdfBundle(school = school, certificate = makeCertificate(CertificateType.TC)),
            scale = 1
        )
        verifyA4Bitmap(tcBmp, "Transfer Certificate (TC)")

        // 2. Cancelled TC with diagonal watermark
        val cancelledTcBmp = Phase10PdfGenerator.renderCertificatePreviewBitmap(
            CertificatePdfBundle(
                school = school,
                certificate = makeCertificate(CertificateType.TC, status = CertificateStatus.CANCELLED)
            ),
            scale = 1
        )
        verifyA4Bitmap(cancelledTcBmp, "Cancelled Transfer Certificate")

        // 3. Bonafide Certificate (re-issued)
        val bonBmp = Phase10PdfGenerator.renderCertificatePreviewBitmap(
            CertificatePdfBundle(
                school = school,
                certificate = makeCertificate(CertificateType.BONAFIDE, reissuedFrom = "BON-2627-A1B-0000")
            ),
            scale = 1
        )
        verifyA4Bitmap(bonBmp, "Bonafide Certificate")

        // 4. Character Certificate
        val chrBmp = Phase10PdfGenerator.renderCertificatePreviewBitmap(
            CertificatePdfBundle(school = school, certificate = makeCertificate(CertificateType.CHARACTER)),
            scale = 1
        )
        verifyA4Bitmap(chrBmp, "Character Certificate")
    }

    @Test
    fun cashBookDayBookMonthSummaryAndCategoryExpensePdfsRenderCleanlyOnA4() {
        val settings = CashBookSettingsConfig.buildDefault("sch_1").copy(defaultOpeningBalanceRs = 5000)

        // Generate 28 transactions so Day Book exercises multi-page A4 pagination (24 rows/page)
        val receipts = (1..15).map { i ->
            FeeReceiptRecord(
                id = "r_$i",
                schoolId = "sch_1",
                session = "2026-2027",
                receiptNumber = "RCP-2627-A1B-%04d".format(i),
                deviceNodeCode = "A1B",
                sequenceNo = i,
                receiptDateIso = "2026-04-15",
                studentId = "stu_$i",
                studentName = "Student $i",
                studentAdmNo = "ADM-%03d".format(i),
                fatherName = "Guardian $i",
                className = "3rd",
                section = "A",
                rollNo = i,
                lineItems = emptyList(),
                totalBeforeConcession = 1000,
                concessionMode = ConcessionMode.RUPEES,
                concessionScope = ConcessionScope.WHOLE_RECEIPT,
                concessionAmount = 0,
                netPayableAmount = 1000,
                amountPaid = 1000,
                balanceDueOnReceipt = 0,
                paymentMode = if (i % 2 == 0) PaymentMode.UPI else PaymentMode.CASH,
                receivedByUid = "u_clerk",
                receivedByName = "Fee Clerk",
                issuedAtMs = 1713160000000L + i * 1000L
            )
        }

        val categories = listOf("Electricity", "Stationery", "Repairs", "Transport Fuel", "Salary")
        val entries = (1..14).map { i ->
            CashBookEntryRecord(
                id = "e_$i",
                schoolId = "sch_1",
                dateIso = "2026-04-15",
                entryType = if (i == 1) CashBookEntryType.OTHER_INCOME else CashBookEntryType.EXPENSE,
                category = if (i == 1) "Admission Form Sale" else categories[i % categories.size],
                amount = if (i == 1) 400 else 350 * i,
                paymentMode = if (i % 3 == 0) PaymentMode.UPI else PaymentMode.CASH,
                note = "Voucher #$i",
                approvedByAdminName = if (350 * i > 5000) "Principal" else "",
                createdByUid = "u_clerk",
                createdByName = "Office Clerk",
                createdAtMs = 1713162000000L + i * 1000L
            )
        }

        val closing = CashBookDayClosingRecord(
            id = CashBookDayClosingRecord.makeDocId("sch_1", "2026-04-15"),
            schoolId = "sch_1",
            dateIso = "2026-04-15",
            openingCashBalance = 5000,
            cashInTotal = 8400,
            cashOutTotal = 12000,
            expectedClosingCash = 1400,
            countedCash = 1350,
            cashDifference = -50,
            closedByUid = "u_clerk",
            closedByName = "Office Clerk",
            closedAtMs = 1713190000000L
        )

        val daySummary = CashBookEngine.computeDaySummary(
            schoolId = "sch_1",
            dateIso = "2026-04-15",
            feeReceipts = receipts,
            entries = entries,
            dayClosings = listOf(closing),
            settings = settings
        )

        val dayPages = Phase10PdfGenerator.renderCashBookDayBookPreviewBitmaps(
            CashBookDayBookPdfBundle(school = school, summary = daySummary),
            scale = 1
        )
        assertTrue("29 rows should paginate across >= 2 pages", dayPages.size >= 2)
        dayPages.forEachIndexed { idx, bmp ->
            verifyA4Bitmap(bmp, "Cash Book Day Book page $idx")
        }

        val monthSummary = CashBookEngine.computeMonthSummary("sch_1", "2026-04", receipts, entries)
        val monthPages = Phase10PdfGenerator.renderCashBookMonthSummaryPreviewBitmaps(
            CashBookMonthSummaryPdfBundle(school = school, summary = monthSummary),
            scale = 1
        )
        assertTrue(monthPages.isNotEmpty())
        monthPages.forEachIndexed { idx, bmp ->
            verifyA4Bitmap(bmp, "Cash Book Month Summary page $idx")
        }

        val catBmp = Phase10PdfGenerator.renderCashBookCategoryExpensePreviewBitmap(
            CashBookCategoryExpensePdfBundle(school = school, summary = monthSummary),
            scale = 1
        )
        verifyA4Bitmap(catBmp, "Cash Book Category-Wise Expense Report")
    }
}
