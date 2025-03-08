package com.peal.appscheduler.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest


/**
 * Created by Peal Mazumder on 9/3/25.
 */

@Composable
fun <T> ObserveAsEvents(
    events: Flow<T>,
    onEvent: (T) -> Unit
) {
    val lifecycleOwner by rememberUpdatedState(LocalLifecycleOwner.current)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collectLatest { event ->
                onEvent(event)
            }
        }
    }
}