package com.swimweek.app.sync

enum class SyncReason {
    APP_FOREGROUND,
    MANUAL,
    PERIODIC,
    CHANGES,
    MIDNIGHT,
    PERMISSION_GRANTED,
    WEEK_IDENTITY_CHANGED,
    TOKEN_EXPIRED,
    WIDGET_PIN,
}
