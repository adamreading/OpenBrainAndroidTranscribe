package com.openbrain.ambient

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object AppSettings {
    val SUPABASE_URL = stringPreferencesKey("supabase_url")
    val SUPABASE_API_KEY = stringPreferencesKey("supabase_api_key")
    val WHISPER_MODEL = stringPreferencesKey("whisper_model") // "tiny" | "base" | "small"
    val LLM_MODEL = stringPreferencesKey("llm_model")
    val WHISPER_THREADS = intPreferencesKey("whisper_threads") // default 4
    val LLM_THREADS = intPreferencesKey("llm_threads") // default 6
    val WAKE_WORD = stringPreferencesKey("wake_word") // default "hey adam"
    val SLEEP_WORD = stringPreferencesKey("sleep_word") // default "go to sleep"
    val IS_ACTIVE = booleanPreferencesKey("is_active") // persisted state

    val Context.settingsDataStore by preferencesDataStore("app_settings")
}
