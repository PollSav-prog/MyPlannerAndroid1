package com.myplanner.app.data

import java.time.LocalDateTime

enum class RepeatKind { None, Daily, Weekdays, Weekly, Monthly, Yearly, EveryNDays }

data class TaskItem(
    var id: Long = 0,
    var title: String = "",
    var description: String = "",
    var due: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    var priority: Int = 1,              // 0 низкий, 1 обычный, 2 высокий
    var category: String = "",
    var remindEnabled: Boolean = true,
    var warnMinutes: Int = -1,          // -1 как в настройках, 0 не предупреждать
    var repeat: RepeatKind = RepeatKind.None,
    var repeatInterval: Int = 1,
    var repeatDays: Int = 0,            // маска дней: Пн = бит 0
    var isDone: Boolean = false,
    var completedAt: LocalDateTime? = null,
    var isArchived: Boolean = false,    // «удалена» (лежит в истории)
    var lastSignal: LocalDateTime? = null,
    var snoozeUntil: LocalDateTime? = null,
    var warned: Boolean = false,
    var signaled: Boolean = false
)
