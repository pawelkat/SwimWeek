package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritePoolsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_pools",
)

/**
 * On-device favorite Zurich pool ids (shown first in recommendations).
 */
@Singleton
class FavoritePoolsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.favoritePoolsDataStore

    val favoriteIdsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_IDS] ?: emptySet()
    }

    suspend fun getFavoriteIds(): Set<String> = favoriteIdsFlow.first()

    suspend fun setFavorite(poolId: String, favorite: Boolean) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.FAVORITE_IDS] ?: emptySet()).toMutableSet()
            if (favorite) {
                current.add(poolId)
            } else {
                current.remove(poolId)
            }
            prefs[Keys.FAVORITE_IDS] = current
        }
    }

    suspend fun toggleFavorite(poolId: String): Boolean {
        val current = getFavoriteIds()
        val nowFavorite = poolId !in current
        setFavorite(poolId, nowFavorite)
        return nowFavorite
    }

    private object Keys {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_pool_ids")
    }
}
