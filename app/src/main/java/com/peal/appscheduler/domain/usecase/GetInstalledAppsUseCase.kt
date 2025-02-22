package com.peal.appscheduler.domain.usecase

import com.peal.appscheduler.domain.model.InstalledAppInfo
import com.peal.appscheduler.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 22/2/25.
 */

class GetInstalledAppsUseCase @Inject constructor(
    private val repository: InstalledAppsRepository
) {
    operator fun invoke(): Flow<List<InstalledAppInfo>> = repository.getInstalledApps()
}