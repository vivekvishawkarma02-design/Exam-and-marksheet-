package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DayCalendarStatus
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolCalendarEngine
import com.example.domain.SchoolTodayAttendanceSummary
import com.example.domain.SectionDailyAttendanceStatus
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

/**
 * Phase 8 Class Teacher Home Card:
 * Shows each section where the signed-in teacher is Class Teacher, whether today's
 * student attendance has been taken, and a prominent 1-tap "Take Attendance" button.
 */
@Composable
fun ClassTeacherAttendanceHomeCard(
    uiState: SchoolUiState,
    onTakeAttendance: (schoolDocId: String, className: String, section: String, dateIso: String) -> Unit
) {
    val todayIso = Phase6DateUtils.todayIso()
    val ctCards: List<SectionDailyAttendanceStatus> = uiState.computeClassTeacherTodayCards(todayIso)
    if (ctCards.isEmpty()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class_teacher_attendance_home_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.FactCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "My Class Attendance (Today)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SchoolCalendarEngine.formatShortHumanDate(todayIso),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ctCards.forEachIndexed { idx, card ->
                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val cal = uiState.calendarForSchool(card.schoolDocId, uiState.filter.session)
                val dayStatus = SchoolCalendarEngine.dayStatus(cal, todayIso)
                val isHoliday = dayStatus is DayCalendarStatus.NonWorking

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Class ${card.className}-${card.section}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = card.schoolName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        when {
                            isHoliday -> {
                                val reason = (dayStatus as DayCalendarStatus.NonWorking).reasonTitle
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            card.taken -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Taken",
                                        tint = PassGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Taken • ${card.presentEquivalent} P / ${card.absentCount} A (${card.presentPercent}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PassGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = "Pending",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Not taken yet • ${card.totalStudents} student(s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))
                    if (card.taken) {
                        OutlinedButton(
                            onClick = { onTakeAttendance(card.schoolDocId, card.className, card.section, todayIso) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ct_view_attendance_${card.className}_${card.section}")
                        ) {
                            Text("View / Edit")
                        }
                    } else {
                        Button(
                            onClick = { onTakeAttendance(card.schoolDocId, card.className, card.section, todayIso) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ct_take_attendance_${card.className}_${card.section}")
                        ) {
                            Text(if (isHoliday) "Open" else "Take Attendance")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phase 8 Admin / Attendance Viewer Home Summary Tile:
 * Shows today's student attendance summary per school: sections taken / total and present %.
 */
@Composable
fun AdminStudentAttendanceSummaryCard(
    uiState: SchoolUiState,
    onOpenStudentAttendance: (schoolDocId: String, dateIso: String) -> Unit
) {
    if (!uiState.canAccessStudentAttendance) return
    val todayIso = Phase6DateUtils.todayIso()
    val summaries: List<SchoolTodayAttendanceSummary> = uiState.computeTodayAttendanceSummaries(todayIso)
    if (summaries.isEmpty()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_student_attendance_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Today's Student Attendance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${SchoolCalendarEngine.formatShortHumanDate(todayIso)} • Session ${uiState.filter.session}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onOpenStudentAttendance(uiState.activeSchoolDocId, todayIso) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("open_student_attendance_btn")
                ) {
                    Text("Open")
                }
            }

            summaries.forEach { summary ->
                val isHoliday = summary.dayStatus is DayCalendarStatus.NonWorking
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStudentAttendance(summary.schoolDocId, todayIso) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = summary.schoolName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subLine = when (val ds = summary.dayStatus) {
                                is DayCalendarStatus.NonWorking -> ds.reasonTitle
                                is DayCalendarStatus.Working -> {
                                    val halfTag = if (ds.isHalfWorkingDay) " • Half Day" else ""
                                    "Sections taken: ${summary.sectionsTaken}/${summary.totalSections}$halfTag"
                                }
                            }
                            Text(
                                text = subLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isHoliday && summary.sectionsTaken > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = PassGreenContainer
                            ) {
                                Text(
                                    text = "${summary.presentPercent}% Present",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PassGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        } else if (!isHoliday) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "0/${summary.totalSections} Taken",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Off",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
