package com.augustuz.openmathpad.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GitpodOrange,
    onPrimary = GitpodDarkBackground,
    primaryContainer = GitpodDarkBorder,
    onPrimaryContainer = GitpodOrange,
    secondary = GitpodCyan,
    onSecondary = GitpodDarkBackground,
    secondaryContainer = GitpodDarkSurface,
    onSecondaryContainer = GitpodCyan,
    background = GitpodDarkBackground,
    onBackground = Color.White,
    surface = GitpodDarkSurface,
    onSurface = Color.White,
    outline = GitpodDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = GitpodRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = GitpodRoyalBlue,
    secondary = GitpodDeepOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7ED),
    onSecondaryContainer = GitpodDeepOrange,
    background = GitpodLightBackground,
    onBackground = GitpodPitchBlack,
    surface = GitpodLightSurface,
    onSurface = GitpodPitchBlack,
    outline = GitpodLightBorder
)

@Composable
fun OpenMathPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}