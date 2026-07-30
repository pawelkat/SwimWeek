package com.swimweek.app.di

import android.content.Context
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.SummaryCacheStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for non-Hilt contexts (Glance widgets).
 * Do not @Inject into GlanceAppWidget constructors.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SwimWeekEntryPoint {
    fun summaryCacheStore(): SummaryCacheStore
    fun preferencesStore(): PreferencesStore
}

fun Context.swimWeekEntryPoint(): SwimWeekEntryPoint =
    EntryPointAccessors.fromApplication(
        applicationContext,
        SwimWeekEntryPoint::class.java,
    )
