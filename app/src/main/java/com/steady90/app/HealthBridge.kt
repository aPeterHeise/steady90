package com.steady90.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HealthDay(val sleep: Int?, val steps: Long?, val source: String, val window: String)
data class HealthResult(val days: Map<String, HealthDay>, val origins: List<String>, val sleepGranted: Boolean, val stepsGranted: Boolean)

class HealthBridge(private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class), HealthPermission.getReadPermission(StepsRecord::class))
    fun status() = HealthConnectClient.getSdkStatus(context)
    suspend fun read(origin: String): HealthResult {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val canSleep = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
        val canSteps = HealthPermission.getReadPermission(StepsRecord::class) in granted
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        val now = Instant.now(); val start = today.minusDays(8).atStartOfDay(zone).toInstant()
        val sessions = mutableListOf<SleepSessionRecord>()
        if (canSleep) {
            var token: String? = null
            do {
                val response = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now), pageSize = 1000, pageToken = token))
                sessions += response.records; token = response.pageToken
            } while (token != null)
        }
        val origins = sessions.map { it.metadata.dataOrigin.packageName }.distinct().sorted()
        // Use a single origin to avoid merging two trackers' competing estimates.
        val chosen = origin.ifBlank {
            if ("com.google.android.apps.fitness" in origins) "com.google.android.apps.fitness"
            else sessions.maxByOrNull { it.endTime }?.metadata?.dataOrigin?.packageName.orEmpty()
        }
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val days = mutableMapOf<String, HealthDay>()
        for (offset in 7 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val nightly = sessions.filter { it.metadata.dataOrigin.packageName == chosen && it.endTime.atZone(zone).toLocalDate() == date }
            val intervals = nightly.flatMap { s ->
                val asleep = s.stages.filter { it.stage in setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM) }
                if (s.stages.isEmpty()) listOf(s.startTime.epochSecond to s.endTime.epochSecond)
                else asleep.map { maxOf(it.startTime.epochSecond, s.startTime.epochSecond) to minOf(it.endTime.epochSecond, s.endTime.epochSecond) }
            }
            val minutes = if (nightly.isEmpty()) null else (Metrics.unionSeconds(intervals) / 60).toInt()
            val window = if (nightly.isEmpty()) "" else {
                val from = nightly.minOf { it.startTime }.atZone(zone).format(formatter)
                val to = nightly.maxOf { it.endTime }.atZone(zone).format(formatter)
                "$from – $to" + if (nightly.any { it.stages.isEmpty() }) " · session estimate" else " · sleep stages"
            }
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
            val steps = if (canSteps) client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(date.atStartOfDay(zone).toInstant(), end)))[StepsRecord.COUNT_TOTAL] else null
            days[date.toString()] = HealthDay(minutes, steps, if (nightly.isEmpty()) "" else chosen, window)
        }
        return HealthResult(days, origins, canSleep, canSteps)
    }
}
