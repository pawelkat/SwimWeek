package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimweek.app.domain.DayOfWeekSerializer
import com.swimweek.app.domain.DistanceUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)

data class UserPreferences(
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val distanceUnit: DistanceUnit = DistanceUnit.defaultForLocale(),
    val onboardingCompleted: Boolean = false,
    val schemaVersion: Int = 1,
)

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.userPrefsDataStore

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            weekStart = DayOfWeekSerializer.parse(
                prefs[Keys.WEEK_START] ?: DayOfWeek.MONDAY.name,
            ),
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.let {
                runCatching { DistanceUnit.valueOf(it) }.getOrNull()
            } ?: DistanceUnit.defaultForLocale(Locale.getDefault()),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            schemaVersion = prefs[Keys.SCHEMA_VERSION] ?: 1,
        )
    }

    suspend fun get(): UserPreferences = preferencesFlow.first()

    suspend fun setWeekStart(day: DayOfWeek) {
        dataStore.edit { it[Keys.WEEK_START] = day.name }
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { it[Keys.DISTANCE_UNIT] = unit.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    private object Keys {
        val WEEK_START = stringPreferencesKey("week_start")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
    }
}
