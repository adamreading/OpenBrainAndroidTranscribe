package com.openbrain.client

data class MemoryRequest(
    val timestamp: String,
    val category: String,
    val text: String,
    val tags: List<String>,
    val source: String,
    val session_id: String
)
