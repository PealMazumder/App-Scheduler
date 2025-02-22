package com.peal.appscheduler.di

import com.peal.appscheduler.data.repositoryImpl.InstalledAppsRepositoryImpl
import com.peal.appscheduler.domain.repository.InstalledAppsRepository
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
    abstract fun bindInstalledAppsRepository(installedAppsRepositoryImpl: InstalledAppsRepositoryImpl): InstalledAppsRepository
}
