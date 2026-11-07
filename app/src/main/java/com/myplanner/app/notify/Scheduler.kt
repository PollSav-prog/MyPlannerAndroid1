package com.myplanner.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.myplanner.app.App
import com.myplanner.app.logic.Reminders
import java.time.LocalDateTime
import java.time.ZoneId

/** Ставит один будильник на ближайшее событие (предупреждение или сигнал). */
object Scheduler {
    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val at = Reminders.nextWake(now)
        val p = pi(ctx)
        if (at == null) { am.cancel(p); return }
        var ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val minMs = System.currentTimeMillis() + 1000
        if (ms < minMs) ms = minMs
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        }
    }
}
