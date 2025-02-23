package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.repository.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleStatus
import com.peal.appscheduler.domain.usecase.ScheduleAppUseCase
import com.peal.appscheduler.domain.utils.ScheduleResult
import com.peal.appscheduler.domain.utils.toFormattedDate
import com.peal.appscheduler.domain.utils.toFormattedTime
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val scheduleAppUseCase: ScheduleAppUseCase
) : ViewModel() {

    private val _schedulerScreenState = MutableStateFlow(SchedulerScreenState())
    val schedulerScreenState: StateFlow<SchedulerScreenState> = _schedulerScreenState

    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null

    fun updateAppInfo(appInfo: DeviceAppInfo?) {
        _schedulerScreenState.update { it.copy(appInfo = appInfo) }
    }

    fun handleIntent(intent: SchedulerScreenIntent) {
        when (intent) {
            is SchedulerScreenIntent.ScheduleApp -> {
                schedulerScreenState.value.appInfo?.let { appInfo ->
                    val date = selectedDate
                    val time = selectedTime

                    if (date != null && time != null) {
                        _schedulerScreenState.update {
                            it.copy(isLoading = true)
                        }
                        val scheduledTime =
                            date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
                                .toEpochMilli()

                        viewModelScope.launch {
                            scheduleAppUseCase.invoke(
                                AppSchedule(
                                    appName = appInfo.name,
                                    packageName = appInfo.packageName,
                                    status = ScheduleStatus.SCHEDULED.name,
                                    scheduledTime = scheduledTime
                                )
                            ).let { result ->
                                when (result) {
                                    is ScheduleResult.Success -> {
                                        _schedulerScreenState.update {
                                            it.copy(
                                                isLoading = false,
                                                message = "App scheduled successfully"
                                            )
                                        }
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
        }
    }
}

