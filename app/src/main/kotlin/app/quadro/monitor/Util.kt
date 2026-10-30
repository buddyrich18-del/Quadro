package app.quadro.monitor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun fmtNum(v: Double, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f", v)

fun fmtTime(ms: Long, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun appVersion(ctx: Context): String =
    try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
