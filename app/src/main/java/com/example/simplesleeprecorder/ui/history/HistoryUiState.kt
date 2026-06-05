package com.example.simplesleeprecorder.ui.history

import com.example.simplesleeprecorder.domain.model.SleepSession

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Success(val sessions: List<SleepSession>) : HistoryUiState()
}
