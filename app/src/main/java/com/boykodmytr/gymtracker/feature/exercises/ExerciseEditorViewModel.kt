package com.boykodmytr.gymtracker.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import com.boykodmytr.gymtracker.domain.repository.DeleteResult
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.ui.navigation.ExerciseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseEditorUiState(
    val loading: Boolean = true,
    /** Null until a new exercise is saved for the first time. */
    val exerciseId: String? = null,
    val details: ExerciseDetails? = null,
    val importingImage: Boolean = false,
)

sealed interface ExerciseEditorEvent {
    data object Saved : ExerciseEditorEvent
    data object Deleted : ExerciseEditorEvent
    data class InUse(val templates: Int, val history: Int) : ExerciseEditorEvent
    data object ImageFailed : ExerciseEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val exerciseId = MutableStateFlow(savedStateHandle.toRoute<ExerciseEditorRoute>().exerciseId)
    private val importing = MutableStateFlow(false)

    private val _events = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseEditorEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExerciseEditorUiState> = combine(
        exerciseId.flatMapLatest { id -> if (id == null) flowOf(null) else exerciseRepository.observeExercise(id) },
        exerciseId,
        importing,
    ) { details, id, importing ->
        ExerciseEditorUiState(loading = false, exerciseId = id, details = details, importingImage = importing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseEditorUiState())

    fun save(name: String, notes: String) {
        viewModelScope.launch {
            val id = exerciseId.value
            if (id == null) {
                exerciseId.value = exerciseRepository.createExercise(name, notes)
            } else {
                exerciseRepository.updateExercise(id, name, notes)
            }
            _events.send(ExerciseEditorEvent.Saved)
        }
    }

    fun addImage(uri: String) {
        val id = exerciseId.value ?: return
        viewModelScope.launch {
            importing.value = true
            runCatching { exerciseRepository.addImage(id, uri) }
                .onFailure { _events.send(ExerciseEditorEvent.ImageFailed) }
            importing.value = false
        }
    }

    fun deleteImage(imageId: String) {
        viewModelScope.launch { exerciseRepository.deleteImage(imageId) }
    }

    fun delete() {
        val id = exerciseId.value ?: return
        viewModelScope.launch {
            when (val result = exerciseRepository.deleteExercise(id)) {
                DeleteResult.Deleted -> _events.send(ExerciseEditorEvent.Deleted)
                is DeleteResult.InUse -> _events.send(ExerciseEditorEvent.InUse(result.templateUsages, result.historyUsages))
            }
        }
    }
}
