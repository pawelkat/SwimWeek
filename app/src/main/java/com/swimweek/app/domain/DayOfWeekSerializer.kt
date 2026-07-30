package com.swimweek.app.domain

import java.time.DayOfWeek

object DayOfWeekSerializer {
    fun parse(name: String): DayOfWeek =
        runCatching { DayOfWeek.valueOf(name) }.getOrDefault(DayOfWeek.MONDAY)
}
