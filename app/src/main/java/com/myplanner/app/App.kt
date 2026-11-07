package com.myplanner.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.myplanner.app.data.Db
import com.myplanner.app.data.Prefs
import com.myplanner.app.notify.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        db = Db(this)
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.nightMode())
        Notifier.createChannels(this)
    }

    companion object {
        lateinit var db: Db
        lateinit var prefs: Prefs
    }
}

/** Сообщает экрану, что данные изменились (например, пришло напоминание). */
object Bus {
    val version = MutableStateFlow(0)
    fun bump() = version.update { it + 1 }
}
