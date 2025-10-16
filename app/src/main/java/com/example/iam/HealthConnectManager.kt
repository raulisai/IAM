package com.example.iam

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
        val hasAll = grantedPermissions.containsAll(PERMISSIONS)
        
        Log.d(TAG, "Granted permissions: ${grantedPermissions.size} of ${PERMISSIONS.size}")
        Log.d(TAG, "Granted: $grantedPermissions")
        Log.d(TAG, "Required: $PERMISSIONS")
        Log.d(TAG, "Has all permissions: $hasAll")
        
        if (!hasAll) {
            val missing = PERMISSIONS - grantedPermissions
            Log.w(TAG, "Missing permissions: $missing")
        }
        
        return hasAll
    }

    suspend fun getDailySummary(): DailySummary {
        val today = ZonedDateTime.now()
        val startOfDay = today.toLocalDate().atStartOfDay(today.zone).toInstant()
        val now = Instant.now()

        Log.d(TAG, "Reading health data from $startOfDay to $now")
        
        val steps = readSteps(startOfDay, now)
        Log.d(TAG, "Steps: $steps")
        
        val distance = readDistance(startOfDay, now)
        Log.d(TAG, "Distance: $distance meters")
        
        val calories = readTotalCaloriesBurned(startOfDay, now)
        Log.d(TAG, "Calories: $calories")
        
        val heartRate = readAverageHeartRate(startOfDay, now)
        Log.d(TAG, "Heart rate: $heartRate")
        
        val sleep = readSleepDuration(startOfDay, now)
        Log.d(TAG, "Sleep: $sleep minutes")

        return DailySummary(
            steps = steps,
            distanceMeters = distance,
            calories = calories,
            averageHeartRate = heartRate,
            sleepMinutes = sleep
        )
    }

    private suspend fun readSteps(start: Instant, end: Instant): Long {
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.count }
    }

    private suspend fun readDistance(start: Instant, end: Instant): Double {
        val request = ReadRecordsRequest(
            recordType = DistanceRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.distance.inMeters }
    }

    private suspend fun readTotalCaloriesBurned(start: Instant, end: Instant): Double {
        val request = ReadRecordsRequest(
            recordType = TotalCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.energy.inKilocalories }
    }

    private suspend fun readAverageHeartRate(start: Instant, end: Instant): Double? {
        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return if (response.records.isNotEmpty()) {
            response.records.flatMap { it.samples }.map { it.beatsPerMinute }.average()
        } else {
            null
        }
    }

    private suspend fun readSleepDuration(start: Instant, end: Instant): Long? {
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.firstOrNull()?.let {
            it.endTime.toEpochMilli() - it.startTime.toEpochMilli()
        }
    }

    companion object {
        private const val TAG = "HealthConnectManager"
        
        val PERMISSIONS =
            setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(SleepSessionRecord::class),
                HealthPermission.getReadPermission(DistanceRecord::class),
                HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                HealthPermission.getReadPermission(WeightRecord::class)
            )
    }
}

data class DailySummary(
    val steps: Long,
    val distanceMeters: Double,
    val calories: Double,
    val averageHeartRate: Double?,
    val sleepMinutes: Long?
)

// Para solicitar permisos, usa Activity Result API en tu Activity o Fragment principal:
// Ejemplo:
// val requestPermissionLauncher = registerForActivityResult(
//     healthConnectClient.permissionController.createRequestPermissionResultContract()
// ) { granted ->
//     // granted: Set<String> con los permisos concedidos
// }
// requestPermissionLauncher.launch(PERMISSIONS)
