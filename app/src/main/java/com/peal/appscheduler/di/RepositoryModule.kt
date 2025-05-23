package com.peal.appscheduler.di

import com.peal.appscheduler.data.repositoryImpl.DeviceAppsRepositoryImpl
import com.peal.appscheduler.data.repositoryImpl.ScheduleRepositoryImpl
import com.peal.appscheduler.data.wapper.AlarmManagerWrapper
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.DeviceAppsRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDeviceAppsRepository(deviceAppsRepositoryImpl: DeviceAppsRepositoryImpl): DeviceAppsRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(scheduleRepositoryImpl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindAlarmManagerRepository(alarmManagerWrapper: AlarmManagerWrapper): AlarmManagerRepository
}
