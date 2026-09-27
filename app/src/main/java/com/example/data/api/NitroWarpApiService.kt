package com.example.data.api

import com.example.data.model.ServerDto
import com.example.data.model.StatusResponse
import com.example.data.model.WarpGenerateRequest
import com.example.data.model.WarpGenerateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface NitroWarpApiService {

    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponse>

    @GET("api/servers")
    suspend fun getServers(): Response<com.example.data.model.ServersResponse>

    @POST("api/warp-generate")
    suspend fun generateWarp(
        @Body request: WarpGenerateRequest
    ): Response<WarpGenerateResponse>
}
