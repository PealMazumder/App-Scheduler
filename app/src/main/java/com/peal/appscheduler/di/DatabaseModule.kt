package com.peal.appscheduler.di

import android.content.Context
import androidx.room.Room
import com.peal.appscheduler.data.local.ScheduleDao
import com.peal.appscheduler.data.local.SchedulerAppDatabase
import com.peal.appscheduler.utils.AppConstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): SchedulerAppDatabase {
        return Room.databaseBuilder(
            context,
            SchedulerAppDatabase::class.java,
            AppConstant.DATABASE_NAME,
        ).build()
    }

    @Provides
    fun provideScheduleDao(database: SchedulerAppDatabase): ScheduleDao {
        return database.scheduleDao()
    }
}