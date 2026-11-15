package com.sajith.scrolllock.ui.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

object WorkoutColors {
    val ObsidianBg = Color(0xFF080914)
    val CardSurface = Color(0xFF14162B)
    val GlassSurface = Color(0xD4121428)
    val GlassTile = Color(0xC2191C38)
    val SubtleBorder = Color(0xFF353868)
    val TurquoiseAccent = Color(0xFF8C77FF)
    val TurquoiseSoft = Color(0x307C5CFF)
    val GhostCyan = Color(0x94A78BFF)
    val OnAccentDark = Color(0xFFF6F7FC)
    val SoftWhite = Color(0xFFF6F7FC)
    val MutedGrey = Color(0xFFA3A7CE)
    val WarningAmber = Color(0xFFF59E0B)
    val WarningSoft = Color(0x33F59E0B)
    val ErrorCoral = Color(0xFFFF5E73)
    val ErrorCoralSoft = Color(0x88FF5E73)
}

private val ScrollLockDarkColors = darkColorScheme(
    primary = WorkoutColors.TurquoiseAccent,
    onPrimary = WorkoutColors.OnAccentDark,
    primaryContainer = WorkoutColors.TurquoiseSoft,
    onPrimaryContainer = WorkoutColors.TurquoiseAccent,
    secondary = Color(0xFF54D6C2),
    onSecondary = Color(0xFF080914),
    background = WorkoutColors.ObsidianBg,
    onBackground = WorkoutColors.SoftWhite,
    surface = WorkoutColors.CardSurface,
    onSurface = WorkoutColors.SoftWhite,
    surfaceVariant = WorkoutColors.GlassTile,
    onSurfaceVariant = WorkoutColors.MutedGrey,
    outline = WorkoutColors.SubtleBorder,
    error = WorkoutColors.ErrorCoral
)

private val ScrollLockWorkoutTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun ScrollLockWorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScrollLockDarkColors,
        typography = ScrollLockWorkoutTypography,
        content = content
    )
}
