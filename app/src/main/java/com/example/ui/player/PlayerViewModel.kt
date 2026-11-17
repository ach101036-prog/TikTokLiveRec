package com.example.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecordingEntity
import com.example.data.local.RecordingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val recording: RecordingEntity) : PlayerUiState
    data object Missing : PlayerUiState
}

class PlayerViewModel(
    recordingId: Long,
    recordingRepository: RecordingRepository
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = recordingRepository.observeRecording(recordingId)
        .map { entity ->
            if (entity != null) PlayerUiState.Ready(entity) else PlayerUiState.Missing
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PlayerUiState.Loading
        )

    class Factory(
        private val recordingId: Long,
        private val recordingRepository: RecordingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(recordingId, recordingRepository) as T
        }
    }
}
