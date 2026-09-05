package com.peal.appscheduler.ui.screens.schedule

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.R
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.ui.mappers.toScheduleAppInfoUi
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import com.peal.appscheduler.ui.shared.components.AppIcon
import com.peal.appscheduler.ui.shared.components.AppTopBar
import com.peal.appscheduler.ui.shared.components.CommonAlertDialog
import com.peal.appscheduler.ui.shared.components.DatePickerDialog
import com.peal.appscheduler.ui.shared.components.TimePickerDialog
import com.peal.appscheduler.ui.shared.navigation.AppSchedulerScreen
import com.peal.appscheduler.ui.utils.debounce
import com.peal.appscheduler.ui.utils.openScheduleExactAlarmPermissionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Composable
fun SchedulerScreenRoute(
    modifier: Modifier = Modifier,
    route: AppSchedulerScreen,
    onBack: () -> Unit = {},
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

    SchedulerScreen(
        modifier = modifier,
        state = schedulerScreenState,
        event = schedulerViewModel.effect,
        onIntent = { intent ->
            schedulerViewModel.handleIntent(intent)
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(
    modifier: Modifier = Modifier,
    state: ScheduleContract.State,
    event: Flow<ScheduleContract.Effect>,
    onIntent: (ScheduleContract.Intent) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var showPermissionDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    showPermissionDialog = true
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    HandleSchedulerEvents(events = event, snackbarHostState = snackbarHostState)

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

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(
                    if (state.isEditable) R.string.edit_schedule_title else R.string.new_schedule_title
                ),
                onBack = onBack,
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = {
            ActionButtons(
                onSave = { onIntent(ScheduleContract.Intent.ScheduleApp) },
                onCancel = { onIntent(ScheduleContract.Intent.CancelSchedule) },
                state = state,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.scheduledAppInfo?.let { SelectedAppCard(it) }

            Text(
                text = stringResource(R.string.scheduling),
                style = MaterialTheme.typography.titleMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleFieldRow(
                    iconPainter = painterResource(id = R.drawable.clock_24),
                    label = state.selectedTime ?: stringResource(R.string.select_time),
                    isPlaceholder = state.selectedTime == null,
                    contentDescription = stringResource(R.string.select_time),
                    onClick = { showTimePicker = true },
                )

                ScheduleFieldRow(
                    icon = Icons.Filled.DateRange,
                    label = state.selectedDate ?: stringResource(R.string.select_date),
                    isPlaceholder = state.selectedDate == null,
                    contentDescription = stringResource(R.string.select_date),
                    onClick = { showDatePicker = true },
                )
            }
        }
    }

    if (showDatePicker) {
        val initialSelectedDateMillis = remember(state.scheduledAppInfo?.utcScheduleTime) {
            state.scheduledAppInfo?.utcScheduleTime?.let { utcScheduleTime ->
                Instant.ofEpochMilli(utcScheduleTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }
        }

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = {
                onIntent.invoke(ScheduleContract.Intent.OnDateSelected(it))
                showDatePicker = false
            },
            initialSelectedDateMillis = initialSelectedDateMillis
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
}

@Composable
private fun SelectedAppCard(
    scheduleAppInfo: ScheduleAppInfoUi,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = scheduleAppInfo.icon, appName = scheduleAppInfo.name)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = scheduleAppInfo.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = scheduleAppInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleFieldRow(
    label: String,
    isPlaceholder: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (iconPainter != null) {
                Icon(painter = iconPainter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ActionButtons(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    state: ScheduleContract.State,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val debouncedSave = remember { onSave.debounce(coroutineScope) }
    val debouncedCancel = remember { onCancel.debounce(coroutineScope) }
    val showCancel = state.isEditable && state.scheduledAppInfo?.status == ScheduleStatus.SCHEDULED.name

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showCancel) {
                OutlinedButton(
                    onClick = debouncedCancel,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }

            Button(
                onClick = debouncedSave,
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun HandleSchedulerEvents(
    events: Flow<ScheduleContract.Effect>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun showMessage(resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    ObserveAsEvents(events = events) { event ->
        when (event) {
            is ScheduleContract.Effect.AppScheduled -> showMessage(R.string.app_scheduled_successfully)
            is ScheduleContract.Effect.TimeConflict -> showMessage(R.string.an_app_is_already_scheduled_at_this_time)
            is ScheduleContract.Effect.UnknownError -> showMessage(R.string.an_unexpected_error_occurred_please_try_again)
            is ScheduleContract.Effect.MissingDateTime -> showMessage(R.string.select_both_date_and_time_to_schedule)
            is ScheduleContract.Effect.PastDateTime -> showMessage(R.string.please_select_a_future_date_and_time)
            is ScheduleContract.Effect.PreviousDateTime -> showMessage(R.string.select_another_future_date_and_time)
            is ScheduleContract.Effect.ScheduleCancelled -> showMessage(R.string.schedule_cancelled_successfully)
            is ScheduleContract.Effect.ScheduleAlreadyHandled -> showMessage(R.string.ah_schedule_already_handled)
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
        event = flow { },
    )
}
