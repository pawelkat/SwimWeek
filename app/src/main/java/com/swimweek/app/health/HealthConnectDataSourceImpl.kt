package com.swimweek.app.health

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.swimweek.app.domain.SwimType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectDataSourceImpl @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
) : HealthConnectDataSource {

    override suspend fun listExerciseSessions(
        start: Instant,
        endExclusive: Instant,
    ): List<RawExerciseSession> {
        val client = clientProvider.requireClient()
        val out = mutableListOf<RawExerciseSession>()
        var pageToken: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, endExclusive),
                    pageToken = pageToken,
                ),
            )
            for (rec in page.records) {
                out += rec.toRaw()
            }
            pageToken = page.pageToken
        } while (pageToken != null)
        return out
    }

    override suspend fun distanceTotalMeters(
        start: Instant,
        end: Instant,
        dataOriginPackage: String?,
    ): Double {
        val client = clientProvider.requireClient()
        val originFilter = if (dataOriginPackage.isNullOrBlank()) {
            emptySet()
        } else {
            setOf(DataOrigin(dataOriginPackage))
        }
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = originFilter,
            ),
        )
        return response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    }

    private fun ExerciseSessionRecord.toRaw(): RawExerciseSession {
        val lapMeters = laps
            .mapNotNull { lap -> lap.length?.inMeters }
            .sum()
        return RawExerciseSession(
            id = metadata.id,
            swimType = exerciseType.toSwimTypeOrNull(),
            start = startTime,
            end = endTime,
            dataOriginPackage = metadata.dataOrigin.packageName,
            title = title,
            lapLengthMeters = lapMeters,
        )
    }

    private fun Int.toSwimTypeOrNull(): SwimType? =
        when (this) {
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> SwimType.POOL
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> SwimType.OPEN_WATER
            else -> null
        }
}
