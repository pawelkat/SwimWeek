package com.swimweek.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.swimweek.wear.R
import com.swimweek.wear.data.WeeklySwim
import com.swimweek.wear.presentation.MainActivity

/** Weekly swim distance complication, fed by the phone over the Wearable Data Layer. */
class WeeklySwimComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WeeklySwim(2_400.0, 4_000.0, "METERS", 2, 0L), tapAction = null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val data = WeeklySwim.load(this) ?: WeeklySwim(0.0, 0.0, "METERS", 0, 0L)
        val tap = PendingIntent.getActivity(
            this,
            request.complicationInstanceId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return build(request.complicationType, data, tap)
    }

    private fun build(type: ComplicationType, d: WeeklySwim, tapAction: PendingIntent?): ComplicationData? {
        val icon = Icon.createWithResource(this, R.drawable.ic_swim)
        val mono = MonochromaticImage.Builder(icon).build()
        val desc = PlainComplicationText.Builder(
            "${getString(R.string.complication_desc)}: ${ComplicationFormat.fullValue(d.distanceMeters, d.unit)}",
        ).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(ComplicationFormat.shortValue(d.distanceMeters, d.unit)).build(),
                desc,
            ).setTitle(PlainComplicationText.Builder(ComplicationFormat.unitSuffix(d.unit)).build())
                .setMonochromaticImage(mono)
                .setTapAction(tapAction).build()
            ComplicationType.RANGED_VALUE -> {
                val (value, max) = ComplicationFormat.range(d.distanceMeters, d.targetMeters, d.unit)
                RangedValueComplicationData.Builder(value, 0f, max, desc)
                    .setText(PlainComplicationText.Builder(ComplicationFormat.shortValue(d.distanceMeters, d.unit)).build())
                    .setMonochromaticImage(mono)
                    .setTapAction(tapAction).build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(mono, desc)
                .setTapAction(tapAction).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(icon, SmallImageType.ICON).build(),
                desc,
            ).setTapAction(tapAction).build()
            else -> null
        }
    }
}
