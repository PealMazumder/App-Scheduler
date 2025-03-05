package com.peal.appscheduler.ui.screens.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.peal.appscheduler.R


/**
 * Created by Peal Mazumder on 6/3/25.
 */
@Composable
fun CommonAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    showDismissButton: Boolean = false,
    dismissOnBackPress: Boolean = false,
    dismissOnClickOutside: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        ),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            if (showDismissButton) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}