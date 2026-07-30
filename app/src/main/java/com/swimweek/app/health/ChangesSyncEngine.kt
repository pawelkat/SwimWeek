package com.swimweek.app.health

import android.util.Log
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import com.swimweek.app.BuildConfig
import com.swimweek.app.data.ChangesTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect Changes token lifecycle:
 * drain all pages, always persist next token, re-aggregate on exercise/distance
 * upsert or delete, full resync path on expiry.
 */
@Singleton
class ChangesSyncEngine @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
    private val tokenStore: ChangesTokenStore,
) {
    data class DrainResult(
        val nextToken: String?,
        val needsReaggregate: Boolean,
        val tokenExpired: Boolean,
        val error: Exception? = null,
    )

    suspend fun issueToken(): String? {
        val client = clientProvider.getOrNull() ?: return null
        return try {
            val token = client.getChangesToken(
                ChangesTokenRequest(
                    recordTypes = setOf(
                        ExerciseSessionRecord::class,
                        DistanceRecord::class,
                    ),
                ),
            )
            tokenStore.save(token)
            token
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "issueToken failed: ${e.message}")
            }
            null
        }
    }

    /**
     * Drain changes for the stored token (or [tokenOverride]).
     * Always attempts to persist the latest nextChangesToken when available.
     */
    suspend fun drain(tokenOverride: String? = null): DrainResult {
        val client = clientProvider.getOrNull()
            ?: return DrainResult(null, needsReaggregate = true, tokenExpired = false)

        var token = tokenOverride ?: tokenStore.get()
        if (token.isNullOrBlank()) {
            return DrainResult(null, needsReaggregate = true, tokenExpired = false)
        }

        var needsReaggregate = false
        try {
            var hasMore = true
            while (hasMore) {
                val response = client.getChanges(token!!)
                for (change in response.changes) {
                    when (change) {
                        is DeletionChange -> {
                            // Any delete of tracked types forces re-aggregate
                            needsReaggregate = true
                        }
                        is UpsertionChange -> {
                            val record = change.record
                            if (record is ExerciseSessionRecord || record is DistanceRecord) {
                                needsReaggregate = true
                            }
                        }
                    }
                }
                token = response.nextChangesToken
                hasMore = response.hasMore
            }
            // ALWAYS persist next token, even with no relevant changes
            if (!token.isNullOrBlank()) {
                tokenStore.save(token!!)
            }
            return DrainResult(
                nextToken = token,
                needsReaggregate = needsReaggregate,
                tokenExpired = false,
            )
        } catch (e: Exception) {
            val expired = isTokenExpired(e)
            if (expired) {
                tokenStore.clear()
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "drain failed expired=$expired: ${e.message}")
            }
            return DrainResult(
                nextToken = null,
                needsReaggregate = true,
                tokenExpired = expired,
                error = e,
            )
        }
    }

    private fun isTokenExpired(e: Exception): Boolean {
        val name = e::class.java.simpleName
        val message = e.message.orEmpty()
        return name.contains("ChangesTokenExpired", ignoreCase = true) ||
            name.contains("TokenExpired", ignoreCase = true) ||
            message.contains("token", ignoreCase = true) &&
            message.contains("expir", ignoreCase = true)
    }

    companion object {
        private const val TAG = "ChangesSyncEngine"
    }
}
