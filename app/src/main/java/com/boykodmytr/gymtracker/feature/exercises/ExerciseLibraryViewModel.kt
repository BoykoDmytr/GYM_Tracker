package com.boykodmytr.gymtracker.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.ui.navigation.ExerciseLibraryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ExerciseLibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val exercises: List<Exercise> = emptyList(),
    val pickMode: Boolean = false,
    val mergeMode: Boolean = false,
)

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExerciseLibraryRoute>()
    private val pickMode = route.pickForTemplateId != null
    private val mergeMode = route.mergeFromId != null
    private val query = MutableStateFlow("")

    val state: StateFlow<ExerciseLibraryUiState> = combine(exerciseRepository.observeExercises(), query) { all, q ->
        // An exercise cannot be merged into itself.
        val list = all.filter { it.id != route.mergeFromId }
        ExerciseLibraryUiState(
            loading = false,
            query = q,
            exercises = if (q.isBlank()) list else list.filter { it.name.contains(q.trim(), ignoreCase = true) },
            pickMode = pickMode,
            mergeMode = mergeMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState(pickMode = pickMode, mergeMode = mergeMode))

    fun setQuery(value: String) {
        query.value = value
    }
}
