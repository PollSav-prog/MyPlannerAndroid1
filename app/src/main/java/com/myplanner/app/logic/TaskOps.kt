package com.myplanner.app.logic

import android.content.Context
import com.myplanner.app.App
import com.myplanner.app.Bus
import com.myplanner.app.data.RepeatKind
import com.myplanner.app.data.TaskItem
import com.myplanner.app.notify.Scheduler
import java.time.LocalDateTime

/** Действия над задачами. После каждого действия пересчитывается ближайший будильник. */
object TaskOps {
    private fun done(ctx: Context) {
        Scheduler.schedule(ctx)
        Bus.bump()
    }

    private fun resetSignals(t: TaskItem) {
        t.lastSignal = null; t.snoozeUntil = null; t.warned = false; t.signaled = false
    }

    fun save(ctx: Context, t: TaskItem, dueChanged: Boolean = true) {
        if (dueChanged) resetSignals(t)
        if (t.id == 0L) App.db.insert(t) else App.db.update(t)
        done(ctx)
    }

    fun complete(ctx: Context, t: TaskItem) {
        val now = LocalDateTime.now()
        val next = Recurrence.next(t, now)
        if (t.repeat != RepeatKind.None && next != null) {
            // в историю — копия выполненного, а сама задача переезжает на следующую дату
            App.db.insert(t.copy(id = 0, isDone = true, completedAt = now, repeat = RepeatKind.None))
            t.due = next
            resetSignals(t)
            App.db.update(t)
        } else {
            t.isDone = true
            t.completedAt = now
            App.db.update(t)
        }
        done(ctx)
    }

    fun snooze(ctx: Context, t: TaskItem, minutes: Int) {
        t.snoozeUntil = LocalDateTime.now().plusMinutes(minutes.toLong())
        App.db.update(t)
        done(ctx)
    }

    fun archive(ctx: Context, t: TaskItem) { t.isArchived = true; App.db.update(t); done(ctx) }

    fun restore(ctx: Context, t: TaskItem) {
        t.isArchived = false
        t.isDone = false
        t.completedAt = null
        resetSignals(t)
        App.db.update(t)
        done(ctx)
    }

    fun deleteForever(ctx: Context, t: TaskItem) { App.db.delete(t.id); done(ctx) }

    fun moveToDate(ctx: Context, t: TaskItem, days: Long) {
        val d = LocalDateTime.now().toLocalDate().plusDays(days)
        t.due = d.atTime(t.due.toLocalTime())
        resetSignals(t)
        App.db.update(t)
        done(ctx)
    }
}
