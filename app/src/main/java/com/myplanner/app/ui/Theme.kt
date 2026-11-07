package com.myplanner.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()   // AppCompat уже учитывает выбранную пользователем тему
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFFF6B6B), onPrimary = Color(0xFF3A0000),
        background = Color(0xFF14161B), surface = Color(0xFF1D2027), surfaceVariant = Color(0xFF272B34),
        onBackground = Color(0xFFE8EAF0), onSurface = Color(0xFFE8EAF0), onSurfaceVariant = Color(0xFFB4B9C6),
        secondaryContainer = Color(0xFF3A2A2E), onSecondaryContainer = Color(0xFFFFDADA)
    ) else lightColorScheme(
        primary = Color(0xFFD93B3B), onPrimary = Color.White,
        background = Color(0xFFF6F7FB), surface = Color.White, surfaceVariant = Color(0xFFEDEFF5),
        onBackground = Color(0xFF1B1D22), onSurface = Color(0xFF1B1D22), onSurfaceVariant = Color(0xFF5B6070),
        secondaryContainer = Color(0xFFFFE1E1), onSecondaryContainer = Color(0xFF5A0A0A)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
