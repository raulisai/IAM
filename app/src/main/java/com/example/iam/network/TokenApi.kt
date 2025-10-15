package com.example.iam.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Data class para la información del dispositivo.
 * Coincide con el objeto "device_info" del JSON.
 */
data class DeviceInfo(
    val app_version: String,
    val model: String,
    val os_version: String
)

/**
 * Data class actualizado para el cuerpo de la solicitud (request body).
 * Coincide con la estructura JSON principal.
 */
data class RegisterTokenRequest(
    val device_info: DeviceInfo,
    val platform: String,
    val token: String
)

interface TokenApi {
    @POST("/api/notification/register-token")
    suspend fun registerToken(@Body req: RegisterTokenRequest): Response<Unit>
}
