package com.swimweek.app.alert

import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.EveningPoolSelector
import com.swimweek.app.util.ClockProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class PlannedPoolReminder(
    val poolId: String,
    val poolName: String,
    val close: LocalTime,
    val notifyAt: ZonedDateTime,
    val leadMinutes: Long,
    val condition: String?,
)

/**
 * Builds the next evening-pool reminder from bundled opening hours.
 */
@Singleton
class PoolReminderPlanner @Inject constructor(
    private val poolsRepository: ZurichPoolsRepository,
    private val clockProvider: ClockProvider,
) {
    fun planNext(
        leadMinutes: Long = EveningPoolSelector.DEFAULT_LEAD_MINUTES,
        eveningStart: LocalTime = EveningPoolSelector.DEFAULT_EVENING_START,
    ): PlannedPoolReminder? {
        val zone = clockProvider.zoneId()
        val now = ZonedDateTime.ofInstant(clockProvider.now(), zone)
        val catalog = poolsRepository.getCatalog()
        val selection = EveningPoolSelector.selectNext(
            pools = catalog.pools,
            fromDate = now.toLocalDate(),
            nowTime = now.toLocalTime(),
            seasonYear = catalog.seasonYear ?: now.year,
            eveningStart = eveningStart,
            leadMinutes = leadMinutes,
        ) ?: return null

        val notifyLocal = LocalDateTime.of(selection.date, selection.notifyAt)
        val notifyAt = notifyLocal.atZone(zone)
        if (!notifyAt.isAfter(now)) return null

        return PlannedPoolReminder(
            poolId = selection.pool.id,
            poolName = selection.pool.name,
            close = selection.close,
            notifyAt = notifyAt,
            leadMinutes = leadMinutes,
            condition = selection.candidate.condition,
        )
    }

    fun planForDate(
        date: LocalDate,
        leadMinutes: Long = EveningPoolSelector.DEFAULT_LEAD_MINUTES,
    ): PlannedPoolReminder? {
        val zone = clockProvider.zoneId()
        val catalog = poolsRepository.getCatalog()
        val selection = EveningPoolSelector.selectForDate(
            pools = catalog.pools,
            date = date,
            seasonYear = catalog.seasonYear ?: date.year,
            leadMinutes = leadMinutes,
        ) ?: return null
        return PlannedPoolReminder(
            poolId = selection.pool.id,
            poolName = selection.pool.name,
            close = selection.close,
            notifyAt = LocalDateTime.of(date, selection.notifyAt).atZone(zone),
            leadMinutes = leadMinutes,
            condition = selection.candidate.condition,
        )
    }
}
