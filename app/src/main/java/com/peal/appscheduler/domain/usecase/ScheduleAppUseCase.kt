package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import com.peal.appscheduler.domain.utils.ScheduleResult
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleAppUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val alarmManagerRepository: AlarmManagerRepository,
) {
    suspend operator fun invoke(schedule: AppSchedule, isEdit: Boolean): ScheduleResult<Long> =
        runCatching {
            // TODO: Handle past time and time conflict scenario
            var scheduleId: Long = schedule.id
            try {
                if (isEdit) {
                    alarmManagerRepository.updateSchedule(
                        schedule.packageName,
                        schedule.scheduledTime,
                        scheduleId
                    )
                    scheduleRepository.updateSchedule(schedule)
                } else {
                    scheduleId = scheduleRepository.addSchedule(schedule)
                    alarmManagerRepository.scheduleApp(
                        schedule.packageName,
                        schedule.scheduledTime,
                        scheduleId
                    )

                }

                ScheduleResult.Success(scheduleId)
            } catch (e: Exception) {
                ScheduleResult.Error(e.localizedMessage ?: "Unknown error")
            }
        }.getOrElse { exception ->
            ScheduleResult.Error(exception.localizedMessage ?: "Unknown error")
        }
}
