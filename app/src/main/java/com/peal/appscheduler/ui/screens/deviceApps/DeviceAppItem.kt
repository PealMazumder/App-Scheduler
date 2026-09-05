package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.ui.shared.components.AppIcon

/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun InstalledAppItem(
    app: DeviceAppInfo,
    onClick: (DeviceAppInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button) {
                onClick.invoke(app)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            icon = app.icon,
            appName = app.name
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(text = app.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = app.packageName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
