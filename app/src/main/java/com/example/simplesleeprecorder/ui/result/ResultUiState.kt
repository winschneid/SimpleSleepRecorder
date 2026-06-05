package com.example.simplesleeprecorder.ui.result

import com.example.simplesleeprecorder.domain.model.SleepSession

sealed class ResultUiState {
    data object Loading : ResultUiState()
    data class Success(val session: SleepSession) : ResultUiState()
    data object Error : ResultUiState()
}
