package com.boykodmytr.gymtracker.feature.workout.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.logic.ScheduleCalculator
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.ui.navigation.WorkoutPreviewRoute
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutPreviewUiState(
    val loading: Boolean = true,
    val program: Program? = null,
    val template: WorkoutTemplate? = null,
    /** Sets from the last time each exercise was done, keyed by exercise id. */
    val lastPerformance: Map<String, List<SetLog>> = emptyMap(),
    val inProgress: WorkoutSession? = null,
    val settings: AppSettings = AppSettings(),
    val starting: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    programRepository: ProgramRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val requestedTemplateId = savedStateHandle.toRoute<WorkoutPreviewRoute>().templateId
    private val selectedTemplateId = MutableStateFlow(requestedTemplateId)
    private val starting = MutableStateFlow(false)

    private val _started = Channel<String>(Channel.BUFFERED)
    val started: Flow<String> = _started.receiveAsFlow()

    private val program = programRepository.observeActiveProgram()

    private val template: Flow<WorkoutTemplate?> = combine(program, selectedTemplateId) { p, id -> p to id }
        .flatMapLatest { (p, id) ->
            when {
                p == null -> flowOf(null)
                id != null -> flowOf(p.workouts.firstOrNull { it.id == id } ?: p.workouts.firstOrNull())
                else -> workoutRepository.observeLastCompletedTemplateId(p.id)
                    .map { last -> ScheduleCalculator.nextTemplate(p.workouts, last) }
            }
        }

    private val lastPerformance: Flow<Map<String, List<SetLog>>> = template
        .map { t -> t?.exercises?.map { it.exercise.id }.orEmpty() }
        .distinctUntilChanged()
        .mapLatest { ids -> ids.associateWith { workoutRepository.lastPerformance(it) } }
        .onStart { emit(emptyMap()) }

    val state: StateFlow<WorkoutPreviewUiState> = combine(
        combine(program, template, ::Pair),
        lastPerformance,
        workoutRepository.observeInProgressSession(),
        settingsRepository.settings,
        starting,
    ) { (program, template), last, inProgress, settings, starting ->
        WorkoutPreviewUiState(
            loading = false,
            program = program,
            template = template,
            lastPerformance = last,
            inProgress = inProgress,
            settings = settings,
            starting = starting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutPreviewUiState())

    fun selectTemplate(id: String) {
        selectedTemplateId.value = id
    }

    fun start() {
        if (starting.value) return
        viewModelScope.launch {
            val template = state.value.template ?: template.first() ?: return@launch
            starting.value = true
            try {
                _started.send(workoutRepository.startSession(template.id))
            } finally {
                starting.value = false
            }
        }
    }
}
