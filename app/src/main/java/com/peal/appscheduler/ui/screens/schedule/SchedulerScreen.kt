package com.peal.appscheduler.ui.screens.schedule

import android.app.AlarmManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.peal.appscheduler.R
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.mappers.toDeviceAppInfo
import com.peal.appscheduler.domain.mappers.toScheduleAppInfoUi
import com.peal.appscheduler.domain.utils.isAndroidTIRAMISUOrLater
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import com.peal.appscheduler.ui.screens.deviceApps.InstalledAppItem
import com.peal.appscheduler.ui.shared.components.CommonAlertDialog
import com.peal.appscheduler.ui.shared.components.CommonCircularProgressIndicator
import com.peal.appscheduler.ui.shared.components.DatePickerDialog
import com.peal.appscheduler.ui.shared.components.TimePickerDialog
import com.peal.appscheduler.ui.shared.navigation.Screens
import com.peal.appscheduler.ui.shared.viewModel.SharedDeviceAppViewModel
import com.peal.appscheduler.ui.utils.debounce
import com.peal.appscheduler.ui.utils.openScheduleExactAlarmPermissionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime


/**
 * Created by Peal Mazumder on 23/2/25.
 */


@Composable
fun SchedulerScreenRoute(
    modifier: Modifier = Modifier,
    navController: NavController,
    route: Screens.AppSchedulerScreen,
    schedulerViewModel: SchedulerViewModel = hiltViewModel(),
) {
    val schedulerScreenState by schedulerViewModel.schedulerScreenState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val appInfo = remember(route, context) {
        route.toScheduleAppInfoUi(context)
    }

    LaunchedEffect(appInfo) {
        schedulerViewModel.updateAppInfo(appInfo)
    }

    LaunchedEffect(appInfo) {
        schedulerViewModel.updateAppInfo(appInfo)
    }

    SchedulerScreen(
        modifier = modifier,
        state = schedulerScreenState,
        event = schedulerViewModel.effect,
        onIntent = { intent ->
            schedulerViewModel.handleIntent(intent)
        }
    )

}
@Composable
fun SchedulerScreen(
    modifier: Modifier = Modifier,
    state: ScheduleContract.State,
    event: Flow<ScheduleContract.Effect>,
    onIntent: (ScheduleContract.Intent) -> Unit = {},
) {
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var showPermissionDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isAndroidTIRAMISUOrLater() && !alarmManager.canScheduleExactAlarms()) {
                    showPermissionDialog = true
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HandleSchedulerEvents(events = event, context = context)

    if (showPermissionDialog) {
        CommonAlertDialog(
            title = stringResource(R.string.permission_required),
            message = stringResource(R.string.this_app_needs_permission_to_schedule_exact_alarms),
            confirmText = stringResource(R.string.grant_permission),
            onConfirm = {
                context.openScheduleExactAlarmPermissionSettings()
                showPermissionDialog = false
            },
            onDismiss = { showPermissionDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        AppSection(state.scheduledAppInfo)

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
                onIntent.invoke(ScheduleContract.Intent.ScheduleApp)
            },
            onCancel = {
                onIntent.invoke(ScheduleContract.Intent.CancelSchedule)
            },
            state
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = {
                onIntent.invoke(ScheduleContract.Intent.OnDateSelected(it))
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onTimeSelected = {
                onIntent.invoke(ScheduleContract.Intent.OnTimeSelected(it))
                showTimePicker = false
            }
        )
    }

    if (state.isLoading) {
        CommonCircularProgressIndicator(modifier = modifier)
    }
}


@Composable
private fun AppSection(scheduleAppInfo: ScheduleAppInfoUi?) {
    scheduleAppInfo?.let {
        Text(
            text = stringResource(R.string.app),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start
        )

        InstalledAppItem(app = scheduleAppInfo.toDeviceAppInfo())
    }
}

@Composable
private fun ActionButtons(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    state: ScheduleContract.State,
) {
    val coroutineScope = rememberCoroutineScope()
    val debouncedSave = remember { onSave.debounce(coroutineScope) }
    val debouncedCancel = remember { onCancel.debounce(coroutineScope) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isEditable && state.scheduledAppInfo?.status == ScheduleStatus.SCHEDULED.name) {
            Button(
                onClick = debouncedCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        Button(
            onClick = debouncedSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.save))
        }
    }
}


@Composable
private fun HandleSchedulerEvents(
    events: Flow<ScheduleContract.Effect>,
    context: Context
) {
    ObserveAsEvents(events = events) { event ->
        when (event) {
            is ScheduleContract.Effect.AppScheduled -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.app_scheduled_successfully),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.TimeConflict -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.an_app_is_already_scheduled_at_this_time),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.UnknownError -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.an_unexpected_error_occurred_please_try_again),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.MissingDateTime -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.select_both_date_and_time_to_schedule),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.PastDateTime -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.please_select_a_future_date_and_time),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.PreviousDateTime -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.select_another_future_date_and_time),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.ScheduleCancelled -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.schedule_cancelled_successfully),
                    Toast.LENGTH_SHORT
                ).show()
            }

            is ScheduleContract.Effect.ScheduleAlreadyHandled -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.ah_schedule_already_handled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}




@Preview(showBackground = true)
@Composable
fun SchedulerScreenPreview() {
    SchedulerScreen(
        state = ScheduleContract.State(
            scheduledAppInfo = ScheduleAppInfoUi(
                name = "Sample App",
                packageName = "com.example.app",
                icon = null,
                status = ScheduleStatus.SCHEDULED.name,
                id = 1
            ),
            selectedDate = LocalDate.now().toString(),
            selectedTime = LocalTime.now().toString()
        ),
        event = flow {  },
    )
}


