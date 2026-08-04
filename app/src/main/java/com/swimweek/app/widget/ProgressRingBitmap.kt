package com.swimweek.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.swimweek.app.domain.TargetProgress

/**
 * Renders a progress ring for Glance widgets (limited drawing APIs).
 * Returns null when no target is set so the widget can show the bare icon.
 */
object ProgressRingBitmap {

    fun create(
        sizePx: Int,
        distanceMeters: Double,
        targetMeters: Double,
        trackColor: Int = 0xFF4A4A4A.toInt(),
        progressColor: Int = 0xFF4DB6AC.toInt(),
        strokePx: Float = sizePx * 0.08f,
    ): Bitmap? {
        if (!TargetProgress.hasTarget(targetMeters) || sizePx <= 0) return null
        val progress = TargetProgress.fraction(distanceMeters, targetMeters)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = strokePx / 2f
        val oval = RectF(pad, pad, sizePx - pad, sizePx - pad)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)

        if (progress > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = progressColor
            }
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
        }
        return bmp
    }
}
