package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.SavedStateHandle
import com.peal.appscheduler.MainDispatcherRule
import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.usecase.CancelScheduledAppUseCase
import com.peal.appscheduler.domain.usecase.ScheduleAppUseCase
import com.peal.appscheduler.domain.utils.toFormattedDate
import com.peal.appscheduler.domain.utils.toFormattedTime
import com.peal.appscheduler.fakes.FakeAlarmManagerRepository
import com.peal.appscheduler.fakes.FakeScheduleRepository
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import com.peal.appscheduler.core.domain.util.Result as AppResult

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scheduleRepository = FakeScheduleRepository()
    private val alarmManagerRepository = FakeAlarmManagerRepository()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        SchedulerViewModel(
            scheduleAppUseCase = ScheduleAppUseCase(scheduleRepository, alarmManagerRepository),
            cancelScheduledAppUseCase = CancelScheduledAppUseCase(alarmManagerRepository, scheduleRepository),
            savedStateHandle = savedStateHandle,
        )

    private val appInfo = ScheduleAppInfoUi(
        id = 1L,
        name = "Example",
        packageName = "com.example.app",
        icon = null,
    )

    private val futureDate: LocalDate = LocalDate.now().plusDays(10)
    private val futureTime: LocalTime = LocalTime.of(9, 0)

    @Before
    fun setUp() {
        scheduleRepository.addScheduleResult = AppResult.Success(99L)
    }

    @Test
    fun `selecting a date and time updates the displayed state`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo)

        viewModel.handleIntent(ScheduleContract.Intent.OnDateSelected(futureDate))
        viewModel.handleIntent(ScheduleContract.Intent.OnTimeSelected(futureTime))

        val state = viewModel.schedulerScreenState.value
        assertEquals(futureDate.toFormattedDate(), state.selectedDate)
        assertEquals(futureTime.toFormattedTime(), state.selectedTime)
    }

    @Test
    fun `scheduling with no date or time emits MissingDateTime`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo)

        viewModel.handleIntent(ScheduleContract.Intent.ScheduleApp)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.MissingDateTime, viewModel.effect.first())
    }

    @Test
    fun `scheduling a past date and time emits PastDateTime and does not call the use case`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo)
        viewModel.handleIntent(ScheduleContract.Intent.OnDateSelected(LocalDate.now().minusDays(1)))
        viewModel.handleIntent(ScheduleContract.Intent.OnTimeSelected(LocalTime.of(0, 0)))

        viewModel.handleIntent(ScheduleContract.Intent.ScheduleApp)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.PastDateTime, viewModel.effect.first())
        assertEquals(0, alarmManagerRepository.scheduleAppCalls)
    }

    @Test
    fun `scheduling a future date and time succeeds and emits AppScheduled`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo)
        viewModel.handleIntent(ScheduleContract.Intent.OnDateSelected(futureDate))
        viewModel.handleIntent(ScheduleContract.Intent.OnTimeSelected(futureTime))

        viewModel.handleIntent(ScheduleContract.Intent.ScheduleApp)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.AppScheduled, viewModel.effect.first())
        assertEquals(1, alarmManagerRepository.scheduleAppCalls)
        assertEquals(false, viewModel.schedulerScreenState.value.isLoading)
    }

    @Test
    fun `a time-conflicting schedule emits TimeConflict`() = runTest {
        scheduleRepository.addScheduleResult = AppResult.Failure(ScheduleError.TIME_CONFLICT)
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo)
        viewModel.handleIntent(ScheduleContract.Intent.OnDateSelected(futureDate))
        viewModel.handleIntent(ScheduleContract.Intent.OnTimeSelected(futureTime))

        viewModel.handleIntent(ScheduleContract.Intent.ScheduleApp)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.TimeConflict, viewModel.effect.first())
    }

    @Test
    fun `cancelling a schedule succeeds and emits ScheduleCancelled`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo.copy(utcScheduleTime = System.currentTimeMillis() + 60_000L))

        viewModel.handleIntent(ScheduleContract.Intent.CancelSchedule)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.ScheduleCancelled, viewModel.effect.first())
        assertEquals(1, alarmManagerRepository.cancelCalls)
    }

    @Test
    fun `cancelling an already-past schedule emits ScheduleAlreadyHandled`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAppInfo(appInfo.copy(utcScheduleTime = System.currentTimeMillis() - 60_000L))

        viewModel.handleIntent(ScheduleContract.Intent.CancelSchedule)
        advanceUntilIdle()

        assertEquals(ScheduleContract.Effect.ScheduleAlreadyHandled, viewModel.effect.first())
    }

    @Test
    fun `a pending unsaved selection restored from SavedStateHandle is not overwritten by nav-provided time - regression for MED-F`() = runTest {
        // Keys must match SchedulerViewModel's private KEY_SELECTED_DATE/KEY_SELECTED_TIME.
        val restoredHandle = SavedStateHandle(
            mapOf(
                "selected_date" to futureDate.toString(),
                "selected_time" to futureTime.toString(),
            )
        )
        val viewModel = createViewModel(restoredHandle)

        // Simulate the normal nav flow for a brand-new (non-edit) schedule, which would
        // otherwise reset the displayed date/time to blank via updateScheduleTime(null).
        viewModel.updateAppInfo(appInfo)

        val state = viewModel.schedulerScreenState.value
        assertEquals(futureDate.toFormattedDate(), state.selectedDate)
        assertEquals(futureTime.toFormattedTime(), state.selectedTime)
    }
}
