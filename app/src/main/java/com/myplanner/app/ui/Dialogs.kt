package com.myplanner.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import com.myplanner.app.App
import com.myplanner.app.Bus
import com.myplanner.app.R
import com.myplanner.app.data.RepeatKind
import com.myplanner.app.data.TaskItem
import com.myplanner.app.logic.Fmt
import com.myplanner.app.logic.TaskOps
import com.myplanner.app.notify.Notifier
import com.myplanner.app.notify.Scheduler
import com.myplanner.app.weather.Weather
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle

// ---------- общие вещи ----------

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissLabel: String = stringResource(R.string.btn_close),
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { if (confirmLabel != null) TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f)
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

fun describeRepeat(ctx: Context, kind: RepeatKind, interval: Int, days: Int): String = when (kind) {
    RepeatKind.None -> ""
    RepeatKind.Daily -> ctx.getString(R.string.rep_daily)
    RepeatKind.Weekly -> ctx.getString(R.string.rep_weekly)
    RepeatKind.Monthly -> ctx.getString(R.string.rep_monthly)
    RepeatKind.Yearly -> ctx.getString(R.string.rep_yearly)
    RepeatKind.EveryNDays -> ctx.getString(R.string.rep_every_n, maxOf(1, interval).toString())
    RepeatKind.Weekdays -> (0 until 7).filter { (days and (1 shl it)) != 0 }
        .joinToString(", ") { DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, Fmt.locale()) }
}

fun pickDate(ctx: Context, cur: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
}

fun pickTime(ctx: Context, cur: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(ctx, { _, h, mi -> onPick(LocalTime.of(h, mi)) }, cur.hour, cur.minute, DateFormat.is24HourFormat(ctx)).show()
}

fun timeLabel(t: LocalTime): String = Fmt.time(LocalDate.now().atTime(t))

// ---------- повторение ----------

class RepeatState(k: RepeatKind, interval: Int, d: Int) {
    var kind by mutableStateOf(k)
    var interval by mutableStateOf(interval.toString())
    var days by mutableStateOf(d)

    fun intervalValue(): Int = interval.toIntOrNull()?.coerceIn(1, 365) ?: 1

    fun error(ctx: Context): String? = when {
        kind == RepeatKind.Weekdays && days == 0 -> ctx.getString(R.string.err_pick_weekday)
        kind == RepeatKind.EveryNDays && (interval.toIntOrNull() ?: 0) !in 1..365 -> ctx.getString(R.string.err_interval)
        else -> null
    }
}

@Composable
fun RepeatEditor(s: RepeatState) {
    val options = listOf(
        RepeatKind.None to R.string.rep_none,
        RepeatKind.Daily to R.string.rep_daily,
        RepeatKind.Weekdays to R.string.rep_weekdays,
        RepeatKind.Weekly to R.string.rep_weekly,
        RepeatKind.Monthly to R.string.rep_monthly,
        RepeatKind.Yearly to R.string.rep_yearly,
        RepeatKind.EveryNDays to R.string.rep_everyn
    )
    for ((k, res) in options) RadioRow(stringResource(res), s.kind == k) { s.kind = k }
    if (s.kind == RepeatKind.Weekdays) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val on = (s.days and (1 shl i)) != 0
                FilterChip(
                    selected = on,
                    onClick = { s.days = s.days xor (1 shl i) },
                    label = { Text(DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Fmt.locale())) }
                )
            }
        }
    }
    if (s.kind == RepeatKind.EveryNDays) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rep_every_label))
            OutlinedTextField(
                value = s.interval, onValueChange = { s.interval = it.filter(Char::isDigit).take(3) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(90.dp)
                    .padding(horizontal = 8.dp)
            )
            Text(stringResource(R.string.rep_days_label))
        }
    }
}

@Composable
fun RepeatDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val t = vm.find(vm.selectedId)
    if (t == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val s = remember { RepeatState(t.repeat, t.repeatInterval, t.repeatDays) }
    var err by remember { mutableStateOf<String?>(null) }
    AppDialog(
        stringResource(R.string.rep_title), onDismiss,
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            val e = s.error(ctx)
            if (e != null) err = e else {
                t.repeat = s.kind; t.repeatInterval = s.intervalValue(); t.repeatDays = s.days
                TaskOps.save(ctx, t, dueChanged = false)
                onDismiss()
            }
        },
        dismissLabel = stringResource(R.string.btn_cancel)
    ) {
        Text(t.title, fontWeight = FontWeight.SemiBold)
        RepeatEditor(s)
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------- задача ----------

private val categories = listOf(
    "personal" to R.string.cat_personal, "work" to R.string.cat_work, "health" to R.string.cat_health,
    "home" to R.string.cat_home, "study" to R.string.cat_study
)

@Composable
fun TaskDialog(vm: MainViewModel, task: TaskItem?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = task == null || task.id == 0L
    var title by remember { mutableStateOf(task?.title ?: "") }
    var desc by remember { mutableStateOf(task?.description ?: "") }
    var date by remember {
        mutableStateOf(task?.due?.toLocalDate() ?: vm.selectedDate)
    }
    var time by remember {
        mutableStateOf(task?.due?.toLocalTime()?.withSecond(0)?.withNano(0)
            ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0))
    }
    var prio by remember { mutableStateOf(task?.priority ?: 1) }
    var cat by remember { mutableStateOf(task?.category ?: "") }
    var remind by remember { mutableStateOf(task?.remindEnabled ?: true) }
    var warn by remember { mutableStateOf(task?.warnMinutes ?: -1) }
    val rep = remember { RepeatState(task?.repeat ?: RepeatKind.None, task?.repeatInterval ?: 1, task?.repeatDays ?: 0) }
    var err by remember { mutableStateOf<String?>(null) }
    var warnMenu by remember { mutableStateOf(false) }

    val warnOptions = listOf(
        -1 to R.string.warn_default, 0 to R.string.warn_off, 30 to R.string.warn_30, 60 to R.string.warn_1h,
        90 to R.string.warn_1h30, 120 to R.string.warn_2h, 180 to R.string.warn_3h
    )

    AppDialog(
        stringResource(if (isNew) R.string.dlg_new_task else R.string.dlg_edit_task), onDismiss,
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            val e = if (title.isBlank()) ctx.getString(R.string.err_title_empty) else rep.error(ctx)
            if (e != null) err = e else {
                val t = if (isNew) (task ?: TaskItem()) else task!!
                val newDue = LocalDateTime.of(date, time)
                val changed = isNew || t.due != newDue || t.warnMinutes != warn || t.remindEnabled != remind
                t.title = title.trim(); t.description = desc.trim(); t.due = newDue
                t.priority = prio; t.category = cat; t.remindEnabled = remind; t.warnMinutes = warn
                t.repeat = rep.kind; t.repeatInterval = rep.intervalValue(); t.repeatDays = rep.days
                TaskOps.save(ctx, t, dueChanged = changed)
                vm.selectDate(newDue.toLocalDate())
                vm.selectedId = t.id
                onDismiss()
            }
        },
        dismissLabel = stringResource(R.string.btn_cancel)
    ) {
        OutlinedTextField(
            value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.lbl_title)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = desc, onValueChange = { desc = it }, label = { Text(stringResource(R.string.lbl_description)) },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        SectionLabel(stringResource(R.string.lbl_date_time))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dateMedium(date)) }
            OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }) { Text(timeLabel(time)) }
        }
        SectionLabel(stringResource(R.string.lbl_priority))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to R.string.prio_low, 1 to R.string.prio_normal, 2 to R.string.prio_high).forEach { (v, r) ->
                FilterChip(selected = prio == v, onClick = { prio = v }, label = { Text(stringResource(r)) })
            }
        }
        SectionLabel(stringResource(R.string.lbl_category))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = cat == "", onClick = { cat = "" }, label = { Text("—") })
            categories.forEach { (k, r) ->
                FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(stringResource(r)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(stringResource(R.string.lbl_remind), remind) { remind = it }
        if (remind) {
            SectionLabel(stringResource(R.string.lbl_warn))
            Box {
                OutlinedButton(onClick = { warnMenu = true }) {
                    Text(stringResource(warnOptions.first { it.first == warn }.second))
                }
                DropdownMenu(expanded = warnMenu, onDismissRequest = { warnMenu = false }) {
                    warnOptions.forEach { (v, r) ->
                        DropdownMenuItem(text = { Text(stringResource(r)) }, onClick = { warn = v; warnMenu = false })
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.lbl_repeat))
        RepeatEditor(rep)
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

// ---------- карточка задачи (из уведомления / капельки) ----------

@Composable
fun CardDialog(vm: MainViewModel, id: Long, onEdit: (TaskItem) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val t = vm.find(id)
    if (t == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val upcoming = !t.isDone && t.due.isAfter(vm.now)
    AppDialog(
        stringResource(if (upcoming) R.string.droplet_title else R.string.due_title), onDismiss,
        confirmLabel = stringResource(R.string.tip_edit), onConfirm = { onEdit(t) }
    ) {
        Text(t.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(Fmt.dateFull(t.due.toLocalDate()) + ", " + Fmt.time(t.due))
        if (t.description.isNotBlank()) Text(t.description, modifier = Modifier.padding(top = 6.dp))
        if (upcoming) {
            Text(
                App.prefs.warnText.ifBlank { stringResource(R.string.droplet_body) },
                modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!t.isDone) {
            TextButton(onClick = { TaskOps.complete(ctx, t); Notifier.cancel(ctx, t.id); onDismiss() }) {
                Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.btn_done))
            }
            Text(stringResource(R.string.btn_postpone), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 30, 60).forEach { m ->
                    OutlinedButton(onClick = { TaskOps.snooze(ctx, t, m); Notifier.cancel(ctx, t.id); onDismiss() }) { Text("$m") }
                }
            }
        }
    }
}

// ---------- звук ----------

@Composable
fun SoundDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = App.prefs
    var on by remember { mutableStateOf(p.soundEnabled) }
    var id by remember { mutableStateOf(p.soundId) }
    var repeat by remember { mutableStateOf(p.repeatMinutes.toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    AppDialog(
        stringResource(R.string.tip_sound), onDismiss,
        confirmLabel = stringResource(R.string.btn_save),
        onConfirm = {
            val r = repeat.toIntOrNull()
            if (r == null || r !in 1..1440) err = ctx.getString(R.string.err_repeat_min) else {
                p.soundEnabled = on; p.soundId = id; p.repeatMinutes = r
                Scheduler.schedule(ctx); Bus.bump(); onDismiss()
            }
        },
        dismissLabel = stringResource(R.string.btn_cancel)
    ) {
        SwitchRow(stringResource(R.string.set_sound_on), on) { on = it }
        SectionLabel(stringResource(R.string.set_sound_pick))
        listOf("soft" to R.string.sound_soft, "bell" to R.string.sound_bell, "marimba" to R.string.sound_marimba).forEach { (k, r) ->
            RadioRow(stringResource(r), id == k) { id = k; p.soundId = k }
        }
        OutlinedButton(onClick = { p.soundEnabled = on; p.soundId = id; Notifier.test(ctx) }) {
            Text(stringResource(R.string.btn_test_sound))
        }
        Text(stringResource(R.string.sound_volume_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        SectionLabel(stringResource(R.string.set_repeat_min))
        OutlinedTextField(
            value = repeat, onValueChange = { repeat = it.filter(Char::isDigit).take(4) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp)
        )
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------- тихий режим ----------

@Composable
fun QuietDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    fun setFor(minutes: Long) {
        App.prefs.quietUntil = System.currentTimeMillis() + minutes * 60_000
        vm.reload(); onDismiss()
    }
    val until = App.prefs.quietUntil
    AppDialog(stringResource(R.string.quiet_title), onDismiss) {
        Text(stringResource(R.string.quiet_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (System.currentTimeMillis() < until) {
            val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(until), java.time.ZoneId.systemDefault())
            Text(stringResource(R.string.quiet_until, Fmt.time(dt)), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        listOf(30L to R.string.quiet_30, 60L to R.string.quiet_60, 120L to R.string.quiet_120, 240L to R.string.quiet_240).forEach { (m, r) ->
            TextButton(onClick = { setFor(m) }) { Text(stringResource(r)) }
        }
        TextButton(onClick = {
            var t = LocalDate.now().atTime(8, 0)
            if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
            App.prefs.quietUntil = t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            vm.reload(); onDismiss()
        }) { Text(stringResource(R.string.quiet_morning)) }
        TextButton(onClick = { App.prefs.quietUntil = 0; vm.reload(); onDismiss() }) { Text(stringResource(R.string.quiet_off)) }
    }
}

// ---------- погода ----------

@Composable
fun WeatherDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var city by remember { mutableStateOf(App.prefs.city) }
    AppDialog(
        stringResource(R.string.set_weather), onDismiss,
        confirmLabel = stringResource(R.string.btn_refresh_weather),
        onConfirm = {
            App.prefs.city = city.trim()
            vm.weatherNotFound = false
            vm.refreshWeather(true)
            onDismiss()
        },
        dismissLabel = stringResource(R.string.btn_cancel)
    ) {
        OutlinedTextField(
            value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.set_city)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        val w = vm.weather
        if (w != null) {
            Text(
                w.city + ": " + w.temp + "°C, " + stringResource(Weather.wxRes(w.code)),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (vm.weatherOffline) Text(stringResource(R.string.weather_offline_note), color = MaterialTheme.colorScheme.error)
        if (vm.weatherNotFound) Text(stringResource(R.string.weather_not_found), color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(R.string.set_weather_hint), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ---------- история ----------

@Composable
fun HistoryDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val now = vm.now
    val list: List<TaskItem> = when (tab) {
        0 -> vm.tasks.filter { it.isDone && !it.isArchived }.sortedByDescending { it.completedAt ?: it.due }
        1 -> vm.tasks.filter { !it.isDone && !it.isArchived && it.due.isBefore(now) }.sortedByDescending { it.due }
        else -> vm.tasks.filter { it.isArchived }.sortedByDescending { it.due }
    }
    AppDialog(stringResource(R.string.hist_title), onDismiss) {
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.hist_done, R.string.hist_overdue, R.string.hist_deleted).forEachIndexed { i, r ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r), maxLines = 1) })
            }
        }
        if (list.isEmpty()) Text(stringResource(R.string.no_tasks), modifier = Modifier.padding(16.dp))
        list.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold)
                    val sub = if (tab == 0 && t.completedAt != null)
                        ctx.getString(R.string.hist_completed, Fmt.dateTime(t.completedAt!!))
                    else Fmt.dateTime(t.due)
                    Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tab == 1) {
                    IconButton(onClick = { TaskOps.complete(ctx, t) }) { Icon(Icons.Filled.Check, stringResource(R.string.btn_done)) }
                } else {
                    IconButton(onClick = { TaskOps.restore(ctx, t) }) { Icon(Icons.Filled.Restore, stringResource(R.string.btn_restore)) }
                }
                IconButton(onClick = { TaskOps.deleteForever(ctx, t) }) { Icon(Icons.Filled.DeleteForever, stringResource(R.string.btn_delete_forever)) }
            }
        }
        if (list.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.btn_clear_history)) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.msg_confirm_clear, list.size.toString())) },
            confirmButton = {
                TextButton(onClick = { list.forEach { App.db.delete(it.id) }; Bus.bump(); confirmClear = false }) { Text(stringResource(R.string.btn_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.btn_no)) } }
        )
    }
}

// ---------- язык ----------

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val cur = Fmt.locale().language
    AppDialog(stringResource(R.string.set_language_ui), onDismiss) {
        listOf("ru" to "Русский", "en" to "English", "de" to "Deutsch", "fr" to "Français").forEach { (tag, name) ->
            RadioRow(name, cur == tag) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                onDismiss()
            }
        }
        Text(
            stringResource(R.string.set_language_hint), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ---------- настройки ----------

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = App.prefs
    var theme by remember { mutableStateOf(p.theme) }
    var tts by remember { mutableStateOf(p.ttsEnabled) }
    var warnOn by remember { mutableStateOf(p.warnEnabled) }
    var warnMin by remember { mutableStateOf(p.warnMinutes) }
    var warnText by remember { mutableStateOf(p.warnText) }
    var qhOn by remember { mutableStateOf(p.quietHoursOn) }
    var qFrom by remember { mutableStateOf(p.quietFrom) }
    var qTo by remember { mutableStateOf(p.quietTo) }
    var confirmRestore by remember { mutableStateOf(false) }

    val msgSaved = stringResource(R.string.backup_saved)
    val msgFail = stringResource(R.string.backup_failed)
    val msgRestored = stringResource(R.string.restore_done)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) Toast.makeText(ctx, if (App.db.exportTo(uri)) msgSaved else msgFail, Toast.LENGTH_LONG).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = App.db.importFrom(uri)
            if (ok) { Scheduler.schedule(ctx); Bus.bump() }
            Toast.makeText(ctx, if (ok) msgRestored else msgFail, Toast.LENGTH_LONG).show()
        }
    }

    fun parse(s: String, d: LocalTime) = try { LocalTime.parse(s) } catch (e: Exception) { d }

    AppDialog(stringResource(R.string.set_title), onDismiss) {
        SectionLabel(stringResource(R.string.set_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("system" to R.string.theme_system_a, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (k, r) ->
                FilterChip(selected = theme == k, onClick = {
                    theme = k; p.theme = k
                    AppCompatDelegate.setDefaultNightMode(p.nightMode())
                }, label = { Text(stringResource(r)) })
            }
        }

        SectionLabel(stringResource(R.string.set_voice))
        SwitchRow(stringResource(R.string.set_tts_on), tts) { tts = it; p.ttsEnabled = it }

        SectionLabel(stringResource(R.string.set_droplet))
        SwitchRow(stringResource(R.string.set_droplet_on), warnOn) { warnOn = it; p.warnEnabled = it; Scheduler.schedule(ctx) }
        if (warnOn) {
            Text(stringResource(R.string.set_droplet_before), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(30 to R.string.warn_30, 60 to R.string.warn_1h, 90 to R.string.warn_1h30, 120 to R.string.warn_2h, 180 to R.string.warn_3h).forEach { (m, r) ->
                    FilterChip(selected = warnMin == m, onClick = { warnMin = m; p.warnMinutes = m; Scheduler.schedule(ctx) }, label = { Text(stringResource(r)) })
                }
            }
            OutlinedTextField(
                value = warnText, onValueChange = { warnText = it; p.warnText = it },
                label = { Text(stringResource(R.string.set_droplet_text)) },
                placeholder = { Text(stringResource(R.string.droplet_body)) },
                minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            TextButton(onClick = { warnText = ""; p.warnText = "" }) { Text(stringResource(R.string.btn_reset_text)) }
        }

        SectionLabel(stringResource(R.string.set_quiet))
        SwitchRow(stringResource(R.string.set_quiet_on), qhOn) { qhOn = it; p.quietHoursOn = it; vm.reload() }
        if (qhOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickTime(ctx, parse(qFrom, LocalTime.of(22, 0))) { qFrom = it.toString(); p.quietFrom = it.toString(); vm.reload() } }) {
                    Text(stringResource(R.string.set_quiet_from).substringBefore(" (") + " " + timeLabel(parse(qFrom, LocalTime.of(22, 0))))
                }
                OutlinedButton(onClick = { pickTime(ctx, parse(qTo, LocalTime.of(8, 0))) { qTo = it.toString(); p.quietTo = it.toString(); vm.reload() } }) {
                    Text(stringResource(R.string.set_quiet_to).substringBefore(" (") + " " + timeLabel(parse(qTo, LocalTime.of(8, 0))))
                }
            }
            Text(stringResource(R.string.set_quiet_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel(stringResource(R.string.set_backup))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("planner-backup.db") }) { Text(stringResource(R.string.btn_backup_now)) }
        }
        OutlinedButton(onClick = { confirmRestore = true }) { Text(stringResource(R.string.btn_restore_backup)) }

        SectionLabel(stringResource(R.string.set_version))
        Text("1.0.0")
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            text = { Text(stringResource(R.string.msg_confirm_restore)) },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.btn_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.btn_no)) } }
        )
    }
}
