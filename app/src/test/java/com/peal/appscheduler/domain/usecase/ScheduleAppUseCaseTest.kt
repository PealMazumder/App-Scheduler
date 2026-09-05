package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.fakes.FakeAlarmManagerRepository
import com.peal.appscheduler.fakes.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.peal.appscheduler.core.domain.util.Result as AppResult

class ScheduleAppUseCaseTest {

    private val scheduleRepository = FakeScheduleRepository()
    private val alarmManagerRepository = FakeAlarmManagerRepository()
    private val useCase = ScheduleAppUseCase(scheduleRepository, alarmManagerRepository)

    private val newSchedule = AppSchedule(
        id = 0,
        packageName = "com.example.app",
        appName = "Example",
        scheduledTime = 1_000L,
        status = "SCHEDULED",
    )

    @Before
    fun setUp() {
        scheduleRepository.addScheduleResult = AppResult.Success(42L)
    }

    @Test
    fun `creating a new schedule succeeds when the DB write and the alarm both succeed`() = runTest {
        val result = useCase(newSchedule, isEdit = false)

        assertTrue(result is AppResult.Success)
        assertEquals(42L, (result as AppResult.Success).data)
        assertEquals(1, alarmManagerRepository.scheduleAppCalls)
    }

    @Test
    fun `creating a new schedule reports failure when the alarm fails to arm - regression for H1`() = runTest {
        // Before the H1 fix, ScheduleAppUseCase discarded this result entirely and always
        // reported success, even though no alarm was ever armed.
        alarmManagerRepository.scheduleAppResult = Result.failure(SecurityException("no exact alarm permission"))

        val result = useCase(newSchedule, isEdit = false)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.UNKNOWN_ERROR, (result as AppResult.Failure).error)
    }

    @Test
    fun `creating a new schedule does not attempt to arm an alarm when the DB write is conflicted`() = runTest {
        scheduleRepository.addScheduleResult = AppResult.Failure(ScheduleError.TIME_CONFLICT)

        val result = useCase(newSchedule, isEdit = false)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.TIME_CONFLICT, (result as AppResult.Failure).error)
        assertEquals(0, alarmManagerRepository.scheduleAppCalls)
    }

    @Test
    fun `editing a schedule succeeds when the alarm update succeeds`() = runTest {
        val edited = newSchedule.copy(id = 7L)

        val result = useCase(edited, isEdit = true)

        assertTrue(result is AppResult.Success)
        assertEquals(7L, (result as AppResult.Success).data)
        assertEquals(1, scheduleRepository.updateScheduleCalls)
        assertEquals(edited, scheduleRepository.lastUpdatedSchedule)
    }

    @Test
    fun `editing a schedule reports failure and does not touch the DB when the alarm update fails - regression for H1`() = runTest {
        alarmManagerRepository.updateScheduleResult = Result.failure(SecurityException("no exact alarm permission"))

        val result = useCase(newSchedule.copy(id = 7L), isEdit = true)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.UNKNOWN_ERROR, (result as AppResult.Failure).error)
        assertEquals(0, scheduleRepository.updateScheduleCalls)
    }
}
