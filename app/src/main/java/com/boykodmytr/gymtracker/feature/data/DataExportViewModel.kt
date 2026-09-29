package com.boykodmytr.gymtracker.feature.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.data.transfer.DocumentFiles
import com.boykodmytr.gymtracker.domain.repository.DataTransferRepository
import com.boykodmytr.gymtracker.domain.transfer.CsvDialect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class ExportKind(val filePrefix: String) { WORKOUTS("gym-tracker-trenuvannia"), BODY("gym-tracker-parametry-tila") }

data class ExportUiState(val dialect: CsvDialect = CsvDialect.SEMICOLON, val busy: Boolean = false)

sealed interface ExportEvent {
    data class Saved(val rows: Int) : ExportEvent
    data object Failed : ExportEvent
}

@HiltViewModel
class DataExportViewModel @Inject constructor(
    private val repository: DataTransferRepository,
    private val files: DocumentFiles,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    fun setDialect(dialect: CsvDialect) {
        _state.value = _state.value.copy(dialect = dialect)
    }

    /** Suggested name for the system "save as" dialog, e.g. gym-tracker-trenuvannia-2026-09-29.csv. */
    fun fileName(kind: ExportKind): String = "${kind.filePrefix}-${LocalDate.now(clock)}.csv"

    fun export(kind: ExportKind, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val dialect = _state.value.dialect
                val result = when (kind) {
                    ExportKind.WORKOUTS -> repository.exportWorkouts(dialect)
                    ExportKind.BODY -> repository.exportMeasurements(dialect)
                }
                files.writeText(uri, result.text)
                _events.send(ExportEvent.Saved(result.rows))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DataExport", "Export failed", e)
                _events.send(ExportEvent.Failed)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
