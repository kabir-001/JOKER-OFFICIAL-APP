package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimaryDark,
  onPrimary = Color(0xFF022C22),
  primaryContainer = EmeraldDarkContainer,
  onPrimaryContainer = OnEmeraldContainerDark,
  secondary = ChampagneGold,
  onSecondary = Color(0xFF271B00),
  secondaryContainer = GoldContainerDark,
  onSecondaryContainer = OnGoldContainerDark,
  tertiary = WhatsAppBright,
  onTertiary = Color(0xFF03230F),
  background = ObsidianBg,
  onBackground = ObsidianText,
  surface = ObsidianSurface,
  onSurface = ObsidianText,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = ObsidianMuted,
  outline = ObsidianOutline,
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimaryLight,
  onPrimary = Color.White,
  primaryContainer = EmeraldLightContainer,
  onPrimaryContainer = OnEmeraldContainerLight,
  secondary = ChampagneGoldDark,
  onSecondary = Color.White,
  secondaryContainer = GoldContainerLight,
  onSecondaryContainer = OnGoldContainerLight,
  tertiary = WhatsAppGreen,
  onTertiary = Color.White,
  background = IvoryBg,
  onBackground = IvoryText,
  surface = IvorySurface,
  onSurface = IvoryText,
  surfaceVariant = IvorySurfaceVariant,
  onSurfaceVariant = IvoryMuted,
  outline = IvoryOutline,
)

@Composable
fun TargetPapersTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
