package com.swimweek.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimweek.app.domain.Pool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.customPoolDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "custom_pools",
)

const val CUSTOM_POOL_ID_PREFIX = "custom-"

/**
 * Pools the user added because they are not in the Zurich catalogue.
 * Name, kind, and an optional note. No opening hours.
 * On-device only; backup rules exclude the datastore directory.
 */
@Singleton
class CustomPoolStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.customPoolDataStore

    val poolsFlow: Flow<List<Pool>> = dataStore.data.map { prefs ->
        decode(prefs[Keys.POOLS_BLOB] ?: return@map emptyList())
    }

    suspend fun add(name: String, kind: String, note: String?): Pool {
        val pool = Pool(
            id = CUSTOM_POOL_ID_PREFIX + UUID.randomUUID(),
            name = name.trim(),
            kind = kind,
            category = CUSTOM_POOL_CATEGORY,
            description = note?.trim()?.takeIf { it.isNotEmpty() },
            selectable = true,
        )
        require(pool.name.isNotBlank()) { "name must not be blank" }
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.POOLS_BLOB] ?: "[]").toMutableList()
            current += pool
            prefs[Keys.POOLS_BLOB] = encode(current)
        }
        return pool
    }

    private fun encode(pools: List<Pool>): String {
        val root = JSONArray()
        pools.forEach { pool ->
            root.put(
                JSONObject()
                    .put("id", pool.id)
                    .put("name", pool.name)
                    .put("kind", pool.kind)
                    .put("note", pool.description.orEmpty()),
            )
        }
        return root.toString()
    }

    private fun decode(blob: String): List<Pool> {
        return try {
            val root = JSONArray(blob)
            buildList {
                for (i in 0 until root.length()) {
                    val obj = root.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name").trim()
                    if (!id.startsWith(CUSTOM_POOL_ID_PREFIX) || name.isEmpty()) continue
                    add(
                        Pool(
                            id = id,
                            name = name,
                            kind = obj.optString("kind").ifBlank { "indoor" },
                            category = CUSTOM_POOL_CATEGORY,
                            description = obj.optString("note").trim().takeIf { it.isNotEmpty() },
                            selectable = true,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private object Keys {
        val POOLS_BLOB = stringPreferencesKey("pools_blob")
    }

    companion object {
        const val CUSTOM_POOL_CATEGORY = "custom"
    }
}
