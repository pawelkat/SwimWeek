package com.swimweek.app.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors [ChangesSyncEngine] expiry heuristics for unit coverage without HC client.
 */
class TokenExpiryDetectorTest {

    private fun isTokenExpired(e: Exception): Boolean {
        val name = e::class.java.simpleName
        val message = e.message.orEmpty()
        return name.contains("ChangesTokenExpired", ignoreCase = true) ||
            name.contains("TokenExpired", ignoreCase = true) ||
            (message.contains("token", ignoreCase = true) &&
                message.contains("expir", ignoreCase = true))
    }

    private class ChangesTokenExpiredException(msg: String) : Exception(msg)

    @Test
    fun detectsByClassName() {
        assertTrue(isTokenExpired(ChangesTokenExpiredException("gone")))
    }

    @Test
    fun detectsByMessage() {
        assertTrue(isTokenExpired(IllegalStateException("Changes token expired")))
    }

    @Test
    fun ignoresUnrelated() {
        assertFalse(isTokenExpired(IllegalStateException("network down")))
    }
}
