package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimweek.app.domain.DayOfWeekSerializer
import com.swimweek.app.domain.DistanceSource
import com.swimweek.app.domain.SourceStatus
import com.swimweek.app.domain.SwimSession
import com.swimweek.app.domain.SwimType
import com.swimweek.app.domain.WeekRange
import com.swimweek.app.domain.WeeklySwimSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.summaryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "summary_prefs",
)

/**
 * On-device cache of derived weekly swim totals (health-sensitive; excluded from backup).
 */
@Singleton
class SummaryCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.summaryDataStore

    val summaryFlow: Flow<WeeklySwimSummary?> = dataStore.data.map { prefs -> prefs.toSummary() }

    /** One-shot read for Glance / workers (DataStore is source of truth). */
    suspend fun getOnce(): WeeklySwimSummary? = summaryFlow.first()

    suspend fun save(summary: WeeklySwimSummary) {
        dataStore.edit { prefs ->
            prefs[Keys.WEEK_IDENTITY] = summary.week.identityKey()
            prefs[Keys.WEEK_START_EPOCH] = summary.week.start.toEpochMilli()
            prefs[Keys.WEEK_END_EPOCH] = summary.week.endExclusive.toEpochMilli()
            prefs[Keys.ZONE_ID] = summary.week.zoneId.id
            prefs[Keys.WEEK_START_DAY] = summary.week.weekStart.name
            prefs[Keys.TOTAL_METERS] = summary.totalDistanceMeters
            prefs[Keys.SESSION_COUNT] = summary.sessionCount
            prefs[Keys.SESSIONS_WITH_DISTANCE] = summary.sessionsWithDistanceCount
            prefs[Keys.PARTIAL_COUNT] = summary.partialDistanceSessionCount
            prefs[Keys.LAST_SYNCED] = summary.lastSyncedAt.toEpochMilli()
            prefs[Keys.SOURCE_STATUS] = summary.sourceStatus.name
            prefs[Keys.USER_REPORTED_MISSING] = summary.userReportedMissingData
            prefs[Keys.SESSIONS_BLOB] = encodeSessions(summary.sessions)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSummary(): WeeklySwimSummary? {
        val identity = this[Keys.WEEK_IDENTITY] ?: return null
        val startEpoch = this[Keys.WEEK_START_EPOCH] ?: return null
        val endEpoch = this[Keys.WEEK_END_EPOCH] ?: return null
        val zone = this[Keys.ZONE_ID] ?: return null
        val weekStartName = this[Keys.WEEK_START_DAY] ?: DayOfWeek.MONDAY.name
        val total = this[Keys.TOTAL_METERS] ?: 0.0
        val sessionCount = this[Keys.SESSION_COUNT] ?: 0
        val withDistance = this[Keys.SESSIONS_WITH_DISTANCE] ?: 0
        val partial = this[Keys.PARTIAL_COUNT] ?: 0
        val lastSynced = this[Keys.LAST_SYNCED] ?: return null
        val statusName = this[Keys.SOURCE_STATUS] ?: SourceStatus.NO_DATA.name
        val userReported = this[Keys.USER_REPORTED_MISSING] ?: false
        val sessionsBlob = this[Keys.SESSIONS_BLOB].orEmpty()

        val week = WeekRange(
            start = Instant.ofEpochMilli(startEpoch),
            endExclusive = Instant.ofEpochMilli(endEpoch),
            zoneId = ZoneId.of(zone),
            weekStart = DayOfWeekSerializer.parse(weekStartName),
        )
        // identity sanity: recompute must match stored key when zone/start align
        if (week.identityKey() != identity) {
            // Still return data; identity stored for rollover comparison
        }

        return WeeklySwimSummary(
            week = week,
            totalDistanceMeters = total,
            sessionCount = sessionCount,
            sessionsWithDistanceCount = withDistance,
            partialDistanceSessionCount = partial,
            sessions = decodeSessions(sessionsBlob),
            lastSyncedAt = Instant.ofEpochMilli(lastSynced),
            sourceStatus = runCatching { SourceStatus.valueOf(statusName) }
                .getOrDefault(SourceStatus.ERROR),
            userReportedMissingData = userReported,
        )
    }

    private fun encodeSessions(sessions: List<SwimSession>): String {
        // id\x1ftype\x1fstart\x1fend\x1fmeters\x1fsource\x1fpartial\x1fpackage\x1ftitle\x1foverlap
        return sessions.joinToString("\n") { s ->
            listOf(
                s.id,
                s.exerciseType.name,
                s.start.toEpochMilli().toString(),
                s.end.toEpochMilli().toString(),
                s.distanceMeters.toString(),
                s.distanceSource.name,
                s.partialDistance.toString(),
                s.dataOriginPackage,
                s.title.orEmpty().replace('\n', ' ').replace('\u001f', ' '),
                s.overlapSuppressed.toString(),
            ).joinToString("\u001f")
        }
    }

    private fun decodeSessions(blob: String): List<SwimSession> {
        if (blob.isBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { line ->
            val p = line.split('\u001f')
            if (p.size < 10) return@mapNotNull null
            runCatching {
                SwimSession(
                    id = p[0],
                    exerciseType = SwimType.valueOf(p[1]),
                    start = Instant.ofEpochMilli(p[2].toLong()),
                    end = Instant.ofEpochMilli(p[3].toLong()),
                    distanceMeters = p[4].toDouble(),
                    distanceSource = DistanceSource.valueOf(p[5]),
                    partialDistance = p[6].toBoolean(),
                    dataOriginPackage = p[7],
                    title = p[8].ifEmpty { null },
                    overlapSuppressed = p[9].toBoolean(),
                )
            }.getOrNull()
        }.toList()
    }

    private object Keys {
        val WEEK_IDENTITY = stringPreferencesKey("week_identity")
        val WEEK_START_EPOCH = longPreferencesKey("week_start_epoch")
        val WEEK_END_EPOCH = longPreferencesKey("week_end_epoch")
        val ZONE_ID = stringPreferencesKey("zone_id")
        val WEEK_START_DAY = stringPreferencesKey("week_start_day")
        val TOTAL_METERS = doublePreferencesKey("total_meters")
        val SESSION_COUNT = intPreferencesKey("session_count")
        val SESSIONS_WITH_DISTANCE = intPreferencesKey("sessions_with_distance")
        val PARTIAL_COUNT = intPreferencesKey("partial_count")
        val LAST_SYNCED = longPreferencesKey("last_synced")
        val SOURCE_STATUS = stringPreferencesKey("source_status")
        val USER_REPORTED_MISSING = booleanPreferencesKey("user_reported_missing")
        val SESSIONS_BLOB = stringPreferencesKey("sessions_blob")
    }
}
