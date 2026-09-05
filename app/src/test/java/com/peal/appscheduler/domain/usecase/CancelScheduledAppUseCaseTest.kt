package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.fakes.FakeAlarmManagerRepository
import com.peal.appscheduler.fakes.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.peal.appscheduler.core.domain.util.Result as AppResult

class CancelScheduledAppUseCaseTest {

    private val scheduleRepository = FakeScheduleRepository()
    private val alarmManagerRepository = FakeAlarmManagerRepository()
    private val useCase = CancelScheduledAppUseCase(alarmManagerRepository, scheduleRepository)

    @Test
    fun `cancelling a future schedule succeeds and marks it cancelled`() = runTest {
        val future = System.currentTimeMillis() + 60_000L

        val result = useCase("com.example.app", id = 1L, scheduleTime = future)

        assertTrue(result is AppResult.Success)
        assertEquals(1, alarmManagerRepository.cancelCalls)
        assertEquals(1, scheduleRepository.updateStatusCalls)
        assertEquals(ScheduleStatus.CANCELLED.name, scheduleRepository.lastStatus)
    }

    @Test
    fun `cancelling a schedule with no known time still cancels the alarm`() = runTest {
        val result = useCase("com.example.app", id = 1L, scheduleTime = null)

        assertTrue(result is AppResult.Success)
        assertEquals(1, alarmManagerRepository.cancelCalls)
    }

    @Test
    fun `cancelling an already-past schedule is rejected without touching the alarm`() = runTest {
        val past = System.currentTimeMillis() - 60_000L

        val result = useCase("com.example.app", id = 1L, scheduleTime = past)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.ALREADY_HANDLED, (result as AppResult.Failure).error)
        assertEquals(0, alarmManagerRepository.cancelCalls)
    }

    @Test
    fun `a failed alarm cancellation is reported and the DB status is left untouched`() = runTest {
        alarmManagerRepository.cancelResult = Result.failure(SecurityException("no permission"))
        val future = System.currentTimeMillis() + 60_000L

        val result = useCase("com.example.app", id = 1L, scheduleTime = future)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.UNKNOWN_ERROR, (result as AppResult.Failure).error)
        assertEquals(0, scheduleRepository.updateStatusCalls)
    }

    @Test
    fun `a DB failure after a successful alarm cancellation is reported as DATABASE_ERROR`() = runTest {
        scheduleRepository.throwOnUpdateStatus = true
        val future = System.currentTimeMillis() + 60_000L

        val result = useCase("com.example.app", id = 1L, scheduleTime = future)

        assertTrue(result is AppResult.Failure)
        assertEquals(ScheduleError.DATABASE_ERROR, (result as AppResult.Failure).error)
    }
}
