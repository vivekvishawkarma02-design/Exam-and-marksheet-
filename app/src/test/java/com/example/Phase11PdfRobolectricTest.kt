package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.ExamCategoryType
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleEntry
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatAllocation
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.StockItemSummary
import com.example.domain.TransportDuesSummary
import com.example.domain.TransportMonthDueStudentRow
import com.example.domain.TransportMonthlyProfitLossReport
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportRouteSummary
import com.example.domain.TransportStop
import com.example.domain.TransportTripType
import com.example.domain.TransportVehicleCostSummary
import com.example.util.AdmitCardStudentItem
import com.example.util.AdmitCardsPdfBundle
import com.example.util.DutyListPdfBundle
import com.example.util.ExamTimetablePdfBundle
import com.example.util.Phase11PdfGenerator
import com.example.util.RoomSeatingPdfBundle
import com.example.util.StockReportPdfBundle
import com.example.util.TransportDuesPdfBundle
import com.example.util.TransportMonthlySummaryPdfBundle
import com.example.util.TransportRoutePdfBundle
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
class Phase11PdfRobolectricTest {

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

    private fun makeStudent(i: Int, withPhoto: Boolean = false): StudentEntity = StudentEntity(
        id = i.toLong(),
        schoolId = 1L,
        classId = 5L,
        session = "2026-2027",
        admNo = "ADM-%03d".format(i),
        name = "Student Name $i",
        fatherName = "Guardian $i",
        motherName = "Mother $i",
        dob = "15-04-2016",
        className = "5th",
        section = "A",
        rollNo = i,
        mobile = "98765432%02d".format(i % 100),
        secondMobile = if (i % 2 == 0) "91234567%02d".format(i % 100) else "",
        address = "123 Station Road, Demo Nagar",
        photoBase64 = if (withPhoto) samplePngBase64(Color.rgb(15, 118, 110)) else "",
        usesTransport = true,
        transportRouteId = "troute_1",
        transportStopName = if (i % 2 == 0) "Railway Station" else "Main Bazar",
        transportRouteName = "North Bus",
        transportMonthlyAmount = if (i % 2 == 0) 750 else 500
    )

    private fun verifyA4Bitmap(bmp: Bitmap, label: String) {
        assertEquals("$label A4 width", Phase11PdfGenerator.A4_W, bmp.width)
        assertEquals("$label A4 height", Phase11PdfGenerator.A4_H, bmp.height)
        var nonWhite = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) nonWhite++
            }
        }
        bmp.recycle()
        assertTrue("$label should render visible vector content", nonWhite > 400)
    }

    private fun sampleExam(): ExamScheduleRecord = ExamScheduleRecord(
        id = "exam_hy_2627",
        schoolId = "sch_1",
        session = "2026-2027",
        examType = ExamCategoryType.HALF_YEARLY,
        title = "Half-Yearly Examination",
        entries = listOf(
            ExamScheduleEntry("e1", "5th", "Hindi", "2026-09-14", "09:00", "12:00", 100),
            ExamScheduleEntry("e2", "5th", "English", "2026-09-15", "09:00", "12:00", 100),
            ExamScheduleEntry("e3", "5th", "Mathematics", "2026-09-16", "09:00", "12:00", 100),
            ExamScheduleEntry("e4", "5th", "Science", "2026-09-17", "09:00", "12:00", 100),
            ExamScheduleEntry("e5", "5th", "Social Studies", "2026-09-18", "09:00", "12:00", 100),
            ExamScheduleEntry("e6", "5th", "Computer", "2026-09-19", "09:00", "11:00", 50)
        )
    )

    @Test
    fun admitCardsRenderTwoPerA4PageWithLogoPhotoAndSubjects() {
        val school = makeSchool()
        val exam = sampleExam()

        // 3 students -> must produce 2 A4 pages (2 cards on page 1 + 1 card on page 2)
        val items = (1..3).map { i ->
            val st = makeStudent(i, withPhoto = (i == 1))
            AdmitCardStudentItem(
                student = st,
                seat = ExamSeatAllocation(
                    roomId = "r1",
                    roomName = "Room 101",
                    seatNumber = i,
                    studentDocId = "stu_$i",
                    studentName = st.name,
                    studentAdmNo = st.admNo,
                    className = st.className,
                    section = st.section,
                    rollNo = st.rollNo
                ),
                entries = exam.entries
            )
        }

        val pages = Phase11PdfGenerator.renderAdmitCardPreviewBitmaps(
            AdmitCardsPdfBundle(school = school, exam = exam, className = "5th", items = items),
            scale = 1
        )
        assertEquals("3 admit cards at 2/page must produce 2 A4 pages", 2, pages.size)
        pages.forEachIndexed { idx, bmp ->
            verifyA4Bitmap(bmp, "Admit Cards page ${idx + 1}")
        }

        // Also test 2-column compact layout when class has >7 subjects (10 papers)
        val manyEntries = (1..10).map { d ->
            ExamScheduleEntry("m_$d", "8th", "Subject $d", "2026-09-%02d".format(10 + d), "09:00", "12:00", 100)
        }
        val compactPages = Phase11PdfGenerator.renderAdmitCardPreviewBitmaps(
            AdmitCardsPdfBundle(
                school = school,
                exam = exam,
                className = "8th",
                items = listOf(AdmitCardStudentItem(makeStudent(1, withPhoto = true), items[0].seat, manyEntries))
            ),
            scale = 1
        )
        assertEquals(1, compactPages.size)
        verifyA4Bitmap(compactPages[0], "Compact 10-subject Admit Card")
    }

    @Test
    fun seatingListClassTimetableAndDutyListPdfsRenderCleanlyOnA4() {
        val school = makeSchool()
        val exam = sampleExam()

        // Seating list with 30 students -> paginates across 2 A4 pages (26 rows/page)
        val room = ExamRoomRecord(id = "r1", schoolId = "sch_1", roomName = "Room 101", capacity = 30)
        val allocations = (1..30).map { seat ->
            ExamSeatAllocation(
                roomId = room.id,
                roomName = room.roomName,
                seatNumber = seat,
                studentDocId = "stu_$seat",
                studentName = "Student $seat",
                studentAdmNo = "ADM-%03d".format(seat),
                className = if (seat % 2 == 0) "6th" else "5th",
                section = "A",
                rollNo = seat
            )
        }
        val seatingPages = Phase11PdfGenerator.renderSeatingListPreviewBitmaps(
            RoomSeatingPdfBundle(school = school, exam = exam, room = room, allocations = allocations),
            scale = 1
        )
        assertEquals("30 seated students should paginate to 2 pages", 2, seatingPages.size)
        seatingPages.forEachIndexed { idx, bmp ->
            verifyA4Bitmap(bmp, "Room Seating List page ${idx + 1}")
        }

        // Class Exam Timetable
        val timetableBmp = Phase11PdfGenerator.renderClassTimetablePreviewBitmap(
            ExamTimetablePdfBundle(school = school, exam = exam, className = "5th", entries = exam.entries),
            scale = 1
        )
        verifyA4Bitmap(timetableBmp, "Class Exam Timetable")

        // Invigilator Duty List
        val duties = listOf(
            ExamInvigilatorDutyRecord("d1", "sch_1", "2026-2027", exam.id, exam.title, "2026-09-14", "09:00", "12:00", "r1", "Room 101", "t1", "u1", "Anjali Sharma"),
            ExamInvigilatorDutyRecord("d2", "sch_1", "2026-2027", exam.id, exam.title, "2026-09-14", "09:00", "12:00", "r2", "Room 102", "t2", "u2", "Bhavna Verma"),
            ExamInvigilatorDutyRecord("d3", "sch_1", "2026-2027", exam.id, exam.title, "2026-09-15", "09:00", "12:00", "r1", "Room 101", "t2", "u2", "Bhavna Verma")
        )
        val dutyBmp = Phase11PdfGenerator.renderDutyListPreviewBitmap(
            DutyListPdfBundle(school = school, exam = exam, duties = duties),
            scale = 1
        )
        verifyA4Bitmap(dutyBmp, "Invigilator Duty List")
    }

    @Test
    fun stockReportAndTransportRouteAndDriverListPdfsRenderCleanlyOnA4() {
        val school = makeSchool()

        val stockSummaries = listOf(
            StockItemSummary(
                item = OptionalFeeCatalogueItem("cat_tie", "School Tie", 80, unit = "pcs", openingStock = 20, lowStockLevel = 10),
                openingStock = 20, purchasedIn = 30, soldOnReceipts = 15, cancelledReturnCount = 2,
                manualAdjustmentsNet = 0, currentStock = 35, isLowStock = false,
                totalPurchaseSpendRs = 1500, estimatedStockValueRs = 2800
            ),
            StockItemSummary(
                item = OptionalFeeCatalogueItem("cat_belt", "School Belt", 120, unit = "pcs", openingStock = 10, lowStockLevel = 8),
                openingStock = 10, purchasedIn = 0, soldOnReceipts = 6, cancelledReturnCount = 0,
                manualAdjustmentsNet = -1, currentStock = 3, isLowStock = true,
                totalPurchaseSpendRs = 0, estimatedStockValueRs = 360
            ),
            StockItemSummary(
                item = OptionalFeeCatalogueItem("cat_diary", "School Diary", 60, unit = "pcs", openingStock = 5, lowStockLevel = 5),
                openingStock = 5, purchasedIn = 0, soldOnReceipts = 5, cancelledReturnCount = 0,
                manualAdjustmentsNet = 0, currentStock = 0, isLowStock = true,
                totalPurchaseSpendRs = 0, estimatedStockValueRs = 0
            )
        )
        val stockBmp = Phase11PdfGenerator.renderStockReportPreviewBitmap(
            StockReportPdfBundle(
                school = school,
                session = "2026-2027",
                generatedDateIso = "2026-04-20",
                summaries = stockSummaries
            ),
            scale = 1
        )
        verifyA4Bitmap(stockBmp, "Stock Summary Report")

        val route = TransportRouteRecord(
            id = "troute_1",
            schoolId = "sch_1",
            routeName = "North Bus",
            vehicleNumber = "UP-61-T-1234",
            vehicleType = "Bus",
            seatCapacity = 24,
            driverName = "Ramesh Yadav",
            driverPhone = "9876500001",
            monthlyFeeRs = 600,
            stops = listOf(
                TransportStop("s1", "Main Bazar", "07:30 AM", 500),
                TransportStop("s2", "Railway Station", "07:45 AM", 750)
            )
        )
        val routeSummary = TransportRouteSummary(
            route = route,
            assignedStudents = (1..8).map { makeStudent(it) },
            seatsUsed = 8,
            seatsRemaining = 16,
            isOverCapacity = false,
            expectedMonthlyIncomeRs = 5000
        )
        val transportBundle = TransportRoutePdfBundle(
            school = school,
            session = "2026-2027",
            generatedDateIso = "2026-04-20",
            summary = routeSummary
        )

        val routeListBmp = Phase11PdfGenerator.renderTransportRouteListPreviewBitmap(transportBundle, scale = 1)
        verifyA4Bitmap(routeListBmp, "Transport Route Student List")

        val driverListBmp = Phase11PdfGenerator.renderTransportDriverListPreviewBitmap(transportBundle, scale = 1)
        verifyA4Bitmap(driverListBmp, "Transport Driver Contact Sheet")

        val duesSummary = TransportDuesSummary(
            monthCode = "AUG",
            rows = (1..3).map { idx ->
                val st = makeStudent(idx)
                TransportMonthDueStudentRow(
                    student = st,
                    studentDocId = "stu_$idx",
                    routeId = route.id,
                    routeName = route.routeName,
                    stopName = st.transportStopName,
                    tripType = TransportTripType.PICKUP_ONLY,
                    monthCode = "AUG",
                    scheduledRs = st.transportMonthlyAmount,
                    paidRs = 0,
                    dueRs = st.transportMonthlyAmount
                )
            },
            totalStudentsDue = 3,
            totalExpectedRs = 1750,
            totalCollectedRs = 0,
            totalDueRs = 1750,
            dueByRouteName = listOf("North Bus" to 1750)
        )
        val duesPages = Phase11PdfGenerator.renderTransportDuesPreviewBitmaps(
            TransportDuesPdfBundle(
                school = school,
                session = "2026-2027",
                generatedDateIso = "2026-08-10",
                duesSummary = duesSummary,
                unassignedStudents = listOf(makeStudent(99).copy(transportRouteId = "", transportRouteName = "")),
                routes = listOf(route)
            ),
            scale = 1
        )
        assertEquals("Dues table page + schedule/unassigned page = 2 pages", 2, duesPages.size)
        duesPages.forEachIndexed { i, bmp ->
            verifyA4Bitmap(bmp, "Transport Dues PDF page ${i + 1}")
        }

        val plBmp = Phase11PdfGenerator.renderTransportMonthlySummaryPreviewBitmap(
            TransportMonthlySummaryPdfBundle(
                school = school,
                generatedDateIso = "2026-08-31",
                report = TransportMonthlyProfitLossReport(
                    schoolName = school.name,
                    session = "2026-2027",
                    monthCode = "AUG",
                    yearMonthIso = "2026-08",
                    vehicleRows = listOf(
                        TransportVehicleCostSummary(
                            routeId = route.id,
                            routeName = route.routeName,
                            vehicleNumber = route.vehicleNumber,
                            vehicleType = route.vehicleType,
                            expectedIncomeRs = 5000,
                            collectedIncomeRs = 4200,
                            duesRs = 800,
                            expenseRs = 1800,
                            profitOrLossRs = 2400,
                            expenseCount = 2
                        )
                    ),
                    unallocatedExpensesRs = 200,
                    totalExpectedIncomeRs = 5000,
                    totalCollectedIncomeRs = 4200,
                    totalDuesRs = 800,
                    totalExpenseRs = 2000,
                    netProfitOrLossRs = 2200
                )
            ),
            scale = 1
        )
        verifyA4Bitmap(plBmp, "Transport Monthly Income vs Expense Summary")
    }
}
