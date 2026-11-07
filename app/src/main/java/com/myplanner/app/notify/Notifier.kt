package com.myplanner.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.myplanner.app.App
import com.myplanner.app.R
import com.myplanner.app.data.TaskItem
import com.myplanner.app.logic.Fmt
import com.myplanner.app.ui.MainActivity

object Notifier {
    private const val CH_WARN = "warn"
    private const val CH_SILENT = "due_silent"
    private const val MISSED_ID = 999_999

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sounds = listOf(
            Triple("soft", R.raw.soft, R.string.sound_soft),
            Triple("bell", R.raw.bell, R.string.sound_bell),
            Triple("marimba", R.raw.marimba, R.string.sound_marimba)
        )
        for ((id, raw, label) in sounds) {
            val ch = NotificationChannel(
                "due_$id",
                ctx.getString(R.string.notif_channel_due) + " · " + ctx.getString(label),
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.setSound(Uri.parse("android.resource://" + ctx.packageName + "/" + raw), attrs)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val silent = NotificationChannel(CH_SILENT, ctx.getString(R.string.notif_channel_silent), NotificationManager.IMPORTANCE_HIGH)
        silent.setSound(null, null)
        silent.enableVibration(false)
        nm.createNotificationChannel(silent)

        val warn = NotificationChannel(CH_WARN, ctx.getString(R.string.notif_channel_warn), NotificationManager.IMPORTANCE_DEFAULT)
        warn.setSound(null, null)
        nm.createNotificationChannel(warn)
    }

    private fun dueChannel(): String =
        if (App.prefs.isQuiet() || !App.prefs.soundEnabled) CH_SILENT else "due_" + App.prefs.soundId

    private fun openIntent(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_TASK, id)
        }
        return PendingIntent.getActivity(ctx, (id % 100000).toInt() * 10, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(ctx: Context, t: TaskItem, act: String, minutes: Int, code: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            action = act + t.id + "_" + minutes
            putExtra("act", act)
            putExtra("id", t.id)
            putExtra("min", minutes)
        }
        return PendingIntent.getBroadcast(ctx, (t.id % 100000).toInt() * 10 + code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun due(ctx: Context, tasks: List<TaskItem>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (t in tasks) {
            val n = NotificationCompat.Builder(ctx, dueChannel())
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ctx.getString(R.string.toast_due_title))
                .setContentText(t.title + " · " + Fmt.time(t.due))
                .setStyle(NotificationCompat.BigTextStyle().bigText(t.title + "\n" + Fmt.dateTime(t.due) + if (t.description.isNotBlank()) "\n" + t.description else ""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setOnlyAlertOnce(false)
                .setAutoCancel(false)
                .setContentIntent(openIntent(ctx, t.id))
                .addAction(0, ctx.getString(R.string.btn_done), action(ctx, t, ActionReceiver.DONE, 0, 1))
                .addAction(0, ctx.getString(R.string.btn_snooze_10), action(ctx, t, ActionReceiver.SNOOZE, 10, 2))
                .addAction(0, ctx.getString(R.string.btn_snooze_30), action(ctx, t, ActionReceiver.SNOOZE, 30, 3))
                .build()
            nm.notify(t.id.toInt(), n)
        }
    }

    fun warn(ctx: Context, tasks: List<TaskItem>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val body = App.prefs.warnText.ifBlank { ctx.getString(R.string.droplet_body) }
        for (t in tasks) {
            val n = NotificationCompat.Builder(ctx, CH_WARN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ctx.getString(R.string.droplet_title))
                .setContentText(ctx.getString(R.string.droplet_name, t.title) + " · " + ctx.getString(R.string.droplet_time, Fmt.time(t.due)))
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    ctx.getString(R.string.droplet_name, t.title) + "\n" + ctx.getString(R.string.droplet_time, Fmt.dateTime(t.due)) + "\n" + body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openIntent(ctx, t.id))
                .build()
            nm.notify(-(t.id.toInt() + 1), n)
        }
    }

    fun missed(ctx: Context, tasks: List<TaskItem>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val text = tasks.joinToString("\n") { Fmt.time(it.due) + " " + it.title }
        val n = NotificationCompat.Builder(ctx, CH_SILENT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.toast_missed_title))
            .setContentText(ctx.getString(R.string.toast_missed, tasks.size.toString()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.toast_missed, tasks.size.toString()) + "\n" + text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, tasks.first().id))
            .build()
        nm.notify(MISSED_ID, n)
    }

    fun cancel(ctx: Context, id: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(id.toInt())
        nm.cancel(-(id.toInt() + 1))
    }

    /** Проверка сигнала из настроек. */
    fun test(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(ctx, dueChannel())
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.toast_due_title))
            .setContentText(ctx.getString(R.string.btn_test_sound))
            .setAutoCancel(true)
            .build()
        nm.notify(MISSED_ID - 1, n)
    }
}
