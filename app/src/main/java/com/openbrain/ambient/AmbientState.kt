package com.openbrain.ambient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AmbientState {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    fun appendTranscript(text: String) {
        if (text.isNotBlank()) {
            _transcript.value += "\n" + text.trim()
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
}
