package com.swimweek.app.data

import android.util.Log
import com.swimweek.app.BuildConfig
import com.swimweek.app.domain.PoolNotice
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches a Stadt Zürich pool HTML page and extracts `stzh-disturber` notices.
 * Failures never throw to UI layers — returns Result.failure.
 */
@Singleton
class PoolNoticeClient @Inject constructor() {

    suspend fun fetchNotices(pageUrl: String): Result<List<PoolNotice>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val html = downloadHtml(pageUrl)
                StzhDisturberParser.parse(html)
            }.onFailure { e ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "notice fetch failed for $pageUrl: ${e.message}")
                }
            }
        }

    private fun downloadHtml(pageUrl: String): String {
        val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: error("HTTP $code")
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "PoolNoticeClient"
        private const val USER_AGENT = "SwimWeek/1.0 (pool-notice; Android)"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
