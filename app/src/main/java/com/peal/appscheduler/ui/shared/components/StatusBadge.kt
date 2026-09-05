package com.peal.appscheduler.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peal.appscheduler.R
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.ui.theme.StatusCancelled
import com.peal.appscheduler.ui.theme.StatusExecuted
import com.peal.appscheduler.ui.theme.StatusFailed
import com.peal.appscheduler.ui.theme.StatusScheduled

private data class StatusStyle(val labelRes: Int, val color: Color)

private fun styleFor(status: String?): StatusStyle = when (status) {
    ScheduleStatus.SCHEDULED.name -> StatusStyle(R.string.status_scheduled, StatusScheduled)
    ScheduleStatus.EXECUTED.name -> StatusStyle(R.string.status_executed, StatusExecuted)
    ScheduleStatus.FAILED.name -> StatusStyle(R.string.status_failed, StatusFailed)
    ScheduleStatus.CANCELLED.name -> StatusStyle(R.string.status_cancelled, StatusCancelled)
    else -> StatusStyle(R.string.status_scheduled, StatusScheduled)
}

@Composable
fun StatusBadge(status: String?, modifier: Modifier = Modifier) {
    val style = styleFor(status)
    Text(
        text = stringResource(style.labelRes),
        color = style.color,
        fontSize = 12.sp,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
