package com.boykodmytr.gymtracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.feature.exercises.ExerciseEditorScreen
import com.boykodmytr.gymtracker.feature.exercises.ExerciseLibraryScreen
import com.boykodmytr.gymtracker.feature.history.HistoryScreen
import com.boykodmytr.gymtracker.feature.history.SessionDetailScreen
import com.boykodmytr.gymtracker.feature.home.HomeScreen
import com.boykodmytr.gymtracker.feature.profile.MeasurementDetailScreen
import com.boykodmytr.gymtracker.feature.profile.ProfileScreen
import com.boykodmytr.gymtracker.feature.programs.ProgramDetailScreen
import com.boykodmytr.gymtracker.feature.programs.ProgramsScreen
import com.boykodmytr.gymtracker.feature.programs.TemplateEditorScreen
import com.boykodmytr.gymtracker.feature.settings.SettingsScreen
import com.boykodmytr.gymtracker.feature.stats.ExerciseProgressScreen
import com.boykodmytr.gymtracker.feature.stats.StatsScreen
import com.boykodmytr.gymtracker.feature.timer.TimerScreen
import com.boykodmytr.gymtracker.feature.workout.active.ActiveWorkoutScreen
import com.boykodmytr.gymtracker.feature.workout.preview.WorkoutPreviewScreen
import com.boykodmytr.gymtracker.feature.workout.summary.WorkoutSummaryScreen

/** Result key: the exercise picked in the library for the template editor below it. */
private const val PICKED_EXERCISE_ID = "picked_exercise_id"

@Composable
fun GymNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        // Top-level sections
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
        composable<HistoryRoute> {
            HistoryScreen(onOpenSession = { navController.navigate(SessionDetailRoute(it)) })
        }
        composable<StatsRoute> {
            StatsScreen(
                onOpenExercise = { navController.navigate(ExerciseProgressRoute(it)) },
                onOpenBodyWeight = { navController.navigate(MeasurementDetailRoute(BuiltInMeasurementTypes.WEIGHT)) },
            )
        }
        composable<ProgramsRoute> {
            ProgramsScreen(
                onOpenProgram = { navController.navigate(ProgramDetailRoute(it)) },
                onOpenLibrary = { navController.navigate(ExerciseLibraryRoute()) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onOpenMeasurement = { navController.navigate(MeasurementDetailRoute(it)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }

        // Workout flow
        composable<WorkoutPreviewRoute> {
            WorkoutPreviewScreen(
                onBack = back,
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
        composable<TimerRoute> { TimerScreen(onBack = back) }

        // History & progress
        composable<SessionDetailRoute> {
            SessionDetailScreen(
                onBack = back,
                onOpenExerciseProgress = { navController.navigate(ExerciseProgressRoute(it)) },
            )
        }
        composable<ExerciseProgressRoute> {
            ExerciseProgressScreen(
                onBack = back,
                onEditExercise = { navController.navigate(ExerciseEditorRoute(it)) },
                onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
            )
        }

        // Programs & exercises
        composable<ProgramDetailRoute> {
            ProgramDetailScreen(
                onBack = back,
                onOpenTemplate = { navController.navigate(TemplateEditorRoute(it)) },
            )
        }
        composable<TemplateEditorRoute> { entry ->
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_EXERCISE_ID, null).collectAsStateWithLifecycle()
            TemplateEditorScreen(
                pickedExerciseId = picked,
                onPickedExerciseConsumed = { entry.savedStateHandle[PICKED_EXERCISE_ID] = null },
                onBack = back,
                onPickExercise = { navController.navigate(ExerciseLibraryRoute(pickForTemplateId = it)) },
            )
        }
        composable<ExerciseLibraryRoute> { entry ->
            val picking = entry.toRoute<ExerciseLibraryRoute>().pickForTemplateId != null
            ExerciseLibraryScreen(
                onBack = back,
                onSelect = { exerciseId ->
                    if (picking) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE_ID, exerciseId)
                        navController.popBackStack()
                    } else {
                        navController.navigate(ExerciseEditorRoute(exerciseId))
                    }
                },
                onCreate = { navController.navigate(ExerciseEditorRoute()) },
            )
        }
        composable<ExerciseEditorRoute> {
            ExerciseEditorScreen(
                onBack = back,
                onOpenProgress = { navController.navigate(ExerciseProgressRoute(it)) },
            )
        }

        // Profile & settings
        composable<MeasurementDetailRoute> { MeasurementDetailScreen(onBack = back) }
        composable<SettingsRoute> { SettingsScreen(onBack = back) }
    }
}
