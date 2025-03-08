package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import com.peal.appscheduler.core.domain.util.Result
import com.peal.appscheduler.core.domain.util.onError
import com.peal.appscheduler.core.domain.util.onSuccess
import javax.inject.Inject


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
        return runCatching {
            val scheduleId: Long = schedule.id

            if (isEdit) {
                alarmManagerRepository.updateSchedule(
                    schedule.packageName,
                    schedule.scheduledTime,
                    scheduleId
                )
                scheduleRepository.updateSchedule(schedule)
                Result.Success(scheduleId)
            } else {
                scheduleRepository.addSchedule(schedule)
                    .onSuccess { id ->
                        alarmManagerRepository.scheduleApp(
                            schedule.packageName,
                            schedule.scheduledTime,
                            id
                        )
                    }.onError {
                        Result.Failure(it)
                    }
            }
        }.getOrElse { _ ->
            Result.Failure(ScheduleError.UNKNOWN_ERROR)
        }
    }
}

