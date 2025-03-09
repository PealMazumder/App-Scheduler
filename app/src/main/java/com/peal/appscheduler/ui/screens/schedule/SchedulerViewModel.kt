package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.core.domain.util.onError
import com.peal.appscheduler.core.domain.util.onSuccess
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.usecase.CancelScheduledAppUseCase
import com.peal.appscheduler.domain.usecase.ScheduleAppUseCase
import com.peal.appscheduler.domain.utils.toFormattedDate
import com.peal.appscheduler.domain.utils.toFormattedPattern
import com.peal.appscheduler.domain.utils.toFormattedTime
import com.peal.appscheduler.domain.utils.toLocalDate
import com.peal.appscheduler.domain.utils.toLocalTime
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class SchedulerViewModel @Inject constructor(
    private val scheduleAppUseCase: ScheduleAppUseCase,
    private val cancelScheduledAppUseCase: CancelScheduledAppUseCase,
) : ViewModel() {

    private val _schedulerScreenState = MutableStateFlow(SchedulerScreenState())
    val schedulerScreenState: StateFlow<SchedulerScreenState> = _schedulerScreenState

    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null

    private val _events = Channel<SchedulerScreenEvent>()
    val events = _events.receiveAsFlow()

    private var previousScheduleTimeInMilli: Long? = null

    fun updateAppInfo(scheduleAppInfo: ScheduleAppInfoUi?) {
        _schedulerScreenState.update {
            it.copy(
                scheduledAppInfo = scheduleAppInfo,
                isEditable = scheduleAppInfo?.utcScheduleTime != null
            )
        }

        updateScheduleTime(scheduleAppInfo?.utcScheduleTime)
    }

    private fun updateScheduleTime(time: Long?) {
        previousScheduleTimeInMilli = time
        _schedulerScreenState.update {
            it.copy(
                selectedDate = time?.toFormattedPattern(),
                selectedTime = time?.toFormattedPattern(toPattern = "hh:mm a")
            )
        }
    }

    fun handleIntent(intent: SchedulerScreenIntent) {
        when (intent) {
            is SchedulerScreenIntent.ScheduleApp -> {
                insertSchedule(schedulerScreenState.value.isEditable)
            }

            is SchedulerScreenIntent.OnDateSelected -> {
                selectedDate = intent.date
                _schedulerScreenState.update { it.copy(selectedDate = intent.date.toFormattedDate()) }
            }

            is SchedulerScreenIntent.OnTimeSelected -> {
                selectedTime = intent.time
                _schedulerScreenState.update {
                    it.copy(selectedTime = intent.time.toFormattedTime())
                }
            }

            is SchedulerScreenIntent.CancelSchedule -> {
                viewModelScope.launch {
                    val scheduleTime = schedulerScreenState.value.scheduledAppInfo?.utcScheduleTime
                    _schedulerScreenState.update { it.copy(isLoading = true) }
                    schedulerScreenState.value.scheduledAppInfo?.let { appInfo ->
                        cancelScheduledAppUseCase.invoke(
                            appInfo.packageName,
                            appInfo.id,
                            scheduleTime
                        ).let { result ->
                            delay(500)
                            result.onSuccess {
                                _schedulerScreenState.update { it.copy(isLoading = false) }
                                viewModelScope.launch {
                                    _events.send(SchedulerScreenEvent.ScheduleCancelled)
                                }
                            }
                            result.onError { error ->
                                _schedulerScreenState.update { it.copy(isLoading = false) }
                                viewModelScope.launch {
                                    when (error) {
                                        ScheduleError.ALREADY_HANDLED -> _events.send(
                                            SchedulerScreenEvent.ScheduleAlreadyHandled
                                        )

                                        else -> _events.send(SchedulerScreenEvent.UnknownError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun insertSchedule(edit: Boolean) {
        schedulerScreenState.value.scheduledAppInfo?.let { appInfo ->
            val date = selectedDate ?: _schedulerScreenState.value.selectedDate?.toLocalDate()
            val time = selectedTime ?: _schedulerScreenState.value.selectedTime?.toLocalTime()

            if (date != null && time != null) {
                _schedulerScreenState.update {
                    it.copy(isLoading = true)
                }
                val scheduledTime =
                    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
                        .toEpochMilli()

                if (scheduledTime == previousScheduleTimeInMilli) {
                    _schedulerScreenState.update { it.copy(isLoading = false) }
                    viewModelScope.launch {
                        _events.send(SchedulerScreenEvent.PreviousDateTime)
                    }
                    return
                } else if (scheduledTime < System.currentTimeMillis()) {
                    _schedulerScreenState.update { it.copy(isLoading = false) }
                    viewModelScope.launch {
                        _events.send(SchedulerScreenEvent.PastDateTime)
                    }
                    return
                }

                viewModelScope.launch {
                    delay(500)
                    scheduleAppUseCase.invoke(
                        AppSchedule(
                            id = appInfo.id,
                            appName = appInfo.name,
                            packageName = appInfo.packageName,
                            status = ScheduleStatus.SCHEDULED.name,
                            scheduledTime = scheduledTime
                        ),
                        edit
                    ).let { result ->
                        result.onSuccess {
                            previousScheduleTimeInMilli = scheduledTime
                            _schedulerScreenState.update { it.copy(isLoading = false) }
                            _events.send(SchedulerScreenEvent.AppScheduled)
                        }.onError {
                            _schedulerScreenState.update { it.copy(isLoading = false) }
                            when (it) {
                                ScheduleError.TIME_CONFLICT -> {
                                    _events.send(SchedulerScreenEvent.TimeConflict)
                                }

                                else -> {
                                    _events.send(SchedulerScreenEvent.UnknownError)
                                }
                            }

                        }
                    }
                }
            } else {
                _schedulerScreenState.update { it.copy(isLoading = false) }
                viewModelScope.launch {
                    _events.send(SchedulerScreenEvent.MissingDateTime)
                }
            }
        }
    }


}

