package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 25/2/25.
 */

class CancelScheduledAppUseCase @Inject constructor(
    private val alarmManagerRepository: AlarmManagerRepository,
) {
    operator fun invoke(packageName: String, id: Long): Result<Unit> {
        return alarmManagerRepository.cancelSchedule(packageName, id)
    }
}