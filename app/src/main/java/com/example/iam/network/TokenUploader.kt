package com.example.iam.network

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object TokenUploader {
    private const val TAG = "TokenUploader"
    // Cambia la baseURL a la de tu backend
    private const val BASE_URL = "https://s8s23kr8-5000.usw3.devtunnels.ms"
    private const val STATIC_BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNmEwMTI3NzctZmRhZi00ZWUxLWI0MWItYjU5ZjQ4Mzc0ZjU5IiwiZW1haWwiOiJkakB4eC5jb20iLCJuYW1lIjoiRGpva2VyIE0iLCJleHAiOjE3NjA1OTQzNzQsImlhdCI6MTc2MDUwNzk3NH0.q4F1_e6i_zWVnjsUdAHE980Ju71VM1IEK_b3jfIM0TE"

    private val api: TokenApi by lazy {
        // Interceptor para logging
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        // Interceptor para añadir el token de autorización
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithAuth = originalRequest.newBuilder()
                .header("Authorization", "Bearer $STATIC_BEARER_TOKEN")
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
            .addConverterFactory(MoshiConverterFactory.create())
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
                val resp = api.registerToken(req)
                Log.d(TAG, "Respuesta de registerToken: ${resp.code()}")

            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar el token", e)
            }
        }
    }
}
