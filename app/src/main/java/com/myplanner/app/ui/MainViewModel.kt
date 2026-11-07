package com.myplanner.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myplanner.app.App
import com.myplanner.app.Bus
import com.myplanner.app.data.TaskItem
import com.myplanner.app.logic.Fmt
import com.myplanner.app.weather.Weather
import com.myplanner.app.weather.WeatherInfo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var tasks by mutableStateOf<List<TaskItem>>(emptyList()); private set
    var now by mutableStateOf(LocalDateTime.now()); private set
    var selectedDate by mutableStateOf(LocalDate.now())
    var month by mutableStateOf(YearMonth.now())
    var selectedId by mutableStateOf<Long?>(null)
    var cardId by mutableStateOf<Long?>(null)          // открыть карточку задачи (из уведомления)
    var weather by mutableStateOf<WeatherInfo?>(Weather.cached())
    var weatherOffline by mutableStateOf(false)
    var weatherNotFound by mutableStateOf(false)
    var quiet by mutableStateOf(false); private set

    init {
        reload()
        viewModelScope.launch { Bus.version.collect { reload() } }
        refreshWeather(false)
    }

    fun reload() {
        tasks = App.db.loadAll()
        now = LocalDateTime.now()
        quiet = App.prefs.isQuiet(now)
    }

    fun tick() {
        now = LocalDateTime.now()
        quiet = App.prefs.isQuiet(now)
    }

    fun selectDate(d: LocalDate) {
        selectedDate = d
        month = YearMonth.from(d)
        selectedId = null
    }

    val dayTasks: List<TaskItem>
        get() = tasks.filter { !it.isArchived && it.due.toLocalDate() == selectedDate }
            .sortedWith(compareBy({ it.isDone }, { it.due }))

    fun datesWithTasks(): Set<LocalDate> =
        tasks.filter { !it.isArchived && !it.isDone }.map { it.due.toLocalDate() }.toSet()

    /** Задачи, по которым сейчас показана «капелька»: предупреждение было, событие ещё не наступило. */
    fun dropletTasks(): List<TaskItem> =
        tasks.filter { !it.isArchived && !it.isDone && it.remindEnabled && it.warned && it.due.isAfter(now) }
            .sortedBy { it.due }

    fun find(id: Long?): TaskItem? = tasks.firstOrNull { it.id == id }

    fun refreshWeather(force: Boolean) {
        val city = App.prefs.city
        if (city.isBlank()) return
        val c = weather
        if (!force && c != null && c.city.isNotEmpty() && System.currentTimeMillis() - c.time < 30 * 60 * 1000L &&
            c.city.equals(city, ignoreCase = true)) return
        viewModelScope.launch {
            val lang = Fmt.locale().language
            val r = Weather.fetch(city, lang)
            when {
                r == null -> weatherOffline = true
                r.code == -2 -> { weatherNotFound = true; weatherOffline = false }
                else -> { weather = r; weatherOffline = false; weatherNotFound = false }
            }
        }
    }
}
