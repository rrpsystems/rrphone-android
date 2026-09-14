package com.rrpsystems.rrphone.core.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RRPAuthService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/softphone/provision")
    suspend fun fetchProvisioning(): Response<ProvisionResponse>
}
