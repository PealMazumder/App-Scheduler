package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 24/2/25.
 */

class GetScheduledAppUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    operator fun invoke(): Flow<List<AppSchedule>> {
        return repository.getAllScheduledApps()
    }
}
