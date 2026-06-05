package com.example.simplesleeprecorder.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplesleeprecorder.data.repository.SleepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(private val repository: SleepRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            _uiState.value = if (session != null) ResultUiState.Success(session) else ResultUiState.Error
        }
    }
}
