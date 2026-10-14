package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimweek.app.domain.ManualSwim
import com.swimweek.app.domain.decodeManualKind
import com.swimweek.app.domain.decodeWatchMissed
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.manualSwimDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "manual_swims",
)

/**
 * Swims the user typed on this phone. On-device only; the app backup rules
 * exclude the datastore directory. Not written to the vault.
 */
@Singleton
class ManualSwimStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.manualSwimDataStore

    val swimsFlow: Flow<Map<String, ManualSwim>> = dataStore.data.map { prefs ->
        decode(prefs[Keys.SWIMS_BLOB] ?: return@map emptyMap())
    }

    suspend fun getAll(): List<ManualSwim> = swimsFlow.first().values.toList()

    suspend fun upsert(swim: ManualSwim) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.SWIMS_BLOB] ?: "{}").toMutableMap()
            current[swim.id] = swim
            prefs[Keys.SWIMS_BLOB] = encode(current)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.SWIMS_BLOB] ?: "{}").toMutableMap()
            if (current.remove(id) != null) {
                prefs[Keys.SWIMS_BLOB] = encode(current)
            }
        }
    }

    private fun encode(map: Map<String, ManualSwim>): String {
        val root = JSONObject()
        map.forEach { (id, swim) ->
            root.put(
                id,
                JSONObject()
                    .put("kind", swim.kind.name)
                    .put("name", swim.name)
                    .put("startEpochMs", swim.start.toEpochMilli())
                    .put("distanceMeters", swim.distanceMeters)
                    .put("poolId", swim.poolId.orEmpty())
                    .put("watchMissed", swim.watchMissed),
            )
        }
        return root.toString()
    }

    private fun decode(blob: String): Map<String, ManualSwim> {
        return try {
            val root = JSONObject(blob)
            val out = mutableMapOf<String, ManualSwim>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                val rawKind = obj.optString("kind")
                val kind = decodeManualKind(rawKind) ?: continue
                val name = obj.optString("name").trim()
                val meters = obj.optDouble("distanceMeters", 0.0)
                if (name.isEmpty() || meters <= 0.0) continue
                val swim = runCatching {
                    ManualSwim(
                        id = id,
                        kind = kind,
                        name = name,
                        start = Instant.ofEpochMilli(obj.getLong("startEpochMs")),
                        distanceMeters = meters,
                        poolId = obj.optString("poolId").takeIf { it.isNotBlank() },
                        watchMissed = decodeWatchMissed(rawKind, obj.optBoolean("watchMissed", false)),
                    )
                }.getOrNull() ?: continue
                out[id] = swim
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private object Keys {
        val SWIMS_BLOB = stringPreferencesKey("swims_blob")
    }
}
