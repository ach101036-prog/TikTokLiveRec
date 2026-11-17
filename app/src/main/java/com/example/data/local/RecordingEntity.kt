package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val streamTitle: String,
    val fileName: String,
    val contentUri: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val qualityLabel: String,
    val recordedAtMillis: Long
)
