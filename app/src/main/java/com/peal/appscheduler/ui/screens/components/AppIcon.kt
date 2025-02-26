package com.peal.appscheduler.ui.screens.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.peal.appscheduler.ui.utils.ImageUtils.drawableToBitmap


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Composable
fun AppIcon(
    icon: Drawable?,
    appName: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember (icon) { icon?.let { drawableToBitmap(it) } }

    bitmap?.asImageBitmap()?.let { imageBitmap ->
        Image(
            bitmap = imageBitmap,
            contentDescription = appName,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } ?: Box(
        modifier = modifier
            .size(48.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
    )
}