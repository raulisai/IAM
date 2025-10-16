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

data class SnapshotRequest(
    val snapshot_at: String, // ISO-8601 format "YYYY-MM-DDTHH:mm:ss.sssZ"
    val energy: Float? = null,
    val stamina: Float? = null,
    val strength: Float? = null,
    val flexibility: Float? = null,
    val attention: Float? = null,
    val score_body: Float? = null,
    val score_mind: Float? = null,
    val model_version: String? = "v1.0",
    val calories_burned: String? = null,
    val steps_daily: String? = null,
    val heart_rate: String? = null,
    val sleep_score: String? = null,
    val inputs: Map<String, String>? = null
)

interface TokenApi {
    @POST("/api/notification/register-token")
    suspend fun registerToken(@Body req: RegisterTokenRequest): Response<Unit>

    @POST("/api/stats/snapshots/update-latest")
    suspend fun createSnapshot(@Body req: SnapshotRequest): Response<Unit>
}
