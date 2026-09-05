package com.peal.appscheduler.fakes

import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.repository.DeviceAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeDeviceAppsRepository : DeviceAppsRepository {
    var apps: List<DeviceAppInfo> = emptyList()
    var throwable: Throwable? = null

    override fun getDeviceApps(): Flow<List<DeviceAppInfo>> = flow {
        throwable?.let { throw it }
        emit(apps)
    }
}
