package com.boykodmytr.gymtracker.feature.workout.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.logic.AfterSet
import com.boykodmytr.gymtracker.domain.logic.ProgressionAdvisor
import com.boykodmytr.gymtracker.domain.logic.ProgressionHint
import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.logic.WorkoutStep
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.ui.navigation.ActiveWorkoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    /** The session no longer exists or is already finished; the screen should close. */
    val closed: Boolean = false,
    val session: WorkoutSession? = null,
    val step: WorkoutStep? = null,
    val exerciseNumber: Int = 0,
    val exerciseCount: Int = 0,
    val closedExercises: Int = 0,
    val lastPerformance: List<SetLog> = emptyList(),
    val hint: ProgressionHint? = null,
    val technique: ExerciseDetails? = null,
    val suggestedInput: SetInput = SetInput(0.0, 0),
    val restSeconds: Int = AppSettings().defaultRestSeconds,
    val settings: AppSettings = AppSettings(),
    /** The current exercise's superset in order; empty when it is done on its own. */
    val superset: List<SessionExercise> = emptyList(),
    /** In a superset: what follows the set the user is about to do. */
    val afterSet: AfterSet? = null,
)

sealed interface ActiveWorkoutEvent {
    data class Finished(val sessionId: String) : ActiveWorkoutEvent
    data object Discarded : ActiveWorkoutEvent
}

/**
 * Drives the active workout. The ViewModel only writes to the database (sets, statuses, current
 * exercise, rest end time); the UI re-derives everything from it through [WorkoutFlow]. Alarms and the
 * ongoing notification react to the same database state in the background, not to this ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val sessionId: String = savedStateHandle.toRoute<ActiveWorkoutRoute>().sessionId

    /** Session-exercise id for which the user asked for a set beyond the plan. */
    private val extraSetFor = MutableStateFlow<String?>(null)

    /**
     * Set when this screen finishes or discards the session itself. Navigation then follows the
     * event; without the flag the database update could close the screen first and the summary
     * event would be lost with it.
     */
    private val leaving = MutableStateFlow(false)

    private val _events = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events: Flow<ActiveWorkoutEvent> = _events.receiveAsFlow()

    private val session = workoutRepository.observeSession(sessionId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val currentExerciseId = session
        .map { s -> s?.let(WorkoutFlow::currentExercise)?.exerciseId }
        .distinctUntilChanged()

    private val lastPerformance = currentExerciseId
        .mapLatest { id -> if (id == null) emptyList() else workoutRepository.lastPerformance(id, sessionId) }
        .onStart { emit(emptyList()) }

    private val technique = currentExerciseId
        .flatMapLatest { id -> if (id == null) flowOf(null) else exerciseRepository.observeExercise(id) }
        .onStart { emit(null) }

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        combine(session, leaving, ::Pair),
        lastPerformance,
        technique,
        settingsRepository.settings,
        extraSetFor,
    ) { (session, leaving), last, technique, settings, extraFor ->
        if (session == null || session.status == SessionStatus.COMPLETED) {
            // Closed elsewhere (e.g. from Home) → leave; closed by us → wait for the event.
            return@combine ActiveWorkoutUiState(loading = leaving, closed = !leaving)
        }
        val current = WorkoutFlow.currentExercise(session)
        val step = WorkoutFlow.step(session, extraSetRequested = current != null && extraFor == current.id)
        val ordered = WorkoutFlow.ordered(session)
        val superset = current?.let { WorkoutFlow.supersetOf(session, it) }?.takeIf { it.size > 1 }.orEmpty()
        ActiveWorkoutUiState(
            loading = false,
            session = session,
            step = step,
            exerciseNumber = ordered.indexOfFirst { it.id == current?.id } + 1,
            exerciseCount = ordered.size,
            closedExercises = ordered.count { it.status != ExerciseStatus.PENDING || it.sets.size >= it.target.setsMax },
            lastPerformance = last,
            hint = current?.let { ProgressionAdvisor.hint(last, it.target) },
            technique = technique,
            suggestedInput = suggestInput(step, last),
            restSeconds = current?.restSeconds ?: settings.defaultRestSeconds,
            settings = settings,
            superset = superset,
            afterSet = if (superset.isNotEmpty() && step is WorkoutStep.PerformSet) WorkoutFlow.afterSet(session, step.exercise.id) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    /** Pre-fills the result sheet: repeat this workout's previous set, else last time's matching set. */
    private fun suggestInput(step: WorkoutStep?, last: List<SetLog>): SetInput {
        val exercise = (step as? WorkoutStep.PerformSet)?.exercise ?: return SetInput(0.0, 0)
        val previous = exercise.sets.lastOrNull()
        val lastTimeSame = last.getOrNull(step.setNumber - 1) ?: last.lastOrNull()
        val weight = previous?.weightKg ?: exercise.target.weightKg ?: lastTimeSame?.weightKg ?: 0.0
        val reps = previous?.reps ?: lastTimeSame?.reps ?: exercise.target.repsMin
        return SetInput(weightKg = weight, reps = reps)
    }

    private suspend fun currentSession(): WorkoutSession? = session.first()

    fun logSet(input: SetInput) = launch {
        val s = currentSession() ?: return@launch
        val step = WorkoutFlow.step(s, extraSetRequested = extraSetFor.value == WorkoutFlow.currentExercise(s)?.id)
        if (step !is WorkoutStep.PerformSet) return@launch
        workoutRepository.logSet(step.exercise.id, input)
        extraSetFor.value = null
        val settings = settingsRepository.settings.first()
        val next = WorkoutFlow.afterSet(s, step.exercise.id)
        if (next.switchTo != null) {
            // Leaving an exercise of a superset that has all its sets closes it, as "next exercise" would.
            if (step.setNumber >= step.plannedSets) {
                workoutRepository.setExerciseStatus(step.exercise.id, ExerciseStatus.COMPLETED)
            }
            workoutRepository.setCurrentExercise(sessionId, next.switchTo)
        }
        if (settings.autoStartRest && next.rest && !next.workoutDone) {
            startRestNow(step.exercise.restSeconds ?: settings.defaultRestSeconds)
        } else {
            workoutRepository.clearRest(sessionId)
        }
    }

    fun updateSet(setId: String, input: SetInput) = launch { workoutRepository.updateSet(setId, input) }

    fun deleteSet(setId: String) = launch { workoutRepository.deleteSet(setId) }

    fun startRest(seconds: Int) = launch { startRestNow(seconds) }

    /** Changes the total rest length (preset chips); already-elapsed time counts toward it. */
    fun setRestDuration(seconds: Int) = launch {
        val rest = currentSession()?.rest ?: return@launch startRestNow(seconds)
        val end = maxOf(clock.instant(), rest.startedAt.plusSeconds(seconds.toLong()))
        workoutRepository.startRest(sessionId, rest.startedAt, end)
    }

    fun adjustRest(deltaSeconds: Long) = launch {
        val rest = currentSession()?.rest ?: return@launch
        val end = maxOf(clock.instant(), rest.endsAt.plusSeconds(deltaSeconds))
        workoutRepository.startRest(sessionId, rest.startedAt, end)
    }

    fun skipRest() = launch { workoutRepository.clearRest(sessionId) }

    /** "Next exercise" and "skip exercise" are the same move: close the current one and go on. */
    fun leaveExercise() = launch {
        val s = currentSession() ?: return@launch
        val current = WorkoutFlow.currentExercise(s) ?: return@launch
        if (current.status == ExerciseStatus.PENDING) {
            workoutRepository.setExerciseStatus(current.id, WorkoutFlow.closingStatus(current))
        }
        extraSetFor.value = null
        WorkoutFlow.nextPending(s, current.id)?.let { workoutRepository.setCurrentExercise(sessionId, it.id) }
    }

    fun completeExerciseEarly() = launch {
        val current = currentSession()?.let(WorkoutFlow::currentExercise) ?: return@launch
        workoutRepository.setExerciseStatus(current.id, ExerciseStatus.COMPLETED)
    }

    fun requestExtraSet() {
        viewModelScope.launch {
            extraSetFor.value = currentSession()?.let(WorkoutFlow::currentExercise)?.id
        }
    }

    fun jumpTo(sessionExerciseId: String) = launch {
        val target = currentSession()?.exercises?.firstOrNull { it.id == sessionExerciseId } ?: return@launch
        if (target.status != ExerciseStatus.PENDING && target.sets.size < target.target.setsMax) {
            workoutRepository.setExerciseStatus(target.id, ExerciseStatus.PENDING)
        }
        extraSetFor.value = null
        workoutRepository.setCurrentExercise(sessionId, target.id)
    }

    fun finish() = launch {
        leaving.value = true
        workoutRepository.finishSession(sessionId, clock.instant())
        _events.send(ActiveWorkoutEvent.Finished(sessionId))
    }

    fun discard() = launch {
        leaving.value = true
        workoutRepository.deleteSession(sessionId)
        _events.send(ActiveWorkoutEvent.Discarded)
    }

    private suspend fun startRestNow(seconds: Int) {
        val now = clock.instant()
        workoutRepository.startRest(sessionId, now, now.plusSeconds(seconds.toLong()))
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

internal val SessionExercise.isClosed: Boolean
    get() = status != ExerciseStatus.PENDING || sets.size >= target.setsMax
