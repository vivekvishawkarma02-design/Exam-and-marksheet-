package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionReportRow
import com.example.domain.ConcessionScope
import com.example.domain.DayCollectionRegisterSummary
import com.example.domain.FeeHeadType
import com.example.domain.FeeLineItemType
import com.example.domain.FeeReceiptLineItem
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.OptionalItemSalesRow
import com.example.domain.PaymentMode
import com.example.domain.StudentDueCellBreakdown
import com.example.domain.StudentDuesListRow
import com.example.util.AdmissionFormPdfBundle
import com.example.util.ConcessionReportPdfBundle
import com.example.util.DayCollectionPdfBundle
import com.example.util.DuesListPdfBundle
import com.example.util.FeeReceiptPdfBundle
import com.example.util.OptionalSalesPdfBundle
import com.example.util.Phase9PdfGenerator
import com.example.util.StudentDuesSlipPdfBundle
import com.example.util.StudentIdCardsPdfBundle
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
class Phase9PdfRobolectricTest {

    private val school = SchoolEntity(
        id = 1L,
        name = "C.P.S. SCHOOL",
        address = "Harikaranpur Bijaura, Ghazipur (U.P.)",
        mobile = "9876543210",
        email = "cps@example.edu",
        active = true
    )

    private fun makeStudent(index: Int = 1): StudentEntity = StudentEntity(
        id = index.toLong(),
        schoolId = 1L,
        session = "2026-2027",
        admNo = "ADM-%03d".format(index),
        name = "Aarav Kumar %02d".format(index),
        fatherName = "Rajesh Kumar",
        motherName = "Sunita Devi",
        dob = "15-04-2016",
        mobile = "9876543210",
        secondMobile = "9123456789",
        address = "Harikaranpur Bijaura, Ghazipur",
        className = "3rd",
        section = "A",
        rollNo = index,
        admissionDate = "01-04-2026",
        previousSchool = "Little Buds",
        usesTransport = true,
        transportRouteName = "Route 2 - Market",
        transportMonthlyAmount = 500
    )

    private fun makeReceipt(status: FeeReceiptStatus = FeeReceiptStatus.ISSUED): FeeReceiptRecord = FeeReceiptRecord(
        id = "rcp_1",
        schoolId = "sch_1",
        session = "2026-2027",
        receiptNumber = "RCP-2627-A1B-0001",
        deviceNodeCode = "A1B",
        sequenceNo = 1,
        receiptDateIso = "2026-04-12",
        studentId = "stu_1",
        studentName = "Aarav Kumar 01",
        studentAdmNo = "ADM-001",
        fatherName = "Rajesh Kumar",
        className = "3rd",
        section = "A",
        rollNo = 1,
        lineItems = listOf(
            FeeReceiptLineItem(
                lineId = "CELL_APR_TUITION",
                lineType = FeeLineItemType.GRID_CELL,
                monthCode = "APR",
                headKey = FeeHeadType.TUITION.key,
                title = "Tuition Fee (Apr)",
                quantity = 1,
                unitPrice = 1000,
                lineGrossAmount = 1000,
                concessionAllocated = 100,
                paidAllocated = 900
            ),
            FeeReceiptLineItem(
                lineId = "OPT_books_APR",
                lineType = FeeLineItemType.OPTIONAL_ITEM,
                monthCode = "APR",
                headKey = "OPTIONAL_books",
                title = "Textbook Set (Apr)",
                quantity = 1,
                unitPrice = 600,
                lineGrossAmount = 600,
                concessionAllocated = 0,
                paidAllocated = 600,
                catalogueItemId = "books"
            )
        ),
        totalBeforeConcession = 1600,
        concessionMode = ConcessionMode.RUPEES,
        concessionScope = ConcessionScope.WHOLE_RECEIPT,
        concessionAmount = 100,
        concessionRemark = "Sibling concession",
        concessionGivenByName = "Fee Clerk",
        netPayableAmount = 1500,
        amountPaid = 1500,
        balanceDueOnReceipt = 0,
        paymentMode = PaymentMode.UPI,
        paymentNote = "GPay Ref 8821",
        status = status,
        receivedByUid = "u_clerk",
        receivedByName = "Fee Clerk",
        issuedAtMs = 1712900000000L,
        cancelledAtMs = if (status == FeeReceiptStatus.CANCELLED) 1712903600000L else 0L,
        cancelledByName = if (status == FeeReceiptStatus.CANCELLED) "Principal" else "",
        cancelReason = if (status == FeeReceiptStatus.CANCELLED) "Duplicate entry" else ""
    )

    private fun verifyBitmap(bmp: Bitmap, expectedWidth: Int, expectedHeight: Int, label: String) {
        assertEquals("$label width", expectedWidth, bmp.width)
        assertEquals("$label height", expectedHeight, bmp.height)
        var nonWhite = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) nonWhite++
            }
        }
        bmp.recycle()
        assertTrue("$label should have visible rendered vector content", nonWhite > 300)
    }

    @Test
    fun allEightPhase9PdfsRenderCleanlyOffline() {
        val student = makeStudent(1)
        val issuedReceipt = makeReceipt(FeeReceiptStatus.ISSUED)
        val cancelledReceipt = makeReceipt(FeeReceiptStatus.CANCELLED)

        // 1. Issued & Cancelled A5 Fee Receipts
        val rcpBmp = Phase9PdfGenerator.renderFeeReceiptPreviewBitmap(
            FeeReceiptPdfBundle(school = school, receipt = issuedReceipt, totalStudentRemainingDueAfterReceipt = 1200),
            scale = 1
        )
        verifyBitmap(rcpBmp, Phase9PdfGenerator.A5_W, Phase9PdfGenerator.A5_H, "Issued Fee Receipt")

        val cancelledRcpBmp = Phase9PdfGenerator.renderFeeReceiptPreviewBitmap(
            FeeReceiptPdfBundle(school = school, receipt = cancelledReceipt, totalStudentRemainingDueAfterReceipt = 2700),
            scale = 1
        )
        verifyBitmap(cancelledRcpBmp, Phase9PdfGenerator.A5_W, Phase9PdfGenerator.A5_H, "Cancelled Fee Receipt")

        // 2. A5 Student Dues Slip
        val slipBmp = Phase9PdfGenerator.renderStudentDuesSlipPreviewBitmap(
            StudentDuesSlipPdfBundle(
                school = school,
                session = "2026-2027",
                slipDateIso = "2026-04-15",
                student = student,
                dueBreakdowns = listOf(
                    StudentDueCellBreakdown("MAY", FeeHeadType.TUITION, "May Tuition", 1000),
                    StudentDueCellBreakdown("MAY", FeeHeadType.TRANSPORT, "May Transport", 500)
                ),
                totalDueAmount = 1500,
                preparedByName = "Fee Clerk"
            ),
            scale = 1
        )
        verifyBitmap(slipBmp, Phase9PdfGenerator.A5_W, Phase9PdfGenerator.A5_H, "Student Dues Slip")

        // 3. A4 Day Collection Register
        val daySummary = DayCollectionRegisterSummary(
            startDateIso = "2026-04-12",
            endDateIso = "2026-04-12",
            activeReceipts = listOf(issuedReceipt),
            cancelledReceipts = listOf(cancelledReceipt),
            totalByMode = mapOf(PaymentMode.UPI to 1500, PaymentMode.CASH to 0),
            totalGrossBeforeConcession = 1600,
            totalConcessionGiven = 100,
            totalNetCollected = 1500
        )
        val dayPages = Phase9PdfGenerator.renderDayCollectionPreviewBitmaps(
            DayCollectionPdfBundle(school = school, session = "2026-2027", summary = daySummary),
            scale = 1
        )
        assertTrue(dayPages.isNotEmpty())
        dayPages.forEachIndexed { idx, bmp ->
            verifyBitmap(bmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Day Collection page $idx")
        }

        // 4. A4 Dues List (multi-page with 35 rows)
        val duesRows = (1..35).map { idx ->
            StudentDuesListRow(
                schoolDocId = "sch_1",
                schoolName = school.name,
                studentDocId = "stu_$idx",
                student = makeStudent(idx),
                dueBreakdowns = listOf(StudentDueCellBreakdown("MAY", FeeHeadType.TUITION, "May Tuition", 1000)),
                dueByHead = mapOf(FeeHeadType.TUITION to 1000),
                dueMonths = listOf("May"),
                totalDueAmount = 1000
            )
        }
        val duesPages = Phase9PdfGenerator.renderDuesListPreviewBitmaps(
            DuesListPdfBundle(
                school = school,
                session = "2026-2027",
                filterTitle = "Class 3rd - A",
                asOfDateIso = "2026-05-10",
                rows = duesRows
            ),
            scale = 1
        )
        assertTrue("35 rows should paginate across >= 2 pages", duesPages.size >= 2)
        duesPages.forEachIndexed { idx, bmp ->
            verifyBitmap(bmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Dues List page $idx")
        }

        // 5. A4 Concession Report
        val concPages = Phase9PdfGenerator.renderConcessionReportPreviewBitmaps(
            ConcessionReportPdfBundle(
                school = school,
                session = "2026-2027",
                filterTitle = "April 2026",
                rows = listOf(
                    ConcessionReportRow(
                        receiptId = "rcp_1",
                        receiptNumber = issuedReceipt.receiptNumber,
                        receiptDateIso = issuedReceipt.receiptDateIso,
                        studentName = student.name,
                        studentAdmNo = student.admNo,
                        className = student.className,
                        section = student.section,
                        totalBeforeConcession = 1600,
                        concessionAmount = 100,
                        concessionMode = ConcessionMode.RUPEES,
                        concessionScope = ConcessionScope.WHOLE_RECEIPT,
                        remark = "Sibling concession",
                        givenByName = "Fee Clerk",
                        approvedByAdminName = ""
                    )
                )
            ),
            scale = 1
        )
        assertTrue(concPages.isNotEmpty())
        concPages.forEachIndexed { idx, bmp ->
            verifyBitmap(bmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Concession Report page $idx")
        }

        // 6. A4 Optional-Item Sales Report
        val optBmp = Phase9PdfGenerator.renderOptionalSalesPreviewBitmap(
            OptionalSalesPdfBundle(
                school = school,
                session = "2026-2027",
                filterTitle = "Session 2026-2027",
                rows = listOf(
                    OptionalItemSalesRow(
                        itemKey = "books",
                        itemName = "Textbook Set",
                        totalQuantitySold = 12,
                        totalGrossAmount = 7200,
                        totalNetCollected = 7200,
                        receiptCount = 12
                    )
                )
            ),
            scale = 1
        )
        verifyBitmap(optBmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Optional Sales Report")

        // 7. A4 Admission Form
        val admBmp = Phase9PdfGenerator.renderAdmissionFormPreviewBitmap(
            AdmissionFormPdfBundle(
                school = school,
                session = "2026-2027",
                student = student,
                siblingNamesSummary = "Ananya Kumar (1st-A)"
            ),
            scale = 1
        )
        verifyBitmap(admBmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Admission Form")

        // 8. A4 Student ID Cards (10 students -> 2 pages of 2x4 grid)
        val idPages = Phase9PdfGenerator.renderStudentIdCardsPreviewBitmaps(
            StudentIdCardsPdfBundle(
                school = school,
                session = "2026-2027",
                students = (1..10).map { makeStudent(it) }
            ),
            scale = 1
        )
        assertEquals(2, idPages.size)
        idPages.forEachIndexed { idx, bmp ->
            verifyBitmap(bmp, Phase9PdfGenerator.A4_W, Phase9PdfGenerator.A4_H, "Student ID Cards page $idx")
        }
    }
}
