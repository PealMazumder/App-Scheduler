package com.peal.appscheduler.data.repositoryImpl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.repository.DeviceAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 22/2/25.
 */

class DeviceAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceAppsRepository {

    override fun getDeviceApps(): Flow<List<DeviceAppInfo>> = flow {
        val packageManager: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0).map {
            val appInfo = it.activityInfo.applicationInfo
            DeviceAppInfo(
                name = packageManager.getApplicationLabel(appInfo).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(packageManager)
            )
        }.sortedBy { it.name }

        emit(apps)
    }.flowOn(Dispatchers.IO)
}