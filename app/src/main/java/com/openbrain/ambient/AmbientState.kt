package com.openbrain.ambient

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openbrain.core.AppSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object AmbientState {

    data class SyncLogEntry(
        val timestamp: Long,
        val status: String, // "success" | "error" | "retry"
        val message: String
    )

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _syncLog = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val syncLog: StateFlow<List<SyncLogEntry>> = _syncLog

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val SYNC_LOG_KEY = stringPreferencesKey("sync_log")

    fun init(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            // Force inactive on start to prevent crash loops.
            // This ensures the app doesn't immediately try to load heavy models if it crashed while active.
            _isActive.value = false

            // Restore sync log from DataStore
            val storedLog = AppSettings.getInstance(appContext).data
                .map { it[SYNC_LOG_KEY] }
                .first()
            if (storedLog != null) {
                try {
                    val type = object : TypeToken<List<SyncLogEntry>>() {}.type
                    _syncLog.value = gson.fromJson(storedLog, type)
                } catch (_: Exception) {
                    _syncLog.value = emptyList()
                }
            }
        }
    }

    private const val MAX_TRANSCRIPT_LENGTH = 10000

    fun appendTranscript(text: String) {
        if (text.isNotBlank()) {
            val current = _transcript.value
            val newContent = if (current.isEmpty()) text.trim() else current + "\n" + text.trim()
            
            // Sliding window: keep only the last MAX_TRANSCRIPT_LENGTH characters
            _transcript.value = if (newContent.length > MAX_TRANSCRIPT_LENGTH) {
                newContent.substring(newContent.length - MAX_TRANSCRIPT_LENGTH)
            } else {
                newContent
            }
        }
    }

    fun clearTranscript() {
        _transcript.value = ""
    }

    fun toggleActive() {
        _isActive.value = !_isActive.value
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    suspend fun setActive(context: Context, active: Boolean) {
        _isActive.value = active
        AppSettings.getInstance(context).edit { prefs ->
            prefs[AppSettings.IS_ACTIVE] = active
        }
    }

    fun addSyncLogEntry(context: Context, entry: SyncLogEntry) {
        val updated = (_syncLog.value + entry).takeLast(50)
        _syncLog.value = updated
        scope.launch {
            AppSettings.getInstance(context).edit { prefs ->
                prefs[SYNC_LOG_KEY] = gson.toJson(updated)
            }
        }
    }
}
