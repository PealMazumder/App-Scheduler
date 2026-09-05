package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.SavedStateHandle
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _schedulerScreenState = MutableStateFlow(ScheduleContract.State())
    val schedulerScreenState: StateFlow<ScheduleContract.State> = _schedulerScreenState

    // Backed by SavedStateHandle (as ISO date/time strings) so an in-progress pick survives
    // process death, instead of being silently lost like a plain instance var would be.
    private var selectedDate: LocalDate?
        get() = savedStateHandle.get<String>(KEY_SELECTED_DATE)?.let(LocalDate::parse)
        set(value) {
            savedStateHandle[KEY_SELECTED_DATE] = value?.toString()
        }

    private var selectedTime: LocalTime?
        get() = savedStateHandle.get<String>(KEY_SELECTED_TIME)?.let(LocalTime::parse)
        set(value) {
            savedStateHandle[KEY_SELECTED_TIME] = value?.toString()
        }

    private val _effect = Channel<ScheduleContract.Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var previousScheduleTimeInMilli: Long? = null

    init {
        // Restore the displayed picker text for a pending, unsaved selection that survived
        // process death - updateAppInfo() below must not clobber it with the original nav-provided
        // time before the user gets a chance to save it.
        if (selectedDate != null || selectedTime != null) {
            _schedulerScreenState.update {
                it.copy(
                    selectedDate = selectedDate?.toFormattedDate(),
                    selectedTime = selectedTime?.toFormattedTime()
                )
            }
        }
    }

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

        // Don't overwrite a pending, unsaved selection (e.g. one restored after process death)
        // with the original nav-provided time.
        if (selectedDate != null || selectedTime != null) return

        _schedulerScreenState.update {
            it.copy(
                selectedDate = time?.toFormattedPattern(),
                selectedTime = time?.toFormattedPattern(toPattern = "hh:mm a")
            )
        }
    }

    fun handleIntent(intent: ScheduleContract.Intent) {
        when (intent) {
            is ScheduleContract.Intent.ScheduleApp -> {
                insertSchedule(schedulerScreenState.value.isEditable)
            }

            is ScheduleContract.Intent.OnDateSelected -> {
                selectedDate = intent.date
                _schedulerScreenState.update { it.copy(selectedDate = intent.date.toFormattedDate()) }
            }

            is ScheduleContract.Intent.OnTimeSelected -> {
                selectedTime = intent.time
                _schedulerScreenState.update {
                    it.copy(selectedTime = intent.time.toFormattedTime())
                }
            }

            is ScheduleContract.Intent.CancelSchedule -> {
                viewModelScope.launch {
                    val scheduleTime = schedulerScreenState.value.scheduledAppInfo?.utcScheduleTime
                    _schedulerScreenState.update { it.copy(isLoading = true) }
                    schedulerScreenState.value.scheduledAppInfo?.let { appInfo ->
                        cancelScheduledAppUseCase.invoke(
                            appInfo.packageName,
                            appInfo.id,
                            scheduleTime
                        ).let { result ->
                            result.onSuccess {
                                _schedulerScreenState.update { it.copy(isLoading = false) }
                                viewModelScope.launch {
                                    _effect.send(ScheduleContract.Effect.ScheduleCancelled)
                                }
                            }
                            result.onError { error ->
                                _schedulerScreenState.update { it.copy(isLoading = false) }
                                viewModelScope.launch {
                                    when (error) {
                                        ScheduleError.ALREADY_HANDLED -> _effect.send(
                                            ScheduleContract.Effect.ScheduleAlreadyHandled
                                        )

                                        else -> _effect.send(ScheduleContract.Effect.UnknownError)
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
                        _effect.send(ScheduleContract.Effect.PreviousDateTime)
                    }
                    return
                } else if (scheduledTime < System.currentTimeMillis()) {
                    _schedulerScreenState.update { it.copy(isLoading = false) }
                    viewModelScope.launch {
                        _effect.send(ScheduleContract.Effect.PastDateTime)
                    }
                    return
                }

                viewModelScope.launch {
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
                            _effect.send(ScheduleContract.Effect.AppScheduled)
                        }.onError {
                            _schedulerScreenState.update { it.copy(isLoading = false) }
                            when (it) {
                                ScheduleError.TIME_CONFLICT -> {
                                    _effect.send(ScheduleContract.Effect.TimeConflict)
                                }

                                else -> {
                                    _effect.send(ScheduleContract.Effect.UnknownError)
                                }
                            }

                        }
                    }
                }
            } else {
                _schedulerScreenState.update { it.copy(isLoading = false) }
                viewModelScope.launch {
                    _effect.send(ScheduleContract.Effect.MissingDateTime)
                }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_DATE = "selected_date"
        const val KEY_SELECTED_TIME = "selected_time"
    }
}

