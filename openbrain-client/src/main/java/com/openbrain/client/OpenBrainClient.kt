package com.openbrain.client

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class OpenBrainClient(baseUrl: String) {

    private val TAG = "OpenBrainClient"

    private val api: OpenBrainApi = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .build()
        .create(OpenBrainApi::class.java)

    suspend fun postMemory(apiKey: String, memory: MemoryRequest): Result<Unit> {
        val auth = "Bearer $apiKey"
        var lastException: Exception? = null

        for (attempt in 1..3) {
            try {
                val response = api.postMemory(apiKey, auth, memory)
                if (response.isSuccessful) {
                    return Result.success(Unit)
                }
                lastException = Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}")
            } catch (e: Exception) {
                lastException = e
            }

            if (attempt < 3) {
                val backoff = (1000L * (1 shl (attempt - 1))) // 1s, 2s
                Log.d(TAG, "Retry attempt $attempt after ${backoff}ms")
                delay(backoff)
            }
        }

        return Result.failure(lastException ?: Exception("Unknown error"))
    }

    suspend fun testConnection(apiKey: String): Result<String> {
        return try {
            val auth = "Bearer $apiKey"
            val response = api.testConnection(apiKey, auth)
            if (response.isSuccessful) {
                Result.success("OK")
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
