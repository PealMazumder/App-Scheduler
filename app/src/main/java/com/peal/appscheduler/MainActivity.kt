package com.peal.appscheduler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.peal.appscheduler.ui.navigation.NavGraph
import com.peal.appscheduler.ui.theme.AppSchedulerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppSchedulerTheme {
                val context = LocalContext.current
                var showDialog by remember { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            showDialog = !Settings.canDrawOverlays(context)
                        }
                    })
                }

                if (showDialog) {
                    OverlayPermissionDialog(
                        context = context,
                        onDismiss = { showDialog = false }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    NavGraph(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController
                    )
                }
            }
        }
    }

    @Composable
    fun OverlayPermissionDialog(context: Context, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.this_app_needs_permission_to_display_over_other_apps)) },
            confirmButton = {
                Button(onClick = {
                    openOverlaySettings(context)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        )
    }

    private fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppSchedulerTheme {
    }

}