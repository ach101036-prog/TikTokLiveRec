package com.example.ui.studio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.local.StreamQualityPreference
import com.example.data.network.LiveCheckResult
import com.example.data.network.LiveCreatorInfo
import com.example.data.network.TikTokLiveChecker
import com.example.service.RecordingCoordinator
import com.example.service.RecordingService
import com.example.service.RecordingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CheckUiState {
    data object Idle : CheckUiState
    data class Checking(val username: String) : CheckUiState
    data class Result(val result: LiveCheckResult) : CheckUiState
}

data class StudioUiState(
    val usernameInput: String = "",
    val recentUsernames: List<String> = emptyList(),
    val preferredQuality: StreamQualityPreference = StreamQualityPreference.BEST,
    val checkState: CheckUiState = CheckUiState.Idle,
    val recordingState: RecordingSessionState = RecordingSessionState(),
    val isPreviewMuted: Boolean = false
)

class StudioViewModel(
    private val liveChecker: TikTokLiveChecker,
    private val preferencesRepository: PreferencesRepository,
    private val recordingCoordinator: RecordingCoordinator
) : ViewModel() {

    private val usernameInput = MutableStateFlow("")
    private val checkState = MutableStateFlow<CheckUiState>(CheckUiState.Idle)
    private val isPreviewMuted = MutableStateFlow(false)

    val uiState: StateFlow<StudioUiState> = combine(
        usernameInput,
        preferencesRepository.recentUsernames,
        preferencesRepository.preferredQuality,
        checkState,
        recordingCoordinator.state,
        isPreviewMuted
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        StudioUiState(
            usernameInput = values[0] as String,
            recentUsernames = values[1] as List<String>,
            preferredQuality = values[2] as StreamQualityPreference,
            checkState = values[3] as CheckUiState,
            recordingState = values[4] as RecordingSessionState,
            isPreviewMuted = values[5] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StudioUiState()
    )

    fun onUsernameChange(value: String) {
        usernameInput.value = value
    }

    fun selectRecentUsername(username: String) {
        usernameInput.value = username
        checkLiveStatus(username)
    }

    fun removeRecentUsername(username: String) {
        viewModelScope.launch {
            preferencesRepository.removeRecentUsername(username)
        }
    }

    fun setPreferredQuality(quality: StreamQualityPreference) {
        viewModelScope.launch {
            preferencesRepository.setPreferredQuality(quality)
            val currentCheck = (checkState.value as? CheckUiState.Result)?.result
            if (currentCheck is LiveCheckResult.Online) {
                checkLiveStatus(currentCheck.info.username)
            }
        }
    }

    fun checkLiveStatus(overrideUsername: String? = null) {
        val raw = overrideUsername ?: usernameInput.value
        val normalized = TikTokLiveChecker.normalizeUsername(raw)
        if (normalized.isBlank()) {
            checkState.value = CheckUiState.Result(
                LiveCheckResult.NotFound("", "Please enter a TikTok @username first.")
            )
            return
        }

        viewModelScope.launch {
            checkState.value = CheckUiState.Checking(normalized)
            preferencesRepository.addRecentUsername(normalized)
            val result = liveChecker.checkLiveStatus(
                rawUsername = raw,
                preferredQuality = uiState.value.preferredQuality
            )
            checkState.value = CheckUiState.Result(result)
        }
    }

    fun startRecording(context: Context, creator: LiveCreatorInfo) {
        recordingCoordinator.clearBannerState()
        RecordingService.startRecording(context.applicationContext, creator)
    }

    fun stopRecording(context: Context) {
        recordingCoordinator.requestStop()
        RecordingService.stopRecording(context.applicationContext)
    }

    fun togglePreviewMute() {
        isPreviewMuted.value = !isPreviewMuted.value
    }

    fun dismissBanner() {
        recordingCoordinator.clearBannerState()
    }

    class Factory(
        private val liveChecker: TikTokLiveChecker,
        private val preferencesRepository: PreferencesRepository,
        private val recordingCoordinator: RecordingCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(liveChecker, preferencesRepository, recordingCoordinator) as T
        }
    }
}
