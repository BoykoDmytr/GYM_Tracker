package com.boykodmytr.gymtracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.boykodmytr.gymtracker.feature.home.HomeScreen
import com.boykodmytr.gymtracker.feature.workout.active.ActiveWorkoutScreen
import com.boykodmytr.gymtracker.feature.workout.preview.WorkoutPreviewScreen
import com.boykodmytr.gymtracker.feature.workout.summary.WorkoutSummaryScreen

@Composable
fun GymNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                onStartWorkout = { navController.navigate(WorkoutPreviewRoute(it)) },
                onContinueSession = { navController.navigate(ActiveWorkoutRoute(it)) },
                onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                onOpenTimer = { navController.navigate(TimerRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenPrograms = { navController.navigate(ProgramsRoute) },
            )
        }
        composable<WorkoutPreviewRoute> {
            WorkoutPreviewScreen(
                onBack = { navController.popBackStack() },
                onStarted = { sessionId ->
                    navController.navigate(ActiveWorkoutRoute(sessionId)) {
                        popUpTo<WorkoutPreviewRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ActiveWorkoutRoute> {
            ActiveWorkoutScreen(
                onExit = { if (!navController.popBackStack()) navController.navigate(HomeRoute) },
                onFinished = { sessionId ->
                    navController.navigate(WorkoutSummaryRoute(sessionId)) {
                        popUpTo<ActiveWorkoutRoute> { inclusive = true }
                    }
                },
                onEditExercise = { navController.navigate(ExerciseEditorRoute(it)) },
            )
        }
        composable<WorkoutSummaryRoute> {
            WorkoutSummaryScreen(onDone = { if (!navController.popBackStack()) navController.navigate(HomeRoute) })
        }
        composable<HistoryRoute> { Placeholder("History") }
        composable<StatsRoute> { Placeholder("Stats") }
        composable<ProgramsRoute> { Placeholder("Programs") }
        composable<ProfileRoute> { Placeholder("Profile") }
        composable<SessionDetailRoute> { Placeholder("Session") }
        composable<ExerciseEditorRoute> { Placeholder("Exercise") }
        composable<SettingsRoute> { Placeholder("Settings") }
        composable<TimerRoute> { Placeholder("Timer") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
