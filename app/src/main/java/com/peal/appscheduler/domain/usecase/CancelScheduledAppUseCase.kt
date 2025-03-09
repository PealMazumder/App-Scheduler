package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import com.peal.appscheduler.core.domain.util.Result
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 25/2/25.
 */

class CancelScheduledAppUseCase @Inject constructor(
    private val alarmManagerRepository: AlarmManagerRepository,
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(
        packageName: String,
        id: Long,
        scheduleTime: Long?
    ): Result<Unit, ScheduleError> {
        val currentTime = System.currentTimeMillis()

        return if (scheduleTime != null && scheduleTime < currentTime) {
            Result.Failure(ScheduleError.ALREADY_HANDLED)
        } else {
            return alarmManagerRepository.cancelSchedule(packageName, id).fold(
                onSuccess = {
                    try {
                        scheduleRepository.updateScheduleStatus(id, ScheduleStatus.CANCELLED.name)
                        Result.Success(Unit)
                    } catch (e: Exception) {
                        Result.Failure(ScheduleError.DATABASE_ERROR)
                    }
                },
                onFailure = {
                    Result.Failure(ScheduleError.UNKNOWN_ERROR)
                }
            )
        }

    }
}