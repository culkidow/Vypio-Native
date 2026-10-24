package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VypioDarkColorScheme = darkColorScheme(
  primary = VypioPrimary,
  onPrimary = VypioTextPrimaryDark,
  secondary = VypioSecondary,
  onSecondary = VypioTextPrimaryDark,
  tertiary = VypioAccent,
  background = VypioBackgroundDark,
  onBackground = VypioTextPrimaryDark,
  surface = VypioSurfaceDark,
  onSurface = VypioTextPrimaryDark,
  surfaceVariant = VypioSurfaceVariantDark,
  onSurfaceVariant = VypioTextSecondaryDark,
  outline = VypioBorderDark,
  error = VypioErrorDark,
)

// For Vypio, provide a consistent sleek dark palette as the site is dark-first (#09090b)
private val VypioLightColorScheme = darkColorScheme(
  primary = VypioPrimary,
  onPrimary = VypioTextPrimaryDark,
  secondary = VypioSecondary,
  onSecondary = VypioTextPrimaryDark,
  tertiary = VypioAccent,
  background = VypioBackgroundDark,
  onBackground = VypioTextPrimaryDark,
  surface = VypioSurfaceDark,
  onSurface = VypioTextPrimaryDark,
  surfaceVariant = VypioSurfaceVariantDark,
  onSurfaceVariant = VypioTextSecondaryDark,
  outline = VypioBorderDark,
  error = VypioErrorDark,
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = VypioDarkColorScheme

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        window.statusBarColor = VypioBackgroundDark.toArgb()
        window.navigationBarColor = VypioBackgroundDark.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        // Dark theme -> light status bar icons (false)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
