package com.openbrain.client

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.TimeUnit

class MemorySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "MemorySyncWorker"
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val queueFile = File(applicationContext.filesDir, "memory_queue.json")
        if (!queueFile.exists()) return Result.success()

        val baseUrl = inputData.getString("base_url") ?: return Result.failure()
        val apiKey = inputData.getString("api_key") ?: return Result.failure()

        val type = object : TypeToken<MutableList<MemoryRequest>>() {}.type
        val queue: MutableList<MemoryRequest> = try {
            gson.fromJson(queueFile.readText(), type) ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read queue: ${e.message}")
            return Result.retry()
        }

        if (queue.isEmpty()) return Result.success()

        val client = OpenBrainClient(baseUrl)
        val failed = mutableListOf<MemoryRequest>()

        for (memory in queue) {
            val result = client.postMemory(apiKey, memory)
            if (result.isFailure) {
                Log.w(TAG, "Failed to sync memory: ${result.exceptionOrNull()?.message}")
                failed.add(memory)
            }
        }

        // Write remaining items back
        if (failed.isEmpty()) {
            queueFile.delete()
        } else {
            queueFile.writeText(gson.toJson(failed))
        }

        return if (failed.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "memory_sync"

        fun enqueue(context: Context, baseUrl: String, apiKey: String) {
            val data = workDataOf(
                "base_url" to baseUrl,
                "api_key" to apiKey
            )

            val request = OneTimeWorkRequestBuilder<MemorySyncWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueueMemory(context: Context, memory: MemoryRequest) {
            val gson = Gson()
            val queueFile = File(context.filesDir, "memory_queue.json")
            val type = object : TypeToken<MutableList<MemoryRequest>>() {}.type
            val queue: MutableList<MemoryRequest> = if (queueFile.exists()) {
                try {
                    gson.fromJson(queueFile.readText(), type) ?: mutableListOf()
                } catch (_: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            queue.add(memory)
            queueFile.writeText(gson.toJson(queue))
        }
    }
}
