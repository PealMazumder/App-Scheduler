package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.data.wapper.AlarmManagerWrapper
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 25/2/25.
 */

class CancelScheduledAppUseCase @Inject constructor(
    private val alarmManagerWrapper: AlarmManagerWrapper
) {
    operator fun invoke(packageName: String, id: Long): Result<Unit> {
        return alarmManagerWrapper.cancelSchedule(packageName, id)
    }
}