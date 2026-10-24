package com.lilyai.gamespace

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Playtime comes from Android's usage statistics, so it covers games opened any way, not only
// from LilyAi. It needs the "Usage access" permission, which the user grants in Android settings.
// Android groups these statistics into time buckets, so the numbers are approximate.
class PlaytimeTracker(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Minutes in the foreground since midnight, by package name. */
    fun minutesToday(): Map<String, Long> = minutesSince(startOfToday())

    /** Minutes in the foreground over the last 7 days, by package name. */
    fun minutesLast7Days(): Map<String, Long> =
        minutesSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))

    private fun minutesSince(start: Long): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        return manager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
            .mapValues { TimeUnit.MILLISECONDS.toMinutes(it.value.totalTimeInForeground) }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
