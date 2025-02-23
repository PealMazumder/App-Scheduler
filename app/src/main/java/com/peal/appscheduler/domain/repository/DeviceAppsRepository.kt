package com.peal.appscheduler.domain.repository

import com.peal.appscheduler.domain.model.DeviceAppInfo
import kotlinx.coroutines.flow.Flow


/**
 * Created by Peal Mazumder on 22/2/25.
 */

interface DeviceAppsRepository {
    fun getDeviceApps(): Flow<List<DeviceAppInfo>>
}