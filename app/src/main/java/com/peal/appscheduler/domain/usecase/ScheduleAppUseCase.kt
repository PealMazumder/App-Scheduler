package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.data.wapper.AlarmManagerWrapper
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.utils.ScheduleResult
import com.peal.appscheduler.domain.repository.ScheduleRepository
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleAppUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val alarmManagerWrapper: AlarmManagerWrapper
) {
    suspend operator fun invoke(schedule: AppSchedule): ScheduleResult<Long> = runCatching {
        // TODO: Handle past time and time conflict scenario

       try {
           alarmManagerWrapper.scheduleApp(schedule.packageName, schedule.scheduledTime)

           val scheduleId = scheduleRepository.addSchedule(schedule)

           ScheduleResult.Success(scheduleId)
       } catch (e: Exception) {
           ScheduleResult.Error(e.localizedMessage ?: "Unknown error")
       }
    }.getOrElse { exception ->
        ScheduleResult.Error(exception.localizedMessage ?: "Unknown error")
    }
}
