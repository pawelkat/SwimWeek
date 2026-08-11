package com.swimweek.app.data

import android.content.Context
import com.swimweek.app.domain.OpeningHourSlot
import com.swimweek.app.domain.Pool
import com.swimweek.app.domain.ScheduleTable
import com.swimweek.app.domain.ZurichPoolsCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads bundled Zurich Hallenbäder + Sommerbäder catalogue and timetables
 * from assets (no network — app has no INTERNET permission).
 */
@Singleton
class ZurichPoolsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: ZurichPoolsCatalog? = null

    fun getCatalog(): ZurichPoolsCatalog {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadFromAssets().also { cached = it }
        }
    }

    fun selectablePools(): List<Pool> = getCatalog().selectablePools()

    fun findPool(id: String): Pool? = getCatalog().findById(id)

    private fun loadFromAssets(): ZurichPoolsCatalog {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val source = root.optJSONObject("source")
        val poolsArr = root.getJSONArray("pools")
        val pools = buildList {
            for (i in 0 until poolsArr.length()) {
                add(parsePool(poolsArr.getJSONObject(i)))
            }
        }
        return ZurichPoolsCatalog(
            schemaVersion = root.optInt("schemaVersion", 1),
            retrievedAt = source?.optString("retrieved_at")?.takeIf { it.isNotBlank() },
            seasonYear = source?.optInt("seasonYear")?.takeIf { it > 0 },
            sourceHallen = source?.optString("hallenbaeder")?.takeIf { it.isNotBlank() },
            sourceSommer = source?.optString("sommerbaeder")?.takeIf { it.isNotBlank() },
            pools = pools,
        )
    }

    private fun parsePool(obj: JSONObject): Pool {
        val hoursArr = obj.optJSONArray("openingHours") ?: JSONArray()
        val hours = buildList {
            for (i in 0 until hoursArr.length()) {
                add(parseHour(hoursArr.getJSONObject(i)))
            }
        }
        val schedulesArr = obj.optJSONArray("schedules") ?: JSONArray()
        val schedules = buildList {
            for (i in 0 until schedulesArr.length()) {
                add(parseSchedule(schedulesArr.getJSONObject(i)))
            }
        }
        return Pool(
            id = obj.getString("id"),
            name = obj.getString("name"),
            kind = obj.optString("kind", "unknown"),
            category = obj.optString("category", "unknown"),
            url = obj.optString("url").takeIf { it.isNotBlank() },
            description = obj.optString("description").takeIf { it.isNotBlank() },
            selectable = obj.optBoolean("selectable", true),
            externalUrl = obj.optString("externalUrl").takeIf { it.isNotBlank() },
            hoursNote = obj.optString("hoursNote").takeIf { it.isNotBlank() },
            openingHours = hours,
            schedules = schedules,
        )
    }

    private fun parseHour(obj: JSONObject): OpeningHourSlot {
        val daysArr = obj.optJSONArray("days")
        val days: List<String>? = if (daysArr == null || daysArr.length() == 0) {
            null
        } else {
            buildList {
                for (i in 0 until daysArr.length()) {
                    add(daysArr.getString(i))
                }
            }
        }
        return OpeningHourSlot(
            period = obj.optString("period").takeIf { it.isNotBlank() && it != "null" },
            days = days,
            open = obj.optString("open").takeIf { it.isNotBlank() && it != "null" },
            close = obj.optString("close").takeIf { it.isNotBlank() && it != "null" },
            condition = obj.optString("condition").takeIf { it.isNotBlank() && it != "null" },
            audience = obj.optString("audience").takeIf { it.isNotBlank() && it != "null" },
            label = obj.optString("label").takeIf { it.isNotBlank() && it != "null" },
            timeText = obj.optString("timeText").takeIf { it.isNotBlank() && it != "null" },
            note = obj.optString("note").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun parseSchedule(obj: JSONObject): ScheduleTable {
        val colsArr = obj.optJSONArray("columns") ?: JSONArray()
        val columns = buildList {
            for (i in 0 until colsArr.length()) {
                add(colsArr.optString(i, ""))
            }
        }
        val entriesArr = obj.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until entriesArr.length()) {
                val e = entriesArr.getJSONObject(i)
                val map = mutableMapOf<String, String>()
                val keys = e.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = e.optString(k, "")
                }
                add(map)
            }
        }
        return ScheduleTable(columns = columns, entries = entries)
    }

    companion object {
        const val ASSET_NAME = "zurich_pools.json"
    }
}
