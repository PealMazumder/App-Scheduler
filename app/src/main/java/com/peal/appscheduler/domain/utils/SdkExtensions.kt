package com.peal.appscheduler.domain.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast


/**
 * Created by Peal Mazumder on 8/3/25.
 */

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun isAndroidTIRAMISUOrLater() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU