package com.peal.appscheduler.ui.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable


/**
 * Created by Peal Mazumder on 22/2/25.
 */

object ImageUtils {
    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            try {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                    drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
