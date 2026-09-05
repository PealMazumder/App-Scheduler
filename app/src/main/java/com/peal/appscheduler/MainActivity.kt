package com.peal.appscheduler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.peal.appscheduler.domain.utils.isAndroidTIRAMISUOrLater
import com.peal.appscheduler.ui.shared.navigation.AppSchedulerNavHost
import com.peal.appscheduler.ui.shared.components.CommonAlertDialog
import com.peal.appscheduler.ui.theme.AppSchedulerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()

        setContent {
            AppSchedulerTheme {
                val context = LocalContext.current

                var showDialog by remember { mutableStateOf(false) }
                var hasAskedThisSession by remember { mutableStateOf(false) }

                // Check overlay permission once on launch, not on every resume
                LaunchedEffect(Unit) {
                    if (!Settings.canDrawOverlays(context)) {
                        showDialog = true
                        hasAskedThisSession = true
                    }
                }

                if (showDialog) {
                    OverlayPermissionDialog(
                        context = context,
                        onDismiss = { showDialog = false }
                    )
                }

                AppSchedulerNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private fun requestNotificationPermission() {
        if (isAndroidTIRAMISUOrLater()) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    @Composable
    fun OverlayPermissionDialog(context: Context, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
        val overlayPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            onDismiss()
        }

        CommonAlertDialog(
            modifier = modifier,
            title = stringResource(R.string.permission_required),
            message = stringResource(R.string.overlay_permission_rationale),
            confirmText = stringResource(R.string.grant_permission),
            showDismissButton = true,
            dismissOnBackPress = true,
            onConfirm = {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onDismiss = onDismiss
        )
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppSchedulerTheme {
    }

}