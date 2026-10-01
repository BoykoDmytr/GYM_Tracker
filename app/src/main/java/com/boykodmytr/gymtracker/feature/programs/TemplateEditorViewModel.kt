package com.boykodmytr.gymtracker.feature.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.ui.navigation.TemplateEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplateEditorUiState(
    val loading: Boolean = true,
    val template: WorkoutTemplate? = null,
    val exercises: List<Exercise> = emptyList(),
    val defaultRestSeconds: Int = 90,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepository: ProgramRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val templateId = savedStateHandle.toRoute<TemplateEditorRoute>().templateId

    val state: StateFlow<TemplateEditorUiState> = combine(
        programRepository.observeTemplate(templateId),
        exerciseRepository.observeExercises(),
        settingsRepository.settings,
    ) { template, exercises, settings ->
        TemplateEditorUiState(
            loading = false,
            template = template,
            exercises = exercises,
            defaultRestSeconds = settings.defaultRestSeconds,
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateEditorUiState())

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    fun rename(name: String) = launch { programRepository.renameTemplate(templateId, name) }
    fun add(exerciseId: String, target: SetTarget, restSeconds: Int?) = launch {
        programRepository.addTemplateExercise(templateId, exerciseId, target, restSeconds)
    }
    fun update(id: String, target: SetTarget, restSeconds: Int?, notes: String) = launch {
        programRepository.updateTemplateExercise(id, target, restSeconds, notes)
    }
    fun remove(id: String) = launch { programRepository.removeTemplateExercise(id) }
    fun move(id: String, offset: Int) = launch { programRepository.moveTemplateExercise(id, offset) }
    fun setSupersetWithNext(id: String, linked: Boolean) = launch { programRepository.setSupersetWithNext(id, linked) }
    fun delete() = launch {
        programRepository.deleteTemplate(templateId)
        _deleted.send(Unit)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
