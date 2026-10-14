package com.swimweek.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the Google Wallet app (Sportabo lives there).
 * Does not read or copy any pass — launch-only shortcut.
 */
object GoogleWalletLauncher {

    val WALLET_PACKAGES = listOf(
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.wallet",
    )

    const val PLAY_STORE_PACKAGE = "com.google.android.apps.walletnfcrel"

    /**
     * @return true if Wallet (or Play Store fallback) was started.
     */
    fun open(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in WALLET_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return startSafely(context, launch)
        }
        // Wallet not installed — offer Play Store / web store
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PLAY_STORE_PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startSafely(context, market)) return true
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startSafely(context, web)
    }

    fun isWalletInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return WALLET_PACKAGES.any { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null
        }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
