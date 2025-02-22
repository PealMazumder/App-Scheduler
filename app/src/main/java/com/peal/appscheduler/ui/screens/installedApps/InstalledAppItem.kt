package com.peal.appscheduler.ui.screens.installedApps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peal.appscheduler.domain.model.InstalledAppInfo
import com.peal.appscheduler.utils.ImageUtils.drawableToBitmap

/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun InstalledAppItem(app: InstalledAppInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clickable {  },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = remember(app.icon) { drawableToBitmap(app.icon) }
        bitmap?.asImageBitmap()?.let { imageBitmap ->
            Image(bitmap = imageBitmap, contentDescription = app.name, modifier = Modifier.size(48.dp))
        } ?: Box(modifier = Modifier.size(48.dp).background(Color.LightGray))

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(text = app.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = app.packageName, fontSize = 14.sp, color = Color.Gray)
        }
    }
}



