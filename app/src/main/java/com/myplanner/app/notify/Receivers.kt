package com.myplanner.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myplanner.app.App
import com.myplanner.app.logic.Reminders
import com.myplanner.app.logic.TaskOps

/** Сработал будильник: показываем напоминания и ставим следующий. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.tick(ctx, catchUp = false)
        Scheduler.schedule(ctx)
    }
}

/** Перезагрузка телефона, смена времени/пояса, обновление приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.createChannels(ctx)
        Reminders.tick(ctx, catchUp = true)
        Scheduler.schedule(ctx)
    }
}

/** Кнопки в уведомлении: «Выполнено», «Отложить». */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val t = App.db.get(id) ?: return
        when (intent.getStringExtra("act")) {
            DONE -> TaskOps.complete(ctx, t)
            SNOOZE -> TaskOps.snooze(ctx, t, intent.getIntExtra("min", 10))
        }
        Notifier.cancel(ctx, id)
    }

    companion object {
        const val DONE = "done"
        const val SNOOZE = "snooze"
    }
}
