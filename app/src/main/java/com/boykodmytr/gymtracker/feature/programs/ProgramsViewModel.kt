package com.boykodmytr.gymtracker.feature.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val programRepository: ProgramRepository,
) : ViewModel() {

    val programs: StateFlow<List<Program>?> = programRepository.observePrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _created = Channel<String>(Channel.BUFFERED)
    val created: Flow<String> = _created.receiveAsFlow()

    fun createProgram(name: String, description: String) {
        viewModelScope.launch { _created.send(programRepository.createProgram(name, description)) }
    }
}
