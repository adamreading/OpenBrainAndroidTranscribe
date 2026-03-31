package com.openbrain.llm

data class MemoryItem(
    val timestamp: String,
    val category: String, // "decision" | "task" | "fact" | "reminder"
    val text: String,
    val tags: List<String>,
    val source: String = "android-ambient",
    val sessionId: String
)
