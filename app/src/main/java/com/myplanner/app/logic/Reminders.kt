package com.myplanner.app.logic

import android.content.Context
import com.myplanner.app.App
import com.myplanner.app.Bus
import com.myplanner.app.data.TaskItem
import com.myplanner.app.notify.Notifier
import java.time.LocalDateTime

/**
 * Логика напоминаний. Всё состояние (когда сигнал звучал последний раз, до какого времени отложено,
 * было ли предупреждение) хранится в базе, поэтому после перезагрузки телефона оно восстанавливается.
 */
object Reminders {
    private const val GAP_MIN = 3L

    fun nextSignal(t: TaskItem): LocalDateTime =
        t.snoozeUntil ?: t.lastSignal?.plusMinutes(App.prefs.repeatMinutes.toLong()) ?: t.due

    private fun active(t: TaskItem) = !t.isDone && !t.isArchived && t.remindEnabled

    /** Когда нужно проснуться в следующий раз. */
    fun nextWake(now: LocalDateTime): LocalDateTime? {
        var best: LocalDateTime? = null
        for (t in App.db.loadAll()) {
            if (!active(t)) continue
            val w = App.prefs.effectiveWarn(t)
            if (w > 0 && !t.warned) {
                val wt = t.due.minusMinutes(w.toLong())
                if (best == null || wt < best) best = wt
            }
            val s = nextSignal(t)
            if (best == null || s < best) best = s
        }
        return best
    }

    /** catchUp = true при запуске телефона/приложения: пропущенное сообщается одним уведомлением. */
    fun tick(ctx: Context, catchUp: Boolean) {
        val now = LocalDateTime.now()
        val warn = ArrayList<TaskItem>()
        val due = ArrayList<TaskItem>()
        val missed = ArrayList<TaskItem>()

        for (t in App.db.loadAll()) {
            if (!active(t)) continue

            val w = App.prefs.effectiveWarn(t)
            if (w > 0 && !t.warned && !now.isBefore(t.due.minusMinutes(w.toLong()))) {
                t.warned = true
                App.db.update(t)
                if (now.isBefore(t.due)) warn.add(t)
            }

            val next = nextSignal(t)
            if (next.isAfter(now)) continue

            val wasAway = catchUp && next.plusMinutes(GAP_MIN).isBefore(now)
            t.lastSignal = now
            t.snoozeUntil = null
            t.signaled = true
            t.warned = true
            App.db.update(t)
            if (wasAway) missed.add(t) else due.add(t)
        }

        if (warn.isNotEmpty()) Notifier.warn(ctx, warn)
        if (missed.isNotEmpty()) Notifier.missed(ctx, missed)
        if (due.isNotEmpty()) Notifier.due(ctx, due)
        if (warn.isNotEmpty() || missed.isNotEmpty() || due.isNotEmpty()) Bus.bump()
    }
}
