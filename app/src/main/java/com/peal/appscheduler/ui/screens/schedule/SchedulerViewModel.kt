package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.usecase.CancelScheduledAppUseCase
import com.peal.appscheduler.domain.usecase.ScheduleAppUseCase
import com.peal.appscheduler.domain.utils.ScheduleResult
import com.peal.appscheduler.domain.utils.toFormattedDate
import com.peal.appscheduler.domain.utils.toFormattedPattern
import com.peal.appscheduler.domain.utils.toFormattedTime
import com.peal.appscheduler.domain.utils.toLocalDate
import com.peal.appscheduler.domain.utils.toLocalTime
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var previousScheduleTimeInMilli: Long? = null

    fun updateAppInfo(scheduleAppInfo: ScheduleAppInfoUi?) {
        _schedulerScreenState.update {
            it.copy(
                scheduledAppInfo = scheduleAppInfo,
                isEdit = scheduleAppInfo?.utcScheduleTime != null
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
                insertSchedule(schedulerScreenState.value.isEdit)
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
                    _schedulerScreenState.update { it.copy(isLoading = true) }
                    delay(500)
                    schedulerScreenState.value.scheduledAppInfo?.let { appInfo ->
                        cancelScheduledAppUseCase.invoke(appInfo.packageName, appInfo.id)
                    }

                    _schedulerScreenState.update { it.copy(isLoading = false) }
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
                        when (result) {
                            is ScheduleResult.Success -> {
                                _schedulerScreenState.update {
                                    it.copy(
                                        isLoading = false,
                                        message = "App scheduled successfully"
                                    )
                                }

                                previousScheduleTimeInMilli = scheduledTime
                            }

                            is ScheduleResult.Error -> {
                                _schedulerScreenState.update {
                                    it.copy(isLoading = false, message = result.message)
                                }
                            }
                        }
                    }
                }
            } else {
                _schedulerScreenState.update {
                    it.copy(
                        isLoading = false,
                        message = "Please select both date and time for scheduling."
                    )
                }
            }
        }
    }


}

