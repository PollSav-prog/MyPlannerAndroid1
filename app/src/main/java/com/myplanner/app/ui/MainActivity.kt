package com.myplanner.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.myplanner.app.App
import com.myplanner.app.logic.Reminders
import com.myplanner.app.notify.Scheduler

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            PlannerTheme {
                MainScreen(vm)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val id = i?.getLongExtra(EXTRA_TASK, -1L) ?: -1L
        if (id > 0) vm.cardId = id
    }

    override fun onResume() {
        super.onResume()
        // при открытии: пропущенное сообщаем одним уведомлением, ставим будильник, делаем копию раз в сутки
        Reminders.tick(this, catchUp = true)
        Scheduler.schedule(this)
        App.db.autoBackup()
        vm.reload()
        vm.refreshWeather(false)
    }

    companion object {
        const val EXTRA_TASK = "taskId"
    }
}
