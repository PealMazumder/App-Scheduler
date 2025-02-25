package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 25/2/25.
 */

class CancelScheduledAppUseCase @Inject constructor(
    private val alarmManagerRepository: AlarmManagerRepository,
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(packageName: String, id: Long): Result<Unit> {
        return alarmManagerRepository.cancelSchedule(packageName, id).fold(
            onSuccess = {
                try {
                    scheduleRepository.updateScheduleStatus(id, ScheduleStatus.CANCELLED.name)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception("Alarm cancelled but failed to update database: ${e.message}"))
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}