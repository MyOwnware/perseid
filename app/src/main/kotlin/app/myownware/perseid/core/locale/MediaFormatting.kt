package app.myownware.perseid.core.locale

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import app.myownware.perseid.R
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun formatPercent(
    fraction: Float,
    locale: Locale,
): String = NumberFormat.getPercentInstance(locale).format(fraction.toDouble())

internal fun formatVideoResolution(
    context: Context,
    pixels: Int,
): String {
    val digits = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    digits.isGroupingUsed = false
    return context.getString(R.string.media_resolution, digits.format(pixels))
}

private const val FILE_SIZE_SCALE = 1000
private val FileSizeUnits =
    listOf(
        MeasureUnit.BYTE,
        MeasureUnit.KILOBYTE,
        MeasureUnit.MEGABYTE,
        MeasureUnit.GIGABYTE,
        MeasureUnit.TERABYTE,
    )

internal fun formatFileSize(
    context: Context,
    bytes: Long,
): String {
    var size = bytes.toDouble()
    var unit = 0
    while (size >= FILE_SIZE_SCALE && unit < FileSizeUnits.lastIndex) {
        size /= FILE_SIZE_SCALE
        unit++
    }
    val locale = context.resources.configuration.locales[0]
    val numbers =
        android.icu.text.NumberFormat
            .getNumberInstance(locale)
    numbers.maximumFractionDigits = 1
    val formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, numbers)
    return formatter.formatMeasures(Measure(size, FileSizeUnits[unit]))
}

internal fun formatTransferDetails(
    context: Context,
    metrics: TransferMetrics,
): String {
    val speed = context.getString(R.string.download_speed, formatFileSize(context, metrics.bytesPerSecond))
    val duration = formatDuration(context.resources.configuration.locales[0], metrics.secondsRemaining)
    val remaining = context.getString(R.string.download_eta, duration)
    return context.getString(R.string.download_progress_details, speed, remaining)
}

private fun formatDuration(
    locale: Locale,
    seconds: Long,
): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds - TimeUnit.HOURS.toSeconds(hours))
    val secondsInMinute = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
    val formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
    val second = Measure(secondsInMinute, MeasureUnit.SECOND)
    val hour = Measure(hours, MeasureUnit.HOUR)
    val minute = Measure(minutes, MeasureUnit.MINUTE)
    return when {
        hours > 0 -> formatter.formatMeasures(hour, minute, second)
        minutes > 0 -> formatter.formatMeasures(minute, second)
        else -> formatter.formatMeasures(second)
    }
}
