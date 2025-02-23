package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.repository.DeviceAppsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 22/2/25.
 */

class GetDeviceAppsUseCase @Inject constructor(
    private val repository: DeviceAppsRepository
) {
    operator fun invoke(): Flow<List<DeviceAppInfo>> = repository.getDeviceApps()
}