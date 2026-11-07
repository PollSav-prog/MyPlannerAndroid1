package com.myplanner.app.logic

import com.myplanner.app.data.RepeatKind
import com.myplanner.app.data.TaskItem
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Правила повторения задач. */
object Recurrence {
    /** Пн = бит 0. */
    fun dayBit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    /** Следующее вхождение строго после due и позже now. */
    fun next(t: TaskItem, now: LocalDateTime): LocalDateTime? {
        if (t.repeat == RepeatKind.None) return null
        var next = step(t, t.due) ?: return null
        var guard = 0
        while (next <= now && guard++ < 5000) {
            next = step(t, next) ?: return null
        }
        return next
    }

    private fun step(t: TaskItem, from: LocalDateTime): LocalDateTime? = when (t.repeat) {
        RepeatKind.Daily -> from.plusDays(1)
        RepeatKind.Weekly -> from.plusDays(7)
        RepeatKind.Monthly -> from.plusMonths(1)
        RepeatKind.Yearly -> from.plusYears(1)
        RepeatKind.EveryNDays -> from.plusDays(maxOf(1, t.repeatInterval).toLong())
        RepeatKind.Weekdays -> {
            if (t.repeatDays == 0) from.plusDays(1)
            else {
                var res: LocalDateTime? = null
                for (i in 1..7) {
                    val d = from.plusDays(i.toLong())
                    if ((t.repeatDays and dayBit(d.dayOfWeek)) != 0) { res = d; break }
                }
                res
            }
        }
        else -> null
    }
}
