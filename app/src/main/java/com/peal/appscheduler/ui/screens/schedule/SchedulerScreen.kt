package com.peal.appscheduler.ui.screens.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.peal.appscheduler.R
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.ui.screens.components.DatePickerDialog
import com.peal.appscheduler.ui.screens.components.TimePickerDialog
import com.peal.appscheduler.ui.screens.deviceApps.InstalledAppItem
import java.time.LocalDate
import java.time.LocalTime


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Composable
fun SchedulerScreen(
    modifier: Modifier = Modifier,
    state: SchedulerScreenState,
    onIntent: (SchedulerScreenIntent) -> Unit = {},
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        AppSection(state.appInfo)

        Text(
            text = stringResource(R.string.scheduling),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimePicker = true },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.clock_24),
                contentDescription = stringResource(R.string.select_time)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.selectedTime ?: stringResource(R.string.select_time),
                style = MaterialTheme.typography.bodyLarge
            )
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = stringResource(R.string.select_date),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.selectedDate ?: stringResource(R.string.select_date),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        ActionButtons(
            onSave = {
                onIntent.invoke(SchedulerScreenIntent.ScheduleApp)
            },
            onCancel = {

            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = {
                onIntent.invoke(SchedulerScreenIntent.OnDateSelected(it))
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onTimeSelected = {
                onIntent.invoke(SchedulerScreenIntent.OnTimeSelected(it))
                showTimePicker = false
            }
        )
    }
}


@Composable
private fun AppSection(appInfo: DeviceAppInfo?) {
    appInfo?.let {
        Text(
            text = stringResource(R.string.app),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start
        )

        InstalledAppItem(app = it)
    }
}

@Composable
private fun ActionButtons(
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(stringResource(R.string.cancel))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.save))
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SchedulerScreenPreview() {
    SchedulerScreen(
        state = SchedulerScreenState(
            appInfo = DeviceAppInfo(
                name = "Sample App",
                packageName = "com.example.app",
                icon = null
            ),
            selectedDate = LocalDate.now().toString(),
            selectedTime = LocalTime.now().toString()
        ),
    )
}


