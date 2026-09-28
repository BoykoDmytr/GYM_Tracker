package com.boykodmytr.gymtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.boykodmytr.gymtracker.domain.model.ThemeMode

private val BaseTypography = Typography()

internal val AppTypography = BaseTypography.copy(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular digits keep timers from jittering as numbers change. */
val TimerTextStyle = TextStyle(
    fontSize = 64.sp,
    lineHeight = 68.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

val NumberTextStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun GymTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
