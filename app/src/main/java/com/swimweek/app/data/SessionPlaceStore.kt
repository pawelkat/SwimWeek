package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimweek.app.domain.SessionPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.sessionPlaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session_places",
)

/**
 * Maps Health Connect session ids to a locally chosen Zurich pool.
 * Stored on-device only (no cloud); excluded from backup via app policy.
 */
@Singleton
class SessionPlaceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.sessionPlaceDataStore

    val placesFlow: Flow<Map<String, SessionPlace>> = dataStore.data.map { prefs ->
        val blob = prefs[Keys.PLACES_BLOB] ?: return@map emptyMap()
        decode(blob)
    }

    suspend fun getMap(): Map<String, SessionPlace> = placesFlow.first()

    suspend fun get(sessionId: String): SessionPlace? = getMap()[sessionId]

    suspend fun setPlace(sessionId: String, poolId: String, poolName: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.PLACES_BLOB] ?: "{}").toMutableMap()
            current[sessionId] = SessionPlace(
                sessionId = sessionId,
                poolId = poolId,
                poolName = poolName,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            prefs[Keys.PLACES_BLOB] = encode(current)
        }
    }

    suspend fun clearPlace(sessionId: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.PLACES_BLOB] ?: "{}").toMutableMap()
            if (current.remove(sessionId) != null) {
                prefs[Keys.PLACES_BLOB] = encode(current)
            }
        }
    }

    private fun encode(map: Map<String, SessionPlace>): String {
        val root = JSONObject()
        map.forEach { (id, place) ->
            root.put(
                id,
                JSONObject()
                    .put("poolId", place.poolId)
                    .put("poolName", place.poolName)
                    .put("updatedAtEpochMs", place.updatedAtEpochMs),
            )
        }
        return root.toString()
    }

    private fun decode(blob: String): Map<String, SessionPlace> {
        return try {
            val root = JSONObject(blob)
            val out = mutableMapOf<String, SessionPlace>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val sessionId = keys.next()
                val obj = root.getJSONObject(sessionId)
                out[sessionId] = SessionPlace(
                    sessionId = sessionId,
                    poolId = obj.getString("poolId"),
                    poolName = obj.getString("poolName"),
                    updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private object Keys {
        val PLACES_BLOB = stringPreferencesKey("places_blob")
    }
}
