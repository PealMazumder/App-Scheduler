package com.peal.appscheduler.ui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Created by Peal Mazumder on 26/2/25.
 */

fun (() -> Unit).debounce(
    coroutineScope: CoroutineScope,
    waitMs: Long = 500L
): () -> Unit {
    var job: Job? = null
    return {
        if (job?.isActive != true) {
            job = coroutineScope.launch {
                this@debounce()
                delay(waitMs)
            }
        }
    }
}