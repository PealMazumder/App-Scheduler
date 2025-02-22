package com.peal.appscheduler.domain.repository

import com.peal.appscheduler.domain.model.InstalledAppInfo
import kotlinx.coroutines.flow.Flow


/**
 * Created by Peal Mazumder on 22/2/25.
 */

interface InstalledAppsRepository {
    fun getInstalledApps(): Flow<List<InstalledAppInfo>>
}