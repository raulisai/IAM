package com.example.iam.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.iam.DailySummary
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant

object TokenUploader {
    private const val TAG = "TokenUploader"
    // Cambia la baseURL a la de tu backend
    private const val BASE_URL = "https://s8s23kr8-5000.usw3.devtunnels.ms"
    private const val STATIC_BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNmEwMTI3NzctZmRhZi00ZWUxLWI0MWItYjU5ZjQ4Mzc0ZjU5IiwiZW1haWwiOiJkakB4eC5jb20iLCJuYW1lIjoiRGpva2VyIE0iLCJleHAiOjE3NjA3MTg1ODcsImlhdCI6MTc2MDYzMjE4N30.UonZrnr_sZjrdCO5CtTsoat0vCFDkP9cMud06GI5xEA"

    private val api: TokenApi by lazy {
        // Configurar Moshi con soporte para Kotlin
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        // Interceptor para logging
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        // Interceptor para añadir headers necesarios
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithAuth = originalRequest.newBuilder()
                .header("Authorization", "Bearer $STATIC_BEARER_TOKEN")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()
            chain.proceed(requestWithAuth)
        }

        // Construir el cliente OkHttp con ambos interceptores
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor(authInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TokenApi::class.java)
    }

    fun uploadToken(context: Context, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtener la información del dispositivo
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "N/A"
                }
                val model = Build.MODEL
                val osVersion = Build.VERSION.RELEASE

                // 2. Construir el objeto de la petición con la nueva estructura
                val deviceInfo = DeviceInfo(
                    app_version = appVersion ?: "",
                    model = model ?: "Unknown",
                    os_version = osVersion ?: "Unknown"
                )

                val req = RegisterTokenRequest(
                    device_info = deviceInfo,
                    platform = "android",
                    token = token
                )

                // 3. Enviar la petición
                Log.d(TAG, "Enviando token: $req")
                val resp = api.registerToken(req)
                if (resp.isSuccessful) {
                    Log.d(TAG, "Token registrado exitosamente: ${resp.code()}")
                } else {
                    Log.e(TAG, "Error al registrar token: ${resp.code()} - ${resp.errorBody()?.string()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar el token", e)
            }
        }
    }

    fun uploadHealthData(context: Context, dailySummary: DailySummary) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshotRequest = SnapshotRequest(
                    snapshot_at = Instant.now().toString(),
                    calories_burned = dailySummary.calories.toString(),
                    steps_daily = dailySummary.steps.toString(),
                    heart_rate = dailySummary.averageHeartRate?.toString(),
                    sleep_score = dailySummary.sleepMinutes?.toString() // Sending minutes as score
                )

                Log.d(TAG, "Enviando datos de salud: $snapshotRequest")
                val resp = api.createSnapshot(snapshotRequest)
                if (resp.isSuccessful) {
                    Log.d(TAG, "Datos de salud enviados exitosamente: ${resp.code()}")
                } else {
                    Log.e(TAG, "Error al enviar datos de salud: ${resp.code()} - ${resp.errorBody()?.string()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al subir datos de salud", e)
            }
        }
    }
}
