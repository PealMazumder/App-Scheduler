package com.peal.appscheduler.di

import android.content.Context
import android.content.pm.PackageManager
import com.peal.appscheduler.data.wapper.AlarmManagerWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAlarmManagerWrapper(@ApplicationContext context: Context): AlarmManagerWrapper {
        return AlarmManagerWrapper(context)
    }

    @Provides
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }
}