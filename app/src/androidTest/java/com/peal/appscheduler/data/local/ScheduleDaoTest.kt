package com.peal.appscheduler.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peal.appscheduler.data.local.model.ScheduleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Created by Peal Mazumder on 24/2/25.
 */

enum class ScheduleStatus {
    SCHEDULED,
    EXECUTED,
    CANCELLED,
    FAILED
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ScheduleDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var scheduleDao: ScheduleDao
    private lateinit var db: SchedulerAppDatabase

    private val sampleApp1 = ScheduleEntity(
        packageName = "com.example.app1",
        appName = "Test App 1",
        scheduledTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
        status = ScheduleStatus.SCHEDULED.name
    )

    private val sampleApp2 = ScheduleEntity(
        packageName = "com.example.app2",
        appName = "Test App 2",
        scheduledTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
        status = ScheduleStatus.SCHEDULED.name
    )

    private val sampleAppWithPastTime = ScheduleEntity(
        packageName = "com.example.app3",
        appName = "Test App 3",
        scheduledTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
        status = ScheduleStatus.SCHEDULED.name
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, SchedulerAppDatabase::class.java
        ).allowMainThreadQueries().build()
        scheduleDao = db.scheduleDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertSchedule_andRetrieveByFlow() = runTest {
        val id = scheduleDao.insert(sampleApp1)
        assertTrue(id > 0)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(1, result.size)
        assertEquals(sampleApp1.packageName, result[0].packageName)
        assertEquals(sampleApp1.appName, result[0].appName)
        assertEquals(sampleApp1.scheduledTime, result[0].scheduledTime)
        assertEquals(sampleApp1.status, result[0].status)
    }

    @Test
    fun insertMultipleSchedules_andVerifyOrder() = runTest {
        val id1 = scheduleDao.insert(sampleApp1)
        val id2 = scheduleDao.insert(sampleApp2)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(2, result.size)
        val sortedSchedules = result.sortedBy { it.id }

        assertEquals(id1, sortedSchedules[0].id)
        assertEquals(id2, sortedSchedules[1].id)
    }

    @Test
    fun updateSchedule_andVerifyChanges() = runTest {
        val id = scheduleDao.insert(sampleApp1)
        val updatedSchedule = sampleApp1.copy(
            id = id,
            scheduledTime = sampleApp1.scheduledTime + TimeUnit.HOURS.toMillis(5),
            status = ScheduleStatus.CANCELLED.name
        )

        scheduleDao.update(updatedSchedule)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(1, result.size)
        assertEquals(updatedSchedule.scheduledTime, result[0].scheduledTime)
        assertEquals(updatedSchedule.status, result[0].status)
    }

    @Test
    fun updateStatus_andVerifyChange() = runTest {
        val id = scheduleDao.insert(sampleApp1)
        scheduleDao.updateStatus(id, ScheduleStatus.EXECUTED.name)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(1, result.size)
        assertEquals(ScheduleStatus.EXECUTED.name, result[0].status)
    }

    @Test
    fun insertScheduleWithLongAppName_andRetrieve() = runTest {
        val longNameApp = sampleApp1.copy(appName = "A".repeat(255))
        val id = scheduleDao.insert(longNameApp)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(1, result.size)
        assertEquals(longNameApp.appName, result[0].appName)
        assertEquals(255, result[0].appName.length)
    }

    @Test
    fun insertScheduleWithPastTime_andVerifyInsertion() = runTest {
        val id = scheduleDao.insert(sampleAppWithPastTime)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(1, result.size)
        assertTrue(result[0].scheduledTime < System.currentTimeMillis())
    }

    @Test
    fun updateNonExistentSchedule_doesNotAffectDatabase() = runTest {
        val nonExistentSchedule = ScheduleEntity(
            id = 999,
            packageName = "com.example.nonexistent",
            appName = "Non-existent App",
            scheduledTime = System.currentTimeMillis(),
            status = ScheduleStatus.SCHEDULED.name
        )

        scheduleDao.update(nonExistentSchedule)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(0, result.size)
    }

    @Test
    fun updateStatusOfNonExistentSchedule_doesNotAffectDatabase() = runTest {
        scheduleDao.updateStatus(999, ScheduleStatus.EXECUTED.name)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(0, result.size)
    }

    @Test
    fun insertDuplicatePackageName_andRetrieveBothEntries() = runTest {
        val id1 = scheduleDao.insert(sampleApp1)
        val duplicateApp = sampleApp1.copy(
            scheduledTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)
        )
        val id2 = scheduleDao.insert(duplicateApp)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(2, result.size)

        val packageNames = result.map { it.packageName }
        assertEquals(2, packageNames.count { it == sampleApp1.packageName })
    }

    @Test
    fun getAllScheduledAppsWhenEmpty_returnsEmptyList() = runTest {
        val result = scheduleDao.getAllScheduledApps().first()
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun insertSchedulesWithAllStatuses_andVerifyRetrieval() = runTest {
        val baseApp = ScheduleEntity(
            packageName = "com.example.base",
            appName = "Base App",
            scheduledTime = System.currentTimeMillis(),
            status = ScheduleStatus.SCHEDULED.name
        )

        val scheduledApp = baseApp.copy(status = ScheduleStatus.SCHEDULED.name)
        val executedApp = baseApp.copy(packageName = "com.example.executed", status = ScheduleStatus.EXECUTED.name)
        val cancelledApp = baseApp.copy(packageName = "com.example.cancelled", status = ScheduleStatus.CANCELLED.name)
        val failedApp = baseApp.copy(packageName = "com.example.failed", status = ScheduleStatus.FAILED.name)

        scheduleDao.insert(scheduledApp)
        scheduleDao.insert(executedApp)
        scheduleDao.insert(cancelledApp)
        scheduleDao.insert(failedApp)

        val result = scheduleDao.getAllScheduledApps().first()
        assertEquals(4, result.size)

        val statuses = result.map { it.status }
        assertTrue(statuses.contains(ScheduleStatus.SCHEDULED.name))
        assertTrue(statuses.contains(ScheduleStatus.EXECUTED.name))
        assertTrue(statuses.contains(ScheduleStatus.CANCELLED.name))
        assertTrue(statuses.contains(ScheduleStatus.FAILED.name))
    }
}
