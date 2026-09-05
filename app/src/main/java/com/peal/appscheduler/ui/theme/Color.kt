package com.peal.appscheduler.ui.theme

import androidx.compose.ui.graphics.Color

// Indigo, used when dynamic color isn't available (pre-Android 12).
val IndigoLight = Color(0xFF3D5AFE)
val IndigoContainerLight = Color(0xFFDEE1FF)
val OnIndigoContainerLight = Color(0xFF00105C)

val IndigoDark = Color(0xFFB9C3FF)
val OnIndigoDark = Color(0xFF152082)
val IndigoContainerDark = Color(0xFF3547A8)
val OnIndigoContainerDark = Color(0xFFDEE1FF)

// Teal, used for secondary accents (e.g. success/executed states).
val TealLight = Color(0xFF00897B)
val TealDark = Color(0xFF4DB6AC)
val OnTealDark = Color(0xFF00332E)

// Status colors, intentionally independent of the theme's primary/secondary roles so a
// schedule's status reads the same regardless of the active color scheme.
val StatusScheduled = Color(0xFF3D5AFE)
val StatusExecuted = Color(0xFF2E7D32)
val StatusFailed = Color(0xFFC62828)
val StatusCancelled = Color(0xFF6B7280)
