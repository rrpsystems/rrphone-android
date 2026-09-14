package com.rrpsystems.rrphone.core.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    val user: UserDto?
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String
)

data class ProvisionResponse(
    @SerializedName("sip_server")
    val sipServer: String,
    @SerializedName("sip_extension")
    val sipExtension: String,
    @SerializedName("sip_password")
    val sipPassword: String,
    val transport: String,
    @SerializedName("stun_server")
    val stunServer: String?,
    @SerializedName("turn_server")
    val turnServer: String?,
    @SerializedName("turn_user")
    val turnUser: String?,
    @SerializedName("turn_pass")
    val turnPass: String?,
    val features: FeaturesDto?
)

data class FeaturesDto(
    @SerializedName("voz_hd")
    val vozHd: Boolean?,
    @SerializedName("push_voip")
    val pushVoip: Boolean?,
    val presenca: Boolean?,
    val videochamada: Boolean?,
    val mensageria: Boolean?,
    @SerializedName("callcenter_status_agente")
    val callcenterStatusAgente: Boolean?,
    @SerializedName("callcenter_fila_stats")
    val callcenterFilaStats: Boolean?,
    @SerializedName("callcenter_discador")
    val callcenterDiscador: Boolean?
)
