package com.example.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecordingEntity
import com.example.data.local.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val searchQuery: String = "",
    val recordings: List<RecordingEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0L
)

class VaultViewModel(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<VaultUiState> = combine(
        searchQuery,
        recordingRepository.recordings
    ) { query, allRecordings ->
        val filtered = if (query.isBlank()) {
            allRecordings
        } else {
            val q = query.trim().lowercase()
            allRecordings.filter {
                it.username.lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q) ||
                    it.streamTitle.lowercase().contains(q) ||
                    it.fileName.lowercase().contains(q)
            }
        }
        VaultUiState(
            searchQuery = query,
            recordings = filtered,
            totalCount = allRecordings.size,
            totalBytes = allRecordings.sumOf { it.fileSizeBytes }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = VaultUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    class Factory(
        private val recordingRepository: RecordingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultViewModel(recordingRepository) as T
        }
    }
}
