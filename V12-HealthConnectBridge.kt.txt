package com.reps100.app

import android.app.Activity
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

object HealthConnectBridge {
    const val REQUEST_CODE = 7310
    private val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    fun sdkStatus(activity: Activity): Int = HealthConnectClient.getSdkStatus(activity)
    fun isAvailable(activity: Activity): Boolean = sdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE
    fun needsProviderUpdate(activity: Activity): Boolean = sdkStatus(activity) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    fun openProvider(activity: Activity) {
        val uri = android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }
    }

    fun requestPermissions(activity: Activity) {
        if (!isAvailable(activity)) return
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent: Intent = contract.createIntent(activity, permissions)
        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    fun hasPermissions(activity: Activity, callback: (Boolean) -> Unit) {
        if (!isAvailable(activity)) { callback(false); return }
        val client = HealthConnectClient.getOrCreate(activity)
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try { client.permissionController.getGrantedPermissions().containsAll(permissions) } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { callback(ok) }
        }
    }

    fun writeWorkout(activity: Activity, id: String, title: String, startMs: Long, endMs: Long, kcal: Double, details: String, callback: (Boolean, String) -> Unit) {
        if (!isAvailable(activity)) { callback(false, "Health Connect is not available on this device."); return }
        val client = HealthConnectClient.getOrCreate(activity)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val start = Instant.ofEpochMilli(startMs)
                val end = Instant.ofEpochMilli(endMs)
                val zone = ZoneId.systemDefault().rules.getOffset(start)
                val metadata = Metadata.activelyRecorded(
                    clientRecordId = "reps100-session-$id",
                    device = Device(type = Device.TYPE_PHONE)
                )
                val session = ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = title,
                    notes = details,
                    metadata = metadata
                )
                val calories = TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
                    energy = Energy.kilocalories(kcal.coerceAtLeast(0.0)),
                    metadata = Metadata.activelyRecorded(
                        clientRecordId = "reps100-calories-$id",
                        device = Device(type = Device.TYPE_PHONE)
                    )
                )
                client.insertRecords(listOf(session, calories))
                withContext(Dispatchers.Main) { callback(true, "Workout saved to Health Connect.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(false, e.message ?: "Health Connect export failed.") }
            }
        }
    }
}
