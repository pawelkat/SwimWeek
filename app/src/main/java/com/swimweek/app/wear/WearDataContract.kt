package com.swimweek.app.wear

/** Data Layer contract shared with `:wear` (keep in sync with wear/.../WearDataContract.kt). */
object WearDataContract {
    const val PATH_WEEKLY = "/swimweek/weekly"
    const val KEY_DISTANCE_M = "distance_m"
    const val KEY_TARGET_M = "target_m"
    const val KEY_UNIT = "unit"
    const val KEY_SESSIONS = "sessions"
    const val KEY_UPDATED_AT = "updated_at"
}
