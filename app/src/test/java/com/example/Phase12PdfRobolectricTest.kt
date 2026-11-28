package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.SchoolEntity
import com.example.domain.PaymentMode
import com.example.domain.SalaryComponentItem
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SalaryRuleConfig
import com.example.domain.SalarySlipPageSize
import com.example.util.Phase12PdfGenerator
import com.example.util.SalaryMonthSummaryPdfBundle
import com.example.util.SalarySlipPdfBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase12PdfRobolectricTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun samplePngBase64(color: Int): String {
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
        bmp.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun makeSchool(): SchoolEntity = SchoolEntity(
        id = 1L,
        name = "DEMO MODEL SCHOOL",
        address = "123 Station Road, Demo Nagar",
        mobile = "9876543210",
        email = "office@demoschool.example",
        logoBase64 = samplePngBase64(Color.rgb(30, 58, 95)),
        active = true
    )

    private fun makePaidSalaryRecord(index: Int = 1): SalaryMonthlyRecord = SalaryMonthlyRecord(
        id = "salslip_sch_1_2026-10_prof_$index",
        schoolId = "sch_1",
        yearMonth = "2026-10",
        staffId = "prof_$index",
        staffUid = "uid_tchr_$index",
        staffName = "Mrs. Sunita Verma $index",
        designation = "Senior Teacher",
        joiningDateIso = "2024-04-01",
        workingDaysInMonth = 26,
        presentDays = 23,
        absentDays = 2,
        onLeaveDays = 1,
        halfDays = 0,
        lateDays = 1,
        freeLeavesApplied = 1.0,
        unpaidAbsenceDays = 2.0,
        perDayRateRs = 600,
        basicSalaryRs = 18000,
        allowances = listOf(
            SalaryComponentItem("a1", "HRA", 2000),
            SalaryComponentItem("a2", "DA", 1000)
        ),
        totalAllowancesRs = 3000,
        grossEarningsRs = 21000,
        attendanceDeductionRs = 1200,
        fixedDeductions = listOf(
            SalaryComponentItem("d1", "PF", 1200)
        ),
        totalFixedDeductionsRs = 1200,
        advanceDeductionRs = 2000,
        linkedAdvanceIds = listOf("adv_1"),
        manualAdjustmentRs = 500,
        adjustmentRemark = "Festival Incentive",
        netPayableRs = 17100,
        status = SalaryPaymentStatus.PAID,
        paidDateIso = "2026-10-31",
        paymentMode = PaymentMode.UPI,
        paymentReference = "UTR998877",
        updatedByUid = "u_owner",
        updatedByName = "Principal Owner"
    )

    private fun assertValidRenderedPage(bmp: Bitmap, expectedW: Int, expectedH: Int, label: String) {
        assertEquals("$label width", expectedW, bmp.width)
        assertEquals("$label height", expectedH, bmp.height)
        var nonWhite = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) nonWhite++
            }
        }
        bmp.recycle()
        assertTrue("$label should render visible vector content", nonWhite > 350)
    }

    @Test
    fun salarySlipRendersCorrectlyInBothA4AndA5Sizes() {
        val school = makeSchool()
        val record = makePaidSalaryRecord(1)
        val ruleConfig = SalaryRuleConfig(schoolId = "sch_1")

        // 1. A4 Salary Slip (595 x 842)
        val a4Bundle = SalarySlipPdfBundle(
            school = school,
            record = record,
            ruleConfig = ruleConfig,
            remainingAdvanceBalanceRs = 4000,
            pageSize = SalarySlipPageSize.A4,
            schoolLogoBase64 = school.logoBase64
        )
        val a4Bmp = Phase12PdfGenerator.renderSalarySlipPreviewBitmap(a4Bundle, scale = 1f)
        assertValidRenderedPage(
            bmp = a4Bmp,
            expectedW = 595,
            expectedH = 842,
            label = "A4 Salary Slip"
        )

        // 2. A5 Salary Slip (595 x 420)
        val a5Bundle = a4Bundle.copy(pageSize = SalarySlipPageSize.A5)
        val a5Bmp = Phase12PdfGenerator.renderSalarySlipPreviewBitmap(a5Bundle, scale = 1f)
        assertValidRenderedPage(
            bmp = a5Bmp,
            expectedW = 595,
            expectedH = 420,
            label = "A5 Salary Slip"
        )
    }

    @Test
    fun salaryMonthSummaryRendersA4PdfWithAllStaffPaginationAndLockedBadge() {
        val school = makeSchool()

        // Single-page locked summary (4 staff)
        val fourRecords = (1..4).map { makePaidSalaryRecord(it) }
        val singleBundle = SalaryMonthSummaryPdfBundle(
            school = school,
            yearMonth = "2026-10",
            records = fourRecords,
            ruleConfig = SalaryRuleConfig(schoolId = "sch_1"),
            isLocked = true,
            schoolLogoBase64 = school.logoBase64
        )
        val singlePreviews = Phase12PdfGenerator.renderSalaryMonthSummaryPreviewBitmaps(singleBundle, scale = 1f)
        assertEquals(1, singlePreviews.size)
        assertValidRenderedPage(
            bmp = singlePreviews.first(),
            expectedW = 595,
            expectedH = 842,
            label = "Salary Month Summary A4 (Locked)"
        )

        // Multi-page pagination check (28 staff -> 2 A4 pages)
        val manyRecords = (1..28).map { makePaidSalaryRecord(it) }
        val multiBundle = singleBundle.copy(records = manyRecords, isLocked = false)
        val multiPreviews = Phase12PdfGenerator.renderSalaryMonthSummaryPreviewBitmaps(multiBundle, scale = 1f)
        assertTrue("28 staff records should paginate across at least 2 A4 pages", multiPreviews.size >= 2)
        multiPreviews.forEachIndexed { idx, bmp ->
            assertValidRenderedPage(
                bmp = bmp,
                expectedW = 595,
                expectedH = 842,
                label = "Salary Month Summary A4 page ${idx + 1}"
            )
        }
    }
}
