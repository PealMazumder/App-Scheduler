package com.peal.appscheduler.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peal.appscheduler.data.local.model.ScheduleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        scheduledTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), // Past time
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
    fun insertAndGetSchedule() = runTest {
        // Insert a schedule
        val id = scheduleDao.insert(sampleApp1)

        // Verify the ID is valid
        assertTrue(id > 0)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify the schedule was inserted
        assertEquals(1, allSchedules.size)
        assertEquals(sampleApp1.packageName, allSchedules[0].packageName)
        assertEquals(sampleApp1.appName, allSchedules[0].appName)
        assertEquals(sampleApp1.scheduledTime, allSchedules[0].scheduledTime)
        assertEquals(sampleApp1.status, allSchedules[0].status)
        assertEquals(id, allSchedules[0].id)
    }

    @Test
    fun insertMultipleSchedules() = runTest {
        // Insert multiple schedules
        val id1 = scheduleDao.insert(sampleApp1)
        val id2 = scheduleDao.insert(sampleApp2)

        // Verify IDs are valid and different
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify multiple schedules were inserted
        assertEquals(2, allSchedules.size)

        // Verify schedules are ordered by ID (assuming insertion order)
        val sortedSchedules = allSchedules.sortedBy { it.id }
        assertEquals(id1, sortedSchedules[0].id)
        assertEquals(id2, sortedSchedules[1].id)
    }

    @Test
    fun updateSchedule() = runTest {
        // Insert a schedule
        val id = scheduleDao.insert(sampleApp1)

        // Create updated version
        val updatedSchedule = sampleApp1.copy(
            id = id,
            scheduledTime = sampleApp1.scheduledTime + TimeUnit.HOURS.toMillis(5),
            status = ScheduleStatus.CANCELLED.name
        )

        // Update the schedule
        scheduleDao.update(updatedSchedule)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify the schedule was updated
        assertEquals(1, allSchedules.size)
        assertEquals(updatedSchedule.scheduledTime, allSchedules[0].scheduledTime)
        assertEquals(updatedSchedule.status, allSchedules[0].status)
        assertEquals(ScheduleStatus.CANCELLED.name, allSchedules[0].status)
        assertEquals(id, allSchedules[0].id)
    }

    @Test
    fun updateStatus() = runTest {
        // Insert a schedule
        val id = scheduleDao.insert(sampleApp1)

        // Update just the status
        val newStatus = ScheduleStatus.EXECUTED.name
        scheduleDao.updateStatus(id, newStatus)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify only the status was updated
        assertEquals(1, allSchedules.size)
        assertEquals(newStatus, allSchedules[0].status)
        assertEquals(sampleApp1.scheduledTime, allSchedules[0].scheduledTime)
        assertEquals(sampleApp1.packageName, allSchedules[0].packageName)
    }

    // Corner cases

    @Test
    fun insertScheduleWithLongAppName() = runTest {
        // Create a schedule with very long app name
        val longNameApp = sampleApp1.copy(
            appName = "A".repeat(255) // Max string length in some DB implementations
        )

        // Insert the schedule
        val id = scheduleDao.insert(longNameApp)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify the schedule was inserted correctly with long name
        assertEquals(1, allSchedules.size)
        assertEquals(longNameApp.appName, allSchedules[0].appName)
        assertEquals(255, allSchedules[0].appName.length)
    }

    @Test
    fun insertScheduleWithPastTime() = runTest {
        // Insert a schedule with past time
        val id = scheduleDao.insert(sampleAppWithPastTime)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify the schedule was inserted despite having past time
        assertEquals(1, allSchedules.size)
        assertTrue(allSchedules[0].scheduledTime < System.currentTimeMillis())
    }

    @Test
    fun updateNonExistentSchedule() = runTest {
        // Try to update a schedule that doesn't exist
        val nonExistentSchedule = ScheduleEntity(
            id = 999, // Non-existent ID
            packageName = "com.example.nonexistent",
            appName = "Non-existent App",
            scheduledTime = System.currentTimeMillis(),
            status = ScheduleStatus.SCHEDULED.name
        )

        // Update should not throw an exception
        scheduleDao.update(nonExistentSchedule)

        // Verify no schedules were inserted
        val allSchedules = scheduleDao.getAllScheduledApps()
        assertEquals(0, allSchedules.size)
    }

    @Test
    fun updateStatusOfNonExistentSchedule() = runTest {
        // Try to update status of a schedule that doesn't exist
        scheduleDao.updateStatus(999, ScheduleStatus.EXECUTED.name)

        // This should not throw an exception, and database should remain empty
        val allSchedules = scheduleDao.getAllScheduledApps()
        assertEquals(0, allSchedules.size)
    }

    @Test
    fun insertDuplicatePackageName() = runTest {
        // Insert a schedule
        val id1 = scheduleDao.insert(sampleApp1)

        // Insert another schedule with the same package name
        val duplicateApp = sampleApp1.copy(
            scheduledTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)
        )
        val id2 = scheduleDao.insert(duplicateApp)

        // Verify both were inserted (Room doesn't enforce uniqueness by default)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()
        assertEquals(2, allSchedules.size)

        // Verify both have the same package name
        val packageNames = allSchedules.map { it.packageName }
        assertEquals(2, packageNames.count { it == sampleApp1.packageName })
    }

    @Test
    fun getAllScheduledAppsWhenEmpty() = runTest {
        // Get all schedules when DB is empty
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify we get an empty list, not null
        assertNotNull(allSchedules)
        assertEquals(0, allSchedules.size)
    }

    @Test
    fun testAllStatusValues() = runTest {
        // Insert schedules with each possible status
        val baseApp = ScheduleEntity(
            packageName = "com.example.base",
            appName = "Base App",
            scheduledTime = System.currentTimeMillis(),
            status = ScheduleStatus.SCHEDULED.name
        )

        // Create and insert entities with different statuses
        val scheduledApp = baseApp.copy(status = ScheduleStatus.SCHEDULED.name)
        val executedApp = baseApp.copy(
            packageName = "com.example.executed",
            status = ScheduleStatus.EXECUTED.name
        )
        val cancelledApp = baseApp.copy(
            packageName = "com.example.cancelled",
            status = ScheduleStatus.CANCELLED.name
        )
        val failedApp = baseApp.copy(
            packageName = "com.example.failed",
            status = ScheduleStatus.FAILED.name
        )

        // Insert all status types
        scheduleDao.insert(scheduledApp)
        scheduleDao.insert(executedApp)
        scheduleDao.insert(cancelledApp)
        scheduleDao.insert(failedApp)

        // Get all schedules
        val allSchedules = scheduleDao.getAllScheduledApps()

        // Verify all were inserted
        assertEquals(4, allSchedules.size)

        // Verify we have one of each status
        val statuses = allSchedules.map { it.status }
        assertTrue(statuses.contains(ScheduleStatus.SCHEDULED.name))
        assertTrue(statuses.contains(ScheduleStatus.EXECUTED.name))
        assertTrue(statuses.contains(ScheduleStatus.CANCELLED.name))
        assertTrue(statuses.contains(ScheduleStatus.FAILED.name))
    }

    @Test
    fun updateStatusTransitions() = runTest {
        // Insert a schedule
        val id = scheduleDao.insert(sampleApp1)

        // Transition through all possible states
        scheduleDao.updateStatus(id, ScheduleStatus.EXECUTED.name)
        var updatedSchedule = scheduleDao.getAllScheduledApps().first()
        assertEquals(ScheduleStatus.EXECUTED.name, updatedSchedule.status)

        scheduleDao.updateStatus(id, ScheduleStatus.FAILED.name)
        updatedSchedule = scheduleDao.getAllScheduledApps().first()
        assertEquals(ScheduleStatus.FAILED.name, updatedSchedule.status)

        scheduleDao.updateStatus(id, ScheduleStatus.CANCELLED.name)
        updatedSchedule = scheduleDao.getAllScheduledApps().first()
        assertEquals(ScheduleStatus.CANCELLED.name, updatedSchedule.status)

        // Back to SCHEDULED (if your app allows this)
        scheduleDao.updateStatus(id, ScheduleStatus.SCHEDULED.name)
        updatedSchedule = scheduleDao.getAllScheduledApps().first()
        assertEquals(ScheduleStatus.SCHEDULED.name, updatedSchedule.status)
    }
}