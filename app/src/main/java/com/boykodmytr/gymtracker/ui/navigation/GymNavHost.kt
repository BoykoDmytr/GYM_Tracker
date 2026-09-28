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
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.feature.history.HistoryScreen
import com.boykodmytr.gymtracker.feature.history.SessionDetailScreen
import com.boykodmytr.gymtracker.feature.home.HomeScreen
import com.boykodmytr.gymtracker.feature.stats.ExerciseProgressScreen
import com.boykodmytr.gymtracker.feature.stats.StatsScreen
import com.boykodmytr.gymtracker.feature.timer.TimerScreen
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
        composable<HistoryRoute> {
            HistoryScreen(onOpenSession = { navController.navigate(SessionDetailRoute(it)) })
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenExerciseProgress = { navController.navigate(ExerciseProgressRoute(it)) },
            )
        }
        composable<StatsRoute> {
            StatsScreen(
                onOpenExercise = { navController.navigate(ExerciseProgressRoute(it)) },
                onOpenBodyWeight = { navController.navigate(MeasurementDetailRoute(BuiltInMeasurementTypes.WEIGHT)) },
            )
        }
        composable<ExerciseProgressRoute> {
            ExerciseProgressScreen(
                onBack = { navController.popBackStack() },
                onEditExercise = { navController.navigate(ExerciseEditorRoute(it)) },
                onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
            )
        }
        composable<ProgramsRoute> { Placeholder("Programs") }
        composable<ProfileRoute> { Placeholder("Profile") }
        composable<MeasurementDetailRoute> { Placeholder("Measurement") }
        composable<ExerciseEditorRoute> { Placeholder("Exercise") }
        composable<SettingsRoute> { Placeholder("Settings") }
        composable<TimerRoute> { TimerScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
