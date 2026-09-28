package com.boykodmytr.gymtracker.feature.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.ui.navigation.ProgramDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgramDetailUiState(val loading: Boolean = true, val program: Program? = null)

sealed interface ProgramDetailEvent {
    data class TemplateCreated(val templateId: String) : ProgramDetailEvent
    data object Deleted : ProgramDetailEvent
}

@HiltViewModel
class ProgramDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepository: ProgramRepository,
) : ViewModel() {

    private val programId = savedStateHandle.toRoute<ProgramDetailRoute>().programId

    val state: StateFlow<ProgramDetailUiState> = programRepository.observeProgram(programId)
        .map { ProgramDetailUiState(loading = false, program = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramDetailUiState())

    private val _events = Channel<ProgramDetailEvent>(Channel.BUFFERED)
    val events: Flow<ProgramDetailEvent> = _events.receiveAsFlow()

    fun update(name: String, description: String) = launch { programRepository.updateProgram(programId, name, description) }
    fun makeActive() = launch { programRepository.setActiveProgram(programId) }
    fun moveTemplate(id: String, offset: Int) = launch { programRepository.moveTemplate(id, offset) }

    fun addTemplate(name: String) = launch {
        _events.send(ProgramDetailEvent.TemplateCreated(programRepository.addTemplate(programId, name)))
    }

    fun delete() = launch {
        programRepository.deleteProgram(programId)
        _events.send(ProgramDetailEvent.Deleted)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
