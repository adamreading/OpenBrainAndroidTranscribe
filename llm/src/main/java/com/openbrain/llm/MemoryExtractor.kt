package com.openbrain.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser

class MemoryExtractor(private val llamaLib: LlamaLib) {

    private val TAG = "MemoryExtractor"
    private val gson = Gson()

    private val EXTRACTION_PROMPT = """
You are a memory extraction assistant. Given a conversation transcript, extract ONLY the important information: decisions made, tasks assigned, facts stated, dates/deadlines, and key names or contacts.

Output ONLY a JSON array. Each item must have this exact structure:
{"timestamp":"<ISO8601>","category":"decision|task|fact|reminder","text":"<concise extracted item>","tags":["<tag1>","<tag2>"],"source":"android-ambient","session_id":"<session_id>"}

If nothing important was said, output an empty array: []

Transcript:
""".trimIndent()

    fun extract(transcript: String, sessionId: String, contextPtr: Long): List<MemoryItem> {
        if (transcript.isBlank()) return emptyList()

        val prompt = EXTRACTION_PROMPT + transcript + "\n\nJSON output:"
        val raw = llamaLib.runInference(contextPtr, prompt, 512)
        return parseMemoryItems(raw, sessionId)
    }

    private fun parseMemoryItems(raw: String, sessionId: String): List<MemoryItem> {
        try {
            // Find JSON array in the raw output
            val jsonStart = raw.indexOf('[')
            val jsonEnd = raw.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.d(TAG, "No JSON array found in output")
                return emptyList()
            }

            val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
            val jsonArray = JsonParser.parseString(jsonStr).asJsonArray

            return jsonArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    val tags = obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()
                    MemoryItem(
                        timestamp = obj.get("timestamp")?.asString ?: "",
                        category = obj.get("category")?.asString ?: "fact",
                        text = obj.get("text")?.asString ?: return@mapNotNull null,
                        tags = tags,
                        source = "android-ambient",
                        sessionId = obj.get("session_id")?.asString ?: sessionId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse memory item: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse memory extraction output: ${e.message}")
            return emptyList()
        }
    }
}
