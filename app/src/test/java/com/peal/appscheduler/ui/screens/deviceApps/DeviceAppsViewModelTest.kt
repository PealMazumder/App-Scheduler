package com.peal.appscheduler.ui.screens.deviceApps

import com.peal.appscheduler.MainDispatcherRule
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.usecase.GetDeviceAppsUseCase
import com.peal.appscheduler.fakes.FakeDeviceAppsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceAppsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDeviceAppsRepository()

    private fun createViewModel() = DeviceAppsViewModel(GetDeviceAppsUseCase(repository))

    @Test
    fun `loads installed apps into state on init`() = runTest {
        repository.apps = listOf(
            DeviceAppInfo(id = 0, name = "Example", packageName = "com.example.app", icon = null)
        )

        val viewModel = createViewModel()

        val state = viewModel.deviceAppsScreenState.value
        assertEquals(1, state.deviceApps.size)
        assertEquals("com.example.app", state.deviceApps.first().packageName)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a repository failure clears loading and sets an error message instead of spinning forever - regression for H10`() = runTest {
        // Before the H10 fix, DeviceAppsViewModel's catch block only printed the stack trace and
        // never cleared isLoading, so the screen spun forever on any failure.
        repository.throwable = IllegalStateException("boom")

        val viewModel = createViewModel()

        val state = viewModel.deviceAppsScreenState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.deviceApps.isEmpty())
    }

    @Test
    fun `selecting an app emits a NavigateToScheduler effect with that app`() = runTest {
        val app = DeviceAppInfo(id = 0, name = "Example", packageName = "com.example.app", icon = null)
        repository.apps = listOf(app)
        val viewModel = createViewModel()

        viewModel.onIntent(DeviceAppsContract.Intent.OnNavigateScheduler(app))

        val effect = viewModel.effect.first()
        assertTrue(effect is DeviceAppsContract.Effect.NavigateToScheduler)
        assertEquals(app, (effect as DeviceAppsContract.Effect.NavigateToScheduler).data)
    }
}
