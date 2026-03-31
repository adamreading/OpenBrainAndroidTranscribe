package com.openbrain.client

import retrofit2.Response
import retrofit2.http.*

interface OpenBrainApi {

    @POST("rest/v1/memories")
    @Headers("Prefer: return=minimal")
    suspend fun postMemory(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body memory: MemoryRequest
    ): Response<Unit>

    @GET("rest/v1/memories")
    suspend fun testConnection(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "id",
        @Query("limit") limit: Int = 1
    ): Response<List<Any>>
}
