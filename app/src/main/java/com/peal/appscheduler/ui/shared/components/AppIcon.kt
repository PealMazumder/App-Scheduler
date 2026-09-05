package com.peal.appscheduler.ui.shared.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.peal.appscheduler.ui.utils.ImageUtils.drawableToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Composable
fun AppIcon(
    icon: Drawable?,
    appName: String,
    modifier: Modifier = Modifier
) {
    // Rasterizing a Drawable (esp. AdaptiveIconDrawable) can be expensive; keep it off the main
    // thread instead of doing it synchronously during composition.
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, icon) {
        value = icon?.let {
            withContext(Dispatchers.Default) { drawableToBitmap(it)?.asImageBitmap() }
        }
    }

    iconBitmap?.let { imageBitmap ->
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