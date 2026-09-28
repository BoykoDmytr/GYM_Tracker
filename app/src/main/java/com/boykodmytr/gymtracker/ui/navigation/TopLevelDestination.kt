package com.boykodmytr.gymtracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.boykodmytr.gymtracker.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    @param:StringRes val label: Int,
) {
    HOME(HomeRoute, HomeRoute::class, Icons.Filled.Home, Icons.Outlined.Home, R.string.nav_home),
    HISTORY(HistoryRoute, HistoryRoute::class, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, R.string.nav_history),
    STATS(StatsRoute, StatsRoute::class, Icons.Filled.BarChart, Icons.Outlined.BarChart, R.string.nav_stats),
    PROGRAMS(ProgramsRoute, ProgramsRoute::class, Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter, R.string.nav_programs),
    PROFILE(ProfileRoute, ProfileRoute::class, Icons.Filled.Person, Icons.Outlined.Person, R.string.nav_profile),
}
