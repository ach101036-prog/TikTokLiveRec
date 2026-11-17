package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "livevault_prefs")

enum class StreamQualityPreference(val label: String) {
    BEST("Auto (Best)"),
    HD("HD 720p"),
    SD("SD 480p")
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val RECENT_USERNAMES = stringPreferencesKey("recent_usernames")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
    }

    val recentUsernames: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.RECENT_USERNAMES].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    val preferredQuality: Flow<StreamQualityPreference> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.PREFERRED_QUALITY]
        StreamQualityPreference.entries.firstOrNull { it.name == name } ?: StreamQualityPreference.BEST
    }

    suspend fun addRecentUsername(username: String) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_USERNAMES]
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() && !it.equals(clean, ignoreCase = true) }
            val updated = listOf(clean) + current.take(7)
            prefs[Keys.RECENT_USERNAMES] = updated.joinToString(",")
        }
    }

    suspend fun removeRecentUsername(username: String) {
        context.dataStore.edit { prefs ->
            val updated = prefs[Keys.RECENT_USERNAMES]
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() && !it.equals(username, ignoreCase = true) }
            prefs[Keys.RECENT_USERNAMES] = updated.joinToString(",")
        }
    }

    suspend fun setPreferredQuality(quality: StreamQualityPreference) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_QUALITY] = quality.name
        }
    }
}
