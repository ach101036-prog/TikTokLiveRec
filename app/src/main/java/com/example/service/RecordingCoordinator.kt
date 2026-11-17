package com.example.service

import com.example.data.local.RecordingEntity
import com.example.data.network.LiveCreatorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RecordingPhase {
    data object Idle : RecordingPhase
    data object Connecting : RecordingPhase
    data object Recording : RecordingPhase
    data object Finalizing : RecordingPhase
    data class Saved(val recording: RecordingEntity) : RecordingPhase
    data class Failed(val message: String) : RecordingPhase
}

data class RecordingSessionState(
    val phase: RecordingPhase = RecordingPhase.Idle,
    val activeCreator: LiveCreatorInfo? = null,
    val elapsedMs: Long = 0L,
    val bytesWritten: Long = 0L,
    val segmentsProcessed: Int = 0
) {
    val isBusy: Boolean
        get() = phase is RecordingPhase.Connecting ||
            phase is RecordingPhase.Recording ||
            phase is RecordingPhase.Finalizing
}

class RecordingCoordinator {
    private val _state = MutableStateFlow(RecordingSessionState())
    val state: StateFlow<RecordingSessionState> = _state.asStateFlow()

    val stopFlag = AtomicBoolean(false)

    fun markConnecting(creator: LiveCreatorInfo) {
        stopFlag.set(false)
        _state.value = RecordingSessionState(
            phase = RecordingPhase.Connecting,
            activeCreator = creator,
            elapsedMs = 0L,
            bytesWritten = 0L,
            segmentsProcessed = 0
        )
    }

    fun markRecordingStarted() {
        _state.update { it.copy(phase = RecordingPhase.Recording) }
    }

    fun updateTimer(elapsedMs: Long) {
        _state.update { it.copy(elapsedMs = elapsedMs) }
    }

    fun updateStats(bytesWritten: Long, segmentsProcessed: Int) {
        _state.update {
            it.copy(
                bytesWritten = bytesWritten,
                segmentsProcessed = segmentsProcessed
            )
        }
    }

    fun markFinalizing() {
        _state.update { it.copy(phase = RecordingPhase.Finalizing) }
    }

    fun markSaved(recording: RecordingEntity) {
        _state.update {
            it.copy(
                phase = RecordingPhase.Saved(recording),
                activeCreator = null
            )
        }
    }

    fun markFailed(message: String) {
        _state.update {
            it.copy(
                phase = RecordingPhase.Failed(message),
                activeCreator = null
            )
        }
    }

    fun requestStop() {
        stopFlag.set(true)
    }

    fun clearBannerState() {
        _state.update { current ->
            if (current.phase is RecordingPhase.Saved || current.phase is RecordingPhase.Failed) {
                current.copy(phase = RecordingPhase.Idle, elapsedMs = 0L, bytesWritten = 0L, segmentsProcessed = 0)
            } else {
                current
            }
        }
    }
}
