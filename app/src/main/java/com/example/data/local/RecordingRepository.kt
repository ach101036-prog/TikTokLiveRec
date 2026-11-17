package com.example.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(
    private val context: Context,
    private val recordingDao: RecordingDao
) {
    val recordings: Flow<List<RecordingEntity>> = recordingDao.observeAll()

    fun observeRecording(id: Long): Flow<RecordingEntity?> = recordingDao.observeById(id)

    suspend fun getRecording(id: Long): RecordingEntity? = recordingDao.getById(id)

    suspend fun saveRecording(recording: RecordingEntity): Long {
        return recordingDao.insert(recording)
    }

    suspend fun deleteRecording(recording: RecordingEntity) {
        withContext(Dispatchers.IO) {
            if (recording.contentUri.startsWith("content://")) {
                runCatching {
                    context.contentResolver.delete(Uri.parse(recording.contentUri), null, null)
                }
            }
            if (recording.filePath.isNotBlank()) {
                runCatching {
                    val file = File(recording.filePath)
                    if (file.exists()) file.delete()
                }
            }
            recordingDao.deleteById(recording.id)
        }
    }
}
