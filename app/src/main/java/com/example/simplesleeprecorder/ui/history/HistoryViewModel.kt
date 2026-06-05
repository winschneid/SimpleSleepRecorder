package com.example.simplesleeprecorder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplesleeprecorder.data.repository.SleepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: SleepRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                _uiState.value = HistoryUiState.Success(sessions)
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        val current = _uiState.value as? HistoryUiState.Success ?: return
        val session = current.sessions.find { it.id == sessionId } ?: return
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }
}
