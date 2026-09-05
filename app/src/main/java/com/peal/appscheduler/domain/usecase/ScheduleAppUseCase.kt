package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import com.peal.appscheduler.core.domain.util.Result
import com.peal.appscheduler.core.domain.util.onError
import com.peal.appscheduler.core.domain.util.onSuccess
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleAppUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val alarmManagerRepository: AlarmManagerRepository,
) {
    suspend operator fun invoke(
        schedule: AppSchedule,
        isEdit: Boolean
    ): Result<Long, ScheduleError> {
        return try {
            val scheduleId: Long = schedule.id

            if (isEdit) {
                val alarmResult = alarmManagerRepository.updateSchedule(
                    schedule.packageName,
                    schedule.scheduledTime,
                    scheduleId
                )
                if (alarmResult.isFailure) {
                    return Result.Failure(ScheduleError.UNKNOWN_ERROR)
                }
                scheduleRepository.updateSchedule(schedule)
                Result.Success(scheduleId)
            } else {
                scheduleRepository.addSchedule(schedule)
                    .onSuccess { id ->
                        val alarmResult = alarmManagerRepository.scheduleApp(
                            schedule.packageName,
                            schedule.scheduledTime,
                            id
                        )
                        if (alarmResult.isFailure) {
                            return Result.Failure(ScheduleError.UNKNOWN_ERROR)
                        }
                    }.onError {
                        return Result.Failure(it)
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(ScheduleError.UNKNOWN_ERROR)
        }
    }
}

