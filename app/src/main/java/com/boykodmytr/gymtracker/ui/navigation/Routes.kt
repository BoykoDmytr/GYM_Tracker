package com.boykodmytr.gymtracker.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe destinations (Navigation Compose + kotlinx.serialization).

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object StatsRoute
@Serializable data object ProgramsRoute
@Serializable data object ProfileRoute

@Serializable data class WorkoutPreviewRoute(val templateId: String? = null)
@Serializable data class ActiveWorkoutRoute(val sessionId: String)
@Serializable data class WorkoutSummaryRoute(val sessionId: String)
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable data class ExerciseProgressRoute(val exerciseId: String)
@Serializable data class ProgramDetailRoute(val programId: String)
@Serializable data class TemplateEditorRoute(val templateId: String)

/** With [pickForTemplateId] set, tapping an exercise adds it to that workout instead of opening it. */
@Serializable data class ExerciseLibraryRoute(val pickForTemplateId: String? = null)
@Serializable data class ExerciseEditorRoute(val exerciseId: String? = null)
@Serializable data class MeasurementDetailRoute(val typeId: String)
@Serializable data object SettingsRoute
@Serializable data object TimerRoute
