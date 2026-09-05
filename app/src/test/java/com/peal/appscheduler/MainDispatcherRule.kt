package com.peal.appscheduler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps Dispatchers.Main for a test dispatcher so ViewModels using the default
 * viewModelScope (Dispatchers.Main.immediate) can be unit tested on the JVM.
 *
 * Defaults to UnconfinedTestDispatcher rather than StandardTestDispatcher deliberately: this
 * dispatcher has its own independent scheduler, constructed before any runTest{} block starts,
 * so it cannot share runTest's virtual clock. StandardTestDispatcher would require calling
 * advanceUntilIdle() on *its* scheduler specifically to run anything dispatched to Main -
 * runTest's own advanceUntilIdle() would silently advance a different, unrelated clock and
 * coroutines launched via viewModelScope would never run. UnconfinedTestDispatcher sidesteps
 * this by running dispatched coroutine bodies eagerly, without needing scheduler coordination.
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}
